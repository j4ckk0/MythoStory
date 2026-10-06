package org.jtools.mythostory.backend

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import org.jtools.mythostory.core.JsonFileStoryRepository
import org.jtools.mythostory.core.NewStory
import org.jtools.mythostory.core.StoryRepository
import java.io.File
import java.util.Properties

/** What the server says of itself (frontend: story.ts, Info); [platform] tells the desktop from Android. */
@Serializable
data class Info(val name: String, val version: String, val platform: String)

/** The platforms the server runs on: the page shows which. */
enum class Platform(val label: String) { DESKTOP("desktop"), ANDROID("android") }

/**
 * Starts the server: the desktop's main and the Android app call this alike, each with its own data directory.
 * Port 0 takes a free port, which `engine.resolvedConnectors()` then tells.
 */
fun startServer(
    host: String,
    port: Int,
    dataDir: File,
    platform: Platform,
): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> =
    embeddedServer(CIO, host = host, port = port) {
        mythoStory(JsonFileStoryRepository(File(dataDir, "stories.json")), platform)
    }.start(wait = false)

/** The application: the API under /api, and the page — the frontend's static/, index.html for any other path. */
fun Application.mythoStory(stories: StoryRepository, platform: Platform) {
    val info = readInfo(platform)
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, e -> call.respond(HttpStatusCode.BadRequest, e.message ?: "bad request") }
    }
    routing {
        route("/api") {
            get("/info") { call.respond(info) }
            get("/stories") { call.respond(stories.all()) }
            post("/stories") { call.respond(HttpStatusCode.Created, stories.add(call.receive<NewStory>())) }
        }
        // the Angular page; its routes are the page's business, so any path it does not serve gets index.html
        staticResources("/", "static") {
            default("index.html")
        }
    }
}

private fun readInfo(platform: Platform): Info {
    val props = Properties()
    Info::class.java.getResourceAsStream("/mythostory.properties")?.use(props::load)
    return Info(props.getProperty("name", "MythoStory"), props.getProperty("version", "dev"), platform.label)
}
