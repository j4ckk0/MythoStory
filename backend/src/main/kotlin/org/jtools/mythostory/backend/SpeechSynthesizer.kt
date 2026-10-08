package org.jtools.mythostory.backend

import kotlinx.serialization.Serializable
import org.jtools.mythostory.core.I18nText

/**
 * What reads texts aloud on the server (/api/speech): the desktop's Piper; none on Android, whose app lends the page
 * Android's own text-to-speech. The page prefers it to the browser's voices, which some systems lack or make robotic.
 */
interface SpeechSynthesizer {
    /** Its voices, each language's default first: the page's settings offer them (frontend: settings page). */
    val voices: List<Voice>

    /** The text spoken by the voice (one of [voices]' ids), as WAV audio. */
    fun synthesize(voice: String, text: String): ByteArray
}

/** A voice the server reads with: its id, its i18n language, its name and what it sounds like ("male, northern English"). */
@Serializable
data class Voice(val id: String, val language: String, val name: String, val description: I18nText? = null)
