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
        setTextColor(Color.WHITE);background=rounded(color("#252e51"),color("#445680"))
        isFocusable=true;isFocusableInTouchMode=false
        stateListAnimator=null
        setOnFocusChangeListener{v,focused->
            background=rounded(color(if(focused)"#39588f" else "#252e51"),color(if(focused)"#b6cdff" else "#445680"))
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
        group=intent.getStringExtra("selected_group") ?: "All channels"
        // Named Home rails (e.g. Sports/Featured) are not necessarily actual provider groups.
        if(group!="All channels" && group!="My Locals" && (0 until channels.length()).none{channels.optJSONObject(it)?.optString("group")==group})group="All channels"
        buildUI()
    }
    private fun buildUI(){
        val root=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL;setBackgroundColor(color("#0c1126"))
            setPadding(dp(12),dp(8),dp(12),dp(8))
        }
        val side=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        side.addView(text("PETALIX  •  LIVE TV",19f,color("#65bbff")).apply{typeface=Typeface.DEFAULT_BOLD})
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
        info.addView(text("LIVE TV PREVIEW",12f,color("#64bfff")))
        details=text("Use the remote to browse channels. Preview will follow your selection.",20f)
        info.addView(details)
        info.addView(text("OK: Watch full screen     Back: Exit guide",13f,color("#adbbcf")))
        top.addView(info,LinearLayout.LayoutParams(0,dp(196),1f))
        center.addView(top)
        val header=text("CHANNEL                          ON NOW                                            UP NEXT",14f,color("#c8d6ff"))
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
        val limit=70  // fewer native views and less work per D-pad move
        val start=page*limit
        val subset=rows.drop(start).take(limit)
        if(page>0){
            val previous=button("↑ Previous channels")
            previous.setOnClickListener{page--;renderChannels()}
            list.addView(previous,LinearLayout.LayoutParams(-1,dp(52)))
        }
        subset.forEachIndexed {ix,ch->
            val label=ch.optString("name","Channel")
            val now=ch.optString("now").ifBlank{"No program info"}
            val next=ch.optString("next").ifBlank{"No program info"}
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            val logo=text(label.take(10).uppercase(),14f,color("#e0e9ff")).apply{
                typeface=Typeface.DEFAULT_BOLD
                gravity=Gravity.CENTER
                maxLines=2
                background=rounded(color("#182346"))
            }
            row.addView(logo,LinearLayout.LayoutParams(dp(114),dp(61)).apply{marginEnd=dp(6)})
            val current=button(now)
            current.textSize=14f
            current.setOnFocusChangeListener{view,focused->
                current.background=rounded(color(if(focused)"#4768a9" else "#293555"),color(if(focused)"#e4f0ff" else "#425581"))
                if(focused){
                    view.post{view.requestRectangleOnScreen(android.graphics.Rect(0,0,view.width,view.height),false)}
                    details.text="$label\nNow: $now"+(if(next!="No program info")"\nNext: $next" else "")
                    schedulePreview(ch.optString("url"))
                }
            }
            current.setOnClickListener{launchChannel(ch)}
            row.addView(current,LinearLayout.LayoutParams(0,dp(61),5f).apply{marginEnd=dp(5)})
            val upcoming=button(next)
            upcoming.textSize=13f
            upcoming.setOnFocusChangeListener {view,focused->
                upcoming.background=rounded(color(if(focused)"#4768a9" else "#222d4b"),color(if(focused)"#e4f0ff" else "#425581"))
                if(focused){
                    view.post{view.requestRectangleOnScreen(android.graphics.Rect(0,0,view.width,view.height),false)}
                    details.text="$label\nUp next: $next"
                    schedulePreview(ch.optString("url"))
                }
            }
            upcoming.setOnClickListener{launchChannel(ch)}
            row.addView(upcoming,LinearLayout.LayoutParams(0,dp(61),3f))
            list.addView(row,LinearLayout.LayoutParams(-1,dp(67)).apply{bottomMargin=dp(3)})
        }
        if(start+limit<rows.size){
            val more=button("↓ More channels (${start+limit} / ${rows.size})")
            more.setOnClickListener{page++;renderChannels()}
            list.addView(more,LinearLayout.LayoutParams(-1,dp(53)))
        }
        list.post{val first=list.getChildAt(if(page>0)1 else 0)
            if(first is LinearLayout) first.getChildAt(1)?.requestFocus() else first?.requestFocus()
        }
    }
    private fun launchChannel(channel:JSONObject){
        val url=channel.optString("url")
        if(!url.startsWith("http://")&&!url.startsWith("https://"))return
        val name=channel.optString("name","Channel")
        val available=(0 until channels.length()).mapNotNull {channels.optJSONObject(it)}
            .map {FeedMatcher.Feed(it.optString("name"),it.optString("url"))}
        val options=FeedMatcher.candidates(FeedMatcher.Feed(name,url),available)
        startActivity(Intent(this,PlayerActivity::class.java).apply{
            putExtra("url",url);putExtra("name",name)
            putExtra("feeds_json",JSONArray().apply{
                options.forEach{put(JSONObject().put("name",it.name).put("url",it.url))}
            }.toString())
        })
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
