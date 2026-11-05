package com.nuvio.tv.petalix

import android.app.AlertDialog
import android.graphics.Color
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import com.nuvio.tv.MainActivity
import kotlinx.coroutines.*

/** App-only remote help. Each session needs approval on this TV. No screen capture. */
object PetalixRemoteControl {
    private var job:Job?=null
    private var banner:View?=null
    private var dialog:AlertDialog?=null
    private var sessionId=""
    private var requestedAction=""
    private val codes=mapOf("UP" to KeyEvent.KEYCODE_DPAD_UP,"DOWN" to KeyEvent.KEYCODE_DPAD_DOWN,
        "LEFT" to KeyEvent.KEYCODE_DPAD_LEFT,"RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "OK" to KeyEvent.KEYCODE_DPAD_CENTER,"BACK" to KeyEvent.KEYCODE_BACK,"MENU" to KeyEvent.KEYCODE_MENU,
        "PLAY_PAUSE" to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,"REWIND" to KeyEvent.KEYCODE_MEDIA_REWIND,
        "FAST_FORWARD" to KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)

    fun attach(activity:MainActivity,coordinator:ManagedCoordinator) {
        job?.cancel()
        val prefs=activity.getSharedPreferences("petalix_remote_help",0)
        job=activity.lifecycleScope.launch {
            while(isActive) {
                var active=false
                try {
                    if(coordinator.ready) {
                        val lastSession=prefs.getString("session","").orEmpty()
                        val ack=if(lastSession==sessionId)prefs.getLong("ack",0) else 0L
                        val action=requestedAction
                        val response=coordinator.remoteControl(sessionId,action,ack)
                        if(action.isNotBlank()&&requestedAction==action)requestedAction=""
                        val session=response.optJSONObject("session")
                        val id=session?.optString("id").orEmpty()
                        val state=session?.optString("state").orEmpty()
                        if(id.isBlank()||state in listOf("ended","declined")) {
                            removeBanner();dialog?.dismiss();dialog=null;sessionId=""
                        } else {
                            sessionId=id
                            if(state=="pending"&&dialog==null&&activity.hasWindowFocus()) {
                                dialog=AlertDialog.Builder(activity).setTitle("Allow remote help?")
                                    .setMessage("Your provider wants to control Petalix on this TV for up to 10 minutes. They cannot see your screen. You can end the session at any time.")
                                    .setPositiveButton("Allow") { _,_->
                                        prefs.edit().putString("consent",id).putString("session",id).putLong("ack",0).apply()
                                        requestedAction="accept";dialog=null
                                    }
                                    .setNegativeButton("Decline") { _,_->requestedAction="decline";dialog=null}
                                    .setOnCancelListener {requestedAction="decline";dialog=null}.create().also { it.show() }
                            }
                            if(state=="active") {
                                if(prefs.getString("consent","")!=id)requestedAction="end"
                                else {
                                    active=true
                                    showBanner(activity)
                                    val commands=response.optJSONArray("commands")
                                    if(commands!=null)for(i in 0 until commands.length()) {
                                        if(!isActive||!activity.hasWindowFocus()||requestedAction=="end")break
                                        val command=commands.getJSONObject(i)
                                        val seq=command.getLong("seq")
                                        if(seq<=prefs.getLong("ack",0))continue
                                        val code=codes[command.optString("key")]?:continue
                                        // Record before delivery: failed polling must never replay navigation.
                                        check(prefs.edit().putString("session",id).putLong("ack",seq).commit())
                                        if(System.currentTimeMillis()-command.getLong("at")>5000)continue
                                        val time=SystemClock.uptimeMillis()
                                        activity.dispatchKeyEvent(KeyEvent(time,time,KeyEvent.ACTION_DOWN,code,0))
                                        activity.dispatchKeyEvent(KeyEvent(time,time+1,KeyEvent.ACTION_UP,code,0))
                                    }
                                }
                            }
                        }
                    }
                } catch(cancel:CancellationException){throw cancel}
                  catch(_:Exception){removeBanner()}
                delay(if(active)300L else 3000L)
            }
        }
    }
    private fun showBanner(activity:MainActivity) {
        if(banner!=null)return
        val root=activity.findViewById<FrameLayout>(android.R.id.content)
        val focused=activity.currentFocus
        val dp=activity.resources.displayMetrics.density
        val button=Button(activity).apply {
            text="Remote help active · End session";setAllCaps(false);textSize=12f
            setTextColor(Color.WHITE);setBackgroundColor(Color.rgb(20,65,115))
            setOnClickListener {requestedAction="end";removeBanner()}
        }
        root.addView(button,FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,(40*dp).toInt(),Gravity.TOP or Gravity.END).apply {topMargin=(8*dp).toInt();rightMargin=(12*dp).toInt()})
        banner=button;focused?.requestFocus()
    }
    private fun removeBanner(){banner?.let{(it.parent as? ViewGroup)?.removeView(it)};banner=null}
    fun detach(activity:MainActivity,coordinator:ManagedCoordinator) {
        job?.cancel();job=null
        dialog?.dismiss();dialog=null;removeBanner()
        val id=sessionId;sessionId="";requestedAction=""
        if(id.isNotBlank())activity.lifecycleScope.launch {runCatching{coordinator.remoteControl(id,"end",0)}}
    }
}
