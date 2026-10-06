package org.jtools.mythostory.backend

import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.excludeContentType
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
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

/** How often a page's session stream is written to: how soon a crashed browser's page is noticed. */
val SESSION_PING: Duration = 2.seconds

/**
 * Starts the server: the desktop's main and the Android app call this alike.
 * Port 0 takes a free port, which `engine.resolvedConnectors()` then tells.
 * [onLastPageClosed], when given, is called once the last page has been closed for [grace] (PageSessions): the desktop
 * stops then; the Android app, whose lifecycle is Android's, gives none.
 */
fun startServer(
    host: String,
    port: Int,
    platform: Platform,
    grace: Duration = 5.seconds,
    onLastPageClosed: (() -> Unit)? = null,
): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> =
    embeddedServer(CIO, host = host, port = port) {
        mythoStory(Catalog.load(), platform, onLastPageClosed?.let { PageSessions(this, grace, it) })
    }.start(wait = false)

/**
 * The application: the API under /api, and the page — the frontend's static/, index.html for any other path.
 * The catalog is served whole, every language in it: the page builds its own indexes and switches language without
 * asking the server again.
 */
fun Application.mythoStory(catalog: Catalog, platform: Platform, sessions: PageSessions? = null) {
    val info = readInfo(platform)
    install(ContentNegotiation) { json() }
    // the catalog weighs megabytes since the Wikidata import, a fifth of it gzipped; never the session streams,
    // whose pings gzip would hold back — and with them the news that a page has gone
    install(Compression) {
        gzip()
        excludeContentType(ContentType.Text.EventStream)
    }
    install(SSE)
    routing {
        route("/api") {
            get("/info") { call.respond(info) }
            get("/catalog") { call.respond(catalog) }
            // each open page holds this stream (frontend: SessionService), whose first event is its session's id; the
            // page closes it with a beacon as it goes away, and if it crashed instead, the pings soon fail
            sse("/session") {
                val id = sessions?.opened() ?: ""
                try {
                    send(ServerSentEvent(data = id, event = "session"))
                    while (true) {
                        send(ServerSentEvent(comments = "ping"))
                        delay(SESSION_PING)
                    }
                } finally {
                    sessions?.closed(id)
                }
            }
            post("/session/{id}/closed") {
                sessions?.closed(call.parameters["id"]!!)
                call.respond(HttpStatusCode.NoContent)
            }
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
