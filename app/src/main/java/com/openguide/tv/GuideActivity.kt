package com.openguide.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Native Android TV guide: D-pad focus, groups, now/next, live player preview. */
class GuideActivity : Activity() {
    private lateinit var player:ExoPlayer
    private lateinit var preview:PlayerView
    private lateinit var details:TextView
    private lateinit var list:LinearLayout
    private lateinit var groups:LinearLayout
    private var channels=JSONArray()
    private var group="All channels"
    private val handler=Handler(Looper.getMainLooper())
    private var pendingPreview:Runnable?=null
    private var activeUrl=""
    private var page=0
    private fun dp(value:Int) = (value*resources.displayMetrics.density).toInt()
    private fun color(hex:String) = Color.parseColor(hex)
    private fun rounded(bg:Int,stroke:Int?=null):GradientDrawable = GradientDrawable().apply {
        setColor(bg);cornerRadius=dp(9).toFloat()
        stroke?.let{setStroke(dp(1),it)}
    }
    private fun text(value:String,size:Float,fg:Int=Color.WHITE) = TextView(this).apply {
        text=value;textSize=size;setTextColor(fg);setPadding(dp(14),dp(10),dp(14),dp(10))
    }
    private fun button(value:String):Button = Button(this).apply {
        text=value;textSize=15f;isAllCaps=false;gravity=Gravity.CENTER_VERTICAL or Gravity.START
        setTextColor(Color.WHITE);background=rounded(color("#202a3b"),color("#455773"))
        isFocusable=true;isFocusableInTouchMode=false
        stateListAnimator=null
        setOnFocusChangeListener{v,focused->
            background=rounded(color(if(focused)"#225683" else "#202a3b"),color(if(focused)"#73b4ff" else "#455773"))
            if(focused)v.post{v.requestRectangleOnScreen(android.graphics.Rect(0,0,v.width,v.height),false)}
        }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility=(View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        player=ExoPlayer.Builder(this).build()
        channels=try{
            JSONObject(File(cacheDir,"petalix-guide.json").readText()).optJSONArray("channels")?:JSONArray()
        }catch(_:Exception){JSONArray()}
        buildUI()
    }
    private fun buildUI(){
        val root=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL;setBackgroundColor(color("#090e17"))
            setPadding(dp(8),dp(8),dp(8),dp(8))
        }
        val side=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        side.addView(text("PETALIX",24f,color("#4ae2cc")).apply{typeface=Typeface.DEFAULT_BOLD})
        side.addView(text("CHANNEL GROUPS",11f,color("#aebdd2")))
        val sideScroller=ScrollView(this).apply{isFillViewport=true;isFocusable=false}
        groups=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        sideScroller.addView(groups)
        side.addView(sideScroller,LinearLayout.LayoutParams(dp(225),0,1f))
        root.addView(side,LinearLayout.LayoutParams(dp(235),ViewGroup.LayoutParams.MATCH_PARENT))

        val center=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(dp(10),0,0,dp(8))}
        preview=PlayerView(this).apply{
            player=this@GuideActivity.player
            useController=false
            isFocusable=false
            setBackgroundColor(Color.BLACK)
        }
        top.addView(preview,LinearLayout.LayoutParams(dp(370),dp(196)))
        val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(10),dp(8),0)}
        info.addView(text("LIVE TV PREVIEW",12f,color("#5fe6d2")))
        details=text("Use the remote to browse channels. Preview will follow your selection.",20f)
        info.addView(details)
        info.addView(text("OK: Watch full screen     Back: Exit guide",13f,color("#adbbcf")))
        top.addView(info,LinearLayout.LayoutParams(0,dp(196),1f))
        center.addView(top)
        val header=text("GUIDE  •  NOW & NEXT",16f,color("#a8c0df"))
        center.addView(header)
        val scroll=ScrollView(this).apply{isFocusable=false;isFillViewport=true}
        list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        scroll.addView(list)
        center.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(center,LinearLayout.LayoutParams(0,-1,1f))
        setContentView(root)
        drawGroups();renderChannels()
    }
    private fun drawGroups(){
        groups.removeAllViews()
        val names=linkedSetOf("All channels")
        for(i in 0 until channels.length()) {
            val ch=channels.optJSONObject(i)?:continue
            names.add(ch.optString("group","Other").ifBlank{"Other"})
            if(names.size>=100)break
        }
        for(name in names){
            val b=button(if(name==group)"● $name" else name)
            b.setOnClickListener{group=name;page=0;drawGroups();renderChannels()}
            groups.addView(b,LinearLayout.LayoutParams(-1,dp(52)).apply{bottomMargin=dp(5)})
        }
    }
    private fun allInGroup():List<JSONObject>{
        val matched=mutableListOf<JSONObject>()
        for(i in 0 until channels.length()){
            val ch=channels.optJSONObject(i)?:continue
            if(group=="All channels"||ch.optString("group")==group)matched.add(ch)
        }
        return matched
    }
    private fun renderChannels(){
        list.removeAllViews()
        val rows=allInGroup()
        val limit=150
        val start=page*limit
        val subset=rows.drop(start).take(limit)
        if(page>0){val previous=button("↑ Previous channels");previous.setOnClickListener{page--;renderChannels()};list.addView(previous)}
        subset.forEachIndexed{ix,ch->
            val label=ch.optString("name","Channel")
            val now=ch.optString("now").ifBlank{"No current listing"}
            val next=ch.optString("next")
            val b=button("${start+ix+1}.  $label    ·    $now"+(if(next.isNotBlank())"   |   Next: $next" else ""))
            b.textSize=14f
            b.setOnFocusChangeListener{view,focused->
                b.background=rounded(color(if(focused)"#275583" else "#202a3b"),color(if(focused)"#7ec5ff" else "#455773"))
                if(focused){
                    view.post{view.requestRectangleOnScreen(android.graphics.Rect(0,0,view.width,view.height),false)}
                    details.text="$label\nNow: $now"+(if(next.isNotBlank())"\nNext: $next" else "")
                    schedulePreview(ch.optString("url"))
                }
            }
            b.setOnClickListener{
                val url=ch.optString("url")
                if(!url.startsWith("http://")&&!url.startsWith("https://"))return@setOnClickListener
                startActivity(Intent(this,PlayerActivity::class.java).apply{
                    putExtra("url",url);putExtra("name",label)
                })
            }
            list.addView(b,LinearLayout.LayoutParams(-1,dp(65)).apply{bottomMargin=dp(5)})
        }
        if(start+limit<rows.size){val next=button("↓ More channels (${start+limit} / ${rows.size})");next.setOnClickListener{page++;renderChannels()};list.addView(next)}
        val first=list.getChildAt(if(page>0)1 else 0)
        first?.requestFocus()
    }
    private fun schedulePreview(url:String){
        pendingPreview?.let{handler.removeCallbacks(it)}
        if(url.isBlank()||url==activeUrl)return
        pendingPreview=Runnable{
            try{
                player.stop();player.clearMediaItems()
                activeUrl=url
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare();player.playWhenReady=true
            }catch(e:Exception){details.append("\nPreview unavailable: "+(e.message?:"unsupported stream"))}
        }
        handler.postDelayed(pendingPreview!!,500)
    }
    override fun onPause(){player.pause();super.onPause()}
    override fun onResume(){super.onResume();if(::player.isInitialized&&player.mediaItemCount>0)player.playWhenReady=true}
    override fun onDestroy(){pendingPreview?.let{handler.removeCallbacks(it)};player.release();super.onDestroy()}
}
