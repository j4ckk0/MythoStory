package org.jtools.mythostory.desktop

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

/** Piper itself, as setup deploys it into external/: skipped where it is not (the tests run from the module's directory). */
class PiperSynthesizerTest {
    private val piper = PiperSynthesizer.program(File("../external/piper"))
    private val voices = File("../external/voices")

    @Test
    fun `reads with every voice of the catalogue, as WAV, and says the same text the same way twice`() {
        assumeTrue(piper.canExecute(), "no external/piper: scripts/setup.sh deploys it")
        PiperSynthesizer(piper, voices).use { speech ->
            assertEquals(PiperVoice.catalogue().map { it.id }, speech.voices.map { it.id }, "setup deploys every voice of voices.json")
            val french = speech.synthesize("fr-siwis", "Zeus est le roi des dieux.")
            assertEquals("RIFF", String(french, 0, 4))
            assertEquals("WAVE", String(french, 8, 4))
            assertTrue(french.size > 20_000, "about a second of speech at least")
            assertEquals("RIFF", String(speech.synthesize("en-alba", "Zeus is the king of the gods."), 0, 4))
            assertTrue(french === speech.synthesize("fr-siwis", "Zeus est le roi des dieux."), "the second time comes from the cache")
            // two speakers of one model: one process, two voices
            val jessica = speech.synthesize("fr-jessica", "Bonjour.")
            val pierre = speech.synthesize("fr-pierre", "Bonjour.")
            assertFalse(jessica.contentEquals(pierre), "each speaker has a voice of their own")
        }
    }

    @Test
    fun `the catalogue has unique ids, each language's default first, in every i18n language`() {
        val catalogue = PiperVoice.catalogue()
        assertEquals(catalogue.size, catalogue.map { it.id }.toSet().size)
        assertEquals(setOf("en", "fr"), catalogue.map { it.language }.toSet())
        assertEquals("fr-siwis", catalogue.first { it.language == "fr" }.id)
        assertEquals("en-alba", catalogue.first { it.language == "en" }.id)
        catalogue.forEach { assertEquals(setOf("en", "fr"), it.description?.keys, "${it.id}'s description in both languages") }
    }

    @Test
    fun `dashes become pauses, and a text one line`() {
        assertEquals(
            "Ce qui se passa à l’autel dépend de qui le raconte, pour Eschyle, elle fut tuée, et Clytemnestre ne pardonna jamais.",
            PiperSynthesizer.forSpeech("Ce qui se passa à l’autel dépend de qui le raconte — pour Eschyle, elle fut tuée —\net Clytemnestre ne pardonna jamais."),
        )
        assertEquals("Le lion, le serpent.", PiperSynthesizer.forSpeech("Le lion — le serpent —."))
    }

    @Test
    fun `without Piper, no voice`() {
        assertEquals(emptyList(), PiperSynthesizer(File("no/such/piper"), voices).voices)
    }

    /**
     * A Piper of our own, a shell script [body] runs each JSON line through ($line; $file, the WAV's path in it), with
     * one voice whose model files exist: what the synthesizer does when Piper misbehaves. Unix only.
     */
    private fun fakePiper(body: String): Pair<File, File> {
        assumeTrue(!System.getProperty("os.name").startsWith("Windows"), "a shell script stands for Piper")
        val dir = Files.createTempDirectory("fake-piper").toFile().apply { deleteOnExit() }
        val program = dir.resolve("piper")
        program.writeText(
            "#!/bin/sh\n" +
                "[ \"\$1\" = --version ] && { echo 1.2.0; exit 0; }\n" +
                "while read -r line; do\n" +
                "  file=$(printf '%s' \"\$line\" | sed -n 's/.*\"output_file\":\"\\([^\"]*\\)\".*/\\1/p')\n" +
                body + "\n" +
                "done\n",
        )
        program.setExecutable(true)
        dir.resolve("model.onnx").writeText("")
        dir.resolve("model.onnx.json").writeText("{}")
        return program to dir
    }

    private val voice = listOf(PiperVoice("fake", "model", language = "fr", name = "Fake"))

    @Test
    fun `a Piper that hangs is killed after the timeout, and the voice answers again`() {
        val (program, dir) = fakePiper("  sleep 60")
        PiperSynthesizer(program, dir, voice, answerTimeout = 300.milliseconds).use { speech ->
            val first = measureTime { assertFailsWith<Exception> { speech.synthesize("fake", "Bonjour.") } }
            assertTrue(first.inWholeMilliseconds < 1500, "one timeout, not retried: $first")
            // the voice is not held for good: the next text is tried, with a new process, and times out too
            val second = measureTime { assertFailsWith<Exception> { speech.synthesize("fake", "Encore.") } }
            assertTrue(second.inWholeMilliseconds < 1500, "$second")
        }
    }

    @Test
    fun `an answer that is not the WAV's path is never taken for it`() {
        // the first process prints a stray line before its answer, as a warning would; the second behaves
        val (program, dir) = fakePiper(
            "  if [ ! -e \"\$0.started\" ]; then touch \"\$0.started\"; echo 'warning: something'; fi\n" +
                "  printf 'RIFF%s' \"\$(basename \"\$file\")\" > \"\$file\"; echo \"\$file\"",
        )
        PiperSynthesizer(program, dir, voice, answerTimeout = 5000.milliseconds).use { speech ->
            val wav = String(speech.synthesize("fake", "Bonjour."))
            assertTrue(wav.startsWith("RIFFspeech"), "the WAV of this text, from a process started again: $wav")
            val next = String(speech.synthesize("fake", "Encore."))
            assertTrue(next.startsWith("RIFFspeech") && next != wav, "and the next text, its own: $next")
        }
    }

    @Test
    fun `a Piper that cannot run on this computer offers no voice, so that the page reads with the browser's`() {
        val (program, dir) = fakePiper("")
        // as when a shared library is missing: the loader refuses the program before it says anything
        program.writeText("#!/bin/sh\necho 'piper: error while loading shared libraries: libonnxruntime.so.1.14.1' >&2\nexit 127\n")
        PiperSynthesizer(program, dir, voice).use { speech ->
            assertFalse(speech.runs)
            assertEquals(emptyList(), speech.voices)
        }
    }

    @Test
    fun `the cache keeps the last texts heard within its size, dropping the least recently heard first`() {
        // each fake WAV is "RIFF" and its file's name, some thirty bytes: a cache of 80 holds two, not three
        val (program, dir) = fakePiper("  printf 'RIFF%s' \"\$(basename \"\$file\")\" > \"\$file\"; echo \"\$file\"")
        PiperSynthesizer(program, dir, voice, cacheBytes = 80).use { speech ->
            val first = speech.synthesize("fake", "Un.")
            val second = speech.synthesize("fake", "Deux.")
            assertTrue(first === speech.synthesize("fake", "Un."), "both kept; the first now the latest heard")
            speech.synthesize("fake", "Trois.")
            assertTrue(first === speech.synthesize("fake", "Un."), "the latest heard stays")
            assertFalse(second === speech.synthesize("fake", "Deux."), "the least recently heard was dropped, said again")
        }
    }
}
