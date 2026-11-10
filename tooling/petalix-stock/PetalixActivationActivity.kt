package com.nuvio.tv.petalix

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.nuvio.tv.MainActivity
import com.nuvio.tv.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PetalixActivationActivity : ComponentActivity() {
    @Inject lateinit var manager: PetalixManagedCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(n: Int) = (n * density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(40), dp(25), dp(40), dp(25))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(31, 43, 61), Color.rgb(10, 14, 23), Color.rgb(10, 14, 23))
            )
        }
        fun line(value: String, size: Float) = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }
        content.addView(ImageView(this).apply {
            setImageResource(R.drawable.app_logo_wordmark)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(440), dp(110)))
        val message = line("Activate with your existing 8-digit Petalix PIN.", 15f)
        val pin = EditText(this).apply {
            hint = "8-digit activation PIN"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            filters = arrayOf(android.text.InputFilter.LengthFilter(8))
        }
        content.addView(pin, LinearLayout.LayoutParams(dp(360), dp(60)))
        val button = Button(this).apply {
            text = "Activate this TV"
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList(
                arrayOf(
                    intArrayOf(-android.R.attr.state_enabled),
                    intArrayOf(android.R.attr.state_focused),
                    intArrayOf()
                ),
                intArrayOf(Color.rgb(46, 52, 61), Color.rgb(166, 30, 48), Color.rgb(90, 35, 47))
            )
        }
        content.addView(button, LinearLayout.LayoutParams(dp(360), dp(58)))
        content.addView(message)
        setContentView(content, ViewGroup.LayoutParams(-1, -1))

        fun enterApp() {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        lateinit var activateFromPin: View.OnClickListener
        fun showPin(reason: String) {
            pin.visibility = View.VISIBLE
            button.visibility = View.VISIBLE
            button.isEnabled = true
            button.text = "Activate this TV"
            button.setOnClickListener(activateFromPin)
            message.text = reason
            pin.requestFocus()
        }

        activateFromPin = View.OnClickListener {
            val code = pin.text.toString().trim()
            if (!code.matches(Regex("[0-9]{8}"))) {
                message.text = "Enter the 8-digit PIN from Petalix Control."
                return@OnClickListener
            }
            button.isEnabled = false
            message.text = "Connecting to Petalix…"
            lifecycleScope.launch {
                try {
                    manager.activate(code)
                    enterApp()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    showPin(e.message ?: "Activation failed. Please retry.")
                }
            }
        }

        button.setOnClickListener(activateFromPin)

        if (manager.hasSavedActivation) {
            // No new PIN for installed customers unless the panel rejects their session.
            pin.visibility = View.GONE
            button.visibility = View.GONE
            message.text = "Restoring your existing Petalix activation…"
            lifecycleScope.launch {
                try {
                    manager.refresh()
                    enterApp()
                } catch (e: CancellationException) { throw e }
                catch (e: PetalixActivationRequired) {
                    showPin("Your saved activation could not be confirmed. Contact support or enter a new PIN.")
                } catch (e: Exception) {
                    message.text = "Cannot contact Petalix Control. Existing activation is saved."
                    button.visibility = View.VISIBLE
                    button.text = "Retry saved activation"
                    button.isEnabled = true
                    button.setOnClickListener {
                        button.isEnabled = false
                        lifecycleScope.launch {
                            try { manager.refresh(); enterApp() }
                            catch (error: CancellationException) { throw error }
                            catch (error: PetalixActivationRequired) {
                                showPin("Your activation was not accepted. Contact support or enter a new PIN.")
                            }
                            catch (_: Exception) {
                                message.text = "Still offline. Please check your connection and retry."
                                button.isEnabled = true
                            }
                        }
                    }
                    button.requestFocus()
                }
            }
        } else pin.requestFocus()
    }
}
