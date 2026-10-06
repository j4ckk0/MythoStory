package org.jtools.mythostory.desktop

import kotlinx.coroutines.runBlocking
import org.jtools.mythostory.backend.Platform
import org.jtools.mythostory.backend.startServer
import java.awt.Desktop
import java.net.URI
import io.ktor.server.engine.EmbeddedServer
import kotlin.concurrent.thread
import kotlin.system.exitProcess

private const val USAGE = """Usage: mythostory [--port N] [--host H] [--no-browser] [--keep-running]
  --port N         the port to serve on (default 8080; 0 for any free one)
  --host H         the interface to listen on (default 127.0.0.1: this computer only)
  --no-browser     do not open the browser on the page
  --keep-running   keep serving once the last page is closed (by default, MythoStory then stops)"""

/**
 * The desktop program: the server on localhost, the browser as its window — and like a window's program, it stops
 * when its last page is closed (unless --keep-running): the browser is its only way out, there is no other to quit it.
 */
fun main(args: Array<String>) {
    var port = 8080
    var host = "127.0.0.1"
    var browser = true
    var keepRunning = false
    val rest = args.iterator()
    while (rest.hasNext()) {
        when (val arg = rest.next()) {
            "--port" -> port = rest.valueOf(arg).toIntOrNull() ?: usage("--port takes a number")
            "--host" -> host = rest.valueOf(arg)
            "--no-browser" -> browser = false
            "--keep-running" -> keepRunning = true
            "--help", "-h" -> { println(USAGE); return }
            else -> usage("unknown argument: $arg")
        }
    }

    lateinit var server: EmbeddedServer<*, *>
    val onLastPageClosed = {
        println("The last MythoStory page is closed: MythoStory stops.")
        // not from the server's own threads, which stop() waits for
        thread(name = "mythostory-stop") {
            server.stop(500, 2000)
            exitProcess(0)
        }
        Unit
    }
    server = startServer(host, port, Platform.DESKTOP, onLastPageClosed = if (keepRunning) null else onLastPageClosed)
    val actualPort = runBlocking { server.engine.resolvedConnectors().first().port }
    val url = "http://${if (host == "0.0.0.0") "localhost" else host}:$actualPort/"
    println("MythoStory is ready on $url; " + if (keepRunning) "Ctrl+C to stop" else "it stops when its last page is closed (or Ctrl+C)")
    if (browser && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI(url))
    }
    Runtime.getRuntime().addShutdownHook(Thread { server.stop(500, 2000) })
    Thread.currentThread().join()
}

private fun Iterator<String>.valueOf(option: String): String = if (hasNext()) next() else usage("$option takes a value")

private fun usage(message: String): Nothing {
    System.err.println(message)
    System.err.println(USAGE)
    exitProcess(2)
}
