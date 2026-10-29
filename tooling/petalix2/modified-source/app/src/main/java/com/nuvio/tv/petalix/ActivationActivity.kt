package com.nuvio.tv.petalix

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
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
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ActivationActivity:ComponentActivity(){
    @Inject lateinit var manager:ManagedCoordinator
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        val density=resources.displayMetrics.density
        fun dp(n:Int)=(n*density).toInt()
        val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(40),dp(25),dp(40),dp(25));setBackgroundColor(Color.rgb(15,16,22))}
        fun line(text:String,size:Float)=TextView(this).apply{this.text=text;textSize=size;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(0,dp(10),0,dp(10))}
        content.addView(ImageView(this).apply{setImageResource(R.drawable.app_logo_wordmark);scaleType=ImageView.ScaleType.FIT_CENTER},LinearLayout.LayoutParams(dp(280),dp(85)))
        content.addView(line("Welcome to Petalix Preview",24f))
        content.addView(line("Enter the activation PIN from your provider.",16f))
        val pin=EditText(this).apply{hint="8-digit activation PIN";setHintTextColor(Color.GRAY);setTextColor(Color.WHITE);textSize=22f;gravity=Gravity.CENTER;inputType=InputType.TYPE_CLASS_NUMBER;setSingleLine(true);filters=arrayOf(android.text.InputFilter.LengthFilter(8))}
        content.addView(pin,LinearLayout.LayoutParams(dp(340),dp(60)))
        val message=line("Live TV · Movies & series · Sports Hub",14f)
        val button=Button(this).apply{text="Activate this TV";isAllCaps=false}
        content.addView(button,LinearLayout.LayoutParams(dp(340),dp(58)))
        content.addView(message)
        val changePin=line("Use a different activation PIN",13f).apply { setTextColor(Color.LTGRAY);isFocusable=true;isClickable=true }
        content.addView(changePin)
        lateinit var restoreSaved:()->Unit
        fun openApp(){startActivity(Intent(this@ActivationActivity,MainActivity::class.java));finish()}
        fun showPin(){
            pin.visibility=android.view.View.VISIBLE
            changePin.visibility=android.view.View.GONE
            button.text="Activate this TV";button.isEnabled=true
            button.setOnClickListener{
            val code=pin.text.toString().trim()
            if(!code.matches(Regex("[0-9]{8}"))){message.text="Enter the 8-digit PIN from Petalix Control.";return@setOnClickListener}
            button.isEnabled=false;message.text="Setting up your TV…"
            lifecycleScope.launch{try{manager.activate(code);openApp()}catch(e:Exception){if(e is ActivationRequired && manager.hasSavedActivation)restoreSaved() else {message.text=e.message?:"Activation failed. Try again.";button.isEnabled=true}}}
        }
        }
        restoreSaved = {
            pin.visibility=android.view.View.GONE
            changePin.visibility=android.view.View.VISIBLE
            button.text="Retry connection";button.isEnabled=false
            message.text="Restoring your saved activation…"
            button.setOnClickListener{restoreSaved()}
            lifecycleScope.launch {
                try { manager.refresh();openApp() }
                catch(e:ActivationRequired){message.text="This TV’s saved activation could not be confirmed. Retry the connection or contact your provider. Your saved login is retained.";button.isEnabled=true;button.requestFocus()}
                catch(e:kotlinx.coroutines.CancellationException){throw e}
                catch(_:Exception){message.text="Cannot check access right now. Check your connection, then select Retry connection. Your saved activation is retained.";button.isEnabled=true;button.requestFocus()}
            }
        }
        changePin.setOnClickListener{showPin();message.text="Enter the activation PIN from your provider.";pin.requestFocus()}
        showPin()
        if(manager.hasSavedActivation)restoreSaved()
        setContentView(content,ViewGroup.LayoutParams(-1,-1))
        if(!manager.hasSavedActivation)pin.requestFocus()
    }
}
