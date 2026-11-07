package com.openguide.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONObject

/** Native full-screen player. Available alternate feeds must come from the user's own catalog. */
class PlayerActivity : Activity() {
    private lateinit var player: ExoPlayer
    private lateinit var view: PlayerView
    private lateinit var title: TextView
    private lateinit var stateText: TextView
    private lateinit var feedButton: Button
    private var feeds = listOf<FeedMatcher.Feed>()
    private var currentFeed = 0
    private val failedFeedIndexes = mutableSetOf<Int>()
    private var selecting = false
    private fun dp(v:Int) = (v * resources.displayMetrics.density).toInt()
    private fun shape(color:Int) = GradientDrawable().apply { setColor(color);cornerRadius=dp(13).toFloat() }
    private fun tvButton(text:String) = Button(this).apply {
        this.text=text;isAllCaps=false;textSize=14f
        setTextColor(Color.WHITE);background=shape(Color.rgb(43,48,77));stateListAnimator=null
        setOnFocusChangeListener { _,focused ->
            background=shape(if(focused)Color.rgb(78,111,192) else Color.rgb(43,48,77))
            animate().scaleX(if(focused)1.035f else 1f).scaleY(if(focused)1.035f else 1f).setDuration(115).start()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility=(View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        val url = intent.getStringExtra("url") ?: run { finish();return }
        val name = intent.getStringExtra("name") ?: "Live TV"
        val parsed = try {
            val arr=JSONArray(intent.getStringExtra("feeds_json")?:"[]")
            (0 until minOf(arr.length(),8)).mapNotNull {i ->
                val f=arr.optJSONObject(i)?:return@mapNotNull null
                val u=f.optString("url")
                if(!u.startsWith("http://")&&!u.startsWith("https://"))null
                else FeedMatcher.Feed(f.optString("name",name),u)
            }
        }catch(_:Exception){emptyList()}
        feeds=(listOf(FeedMatcher.Feed(name,url))+parsed).distinctBy{it.url}.take(8)
        player=ExoPlayer.Builder(this).build()
        player.addListener(object:Player.Listener {
            override fun onPlayerError(error:PlaybackException) {
                failedFeedIndexes.add(currentFeed)
                val next=feeds.indices.firstOrNull { it !in failedFeedIndexes }
                if(next!=null && next!=currentFeed){
                    stateText.text="Feed ${currentFeed+1} unavailable. Trying feed ${next+1}..."
                    startFeed(next)
                }else{
                    stateText.text="Playback unavailable. Press Feeds to retry another source."
                }
            }
            override fun onPlaybackStateChanged(playbackState:Int){
                if(playbackState==Player.STATE_READY){stateText.text=""}
                else if(playbackState==Player.STATE_BUFFERING)stateText.text="Buffering..."
            }
        })
        view=PlayerView(this).apply {player=this@PlayerActivity.player;useController=true;controllerShowTimeoutMs=4000;setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)}
        title=TextView(this).apply{text=name;textSize=18f;setTextColor(Color.WHITE);setPadding(dp(15),dp(9),dp(15),dp(9))}
        stateText=TextView(this).apply{textSize=13f;setTextColor(Color.rgb(210,220,245));setPadding(dp(15),0,dp(15),dp(9))}
        feedButton=tvButton("Feed 1/${feeds.size}")
        feedButton.setOnClickListener {showFeeds()}
        val channelsButton=tvButton("Channels")
        channelsButton.setOnClickListener {startActivity(Intent(this,GuideActivity::class.java))}
        val backButton=tvButton("Back")
        backButton.setOnClickListener {finish()}
        val actions=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(17),dp(6),dp(17),dp(8))
            setBackgroundColor(Color.argb(208,11,14,30))
        }
        actions.addView(feedButton,LinearLayout.LayoutParams(dp(142),dp(48)).apply{marginEnd=dp(10)})
        actions.addView(channelsButton,LinearLayout.LayoutParams(dp(125),dp(48)).apply{marginEnd=dp(10)})
        actions.addView(backButton,LinearLayout.LayoutParams(dp(98),dp(48)))
        val header=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.argb(194,13,17,34));addView(title);addView(stateText)}
        val root=FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(view,FrameLayout.LayoutParams(-1,-1))
            addView(header,FrameLayout.LayoutParams(-1,-2,Gravity.TOP))
            addView(actions,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
        }
        setContentView(root)
        if(feeds.size==1)feedButton.text="Feed 1/1"
        startFeed(0)
    }
    private fun startFeed(index:Int) {
        if(index !in feeds.indices)return
        currentFeed=index
        val uri=feeds[index].url
        feedButton.text="Feed ${index+1}/${feeds.size}"
        try {
            val item=MediaItem.Builder().setUri(uri).apply {
                if(uri.substringBefore('?').endsWith(".m3u8",true))setMimeType(MimeTypes.APPLICATION_M3U8)
                else if(uri.substringBefore('?').endsWith(".mpd",true))setMimeType(MimeTypes.APPLICATION_MPD)
            }.build()
            player.setMediaItem(item);player.prepare();player.playWhenReady=true
        }catch(e:Exception){stateText.text="Could not open this feed: ${e.message?:"unsupported URL"}"}
    }
    private fun showFeeds(){
        if(selecting)return
        selecting=true
        val labels=feeds.mapIndexed {i,feed->
            (if(i==currentFeed)"✓ " else "")+"Feed ${i+1}: "+feed.name.take(52)
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Available feeds")
            .setSingleChoiceItems(labels,currentFeed) {dialog,index->
                failedFeedIndexes.remove(index)
                startFeed(index)
                dialog.dismiss()
            }.setNegativeButton("Cancel",null)
            .setOnDismissListener{selecting=false;feedButton.requestFocus()}
            .show()
    }
    override fun dispatchKeyEvent(event:KeyEvent):Boolean {
        if(event.action==KeyEvent.ACTION_DOWN && (event.keyCode==KeyEvent.KEYCODE_MENU || event.keyCode==KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK)){
            showFeeds();return true
        }
        return super.dispatchKeyEvent(event)
    }
    override fun onStop(){if(::player.isInitialized)player.pause();super.onStop()}
    override fun onResume(){super.onResume();if(::player.isInitialized&&player.mediaItemCount>0)player.playWhenReady=true}
    override fun onDestroy(){if(::player.isInitialized)player.release();super.onDestroy()}
}
