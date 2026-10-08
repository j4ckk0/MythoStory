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

/**
 * Piper (rhasspy/piper): neural speech synthesis, offline. Its voices are those of [catalogue] (voices.json, in the
 * jar: each one's name and description, the order the page offers them in) whose model is in [directory] —
 * <file>.onnx and its <file>.onnx.json, as setup deploys them into external/voices and the distributions carry them;
 * a model may hold several speakers, each a voice. One Piper process per model, started on its first text and kept:
 * loading a model takes a moment, a sentence then takes a fraction of one. Piper reads a JSON line per text — its
 * speaker too — and writes the WAV where the line says, answering with its path.
 */
class PiperSynthesizer(
    private val piper: File,
    private val directory: File,
    catalogue: List<PiperVoice> = PiperVoice.catalogue(),
) : SpeechSynthesizer, AutoCloseable {
    private val available: Map<String, PiperVoice> =
        if (!piper.canExecute()) emptyMap()
        else catalogue.filter { it.language in I18n.LANGUAGES && model(it.file).isFile && directory.resolve("${it.file}.onnx.json").isFile }
            .associateBy { it.id }

    /** Each language's voices in the catalogue's order, so that its first — its default — comes first. */
    override val voices: List<Voice> = available.values.map { Voice(it.id, it.language, it.name, it.description) }

    private class Model(val process: Process, val input: Writer, val output: BufferedReader)

    private val running = HashMap<String, Model>()
    private val workDir: File = Files.createTempDirectory("mythostory-speech").toFile().apply { deleteOnExit() }

    /** The last texts said, by voice and text: a summary read twice is synthesized once. */
    private val cache = object : LinkedHashMap<String, ByteArray>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>) = size > 300
    }

    override fun synthesize(voice: String, text: String): ByteArray {
        val piperVoice = requireNotNull(available[voice]) { "no voice $voice" }
        synchronized(cache) { cache["$voice\n$text"] }?.let { return it }
        // one text at a time per model: Piper answers in the order it is asked
        val wav = synchronized(modelLock(piperVoice.file)) {
            try {
                say(model(piperVoice), piperVoice.speaker, text)
            } catch (e: Exception) {
                // a model that died is started again, once
                synchronized(running) { running.remove(piperVoice.file)?.process?.destroy() }
                say(model(piperVoice), piperVoice.speaker, text)
            }
        }
        synchronized(cache) { cache["$voice\n$text"] = wav }
        return wav
    }

    private fun say(model: Model, speaker: Int?, text: String): ByteArray {
        val file = File.createTempFile("speech", ".wav", workDir)
        try {
            val line = buildJsonObject {
                put("text", JsonPrimitive(forSpeech(text)))
                if (speaker != null) put("speaker_id", JsonPrimitive(speaker))
                put("output_file", JsonPrimitive(file.absolutePath))
            }
            model.input.write(Json.encodeToString(line) + "\n")
            model.input.flush()
            checkNotNull(model.output.readLine()) { "Piper stopped" }
            return file.readBytes()
        } finally {
            file.delete()
        }
    }

    private val locks = HashMap<String, Any>()

    private fun modelLock(file: String): Any = synchronized(locks) { locks.getOrPut(file) { Any() } }

    private fun model(file: String): File = directory.resolve("$file.onnx")

    private fun model(voice: PiperVoice): Model = synchronized(running) {
        running[voice.file]?.takeIf { it.process.isAlive } ?: start(voice.file).also { running[voice.file] = it }
    }

    private fun start(file: String): Model {
        val process = ProcessBuilder(
            piper.absolutePath, "--model", model(file).absolutePath, "--json-input", "--quiet",
            "--length_scale", LENGTH_SCALE, "--sentence_silence", SENTENCE_SILENCE,
        )
            .directory(piper.parentFile)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        return Model(process, process.outputStream.writer(Charsets.UTF_8), process.inputStream.bufferedReader(Charsets.UTF_8))
    }

    override fun close() {
        synchronized(running) {
            running.values.forEach { it.process.destroy() }
            running.clear()
        }
        workDir.deleteRecursively()
    }

    companion object {
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
