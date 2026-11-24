package org.jtools.mythostory.backend

import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
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
import io.ktor.server.request.host
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.path
import io.ktor.server.request.port
import io.ktor.server.response.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import java.net.InetAddress
import java.net.URI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jtools.mythostory.core.Catalog
import org.jtools.mythostory.core.I18n
import java.util.Properties

/**
 * What the server says of itself (frontend: catalog.ts, Info): [platform] tells the desktop from Android, [languages]
 * are the i18n languages the catalog's texts are in, [voices] those it reads aloud with (/api/speech), each language's
 * default first.
 */
@Serializable
data class Info(val name: String, val version: String, val platform: String, val languages: List<String>, val voices: List<Voice>)

/** The longest text /api/speech reads: a sentence, or a long one — the page sends one at a time. */
const val SPEECH_MAX_LENGTH = 1000

/** The platforms the server runs on: the page shows which. */
enum class Platform(val label: String) { DESKTOP("desktop"), ANDROID("android") }

/**
 * How often the server pings a page's session socket, and how long it waits for the answer before taking the page
 * for gone. A browser that crashes closes its sockets at once, which the server notices at once: the pings are for
 * the rarer page that hangs or loses its network, so they are patient — browsers answer them even in background tabs.
 */
val SESSION_PING: Duration = 5.seconds
val SESSION_TIMEOUT: Duration = 30.seconds

/** The names a request from this computer may give the server in its Host header, when it listens on loopback only. */
private val LOOPBACK_NAMES = setOf("127.0.0.1", "localhost", "::1", "[::1]")

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
    speech: SpeechSynthesizer? = null,
): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> =
    embeddedServer(CIO, host = host, port = port) {
        val loopbackOnly = InetAddress.getByName(host).isLoopbackAddress
        mythoStory(Catalog.load(), platform, onLastPageClosed?.let { PageSessions(this, grace, it) }, speech, loopbackOnly)
    }.start(wait = false)

/**
 * The application: the API under /api, and the page — the frontend's static/, index.html for any other path.
 * The catalog is served whole, every language in it: the page builds its own indexes and switches language without
 * asking the server again. [loopbackOnly]: the server listens on this computer's loopback only (the default, the
 * Android app's too), so a request naming any other host is refused (see [refuseForeignRequests]).
 */
fun Application.mythoStory(
    catalog: Catalog,
    platform: Platform,
    sessions: PageSessions? = null,
    speech: SpeechSynthesizer? = null,
    loopbackOnly: Boolean = true,
) {
    val info = readInfo(platform, speech)
    val licences = Licences.load()
    refuseForeignRequests(loopbackOnly)
    // absent rather than null: what the data files leave out, the page receives left out (a story's generation…)
    install(ContentNegotiation) { json(Json { explicitNulls = false }) }
    // the catalog weighs megabytes, a fifth of that gzipped
    install(Compression) {
        gzip()
        excludeContentType(ContentType.Audio.Any)
    }
    install(WebSockets) {
        pingPeriodMillis = SESSION_PING.inWholeMilliseconds
        timeoutMillis = SESSION_TIMEOUT.inWholeMilliseconds
    }
    routing {
        route("/api") {
            get("/info") { call.respond(info) }
            get("/catalog") { call.respond(catalog) }
            // what the application carries, and under which licences: the help page lists them
            get("/licences") { call.respond(licences) }
            // a sentence read aloud: ?lang=fr&voice=fr-pierre&text=…, as WAV — by the language's default voice when the
            // voice is not one of the language's; the same text always sounds the same, so browsers keep it
            get("/speech") {
                val language = call.request.queryParameters["lang"]
                val asked = call.request.queryParameters["voice"]
                val text = call.request.queryParameters["text"]?.trim()
                val ofLanguage = speech?.voices.orEmpty().filter { it.language == language }
                val voice = ofLanguage.firstOrNull { it.id == asked } ?: ofLanguage.firstOrNull()
                when {
                    speech == null || voice == null -> call.respond(HttpStatusCode.NotFound, "no voice for $language")
                    text.isNullOrEmpty() || text.length > SPEECH_MAX_LENGTH ->
                        call.respond(HttpStatusCode.BadRequest, "a text of 1 to $SPEECH_MAX_LENGTH characters")
                    else -> {
                        val wav = withContext(Dispatchers.IO) { speech.synthesize(voice.id, text) }
                        call.response.header(HttpHeaders.CacheControl, "private, max-age=86400")
                        call.respondBytes(wav, ContentType("audio", "wav"))
                    }
                }
            }
            // each open page holds this socket (frontend: SessionService), whose first message is its session's id; the
            // page closes it, and sends a beacon, as it goes away; a crashed browser's socket closes by itself. A
            // WebSocket rather than a stream of server-sent events: browsers allow six HTTP connections per host, all
            // tabs together, and a stream held by every tab would leave the seventh tab without one.
            webSocket("/session") {
                val id = sessions?.opened() ?: ""
                try {
                    send(Frame.Text(id))
                    for (frame in incoming) Unit // the page sends nothing: this waits for the socket to close
                } finally {
                    sessions?.closed(id)
                }
            }
            post("/session/{id}/closed") {
                sessions?.closed(call.parameters["id"]!!)
                call.respond(HttpStatusCode.NoContent)
            }
        }
        // the Angular page, from the frontend's static/. Its routes (/stories, /characters/heracles…) are the page's
        // business: a path that is no file gets index.html — unless it is under /api or names a file (a dot in its last
        // segment: a chunk of a previous version, say), which get 404 rather than a page where JSON or a script was
        // expected. index.html is never cached: after an upgrade, it names chunks the old one did not.
        val page = Application::class.java.getResource("/static/index.html")?.readBytes()
        staticResources("/", "static") {
            cacheControl { url -> if (url.path.endsWith("/index.html")) listOf(CacheControl.NoCache(null)) else emptyList() }
            fallback { path, call ->
                val file = path.substringAfterLast('/').contains('.')
                if (page == null || path == "api" || path.startsWith("api/") || file) call.respond(HttpStatusCode.NotFound)
                else {
                    call.response.header(HttpHeaders.CacheControl, "no-cache")
                    call.respondBytes(page, ContentType.Text.Html.withCharset(Charsets.UTF_8))
                }
            }
        }
    }
}

/**
 * Refuses, with 403, what another website could have a browser ask of this server, which listens on the user's own
 * computer or phone:
 * - when it listens on loopback only, a Host header naming another host: a website whose name was made to resolve to
 *   127.0.0.1 (DNS rebinding) would otherwise read the API as if it were the page;
 * - under /api, a request a browser marks as sent from another site (Sec-Fetch-Site), or whose Origin is not the
 *   server's own: another site's page could otherwise stop the desktop server (the session's beacon, its socket,
 *   which no same-origin rule protects) or keep Piper busy.
 * Requests that carry neither header — curl, the scripts' probes — are not a browser's, and pass. Links from another
 * site to the page itself stay allowed: only the API is guarded against them.
 */
private fun Application.refuseForeignRequests(loopbackOnly: Boolean) {
    intercept(ApplicationCallPipeline.Plugins) {
        val request = call.request
        val foreignHost = loopbackOnly && request.host() !in LOOPBACK_NAMES
        val api = request.path().startsWith("/api/")
        val site = request.headers["Sec-Fetch-Site"]
        val origin = request.headers[HttpHeaders.Origin]
        val crossSite = api && site != null && site != "same-origin" && site != "none"
        val foreignOrigin = api && origin != null && !request.isFrom(origin)
        if (foreignHost || crossSite || foreignOrigin) {
            call.respond(HttpStatusCode.Forbidden, "refused: not this application's page")
            finish()
        }
    }
}

/** Whether an Origin header ("http://127.0.0.1:8080") names the host and port this request was sent to. */
private fun ApplicationRequest.isFrom(origin: String): Boolean = runCatching {
    val uri = URI(origin)
    val port = if (uri.port != -1) uri.port else if (uri.scheme == "https") 443 else 80
    uri.host.equals(host(), ignoreCase = true) && port == port()
}.getOrDefault(false)

private fun readInfo(platform: Platform, speech: SpeechSynthesizer?): Info {
    val props = Properties()
    Info::class.java.getResourceAsStream("/mythostory.properties")?.use(props::load)
    val voices = speech?.voices.orEmpty().filter { it.language in I18n.LANGUAGES }
    return Info(props.getProperty("name", "MythoStory"), props.getProperty("version", "dev"), platform.label, I18n.LANGUAGES, voices)
}
