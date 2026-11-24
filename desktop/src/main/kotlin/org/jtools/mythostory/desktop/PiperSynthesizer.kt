package org.jtools.mythostory.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.Serializable
import org.jtools.mythostory.backend.SpeechSynthesizer
import org.jtools.mythostory.backend.Voice
import org.jtools.mythostory.core.I18n
import org.jtools.mythostory.core.I18nText
import java.io.BufferedReader
import java.io.File
import java.io.Writer
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Piper (rhasspy/piper): neural speech synthesis, offline. Its voices are those of [catalogue] (voices.json, in the
 * jar: each one's name and description, the order the page offers them in) whose model is in [directory] —
 * <file>.onnx and its <file>.onnx.json, as setup deploys them into external/voices and the distributions carry them;
 * a model may hold several speakers, each a voice. One Piper process per model, started on its first text and kept:
 * loading a model takes a moment, a sentence then takes a fraction of one. Piper reads a JSON line per text — its
 * speaker too — and writes the WAV where the line says, answering with its path. A process that does not answer
 * within [answerTimeout] is killed, so that a hung Piper never holds its voice for good.
 */
class PiperSynthesizer(
    private val piper: File,
    private val directory: File,
    catalogue: List<PiperVoice> = PiperVoice.catalogue(),
    private val answerTimeout: Duration = ANSWER_TIMEOUT,
    private val cacheBytes: Long = CACHE_BYTES,
) : SpeechSynthesizer, AutoCloseable {
    /**
     * Whether Piper runs on this computer: present is not enough — a shared library missing, a system too old, and
     * every text would fail, while the page, told of voices, would not fall back on the browser's.
     */
    val runs: Boolean = piper.canExecute() && answersVersion(piper)

    private val available: Map<String, PiperVoice> =
        if (!runs) emptyMap()
        else catalogue.filter { it.language in I18n.LANGUAGES && modelFile(it.file).isFile && directory.resolve("${it.file}.onnx.json").isFile }
            .associateBy { it.id }

    /** Each language's voices in the catalogue's order, so that its first — its default — comes first. */
    override val voices: List<Voice> = available.values.map { Voice(it.id, it.language, it.name, it.description) }

    /** A model hung: killed, and not asked again for this text, which would likely hang it again. */
    private class Hung(message: String) : Exception(message)

    /** Each model, by its file: its Piper process, started on its first text. */
    private val models = HashMap<String, PiperModel>()

    /** The threads waiting for Piper's answers, so that a request can stop waiting while one still blocks. */
    private val answers: ExecutorService = Executors.newCachedThreadPool { task -> Thread(task, "piper-answer").apply { isDaemon = true } }
    private val workDir: File = Files.createTempDirectory("mythostory-speech").toFile().apply { deleteOnExit() }

    /**
     * The last texts said, by voice and text, the least recently heard dropped first: a summary read twice is
     * synthesized once. Bounded by their size, [cacheBytes] in all: a text of 500 characters is half a minute of
     * speech, over a megabyte.
     */
    private val cache = LinkedHashMap<String, ByteArray>(64, 0.75f, true)
    private var cached = 0L

    override fun synthesize(voice: String, text: String): ByteArray {
        val piperVoice = requireNotNull(available[voice]) { "no voice $voice" }
        synchronized(cache) { cache["$voice\n$text"] }?.let { return it }
        val model = synchronized(models) { models.getOrPut(piperVoice.file) { PiperModel(piperVoice.file) } }
        val wav = model.say(piperVoice.speaker, text)
        remember("$voice\n$text", wav)
        return wav
    }

    /**
     * One model's Piper process: started on its first text and kept; started again, once, when it died or answered
     * out of turn; killed when it hangs. One text at a time ([say] is synchronized: Piper answers in the order it is
     * asked), while the other models' voices are said meanwhile.
     */
    private inner class PiperModel(private val file: String) {
        @Volatile private var process: Process? = null
        private lateinit var input: Writer
        private lateinit var output: BufferedReader

        @Synchronized
        fun say(speaker: Int?, text: String): ByteArray = try {
            sayOnce(speaker, text)
        } catch (e: Hung) {
            stop()
            throw e
        } catch (e: Exception) {
            stop()
            sayOnce(speaker, text)
        }

        /** The process stopped: the next text starts another. Not synchronized: closing does not wait for a text. */
        fun stop() {
            process?.destroyForcibly()
            process = null
        }

        private fun sayOnce(speaker: Int?, text: String): ByteArray {
            val process = process?.takeIf { it.isAlive } ?: start()
            val file = File.createTempFile("speech", ".wav", workDir)
            try {
                val line = buildJsonObject {
                    put("text", JsonPrimitive(forSpeech(text)))
                    if (speaker != null) put("speaker_id", JsonPrimitive(speaker))
                    put("output_file", JsonPrimitive(file.absolutePath))
                }
                input.write(Json.encodeToString(line) + "\n")
                input.flush()
                val answer = answers.submit(Callable { output.readLine() })
                val path = try {
                    answer.get(answerTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                } catch (e: TimeoutException) {
                    // killed, the process closes its output: the thread still reading it ends too
                    process.destroyForcibly()
                    throw Hung("Piper gave no answer in $answerTimeout")
                }
                checkNotNull(path) { "Piper stopped" }
                // the answer to this text is the path it was given: any other line (a warning, an answer out of turn)
                // would shift every later answer by one, and return a file half written or another text's
                check(path.trim() == file.absolutePath) { "Piper answered \"$path\" instead of the WAV's path" }
                return file.readBytes()
            } finally {
                file.delete()
            }
        }

        private fun start(): Process {
            val started = ProcessBuilder(
                piper.absolutePath, "--model", modelFile(file).absolutePath, "--json-input", "--quiet",
                "--length_scale", LENGTH_SCALE, "--sentence_silence", SENTENCE_SILENCE,
            )
                .directory(piper.parentFile)
                // stderr discarded: a pipe nobody reads would fill up and stall Piper
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            input = started.outputStream.writer(Charsets.UTF_8)
            output = started.inputStream.bufferedReader(Charsets.UTF_8)
            process = started
            return started
        }
    }

    private fun remember(key: String, wav: ByteArray) = synchronized(cache) {
        cache.put(key, wav)?.let { cached -= it.size }
        cached += wav.size
        val eldest = cache.values.iterator()
        while (cached > cacheBytes && eldest.hasNext()) {
            cached -= eldest.next().size
            eldest.remove()
        }
    }

    /** A model's file: <file>.onnx in [directory]. */
    private fun modelFile(file: String): File = directory.resolve("$file.onnx")

    override fun close() {
        synchronized(models) {
            models.values.forEach { it.stop() }
            models.clear()
        }
        answers.shutdownNow()
        workDir.deleteRecursively()
    }

    companion object {
        /**
         * How long Piper may take to answer a text: a model's first text includes its loading (a second or two), and
         * the page sends up to 500 characters at a time, a few seconds of work on a slow computer.
         */
        val ANSWER_TIMEOUT: Duration = 30.seconds

        /** How much speech the cache keeps: a few stories' worth; the browser keeps what it played too (a day). */
        const val CACHE_BYTES: Long = 64L * 1024 * 1024

        /** A storyteller's pace: a little slower than Piper's default (1.0), which suits short messages. */
        const val LENGTH_SCALE = "1.08"

        /** The silence after each sentence, in seconds: Piper's 0.2 runs a story's sentences together. */
        const val SENTENCE_SILENCE = "0.45"

        /**
         * The text as Piper should hear it: one line, and dashes — which it would run through without a breath — as
         * commas.
         */
        fun forSpeech(text: String): String =
            text.replace(Regex("\\s*[—–]\\s*"), ", ")
                .replace(Regex("\\s+"), " ")
                .replace(Regex(",\\s*([.,;:!?])"), "$1")
                .trim()

        /** Whether the program starts and says its version, within a few seconds: it loads all its libraries to do so. */
        private fun answersVersion(piper: File): Boolean = runCatching {
            val process = ProcessBuilder(piper.absolutePath, "--version")
                .directory(piper.parentFile)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
            process.outputStream.close()
            val done = process.waitFor(10, TimeUnit.SECONDS)
            if (!done) process.destroyForcibly()
            done && process.exitValue() == 0
        }.getOrDefault(false)

        /** Piper's program in a directory: piper, or piper.exe on Windows. */
        fun program(dir: File): File = dir.resolve(if (System.getProperty("os.name").startsWith("Windows")) "piper.exe" else "piper")
    }
}

/**
 * A voice of the catalogue (voices.json): Piper's model [file] (without .onnx), its [speaker] when the model holds
 * several, and what the page shows of it. Each model's licence: licences/embedded.json.
 */
@Serializable
data class PiperVoice(
    val id: String,
    val file: String,
    val speaker: Int? = null,
    val language: String,
    val name: String,
    val description: I18nText? = null,
) {
    companion object {
        fun catalogue(): List<PiperVoice> =
            PiperVoice::class.java.getResourceAsStream("/voices.json")!!.use { Json.decodeFromString(it.readBytes().decodeToString()) }
    }
}
