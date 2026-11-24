package org.jtools.mythostory.desktop

import kotlinx.coroutines.runBlocking
import org.jtools.mythostory.backend.Platform
import org.jtools.mythostory.backend.startServer
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.util.Properties
import io.ktor.server.engine.EmbeddedServer
import kotlin.concurrent.thread
import kotlin.system.exitProcess

private const val USAGE = """Usage: mythostory [--port N] [--host H] [--no-browser] [--keep-running] [--piper DIR] [--voices DIR]
  --port N         the port to serve on (default: mythostory.ini's, else 8080; 0 for any free one)
  --host H         the interface to listen on (default: mythostory.ini's, else 127.0.0.1: this computer only)
  --no-browser     do not open the browser on the page
  --keep-running   keep serving once the last page is closed (by default, MythoStory then stops)
  --piper DIR      Piper, which reads the stories aloud (default: piper/ next to mythostory.jar)
  --voices DIR     its voices, <model>.onnx and <model>.onnx.json (default: voices/ next to mythostory.jar)
A run from the repository (scripts/linux/run.sh) takes external/piper and external/voices when there is none beside the jar."""

/**
 * The desktop program: the server on localhost, the browser as its window — and like a window's program, it stops
 * when its last page is closed (unless --keep-running): the browser is its only way out, there is no other to quit it.
 */
fun main(args: Array<String>) {
    val home = File(Main::class.java.protectionDomain.codeSource.location.toURI()).parentFile
    val settings = installationSettings(home.resolve("mythostory.ini"))
    var port = settings.getProperty("port")?.trim()?.toIntOrNull() ?: 8080
    var host = settings.getProperty("host")?.trim()?.takeIf { it.isNotEmpty() } ?: "127.0.0.1"
    var browser = true
    var keepRunning = false
    var piper = besideTheJarOrExternal(home, "piper")
    var voices = besideTheJarOrExternal(home, "voices")
    val rest = args.iterator()
    while (rest.hasNext()) {
        when (val arg = rest.next()) {
            "--port" -> port = rest.valueOf(arg).toIntOrNull() ?: usage("--port takes a number")
            "--host" -> host = rest.valueOf(arg)
            "--no-browser" -> browser = false
            "--keep-running" -> keepRunning = true
            "--piper" -> piper = File(rest.valueOf(arg))
            "--voices" -> voices = File(rest.valueOf(arg))
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
    val speech = PiperSynthesizer(PiperSynthesizer.program(piper), voices)
    if (PiperSynthesizer.program(piper).canExecute() && !speech.runs) {
        println("Piper in $piper does not run on this computer: the browser's voices will read the stories, if it has some.")
    } else if (speech.voices.isEmpty()) {
        println("No Piper voice in $piper and $voices: the browser's voices will read the stories, if it has some.")
    }
    Runtime.getRuntime().addShutdownHook(Thread { speech.close() })
    server = startServer(
        host, port, Platform.DESKTOP,
        onLastPageClosed = if (keepRunning) null else onLastPageClosed,
        speech = speech.takeIf { it.voices.isNotEmpty() },
    )
    val actualPort = runBlocking { server.engine.resolvedConnectors().first().port }
    val url = "http://${if (host == "0.0.0.0") "localhost" else host}:$actualPort/"
    println("MythoStory is ready on $url; " + if (keepRunning) "Ctrl+C to stop" else "it stops when its last page is closed (or Ctrl+C)")
    if (browser && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI(url))
    }
    Runtime.getRuntime().addShutdownHook(Thread { server.stop(500, 2000) })
    Thread.currentThread().join()
}

/** The installation's settings, mythostory.ini next to the jar (a distribution's); none when it is not there. */
private fun installationSettings(ini: File): Properties = Properties().apply {
    if (ini.isFile) ini.reader(Charsets.UTF_8).use { load(it) }
}

/** A folder next to the jar, as a distribution has it, else the repository's external/ one: a run from the repository. */
private fun besideTheJarOrExternal(home: File, name: String): File {
    val beside = home.resolve(name)
    val external = File("external", name)
    return if (!beside.isDirectory && external.isDirectory) external else beside
}

private fun Iterator<String>.valueOf(option: String): String = if (hasNext()) next() else usage("$option takes a value")

private fun usage(message: String): Nothing {
    System.err.println(message)
    System.err.println(USAGE)
    exitProcess(2)
}

/** The class the program's jar is found by. */
private object Main
