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
    private fun test(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { mythoStory(Catalog.load(), Platform.ANDROID) }
        block()
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
