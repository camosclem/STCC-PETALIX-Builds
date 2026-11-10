package com.nuvio.tv.petalix

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.text.InputFilter
import android.text.TextWatcher
import android.text.Editable
import android.view.Gravity
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.nuvio.tv.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PetalixLocalsActivity : ComponentActivity() {
    @Inject lateinit var manager: PetalixManagedCoordinator
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val setup = intent.getBooleanExtra("setup", false)
        fun leave() {
            if (setup) startActivity(Intent(this, MainActivity::class.java).putExtra("skipLocalsOnce", true))
            finish()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
        fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(36), dp(18), dp(36), dp(18))
            setBackgroundColor(Color.rgb(15, 16, 22))
        }
        fun line(value: String, size: Float) = TextView(this).apply {
            text = value; textSize = size; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setPadding(0, dp(5), 0, dp(5))
        }
        content.addView(line("Local stations", 26f))
        content.addView(line("Enter your ZIP, then confirm the stations available in Petalix.", 16f))
        val zip = EditText(this).apply {
            hint = "5-digit ZIP code"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE)
            textSize = 22f; gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true); filters = arrayOf(InputFilter.LengthFilter(5))
            setText(manager.localsZip)
        }
        content.addView(zip, LinearLayout.LayoutParams(dp(360), dp(52)))
        fun button(label: String) = Button(this).apply {
            text = label; isAllCaps = false
            content.addView(this, LinearLayout.LayoutParams(dp(480), dp(48)))
        }
        val lookup = button("Find my local stations")
        val message = line("Suggested markets depend on the stations in your panel playlist.", 15f)
        content.addView(message, LinearLayout.LayoutParams(-1, dp(108)))
        val save = button("Use these local stations").apply { isEnabled = false }
        val fallback = button("Use NC, SC and MN locals")
        val cancel = button("Choose later / Back")
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(content) })
        var matchedZip: String? = null
        var busy = false
        fun working(value: Boolean) {
            busy = value; zip.isEnabled = !value; lookup.isEnabled = !value
            fallback.isEnabled = !value; cancel.isEnabled = !value
            save.isEnabled = !value && matchedZip != null
        }
        zip.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                matchedZip = null; save.isEnabled = false
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        lookup.setOnClickListener {
            val code = zip.text.toString()
            if (!code.matches(Regex("[0-9]{5}"))) { message.text = "Enter a five-digit ZIP code."; return@setOnClickListener }
            matchedZip = null; working(true); message.text = "Finding available local stations…"
            lifecycleScope.launch {
                try {
                    val result = manager.lookupLocals(code)
                    val count = result.optInt("count")
                    val stations = result.optJSONArray("channels")
                    val names = (0 until minOf(stations?.length() ?: 0, 10)).map { stations!!.getJSONObject(it).optString("name") }
                    if (count > 0) {
                        matchedZip = code
                        message.text = "${result.optString("marketName")} · $count stations\n${names.joinToString(" · ")}${if (count > 10) " …" else ""}\nConfirm these are your local stations."
                    } else message.text = "No local stations for ${result.optString("marketName")} are in your panel playlist. Try another ZIP or use NC, SC and MN."
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { message.text = e.message ?: "Lookup failed. Please retry." }
                finally { working(false) }
                if (matchedZip != null) save.requestFocus()
            }
        }
        fun persist(code: String?) {
            if (busy) return
            working(true); message.text = "Saving your locals and refreshing Live TV…"
            lifecycleScope.launch {
                try { manager.saveLocals(code); leave() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { message.text = e.message ?: "Could not save. Please retry."; working(false) }
            }
        }
        save.setOnClickListener { matchedZip?.let { persist(it) } }
        fallback.setOnClickListener { persist(null) }
        cancel.setOnClickListener { leave() }
        zip.requestFocus()
    }
}
