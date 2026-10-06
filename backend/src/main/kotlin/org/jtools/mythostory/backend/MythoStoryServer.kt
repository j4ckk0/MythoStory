package org.jtools.mythostory.backend

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import org.jtools.mythostory.core.Catalog
import org.jtools.mythostory.core.I18n
import java.util.Properties

/**
 * What the server says of itself (frontend: catalog.ts, Info): [platform] tells the desktop from Android, [languages]
 * are the i18n languages the catalog's texts are in.
 */
@Serializable
data class Info(val name: String, val version: String, val platform: String, val languages: List<String>)

/** The platforms the server runs on: the page shows which. */
enum class Platform(val label: String) { DESKTOP("desktop"), ANDROID("android") }

/**
 * Starts the server: the desktop's main and the Android app call this alike.
 * Port 0 takes a free port, which `engine.resolvedConnectors()` then tells.
 */
fun startServer(host: String, port: Int, platform: Platform): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> =
    embeddedServer(CIO, host = host, port = port) { mythoStory(Catalog.load(), platform) }.start(wait = false)

/**
 * The application: the API under /api, and the page — the frontend's static/, index.html for any other path.
 * The catalog is served whole, every language in it: the page builds its own indexes and switches language without
 * asking the server again.
 */
fun Application.mythoStory(catalog: Catalog, platform: Platform) {
    val info = readInfo(platform)
    install(ContentNegotiation) { json() }
    // the catalog weighs megabytes since the Wikidata import, a fifth of it gzipped
    install(Compression) { gzip() }
    routing {
        route("/api") {
            get("/info") { call.respond(info) }
            get("/catalog") { call.respond(catalog) }
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
    return Info(props.getProperty("name", "MythoStory"), props.getProperty("version", "dev"), platform.label, I18n.LANGUAGES)
}
