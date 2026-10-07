package org.jtools.mythostory.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jtools.mythostory.backend.SpeechSynthesizer
import org.jtools.mythostory.core.I18n
import java.io.BufferedReader
import java.io.File
import java.io.Writer
import java.nio.file.Files

/**
 * Piper (rhasspy/piper): neural speech synthesis, offline, one voice per i18n language — [voices]/<lang>.onnx and its
 * <lang>.onnx.json, those setup deploys into external/voices and the distributions carry. One Piper process per
 * language, started on its first text and kept: loading a voice takes a moment, a sentence then takes a fraction of
 * one. Piper reads a JSON line per text and writes the WAV where the line says, answering with its path.
 */
class PiperSynthesizer(private val piper: File, private val voices: File) : SpeechSynthesizer, AutoCloseable {
    override val languages: Set<String> =
        if (!piper.canExecute()) emptySet()
        else I18n.LANGUAGES.filter { voices.resolve("$it.onnx").isFile && voices.resolve("$it.onnx.json").isFile }.toSet()

    private class Voice(val process: Process, val input: Writer, val output: BufferedReader)

    private val running = HashMap<String, Voice>()
    private val workDir: File = Files.createTempDirectory("mythostory-speech").toFile().apply { deleteOnExit() }

    /** The last texts said, by language and text: a summary read twice is synthesized once. */
    private val cache = object : LinkedHashMap<String, ByteArray>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>) = size > 300
    }

    override fun synthesize(language: String, text: String): ByteArray {
        require(language in languages) { "no voice for $language" }
        synchronized(cache) { cache["$language\n$text"] }?.let { return it }
        // one text at a time per voice: Piper answers in the order it is asked
        val wav = synchronized(voiceLock(language)) {
            try {
                say(voice(language), text)
            } catch (e: Exception) {
                // a voice that died is started again, once
                synchronized(running) { running.remove(language)?.process?.destroy() }
                say(voice(language), text)
            }
        }
        synchronized(cache) { cache["$language\n$text"] = wav }
        return wav
    }

    private fun say(voice: Voice, text: String): ByteArray {
        val file = File.createTempFile("speech", ".wav", workDir)
        try {
            val line = buildJsonObject {
                put("text", JsonPrimitive(text.replace(Regex("\\s+"), " ")))
                put("output_file", JsonPrimitive(file.absolutePath))
            }
            voice.input.write(Json.encodeToString(line) + "\n")
            voice.input.flush()
            checkNotNull(voice.output.readLine()) { "Piper stopped" }
            return file.readBytes()
        } finally {
            file.delete()
        }
    }

    private val locks = HashMap<String, Any>()

    private fun voiceLock(language: String): Any = synchronized(locks) { locks.getOrPut(language) { Any() } }

    private fun voice(language: String): Voice = synchronized(running) {
        running[language]?.takeIf { it.process.isAlive } ?: start(language).also { running[language] = it }
    }

    private fun start(language: String): Voice {
        val process = ProcessBuilder(piper.absolutePath, "--model", voices.resolve("$language.onnx").absolutePath, "--json-input", "--quiet")
            .directory(piper.parentFile)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        return Voice(process, process.outputStream.writer(Charsets.UTF_8), process.inputStream.bufferedReader(Charsets.UTF_8))
    }

    override fun close() {
        synchronized(running) {
            running.values.forEach { it.process.destroy() }
            running.clear()
        }
        workDir.deleteRecursively()
    }

    companion object {
        /** Piper's program in a directory: piper, or piper.exe on Windows. */
        fun program(dir: File): File = dir.resolve(if (System.getProperty("os.name").startsWith("Windows")) "piper.exe" else "piper")
    }
}
