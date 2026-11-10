package com.nuvio.tv.petalix

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.nuvio.tv.MainActivity
import com.nuvio.tv.livetv.model.LiveChannel
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
            if (setup) {
                manager.dismissLocalsPrompt()
                startActivity(Intent(this, MainActivity::class.java).putExtra("skipLocalsOnce", true))
            }
            finish()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
        fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(36), dp(14), dp(36), dp(14))
            setBackgroundColor(Color.rgb(15, 16, 22))
        }
        fun line(value: String, size: Float) = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
            setPadding(0, dp(4), 0, dp(4))
        }
        fun button(label: String) = Button(this).apply { text = label; isAllCaps = false }
        fun row() = LinearLayout(this).also { content.addView(it) }

        content.addView(line("My Locals", 26f))
        val input = row()
        val zip = EditText(this).apply {
            id = View.generateViewId()
            hint = "5-digit ZIP code"
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(5))
            setText(savedInstanceState?.getString("zip") ?: manager.localsZip)
        }
        input.addView(zip, LinearLayout.LayoutParams(dp(190), dp(50)))
        val lookup = button("Find stations")
        input.addView(lookup)
        val toggle = button("Browse all locals").apply { visibility = View.GONE }
        input.addView(toggle)
        val message = line("Enter your U.S. ZIP code to find nearby stations in your playlist.", 16f)
        content.addView(message)
        val search = EditText(this).apply {
            hint = "Search by station name or call sign"
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            textSize = 16f
            setSingleLine(true)
            visibility = View.GONE
        }
        content.addView(search)
        val list = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE
            visibility = View.INVISIBLE
        }
        content.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = row()
        val save = button("Save My Locals").apply { isEnabled = false }
        actions.addView(save)
        val cancel = button(if (setup) "Choose later" else "Back")
        actions.addView(cancel)
        setContentView(content)

        var result: PetalixLocalStations.Result? = null
        var matchedZip: String? = null
        var showAll = false
        var busy = false
        var visibleStations = emptyList<LiveChannel>()
        val selected = linkedSetOf<String>()

        fun renderStations() {
            val data = result ?: return
            val query = search.text.toString().trim()
            val stations = if (showAll) data.available else data.nearby
            visibleStations = stations.filter { channel ->
                listOfNotNull(channel.name, channel.tvgName, channel.tvgId, channel.group)
                    .any { it.contains(query, ignoreCase = true) }
            }
            val labels = visibleStations.map { it.name }
            list.clearChoices()
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, labels)
            visibleStations.forEachIndexed { index, channel -> list.setItemChecked(index, channel.key in selected) }
            toggle.text = if (showAll) "Show nearby locals" else "Browse all locals"
            message.text = if (data.available.isEmpty()) {
                "${data.location} · No locals found in your playlist. If it is still loading, try again shortly."
            } else {
                "${data.location} · ${visibleStations.size} ${if (showAll) "playlist" else "nearby"} stations · ${selected.size} selected"
            }
            save.isEnabled = !busy && selected.isNotEmpty()
        }

        fun working(value: Boolean) {
            busy = value
            zip.isEnabled = !value
            lookup.isEnabled = !value
            toggle.isEnabled = !value
            search.isEnabled = !value
            list.isEnabled = !value
            save.isEnabled = !value && matchedZip != null && selected.isNotEmpty()
        }

        zip.doAfterTextChanged {
            result = null
            matchedZip = null
            selected.clear()
            list.visibility = View.INVISIBLE
            search.visibility = View.GONE
            toggle.visibility = View.GONE
            save.isEnabled = false
        }
        search.doAfterTextChanged { renderStations() }
        toggle.setOnClickListener {
            showAll = !showAll
            renderStations()
        }
        list.setOnItemClickListener { _, _, index, _ ->
            val key = visibleStations[index].key
            if (list.isItemChecked(index)) selected.add(key) else selected.remove(key)
            val data = result ?: return@setOnItemClickListener
            message.text = "${data.location} · ${visibleStations.size} ${if (showAll) "playlist" else "nearby"} stations · ${selected.size} selected"
            save.isEnabled = selected.isNotEmpty()
        }
        lookup.setOnClickListener {
            val code = zip.text.toString()
            if (!code.matches(Regex("[0-9]{5}"))) {
                message.text = "Enter a five-digit ZIP code."
                zip.requestFocus()
                return@setOnClickListener
            }
            working(true)
            message.text = "Finding local stations in your playlist…"
            lifecycleScope.launch {
                try {
                    val found = manager.lookupLocals(code)
                    val previous = if (code == manager.localsZip) manager.localKeys() else emptyList()
                    matchedZip = code
                    selected.clear()
                    selected.addAll(previous.intersect(found.available.map { it.key }.toSet()))
                    showAll = found.nearby.isEmpty()
                    result = found
                    search.setText("")
                    list.visibility = View.VISIBLE
                    search.visibility = if (found.available.isNotEmpty()) View.VISIBLE else View.GONE
                    toggle.visibility = search.visibility
                    renderStations()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    message.text = e.message ?: "Lookup failed. Please try again."
                } finally {
                    working(false)
                }
                if (visibleStations.isNotEmpty()) list.requestFocus() else lookup.requestFocus()
            }
        }
        save.setOnClickListener {
            val code = matchedZip ?: return@setOnClickListener
            working(true)
            lifecycleScope.launch {
                try {
                    manager.saveLocals(code, selected.toList())
                    leave()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    message.text = e.message ?: "Could not save My Locals. Please try again."
                    working(false)
                }
            }
        }
        cancel.setOnClickListener { leave() }
        zip.requestFocus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val content = findViewById<android.view.ViewGroup>(android.R.id.content)
        val form = content.getChildAt(0) as? LinearLayout
        val input = form?.getChildAt(1) as? LinearLayout
        outState.putString("zip", (input?.getChildAt(0) as? EditText)?.text?.toString())
    }
}
