package com.openguide.tv

import android.app.Activity
import android.content.Intent
import java.io.File
import android.webkit.WebViewClient
import android.os.Bundle
import android.view.KeyEvent
import android.media.AudioManager
import android.media.ToneGenerator
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream

class MainActivity : Activity() {
    private lateinit var browser: WebView
    private val io = Executors.newSingleThreadExecutor()
    private val pairing by lazy {getSharedPreferences("petalix_pairing", MODE_PRIVATE)}
    private val fileRequestCode = 2001
    @Volatile private var uiSounds = true
    private var focusTone: ToneGenerator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(pairing.getString("device_token", "").isNullOrBlank() && !pairing.getBoolean("demo_mode",false)){
            startActivity(Intent(this,ActivationActivity::class.java))
            finish()
            return
        }
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
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val address = request?.url?.toString() ?: return true
                    return !address.startsWith("file:///android_asset/")
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view,url)
                    if(!pairing.getString("device_token","").isNullOrBlank()) syncManagedCatalog()
                }
            }
            webChromeClient = WebChromeClient()
            addJavascriptInterface(Bridge(), "AndroidBridge")
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(browser)
    }

    inner class Bridge {
        @JavascriptInterface fun isNative(): Boolean = true
        @JavascriptInterface fun isManaged(): Boolean = !pairing.getString("device_token","").isNullOrBlank()
        @JavascriptInterface fun refreshManaged() { syncManagedCatalog() }
        @JavascriptInterface fun lookupManagedLocals(zip: String) { fetchManagedLocals(zip) }
        @JavascriptInterface fun enterDifferentPin() {
            pairing.edit().remove("device_token").putBoolean("demo_mode",false).apply()
            runOnUiThread { startActivity(Intent(this@MainActivity,ActivationActivity::class.java));finish() }
        }
        @JavascriptInterface fun openNativeGuide(payload: String) {
            io.execute {
                try {
                    // Private cache file avoids Binder's ~1 MB intent extras limit for large catalogs.
                    File(cacheDir,"petalix-guide.json").writeText(payload,Charsets.UTF_8)
                    runOnUiThread {startActivity(Intent(this@MainActivity, GuideActivity::class.java))}
                } catch(e:Exception) {reportError("Unable to open guide: " +(e.message?:"File error"))}
            }
        }


        @JavascriptInterface fun importPlaylist(url: String, name: String) {
            fetchPlaylist(url, name)
        }

        @JavascriptInterface fun importEpg(url: String) {
            fetchText(url, 55 * 1024 * 1024, "onNativeEpg", "EPG")
        }

        @JavascriptInterface fun importXtream(
            server: String, user: String, pass: String, action: String, ref: String, requestKey: String
        ) {
            if (user.isBlank() || pass.isBlank() || !server.matches(Regex("(?i)^https?://[^\\s]+$"))) {
                reportError("Invalid Xtream login")
                return
            }
            val allowed = setOf("", "get_live_categories", "get_live_streams",
                "get_vod_categories", "get_vod_streams", "get_series_categories",
                "get_series", "get_series_info", "get_vod_info")
            if (action !in allowed || requestKey.length > 250) return
            fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
            val suffix = StringBuilder("/player_api.php?username=")
                .append(enc(user)).append("&password=").append(enc(pass))
            if (action.isNotEmpty()) suffix.append("&action=").append(enc(action))
            if (action == "get_series_info" && ref.isNotEmpty())
                suffix.append("&series_id=").append(enc(ref))
            if (action == "get_vod_info" && ref.isNotEmpty())
                suffix.append("&vod_id=").append(enc(ref))
            fetchText(server.trimEnd('/') + suffix.toString(), 40 * 1024 * 1024,
                "onNativeXtream", requestKey)
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

        @JavascriptInterface fun setUiSounds(enabled: Boolean) {
            uiSounds = enabled
        }

        @JavascriptInterface fun exitApp() { runOnUiThread { finish() } }
    }

    /** Stream large M3U files line-by-line; never send the full provider list to WebView. */
    private fun fetchPlaylist(rawUrl: String, name: String) {
        if (!rawUrl.startsWith("https://", true) && !rawUrl.startsWith("http://", true)) {
            reportError("M3U source must be an HTTP or HTTPS URL")
            return
        }
        io.execute {
            try {
                val conn = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 60000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "PetalixTV/0.3 (Android TV)")
                    setRequestProperty("Accept-Encoding", "gzip")
                }
                try {
                    if (conn.responseCode !in 200..299) error("M3U HTTP ${conn.responseCode}")
                    val input = BufferedInputStream(conn.inputStream)
                    input.mark(2)
                    val b1 = input.read(); val b2 = input.read(); input.reset()
                    val compressed = conn.contentEncoding?.contains("gzip", true) == true ||
                        (b1 == 0x1f && b2 == 0x8b)
                    parsePlaylist(if (compressed) GZIPInputStream(input) else input, name)
                } finally { conn.disconnect() }
            } catch (e: Exception) { reportError(e.message ?: "M3U import failed") }
        }
    }

    private fun parsePlaylist(input: InputStream, name: String) {
        val maxByType = mapOf("live" to 2800, "movie" to 800, "show" to 500)
        val maxPerGroup = mapOf("live" to 180, "movie" to 80, "show" to 80)
        val totals = mutableMapOf("live" to 0, "movie" to 0, "show" to 0)
        val groupTotals = mutableMapOf<String, Int>()
        val attrs = Regex("([a-zA-Z][a-zA-Z0-9-]*)=\\\"([^\\\"]*)\\\"")
        val pending = JSONArray()
        var title = ""
        var group = ""
        var logo = ""
        var tvgId = ""
        var skipped = 0
        var seen = 0
        val quotedName = JSONObject.quote(name)
        fun send(last: Boolean) {
            if (pending.length() == 0 && !last) return
            val payload = pending.toString()
            while (pending.length() > 0) pending.remove(pending.length()-1)
            val done = if(last) "true" else "false"
            runOnUiThread {
                browser.evaluateJavascript("window.onNativePlaylistChunk($payload,$quotedName,$done,$skipped)", null)
            }
        }
        BufferedReader(InputStreamReader(input, Charsets.UTF_8), 64 * 1024).use { reader ->
            while (true) {
                val raw = reader.readLine() ?: break
                val line = raw.trim()
                if (line.startsWith("#EXTINF", true)) {
                    val found = attrs.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    title = line.substringAfter(',', "Channel").trim().take(130)
                    group = (found["group-title"] ?: "Live TV").take(100)
                    logo = (found["tvg-logo"] ?: "").take(500)
                    tvgId = (found["tvg-id"] ?: "").take(150)
                } else if (line.startsWith("#EXTGRP:", true)) {
                    group = line.substringAfter(':').trim().take(100)
                } else if (line.startsWith("http://", true) || line.startsWith("https://", true)) {
                    seen++
                    val url = line.take(1400)
                    val lowerGroup = group.lowercase()
                    val baseUrl = url.substringBefore('?').lowercase()
                    val type = when {
                        "series" in lowerGroup || "tv shows" in lowerGroup || "/series/" in baseUrl -> "show"
                        Regex("movies?|films?|cinema|vod").containsMatchIn(lowerGroup) ||
                            "/movie/" in baseUrl || Regex("\\.(mp4|mkv|avi|mov)$").containsMatchIn(baseUrl) -> "movie"
                        else -> "live"
                    }
                    val groupKey = "$type|$lowerGroup"
                    if ((totals[type] ?: 0) < maxByType.getValue(type) &&
                        (groupTotals[groupKey] ?: 0) < maxPerGroup.getValue(type)) {
                        val item = JSONObject()
                            .put("name", (title.ifBlank { "Channel $seen" }))
                            .put("group", group.ifBlank { "Live TV" })
                            .put("logo", logo)
                            .put("tvgId", tvgId)
                            .put("url", url)
                            .put("type", type)
                        pending.put(item)
                        totals[type] = (totals[type] ?: 0)+1
                        groupTotals[groupKey] = (groupTotals[groupKey] ?: 0)+1
                        if (pending.length() >= 75) send(false)
                    } else skipped++
                    title = ""; group = ""; logo = ""; tvgId = ""
                }
            }
        }
        send(true)
    }

    private fun managedBase():String = pairing.getString("panel_url", BuildConfig.CONTROL_URL).orEmpty().trimEnd('/')

    private fun managedGet(path:String):JSONObject {
        val base=managedBase()
        if(!base.startsWith("https://",true)) error("Panel HTTPS address is not configured")
        val token=pairing.getString("device_token", "").orEmpty()
        if(token.isBlank()) error("Enter your activation PIN first")
        val conn=(URL(base+path).openConnection() as HttpURLConnection).apply {
            connectTimeout=12000;readTimeout=45000
            setRequestProperty("X-Device-Token",token)
            setRequestProperty("Accept","application/json")
        }
        return try {
            if(conn.responseCode !in 200..299){
                if(conn.responseCode==401) error("Activation expired. Open Settings to enter a new PIN.")
                error("Panel returned HTTP "+conn.responseCode)
            }
            val payload=conn.inputStream.bufferedReader().use {it.readText()}
            JSONObject(payload)
        }finally {conn.disconnect()}
    }

    private fun syncManagedCatalog() {
        if(pairing.getString("device_token","").isNullOrBlank())return
        io.execute {
            try {
                val caps=mapOf("live" to 12000,"movie" to 3500,"show" to 3500)
                for((kind,cap) in caps){
                    var offset=0
                    var revision=0
                    while(offset<cap){
                        val j=managedGet("/api/device/catalog?kind=$kind&offset=$offset&limit=150")
                        val items=j.optJSONArray("items") ?: JSONArray()
                        revision=j.optInt("revision",0)
                        val next=j.optInt("next_offset",-1)
                        val done=next<0 || next>=cap || items.length()==0
                        val encoded=items.toString()
                        runOnUiThread {browser.evaluateJavascript("window.onManagedCatalogChunk('$kind',$encoded,$done,$revision)",null)}
                        if(done)break
                        offset=next
                    }
                }
            }catch(e:Exception){reportError("Master lineup: "+(e.message?:"Connection failed"))}
        }
    }

    private fun fetchManagedLocals(zip:String) {
        if(!Regex("^\\d{5}$").matches(zip)){
            reportError("Enter a 5-digit ZIP code");return
        }
        io.execute {
            try {
                val response=managedGet("/api/device/locals?zipcode=$zip")
                runOnUiThread {browser.evaluateJavascript("window.onManagedLocals("+response.toString()+")",null)}
            }catch(e:Exception){reportError("ZIP lookup: "+(e.message?:"Unable to search"))}
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val direction = when(event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> "left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
            KeyEvent.KEYCODE_DPAD_UP -> "up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "down"
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "select"
            KeyEvent.KEYCODE_MENU -> "menu"
            else -> null
        }
        if (direction != null && ::browser.isInitialized) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                browser.evaluateJavascript("window.petalixRemote && window.petalixRemote('$direction')", null)
                if (uiSounds && direction != "select" && direction != "menu") {
                    try {
                        if (focusTone == null) focusTone = ToneGenerator(AudioManager.STREAM_MUSIC, 12)
                        focusTone?.startTone(ToneGenerator.TONE_PROP_BEEP, 18)
                    } catch (_: Exception) {}
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
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
                    setRequestProperty("User-Agent", "PetalixTV/0.2 (Android TV)")
                    setRequestProperty("Accept-Encoding", "gzip")
                }
                try {
                    if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
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
                    val text = buffer.toString(Charsets.UTF_8.name())
                    val payload = JSONObject.quote(text)
                    val label = JSONObject.quote(name)
                    runOnUiThread { browser.evaluateJavascript("window.$callback($payload,$label)", null) }
                } finally { conn.disconnect() }
            } catch (e: Exception) { reportError(e.message ?: "Unable to download source") }
        }
    }

    private fun reportError(error: String) {
        val safe = JSONObject.quote(error)
        runOnUiThread { browser.evaluateJavascript("window.onNativeError($safe)", null) }
    }

    @Deprecated("Used for Android TV document picker compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != fileRequestCode || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        io.execute {
            try {
                val stream = contentResolver.openInputStream(uri) ?: error("Could not open playlist")
                parsePlaylist(stream, "Local file")
            } catch (e: Exception) { reportError(e.message ?: "Could not read file") }
        }
    }

    @Deprecated("Use Activity Back navigation")
    override fun onBackPressed() {
        if (::browser.isInitialized) {
            browser.evaluateJavascript("window.handleBack && window.handleBack()", null)
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        // On fresh launch the PIN activity is opened before the WebView exists.
        // Never read a lateinit WebView while closing that redirect activity.
        if (::browser.isInitialized) {
            browser.removeJavascriptInterface("AndroidBridge")
            browser.destroy()
        }
        focusTone?.release()
        io.shutdownNow()
        super.onDestroy()
    }
}
