package org.jtools.mythostory.android

import android.annotation.SuppressLint
import android.app.Activity
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

/** The window: a WebView on the app's own server, as the desktop's browser is on the desktop's. */
class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var frame: FrameLayout
    private lateinit var speech: SpeechBridge

    @SuppressLint("SetJavaScriptEnabled") // the page is Angular, served by the app itself
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            // the app's pages stay in the app; any other link opens in the browser
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == "127.0.0.1") return false
                startActivity(Intent(Intent.ACTION_VIEW, request.url))
                return true
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

    @Deprecated("the predictive back gesture comes with OnBackInvokedCallback; this one serves every version")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    override fun onDestroy() {
        speech.shutdown()
        webView.destroy()
        super.onDestroy()
    }
}
