package org.jtools.mythostory.desktop

import kotlinx.coroutines.runBlocking
import org.jtools.mythostory.backend.Platform
import org.jtools.mythostory.backend.startServer
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.io.File
import java.net.BindException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.util.Properties
import io.ktor.server.engine.EmbeddedServer
import javax.swing.JOptionPane
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * The port the server listens on when neither the command line nor mythostory.ini names one: MythoStory's own, not
 * 8080, the default of so many servers (JToolsFramework: "Ports"). The root pom's jtools.app.port, mythostory.ini,
 * and the page's development proxy say the same; DesktopSettingsTest checks they agree.
 */
const val DEFAULT_PORT = 8090

private const val USAGE = """Usage: mythostory [--port N] [--host H] [--no-browser] [--keep-alive] [--piper DIR] [--voices DIR]
  --port N         the port to serve on (default: mythostory.ini's, else $DEFAULT_PORT; 0 for any free one)
  --host H         the interface to listen on (default: mythostory.ini's, else 127.0.0.1: this computer only)
  --no-browser     do not open the browser on the page
  --keep-alive     keep serving once the last page is closed (by default, MythoStory then stops); --keep-running too
  --piper DIR      Piper, which reads the stories aloud (default: piper/ next to mythostory.jar)
  --voices DIR     its voices, <model>.onnx and <model>.onnx.json (default: voices/ next to mythostory.jar)
A run from the repository (scripts/linux/run.sh) takes external/piper and external/voices when there is none beside the jar.
The log: ~/.mythostory/logs/mythostory.0.log."""

/** The program's own messages, in the log file too (logback.xml): what a double-clicked MythoStory.exe said. */
private val log = LoggerFactory.getLogger("org.jtools.mythostory.desktop.Main")

/**
 * The desktop program: the server on localhost, the browser as its window — and like a window's program, it stops
 * when its last page is closed (unless --keep-alive): the browser is its only way out, there is no other to quit it.
 * Its exit codes are JToolsFramework's: 2 for a command line it does not understand, 3 for a server that cannot start.
 */
fun main(args: Array<String>) {
    val home = File(Main::class.java.protectionDomain.codeSource.location.toURI()).parentFile
    val settings = installationSettings(home.resolve("mythostory.ini"))
    var port = settings.getProperty("port")?.trim()?.toIntOrNull() ?: DEFAULT_PORT
    var host = settings.getProperty("host")?.trim()?.takeIf { it.isNotEmpty() } ?: "127.0.0.1"
    var browser = true
    var keepAlive = false
    var piper = besideTheJarOrExternal(home, "piper")
    var voices = besideTheJarOrExternal(home, "voices")
    val rest = args.iterator()
    while (rest.hasNext()) {
        when (val arg = rest.next()) {
            "--port" -> port = rest.valueOf(arg).toIntOrNull() ?: usage("--port takes a number")
            "--host" -> host = rest.valueOf(arg)
            "--no-browser" -> browser = false
            // --keep-alive is the framework's name; --keep-running, MythoStory's before it, still works
            "--keep-alive", "--keep-running" -> keepAlive = true
            "--piper" -> piper = File(rest.valueOf(arg))
            "--voices" -> voices = File(rest.valueOf(arg))
            "--help", "-h" -> { println(USAGE); return }
            else -> usage("unknown argument: $arg")
        }
    }

    lateinit var server: EmbeddedServer<*, *>
    val onLastPageClosed = {
        say("The last MythoStory page is closed: MythoStory stops.")
        // not from the server's own threads, which stop() waits for
        thread(name = "mythostory-stop") {
            server.stop(500, 2000)
            exitProcess(0)
        }
        Unit
    }
    val speech = PiperSynthesizer(PiperSynthesizer.program(piper), voices)
    if (PiperSynthesizer.program(piper).canExecute() && !speech.runs) {
        say("Piper in $piper does not run on this computer: the browser's voices will read the stories, if it has some.")
    } else if (speech.voices.isEmpty()) {
        say("No Piper voice in $piper and $voices: the browser's voices will read the stories, if it has some.")
    }
    Runtime.getRuntime().addShutdownHook(Thread { speech.close() })
    // launched again, as one double-clicks a program already open: the page of the one running opens instead. Asked
    // before the server starts, which would otherwise print the failed bind's stack trace from its own threads
    val portTaken = {
        val url = pageUrl(host, port)
        if (isMythoStory(url)) {
            say("MythoStory is already running on $url: its page opens.")
            if (browser) openBrowser(url)
            exitProcess(0)
        }
        fail("Port $port is taken by another program: give MythoStory another, with --port N or port= in mythostory.ini.", 3)
    }
    if (port != 0 && !canListen(host, port)) portTaken()
    server = try {
        startServer(
            host, port, Platform.DESKTOP,
            onLastPageClosed = if (keepAlive) null else onLastPageClosed,
            speech = speech.takeIf { it.voices.isNotEmpty() },
        )
    } catch (e: Exception) {
        // taken in the meantime
        if (generateSequence<Throwable>(e) { it.cause }.any { it is BindException }) portTaken()
        log.error("The server could not start", e)
        fail("MythoStory could not start its server on $host:$port: ${e.message}", 3)
    }
    val url = pageUrl(host, runBlocking { server.engine.resolvedConnectors().first().port })
    say("MythoStory is ready on $url; " + if (keepAlive) "Ctrl+C to stop" else "it stops when its last page is closed (or Ctrl+C)")
    if (browser) openBrowser(url)
    // on Ctrl+C, or exitProcess: the server stopped, Piper's processes with it (the hook above)
    Runtime.getRuntime().addShutdownHook(Thread { server.stop(500, 2000) })
    // the server runs on its own threads (start(wait = false)): main only waits, until exitProcess or Ctrl+C
    Thread.currentThread().join()
}

/** A message for whoever runs the program: on the console, and in the log. */
private fun say(message: String) {
    println(message)
    log.info(message)
}

/**
 * The program cannot go on: the message on the console and in the log — and in a dialog when there is no console to
 * read it in (MythoStory.exe, double-clicked), as JToolsFramework's programs do. A run with its output redirected
 * shows the dialog too: tests run with -Djava.awt.headless=true, which shows none.
 */
private fun fail(message: String, code: Int): Nothing {
    System.err.println(message)
    log.error(message)
    val console = System.console()
    if ((console == null || !console.isTerminal) && !GraphicsEnvironment.isHeadless()) {
        runCatching { JOptionPane.showMessageDialog(null, message, "MythoStory", JOptionPane.ERROR_MESSAGE) }
    }
    exitProcess(code)
}

/** The page's address: on this computer, when the server listens on every interface. */
private fun pageUrl(host: String, port: Int): String = "http://${if (host == "0.0.0.0") "localhost" else host}:$port/"

private fun openBrowser(url: String) {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) Desktop.getDesktop().browse(URI(url))
}

/** Whether a server could listen on this interface and port now: no other program does. */
private fun canListen(host: String, port: Int): Boolean =
    runCatching { ServerSocket(port, 1, InetAddress.getByName(host)).close() }.isSuccess

/** Whether the server answering at this address is MythoStory: its /api/info says so (backend: Info). */
private fun isMythoStory(url: String): Boolean = runCatching {
    val connection = URI("${url}api/info").toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = 2000
    connection.readTimeout = 2000
    try {
        connection.responseCode == 200 && connection.inputStream.reader().use { it.readText() }.contains("\"name\":\"MythoStory\"")
    } finally {
        connection.disconnect()
    }
}.getOrDefault(false)

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

private fun usage(message: String): Nothing = fail("$message\n$USAGE", 2)

/** The class the program's jar is found by. */
private object Main
