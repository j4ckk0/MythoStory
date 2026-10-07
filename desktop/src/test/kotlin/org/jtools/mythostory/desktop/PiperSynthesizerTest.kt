package org.jtools.mythostory.desktop

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Piper itself, as setup deploys it into external/: skipped where it is not (the tests run from the module's directory). */
class PiperSynthesizerTest {
    private val piper = PiperSynthesizer.program(File("../external/piper"))
    private val voices = File("../external/voices")

    @Test
    fun `reads French and English aloud, as WAV, and says the same text the same way twice`() {
        assumeTrue(piper.canExecute(), "no external/piper: scripts/setup.sh deploys it")
        PiperSynthesizer(piper, voices).use { speech ->
            assertEquals(setOf("en", "fr"), speech.languages)
            val french = speech.synthesize("fr", "Zeus est le roi des dieux.")
            assertEquals("RIFF", String(french, 0, 4))
            assertEquals("WAVE", String(french, 8, 4))
            assertTrue(french.size > 20_000, "about a second of speech at least")
            assertEquals("RIFF", String(speech.synthesize("en", "Zeus is the king of the gods."), 0, 4))
            assertTrue(french === speech.synthesize("fr", "Zeus est le roi des dieux."), "the second time comes from the cache")
        }
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
        assertEquals(emptySet(), PiperSynthesizer(File("no/such/piper"), voices).languages)
    }
}
