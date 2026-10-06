package org.jtools.mythostory.desktop

import kotlinx.coroutines.runBlocking
import org.jtools.mythostory.backend.Platform
import org.jtools.mythostory.backend.startServer
import java.awt.Desktop
import java.net.URI
import kotlin.system.exitProcess

private const val USAGE = """Usage: mythostory [--port N] [--host H] [--no-browser]
  --port N       the port to serve on (default 8080; 0 for any free one)
  --host H       the interface to listen on (default 127.0.0.1: this computer only)
  --no-browser   do not open the browser on the page"""

/** The desktop program: the server on localhost, the browser as its window. */
fun main(args: Array<String>) {
    var port = 8080
    var host = "127.0.0.1"
    var browser = true
    val rest = args.iterator()
    while (rest.hasNext()) {
        when (val arg = rest.next()) {
            "--port" -> port = rest.valueOf(arg).toIntOrNull() ?: usage("--port takes a number")
            "--host" -> host = rest.valueOf(arg)
            "--no-browser" -> browser = false
            "--help", "-h" -> { println(USAGE); return }
            else -> usage("unknown argument: $arg")
        }
    }

    val server = startServer(host, port, Platform.DESKTOP)
    val actualPort = runBlocking { server.engine.resolvedConnectors().first().port }
    val url = "http://${if (host == "0.0.0.0") "localhost" else host}:$actualPort/"
    println("MythoStory is ready on $url; Ctrl+C to stop")
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
