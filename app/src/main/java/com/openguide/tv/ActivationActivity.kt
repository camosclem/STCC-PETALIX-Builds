package com.openguide.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.openguide.tv.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

/** Native, remote-focusable first-launch pairing. Device never needs a provider URL/login. */
class ActivationActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var pin: EditText
    private lateinit var panel: EditText
    private lateinit var message: TextView
    private val prefs by lazy { getSharedPreferences("petalix_pairing", MODE_PRIVATE) }
    private fun dp(value:Int) = (value*resources.displayMetrics.density).toInt()
    private fun label(value:String, size:Float, color:Int):TextView = TextView(this).apply {
        text=value; textSize=size; setTextColor(color)
        setPadding(dp(10),dp(10),dp(10),dp(10))
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility=(View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER
            setBackgroundColor(Color.rgb(8,12,20))
            setPadding(dp(44),dp(24),dp(44),dp(24))
        }
        val heading=label("PETALIX",40f,Color.rgb(65,222,200)).apply {
            typeface=Typeface.create("sans-serif-black",Typeface.BOLD);gravity=Gravity.CENTER
        }
        root.addView(heading)
        root.addView(label("Welcome. Enter your 8-digit activation PIN.",22f,Color.WHITE).apply{gravity=Gravity.CENTER})
        root.addView(label("Your channel lineup is assigned automatically.",16f,Color.LTGRAY).apply{gravity=Gravity.CENTER})
        pin=EditText(this).apply {
            hint="8-digit PIN"
            filters=arrayOf(InputFilter.LengthFilter(8))
            inputType=InputType.TYPE_CLASS_NUMBER
            gravity=Gravity.CENTER
            textSize=31f
            isSingleLine=true
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.rgb(29,43,58))
            setPadding(dp(20),dp(12),dp(20),dp(12))
        }
        root.addView(pin,LinearLayout.LayoutParams(dp(320),dp(70)).apply{topMargin=dp(18)})
        panel=EditText(this).apply {
            hint="https://your-petalix-beta-panel.example"
            setText(prefs.getString("panel_url",BuildConfig.CONTROL_URL).orEmpty())
            setSingleLine(true)
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(Color.WHITE);setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.rgb(24,30,43))
            setPadding(dp(12),dp(8),dp(12),dp(8))
        }
        if(BuildConfig.CONTROL_URL.isBlank()){
            root.addView(label("BETA SETUP — Panel HTTPS address (admin only)",13f,Color.LTGRAY))
            root.addView(panel,LinearLayout.LayoutParams(dp(520),dp(55)))
        }
        val connect=Button(this).apply {text="Activate Petalix";isAllCaps=false;textSize=19f}
        root.addView(connect,LinearLayout.LayoutParams(dp(300),dp(56)).apply{topMargin=dp(16)})
        message=label("",14f,Color.rgb(255,194,173)).apply{gravity=Gravity.CENTER}
        root.addView(message)
        val demo=Button(this).apply {text="Explore without a PIN (beta preview)";isAllCaps=false}
        if(BuildConfig.DEBUG){
            root.addView(demo,LinearLayout.LayoutParams(dp(365),dp(50)))
            demo.setOnClickListener {
                prefs.edit().putBoolean("demo_mode",true).apply()
                startActivity(Intent(this,MainActivity::class.java));finish()
            }
        }
        setContentView(root)
        connect.setOnClickListener {activate(connect)}
        pin.requestFocus()
    }
    private fun activate(button:Button) {
        val entered=pin.text.toString().trim()
        val server=(if(BuildConfig.CONTROL_URL.isNotBlank()) BuildConfig.CONTROL_URL else panel.text.toString()).trim().trimEnd('/')
        if(!entered.matches(Regex("\\d{8}"))){message.text="Enter the 8-digit PIN";return}
        if(!server.startsWith("https://",true)){message.text="Panel must use HTTPS";return}
        button.isEnabled=false;message.text="Connecting securely..."
        val device=prefs.getString("device_id",null) ?: UUID.randomUUID().toString().also{
            prefs.edit().putString("device_id",it).apply()
        }
        worker.execute {
            try {
                val con=(URL("$server/api/device/activate").openConnection() as HttpURLConnection).apply {
                    requestMethod="POST";doOutput=true
                    connectTimeout=15000;readTimeout=20000
                    setRequestProperty("Content-Type","application/json")
                }
                try {
                    val payload=JSONObject().put("pin",entered).put("device_id",device).toString()
                    con.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                    if(con.responseCode !in 200..299) throw IllegalStateException("Activation rejected (HTTP ${con.responseCode}). Check your PIN and device slots.")
                    val data=JSONObject(con.inputStream.bufferedReader().use {it.readText()})
                    val token=data.getString("device_token")
                    prefs.edit().putString("panel_url",server).putString("device_token",token)
                        .putString("customer",data.optString("customer"))
                        .putBoolean("demo_mode",false).apply()
                    runOnUiThread {startActivity(Intent(this,MainActivity::class.java));finish()}
                } finally {con.disconnect()}
            }catch(e:Exception){
                runOnUiThread {button.isEnabled=true;message.text=e.message?:"Could not reach activation server"}
            }
        }
    }
    override fun onDestroy(){worker.shutdownNow();super.onDestroy()}
}
