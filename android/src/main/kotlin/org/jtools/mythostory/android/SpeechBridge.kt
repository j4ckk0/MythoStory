package org.jtools.mythostory.android

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject
import java.util.Locale

/**
 * Android's text-to-speech lent to the page, whose WebView has no speech synthesis: window.MythoStorySpeech
 * (frontend: AndroidSpeechEngine). The page asks whether a locale has a voice, speaks texts by id and stops; the
 * bridge tells it when the engine is ready (window.mythoStorySpeechReady) and when each text is said
 * (window.mythoStorySpeechDone). Only the app's own pages reach it: the WebView opens no other site.
 */
class SpeechBridge(context: Context, private val webView: WebView) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context, this)

    @Volatile
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}

            override fun onDone(utteranceId: String) = call("mythoStorySpeechDone", utteranceId)

            @Deprecated("the engine's errors end the text all the same", ReplaceWith("onDone(utteranceId)"))
            override fun onError(utteranceId: String) = call("mythoStorySpeechDone", utteranceId)
        })
        call("mythoStorySpeechReady", null)
    }

    /** Whether the engine has a voice for the locale ("fr-FR"), its data installed. */
    @JavascriptInterface
    fun available(locale: String): Boolean = ready && tts.isLanguageAvailable(Locale.forLanguageTag(locale)) >= TextToSpeech.LANG_AVAILABLE

    @JavascriptInterface
    fun speak(id: String, text: String, locale: String) {
        if (!ready) return
        tts.language = Locale.forLanguageTag(locale)
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, id)
    }

    @JavascriptInterface
    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.shutdown()
    }

    /** Calls the page's function, on the WebView's thread, if the page has it. */
    private fun call(function: String, argument: String?) {
        val arg = argument?.let(JSONObject::quote) ?: ""
        webView.post { webView.evaluateJavascript("window.$function && window.$function($arg)", null) }
    }
}
