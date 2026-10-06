package org.jtools.mythostory.backend

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.jtools.mythostory.core.JsonFileStoryRepository
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class MythoStoryServerTest {
    @TempDir
    lateinit var dir: File

    private fun test(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = testApplication {
        application { mythoStory(JsonFileStoryRepository(File(dir, "stories.json")), Platform.ANDROID) }
        block()
    }

    @Test
    fun `the info names the version Maven built and the platform`() = test {
        val body = client.get("/api/info").bodyAsText()
        assertContains(body, "\"name\":\"MythoStory\"")
        assertContains(body, "\"platform\":\"android\"")
        assertContains(body, "\"version\":\"0.") // filtered from the pom, never the placeholder
    }

    @Test
    fun `a story posted is listed`() = test {
        val created = client.post("/api/stories") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"The Iliad","summary":"Wrath"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        assertContains(client.get("/api/stories").bodyAsText(), "\"title\":\"The Iliad\"")
    }

    @Test
    fun `a story without a title is a bad request`() = test {
        val response = client.post("/api/stories") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"  "}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
