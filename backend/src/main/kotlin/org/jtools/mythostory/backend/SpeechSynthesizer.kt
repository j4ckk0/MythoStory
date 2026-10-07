package org.jtools.mythostory.backend

/**
 * What reads texts aloud on the server (/api/speech): the desktop's Piper; none on Android, whose app lends the page
 * Android's own text-to-speech. The page prefers it to the browser's voices, which some systems lack or make robotic.
 */
interface SpeechSynthesizer {
    /** The i18n languages it has a voice for. */
    val languages: Set<String>

    /** The text spoken in the language, as WAV audio. */
    fun synthesize(language: String, text: String): ByteArray
}
