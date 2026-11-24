package org.jtools.mythostory.backend

import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.jtools.mythostory.core.Catalog
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class MythoStoryServerTest {
    /** A synthesizer that speaks French only, with two voices, its "audio" the voice and the text it was given. */
    private val speech = object : SpeechSynthesizer {
        override val voices = listOf(Voice("fr-claire", "fr", "Claire"), Voice("fr-antoine", "fr", "Antoine"))
        override fun synthesize(voice: String, text: String) = "RIFF $voice $text".toByteArray()
    }

    private fun test(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { mythoStory(Catalog.load(), Platform.ANDROID, speech = speech) }
        block()
    }

    @Test
    fun `the license plugin's lines become components, their licences as SPDX identifiers`() {
        val libraries = Licences.libraries(
            "Lists of 2 third-party dependencies.\n" +
                "     (The Apache Software License, Version 2.0) ktor-server-core (io.ktor:ktor-server-core-jvm:3.6.0 - https://github.com/ktorio/ktor)\n" +
                "     (EPL-2.0) (LGPL-2.1-only) Logback Classic Module (ch.qos.logback:logback-classic:1.6.5 - http://logback.qos.ch/logback-classic)\n",
        )
        assertEquals(
            listOf(
                ThirdPartyComponent("ktor-server-core", "3.6.0", "Apache-2.0", "https://github.com/ktorio/ktor", artifact = "io.ktor:ktor-server-core-jvm"),
                ThirdPartyComponent("Logback Classic Module", "1.6.5", "EPL-2.0 OR LGPL-2.1-only", "http://logback.qos.ch/logback-classic", artifact = "ch.qos.logback:logback-classic"),
            ),
            libraries,
        )
        assertEquals("MIT", Licences.spdx("The MIT License"))
    }

    @Test
    fun `a sentence is read aloud in a language there is a voice for, and only then`() = test {
        val spoken = client.get("/api/speech?lang=fr&text=Bonjour%20Zeus")
        assertEquals(HttpStatusCode.OK, spoken.status)
        assertEquals("audio/wav", spoken.headers[HttpHeaders.ContentType])
        assertEquals("RIFF fr-claire Bonjour Zeus", spoken.bodyAsText(), "the language's default voice")
        assertEquals("RIFF fr-antoine Bonjour", client.get("/api/speech?lang=fr&voice=fr-antoine&text=Bonjour").bodyAsText())
        assertEquals("RIFF fr-claire Bonjour", client.get("/api/speech?lang=fr&voice=en-cori&text=Bonjour").bodyAsText(), "not a French voice")
        assertEquals(HttpStatusCode.NotFound, client.get("/api/speech?lang=en&text=Hello").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/speech?lang=fr&text=" + "a".repeat(SPEECH_MAX_LENGTH + 1)).status)
        assertContains(client.get("/api/info").bodyAsText(), "\"voices\":[{\"id\":\"fr-claire\",\"language\":\"fr\",\"name\":\"Claire\"},")
    }

    @Test
    fun `the info names the version Maven built, the platform and the i18n languages`() = test {
        val body = client.get("/api/info").bodyAsText()
        assertContains(body, "\"name\":\"MythoStory\"")
        assertContains(body, "\"platform\":\"android\"")
        assertContains(body, "\"languages\":[\"en\",\"fr\"]")
        assertContains(body, "\"version\":\"0.") // filtered from the pom, never the placeholder
    }

    @Test
    fun `the catalog is served whole, in every language`() = test {
        val response = client.get("/api/catalog")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "\"id\":\"heracles\"")
        assertContains(body, "\"fr\":\"Héraclès\"")
        assertContains(body, "\"id\":\"labours-of-heracles\"")
        assertContains(body, "\"kind\":\"creature\"")
        assertFalse(body.contains(":null"), "what the data leaves out is left out, never null")
    }

    @Test
    fun `the catalog is gzipped for whoever accepts it`() = test {
        val response = client.get("/api/catalog") { header(HttpHeaders.AcceptEncoding, "gzip") }
        assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding])
    }

    @Test
    fun `a page holds a session socket, named by its first message, and the server hears when the last one closes`() {
        val lastClosed = CompletableDeferred<Unit>()
        testApplication {
            application {
                mythoStory(Catalog.load(), Platform.DESKTOP, PageSessions(this, 100.milliseconds) { lastClosed.complete(Unit) })
            }
            val client = createClient { install(WebSockets) }
            client.webSocket("/api/session") {
                val id = (incoming.receive() as Frame.Text).readText()
                assertEquals(36, id.length, "a UUID: $id")
                assertFalse(lastClosed.isCompleted, "open while the page is")
            }
            withTimeout(5.seconds) { lastClosed.await() }
        }
    }

    @Test
    fun `another website cannot use the API through the user's browser`() = test {
        // a browser marks what another site's page asks for
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/speech?lang=fr&text=Bonjour") { header("Sec-Fetch-Site", "cross-site") }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/info") { header("Sec-Fetch-Site", "same-site") }.status)
        assertEquals(HttpStatusCode.Forbidden, client.post("/api/session/x/closed") { header(HttpHeaders.Origin, "https://example.org") }.status)
        // the page itself, and what the user types in the address bar
        assertEquals(HttpStatusCode.OK, client.get("/api/info") { header("Sec-Fetch-Site", "same-origin") }.status)
        assertEquals(HttpStatusCode.OK, client.get("/api/info") { header("Sec-Fetch-Site", "none") }.status)
        assertEquals(HttpStatusCode.NoContent, client.post("/api/session/x/closed") { header(HttpHeaders.Origin, "http://localhost") }.status)
        // a link from another site to the page is fine: only the API is guarded
        assertNotEquals(HttpStatusCode.Forbidden, client.get("/stories") { header("Sec-Fetch-Site", "cross-site") }.status)
    }

    @Test
    fun `a request naming another host is refused while the server listens on loopback only`() {
        test {
            assertEquals(HttpStatusCode.Forbidden, client.get("/api/info") { header(HttpHeaders.Host, "rebound.example.org") }.status)
            assertEquals(HttpStatusCode.Forbidden, client.get("/") { header(HttpHeaders.Host, "rebound.example.org") }.status)
            assertEquals(HttpStatusCode.OK, client.get("/api/info") { header(HttpHeaders.Host, "127.0.0.1:8080") }.status)
        }
        testApplication {
            application { mythoStory(Catalog.load(), Platform.DESKTOP, loopbackOnly = false) }
            assertEquals(HttpStatusCode.OK, client.get("/api/info") { header(HttpHeaders.Host, "mythostory.lan:8080") }.status, "served to the network on purpose")
        }
    }

    @Test
    fun `the page's routes get index html, never cached, while a missing file or API answers 404`() = test {
        // test resources: a static/index.html standing for the page
        for (route in listOf("/", "/stories", "/characters/heracles", "/map?story=labours")) {
            val response = client.get(route)
            assertEquals(HttpStatusCode.OK, response.status, route)
            assertContains(response.bodyAsText(), "<app-root>", message = route)
            assertEquals("no-cache", response.headers[HttpHeaders.CacheControl], "$route: an upgrade's page names other chunks")
        }
        assertEquals(HttpStatusCode.NotFound, client.get("/api/catalogue").status, "a misspelt API")
        assertEquals(HttpStatusCode.NotFound, client.get("/api").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/main-OLDHASH.js").status, "a chunk of a previous version")
    }
}
