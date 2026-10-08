package org.jtools.mythostory.backend

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jtools.mythostory.core.Catalog
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MythoStoryServerTest {
    /** A synthesizer that speaks French only, with two voices, its "audio" the voice and the text it was given. */
    private val speech = object : SpeechSynthesizer {
        override val voices = listOf(Voice("fr-siwis", "fr", "Siwis"), Voice("fr-pierre", "fr", "Pierre"))
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
                Component("ktor-server-core", "3.6.0", "Apache-2.0", "https://github.com/ktorio/ktor", artifact = "io.ktor:ktor-server-core-jvm"),
                Component("Logback Classic Module", "1.6.5", "EPL-2.0 OR LGPL-2.1-only", "http://logback.qos.ch/logback-classic", artifact = "ch.qos.logback:logback-classic"),
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
        assertEquals("RIFF fr-siwis Bonjour Zeus", spoken.bodyAsText(), "the language's default voice")
        assertEquals("RIFF fr-pierre Bonjour", client.get("/api/speech?lang=fr&voice=fr-pierre&text=Bonjour").bodyAsText())
        assertEquals("RIFF fr-siwis Bonjour", client.get("/api/speech?lang=fr&voice=en-alba&text=Bonjour").bodyAsText(), "not a French voice")
        assertEquals(HttpStatusCode.NotFound, client.get("/api/speech?lang=en&text=Hello").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/speech?lang=fr&text=" + "a".repeat(SPEECH_MAX_LENGTH + 1)).status)
        assertContains(client.get("/api/info").bodyAsText(), "\"voices\":[{\"id\":\"fr-siwis\",\"language\":\"fr\",\"name\":\"Siwis\"},")
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
}
