package com.nuvio.tv

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.Window
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

private val tutorialKeys = mapOf(
    KeyEvent.KEYCODE_DPAD_LEFT to "left", KeyEvent.KEYCODE_DPAD_RIGHT to "right",
    KeyEvent.KEYCODE_DPAD_UP to "up", KeyEvent.KEYCODE_DPAD_DOWN to "down",
    KeyEvent.KEYCODE_DPAD_CENTER to "ok", KeyEvent.KEYCODE_ENTER to "ok",
    KeyEvent.KEYCODE_NUMPAD_ENTER to "ok", KeyEvent.KEYCODE_BACK to "back",
    KeyEvent.KEYCODE_ESCAPE to "back", KeyEvent.KEYCODE_CHANNEL_UP to "chUp",
    KeyEvent.KEYCODE_CHANNEL_DOWN to "chDown", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "play",
    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD to "ff", KeyEvent.KEYCODE_MEDIA_REWIND to "rew",
    KeyEvent.KEYCODE_MENU to "settings"
)

@Composable
internal fun PetalixRemoteGuide(onClose: () -> Unit) {
    val context = LocalContext.current
    val close by rememberUpdatedState(onClose)
    DisposableEffect(context) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val page = WebView(context).apply {
            setBackgroundColor(Color.rgb(19, 20, 21))
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            settings.useWideViewPort = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript("document.querySelector('[data-model=onn]').focus()", null)
                }
            }
        }
        dialog.setContentView(page)
        dialog.setOnCancelListener { close() }
        dialog.setOnKeyListener { _, keyCode, event ->
            val key = tutorialKeys[keyCode] ?: return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                page.evaluateJavascript(
                    "typeof window.petalixTutorialInput === 'function' && window.petalixTutorialInput('$key')"
                ) { handled -> if (key == "back" && handled != "true") close() }
            }
            true
        }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.rgb(19, 20, 21)))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        page.requestFocus()
        val html = context.assets.open("petalix/remote-guide/remote.html").bufferedReader().use { it.readText() }
        page.loadDataWithBaseURL("https://petalix.invalid/", html, "text/html", "UTF-8", null)
        onDispose {
            dialog.setOnCancelListener(null)
            dialog.dismiss()
            (page.parent as? ViewGroup)?.removeView(page)
            page.destroy()
        }
    }
}
