package org.jtools.mythostory.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/** The window: a WebView on the app's own server, as the desktop's browser is on the desktop's. */
class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var frame: FrameLayout
    private lateinit var speech: SpeechBridge

    /** The app's server's port, once it listens: its pages, and only they, stay in the WebView. */
    private var serverPort = -1

    /** Back, from Android 13 on: the page before, while there is one (see [followHistory]). */
    private val goBack: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) OnBackInvokedCallback { webView.goBack() } else null
    private var goBackRegistered = false

    @SuppressLint("SetJavaScriptEnabled") // the page is Angular, served by the app itself
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            // the app's pages stay in the app — its own server's, not another app's on this phone, which would get the
            // speech bridge —; any other link opens in the browser, or nowhere when the phone has nothing to open it with
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == "127.0.0.1" && request.url.port == serverPort) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                } catch (e: ActivityNotFoundException) {
                    // no browser, no app for that kind of link: the page stays as it is
                }
                return true
            }

            // every page the WebView goes to, the Angular router's included (history.pushState), and every one back
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                followHistory()
            }
        }
        // Android 15 draws the app under the system bars: keep the page clear of them. The WebView in a frame, padded by
        // the bars — a WebView ignores its own padding, drawing the page under them —; the theme's colour shows there.
        frame = FrameLayout(this)
        frame.addView(webView)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            frame.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        // the page's speaker button: the WebView has no speech synthesis of its own
        speech = SpeechBridge(this, webView)
        webView.addJavascriptInterface(speech, "MythoStorySpeech")
        setContentView(frame)
        paintSystemBars()

        (application as MythoStoryApplication).port.whenComplete { port, error ->
            runOnUiThread {
                if (error != null) {
                    webView.loadData(getString(R.string.server_failed, error.message), "text/plain", "UTF-8")
                } else {
                    serverPort = port
                    webView.loadUrl("http://127.0.0.1:$port/")
                }
            }
        }
    }

    /**
     * The system bars' strips in the page's colour, light or dark (the themes in res/values and res/values-night),
     * and their icons dark on the light one. The theme does it as the app starts; this, as the system switches
     * between light and dark while the app runs — the activity is not recreated then (configChanges: uiMode), so as
     * not to lose the page.
     */
    private fun paintSystemBars() {
        frame.setBackgroundColor(getColor(R.color.page))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (night) 0 else light, light)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        paintSystemBars()
    }

    /**
     * Back goes to the page before while there is one, and leaves the app from the first page, as in a browser. From
     * Android 13 on (the manifest's enableOnBackInvokedCallback), Back reaches the app only through a callback, which
     * is therefore registered only while the WebView has a page to go back to: without one, the system's own Back —
     * and its predictive animation, leaving the app — applies. Android 16 calls onBackPressed no more.
     */
    private fun followHistory() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val wanted = webView.canGoBack()
        if (wanted == goBackRegistered) return
        val callback = goBack as OnBackInvokedCallback
        if (wanted) onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        else onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
        goBackRegistered = wanted
    }

    /** Back before Android 13: the system calls this, and only below 13 (the manifest opts the app into callbacks). */
    @Deprecated("from Android 13 on, Back comes through followHistory's callback")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    override fun onDestroy() {
        speech.shutdown()
        webView.destroy()
        super.onDestroy()
    }
}
