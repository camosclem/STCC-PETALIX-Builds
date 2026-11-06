package com.openguide.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream

class MainActivity : Activity() {
    private lateinit var browser: WebView
    private val io = Executors.newSingleThreadExecutor()
    private val fileRequestCode = 2001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        browser = WebView(this).apply {
            setBackgroundColor(0xff0b0e17.toInt())
            isFocusable = true
            isFocusableInTouchMode = true
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            addJavascriptInterface(Bridge(), "AndroidBridge")
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(browser)
    }

    inner class Bridge {
        @JavascriptInterface fun isNative(): Boolean = true

        @JavascriptInterface fun importPlaylist(url: String, name: String) {
            fetchText(url, 16 * 1024 * 1024, "onNativePlaylist", name)
        }

        @JavascriptInterface fun importEpg(url: String) {
            fetchText(url, 55 * 1024 * 1024, "onNativeEpg", "EPG")
        }

        @JavascriptInterface fun openPlaylistFile() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(intent, fileRequestCode)
            }
        }

        @JavascriptInterface fun playChannel(url: String, name: String) {
            if (!isPlayableUrl(url)) return
            runOnUiThread {
                startActivity(Intent(this@MainActivity, PlayerActivity::class.java).apply {
                    putExtra("url", url)
                    putExtra("name", name)
                })
            }
        }

        @JavascriptInterface fun exitApp() { runOnUiThread { finish() } }
    }

    private fun isPlayableUrl(url: String) = url.startsWith("https://", true)
        || url.startsWith("http://", true)
        || url.startsWith("rtsp://", true)

    private fun fetchText(rawUrl: String, maxSize: Int, callback: String, name: String) {
        if (!rawUrl.startsWith("https://", true) && !rawUrl.startsWith("http://", true)) {
            reportError("Only HTTP and HTTPS URLs are supported")
            return
        }
        io.execute {
            try {
                val conn = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 45000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "OpenGuideTV/0.1 (Android TV)")
                    setRequestProperty("Accept-Encoding", "gzip")
                }
                try {
                    if (conn.responseCode !in 200..299) error("HTTP " + conn.responseCode)
                    val stream = BufferedInputStream(conn.inputStream)
                    stream.mark(2)
                    val signature1 = stream.read()
                    val signature2 = stream.read()
                    stream.reset()
                    val gzipped = conn.contentEncoding?.contains("gzip", true) == true ||
                        (signature1 == 0x1f && signature2 == 0x8b)
                    val textStream: InputStream = if (gzipped) GZIPInputStream(stream) else stream
                    val buffer = ByteArrayOutputStream()
                    textStream.use { source ->
                        val block = ByteArray(16384)
                        while (true) {
                            val count = source.read(block)
                            if (count < 0) break
                            if (buffer.size() + count > maxSize) error("File is too large for this test build")
                            buffer.write(block, 0, count)
                        }
                    }
                    val content = JSONObject.quote(buffer.toString(Charsets.UTF_8.name()))
                    val label = JSONObject.quote(name)
                    runOnUiThread { browser.evaluateJavascript("window." + callback + "(" + content + "," + label + ")", null) }
                } finally { conn.disconnect() }
            } catch (e: Exception) { reportError(e.message ?: "Unable to download source") }
        }
    }

    private fun reportError(error: String) {
        val safe = JSONObject.quote(error)
        runOnUiThread { browser.evaluateJavascript("window.onNativeError(" + safe + ")", null) }
    }

    @Deprecated("Used for Android TV document picker compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != fileRequestCode || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        io.execute {
            try {
                val stream = contentResolver.openInputStream(uri) ?: error("Could not open playlist")
                val bytes = stream.use { input ->
                    val output = ByteArrayOutputStream()
                    val piece = ByteArray(16384)
                    while (true) {
                        val read = input.read(piece)
                        if (read < 0) break
                        if (output.size() + read > 16 * 1024 * 1024) error("Playlist file too large")
                        output.write(piece, 0, read)
                    }
                    output.toString(Charsets.UTF_8.name())
                }
                val content = JSONObject.quote(bytes)
                runOnUiThread { browser.evaluateJavascript("window.onNativePlaylist(" + content + ",'Local file')", null) }
            } catch (e: Exception) { reportError(e.message ?: "Could not read file") }
        }
    }

    @Deprecated("Use Activity Back navigation")
    override fun onBackPressed() {
        browser.evaluateJavascript("window.handleBack && window.handleBack()", null)
    }

    override fun onDestroy() {
        browser.removeJavascriptInterface("AndroidBridge")
        browser.destroy()
        io.shutdownNow()
        super.onDestroy()
    }
}
