package com.openguide.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Remote-first Petalix Android TV shell. The catalog is generated on-device, never bundled. */
class NativeTvActivity : Activity() {
    data class Channel(
        val id: String, val name: String, val group: String, val logo: String,
        val url: String, val now: String, val next: String
    )
    private val pairing by lazy { getSharedPreferences("petalix_pairing", MODE_PRIVATE) }
    private val preferences by lazy { getSharedPreferences("petalix_native_ui", MODE_PRIVATE) }
    private var channels: List<Channel> = emptyList()
    private var tab = "Home"
    private var sportsFilter = "All"
    private lateinit var contentHost: FrameLayout
    private lateinit var navRow: LinearLayout
    private val favorites get() = preferences.getStringSet("favorite_ids", emptySet()) ?: emptySet()
    private val followedTeams get() = preferences.getStringSet("teams", emptySet()) ?: emptySet()
    private val imageThreads = Executors.newFixedThreadPool(3)
    private val imageCache = object: LruCache<String, Bitmap>(24) {}
    private val colorBg = Color.rgb(12, 15, 31)
    private val colorPanel = Color.rgb(38, 44, 69)
    private val colorFocused = Color.rgb(215, 223, 255)
    private val colorMuted = Color.rgb(170, 179, 201)
    private val colorAccent = Color.rgb(66, 200, 193)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun pill(bg: Int, border: Int = Color.TRANSPARENT, radius: Int = 15): GradientDrawable =
        GradientDrawable().apply { setColor(bg); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), border) }
    private fun caption(label: String, size: Float, textColor: Int = Color.WHITE): TextView =
        TextView(this).apply { text = label; textSize = size; setTextColor(textColor); includeFontPadding = true }
    private fun blockText(label: String, size: Float, textColor: Int = Color.WHITE) = caption(label, size, textColor).apply {
        setPadding(dp(7), dp(6), dp(7), dp(6))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (pairing.getString("device_token", "").isNullOrBlank() && !pairing.getBoolean("demo_mode", false)) {
            startActivity(Intent(this, ActivationActivity::class.java))
            finish()
            return
        }
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        tab = savedInstanceState?.getString("selected_tab") ?: "Home"
        loadCatalog()
        createScreen()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected_tab", tab)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() {
        super.onResume()
        if (::contentHost.isInitialized) {
            loadCatalog()
            renderScreen()
        }
    }
    private fun readChannel(j: JSONObject): Channel? {
        val url = j.optString("url").take(2200)
        if (!url.startsWith("https://", true) && !url.startsWith("http://", true)) return null
        val name = j.optString("name", "Channel").take(140)
        return Channel(
            j.optString("id", name + url.hashCode()).take(180), name,
            j.optString("group", "Other").ifBlank { "Other" }.take(90),
            j.optString("logo").take(400), url,
            j.optString("now").take(160), j.optString("next").take(160)
        )
    }
    private fun loadCatalog() {
        val result = ArrayList<Channel>()
        // Native cache is written in bounded chunks by the in-app M3U/Xtream importer.
        val chunks = File(filesDir, "petalix-native-catalog.jsonl")
        if (chunks.exists()) {
            try {
                chunks.bufferedReader().useLines { lines ->
                    lines.take(300).forEach { line ->
                        val array = JSONArray(line)
                        for (i in 0 until array.length()) {
                            if (result.size >= 12000) break
                            readChannel(array.optJSONObject(i) ?: continue)?.let(result::add)
                        }
                    }
                }
            } catch (_: Exception) { result.clear() }
        }
        if (result.isEmpty()) {
            val guide = File(cacheDir, "petalix-guide.json")
            if (guide.exists()) try {
                val array = JSONObject(guide.readText()).optJSONArray("channels") ?: JSONArray()
                for (i in 0 until minOf(array.length(), 6000)) {
                    readChannel(array.optJSONObject(i) ?: continue)?.let(result::add)
                }
            } catch (_: Exception) { /* catalog not imported yet */ }
        }
        channels = result
    }
    private fun tvButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f
        setTextColor(Color.WHITE); gravity = Gravity.CENTER
        minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
        setPadding(dp(15), dp(7), dp(15), dp(7))
        background = pill(colorPanel)
        stateListAnimator = null
        isFocusable = true; isFocusableInTouchMode = false
        setOnFocusChangeListener { _, focused ->
            background = pill(if (focused) colorFocused else colorPanel,
                if (focused) Color.WHITE else Color.TRANSPARENT)
            setTextColor(if (focused) colorBg else Color.WHITE)
            if (focused) post { requestRectangleOnScreen(android.graphics.Rect(0,0,width,height),false) }
        }
        setOnClickListener { onClick() }
    }
    private fun createScreen() {
        val frame = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(colorBg) }
        val navScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            isFocusable = false
            setBackgroundColor(Color.rgb(28, 33, 55))
        }
        navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), dp(7), dp(15), dp(7))
        }
        navScroll.addView(navRow, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(58)))
        frame.addView(navScroll, LinearLayout.LayoutParams(-1, dp(58)))
        contentHost = FrameLayout(this)
        frame.addView(contentHost, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(frame)
        renderScreen()
    }
    private fun renderNavigation() {
        navRow.removeAllViews()
        val mark = caption("●", 27f, colorAccent)
        mark.gravity = Gravity.CENTER_VERTICAL
        navRow.addView(mark, LinearLayout.LayoutParams(dp(37), dp(45)))
        for (label in listOf("Home", "Live", "Sports", "Teams", "Favorites", "Movies", "TV Shows", "Settings")) {
            val button = tvButton(label) {
                when(label) {
                    "Live" -> openGuide()
                    "Settings" -> openSettings()
                    "Movies", "TV Shows" -> openOnDemand(label)
                    else -> {tab = label; renderScreen()}
                }
            }
            button.textSize = 13f
            if (label == tab) {
                button.background = pill(Color.rgb(87, 93, 132))
                button.setTextColor(Color.WHITE)
            }
            navRow.addView(button, LinearLayout.LayoutParams(-2, dp(39)).apply { marginEnd = dp(7) })
        }
        navRow.addView(blockText("PETALIX", 14f, colorAccent).apply {typeface = Typeface.DEFAULT_BOLD})
    }
    private fun addHeading(column: LinearLayout, heading: String, detail: String = "") {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(blockText(heading, 21f).apply { typeface = Typeface.DEFAULT_BOLD })
        if (detail.isNotBlank()) row.addView(blockText(detail, 12f, colorMuted))
        column.addView(row, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(11) })
    }
    private fun addDescription(column: LinearLayout, msg: String, action: String? = null, execute: (() -> Unit)? = null) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = pill(colorPanel)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        panel.addView(blockText(msg, 15f, colorMuted))
        if (action != null && execute != null) {
            panel.addView(tvButton(action, execute), LinearLayout.LayoutParams(-2, dp(47)))
        }
        column.addView(panel, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }
    private fun column(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22), dp(12), dp(22), dp(35))
    }
    private fun renderScreen() {
        if (!::contentHost.isInitialized) return
        renderNavigation()
        contentHost.removeAllViews()
        val vertical = ScrollView(this).apply {
            isFillViewport = true; isFocusable = false; isVerticalScrollBarEnabled = false
        }
        val content = column()
        vertical.addView(content)
        contentHost.addView(vertical, FrameLayout.LayoutParams(-1,-1))
        when(tab) {
            "Sports" -> renderSports(content)
            "Teams" -> renderTeams(content)
            "Favorites" -> renderFavorites(content)
            else -> renderHome(content)
        }
    }
    private fun loadLogo(url: String, target: ImageView, placeholder: View) {
        if (!url.startsWith("https://", true) && !url.startsWith("http://", true)) return
        imageCache.get(url)?.let { target.setImageBitmap(it); placeholder.visibility = View.GONE; return }
        imageThreads.execute {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4500; readTimeout = 4500
                    setRequestProperty("User-Agent", "PetalixTV/0.6")
                }
                try {
                    if (conn.responseCode !in 200..299) return@execute
                    val bytes = conn.inputStream.use { input ->
                        val buffer = java.io.ByteArrayOutputStream()
                        val block = ByteArray(8192)
                        while (true) {
                            val size = input.read(block)
                            if (size < 0 || buffer.size() + size > 512 * 1024) break
                            buffer.write(block, 0, size)
                        }
                        buffer.toByteArray()
                    }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@execute
                    imageCache.put(url, bitmap)
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            target.setImageBitmap(bitmap)
                            placeholder.visibility = View.GONE
                        }
                    }
                } finally { conn.disconnect() }
            } catch (_: Exception) { /* Keep text fallback for missing or offline channel art. */ }
        }
    }
    private fun tile(ch: Channel, width: Int = 225): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = pill(colorPanel,Color.rgb(83,94,124))
            setPadding(dp(15), dp(10), dp(15), dp(10))
            isFocusable = true; isClickable = true
            setOnFocusChangeListener { view, focused ->
                background = pill(if(focused) Color.rgb(80, 91, 131) else colorPanel,
                    if(focused) Color.WHITE else Color.rgb(83,94,124))
                scaleX = if(focused) 1.03f else 1f
                scaleY = if(focused) 1.03f else 1f
                if(focused) view.post { view.requestRectangleOnScreen(android.graphics.Rect(0,0,view.width,view.height),false) }
            }
            setOnClickListener { play(ch) }
            setOnLongClickListener { toggleFavorite(ch.id); true }
        }
        val art = FrameLayout(this)
        val image = ImageView(this).apply { scaleType=ImageView.ScaleType.FIT_CENTER }
        val fallback = caption(ch.name.take(10).uppercase(Locale.ROOT), 18f, colorAccent).apply {
            gravity=Gravity.CENTER;typeface=Typeface.DEFAULT_BOLD
        }
        art.addView(image, FrameLayout.LayoutParams(-1,-1))
        art.addView(fallback, FrameLayout.LayoutParams(-1,-1))
        tile.addView(art, LinearLayout.LayoutParams(-1, dp(45)))
        loadLogo(ch.logo,image,fallback)
        val title = blockText(ch.name, 15f)
        title.typeface = Typeface.DEFAULT_BOLD
        title.maxLines = 2
        tile.addView(title)
        val program = ch.now.ifBlank { ch.group }
        tile.addView(blockText(program, 12f, colorMuted).apply { maxLines = 2 })
        val status = if (favorites.contains(ch.id)) "★  Favorite" else "LIVE  ·  Hold OK to favorite"
        tile.addView(blockText(status, 11f, if(favorites.contains(ch.id)) colorAccent else colorMuted))
        tile.layoutParams = LinearLayout.LayoutParams(dp(width), dp(153))
        return tile
    }
    private fun channelRow(column: LinearLayout, heading: String, channelList: List<Channel>, detail: String = "") {
        if(channelList.isEmpty())return
        addHeading(column, heading, detail)
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(5), dp(8), dp(5), dp(13))
        }
        channelList.take(120).forEach { ch ->
            row.addView(tile(ch),LinearLayout.LayoutParams(dp(225),dp(153)).apply { marginEnd = dp(12) })
        }
        scroller.addView(row)
        column.addView(scroller,LinearLayout.LayoutParams(-1,dp(180)))
    }
    private fun renderHome(column: LinearLayout) {
        addHeading(column,"Live now", "Your connected channels")
        if(channels.isEmpty()) {
            addDescription(column,"No playlist has been imported on this device yet. Connect your M3U or Xtream Codes source in Settings.","Open Playlist Settings") { openSettings() }
            return
        }
        val sport = channels.filter(::isSports)
        channelRow(column,"Featured channels",channels.take(80))
        if (sport.isNotEmpty()) channelRow(column,"Sports",sport,"From your own playlist")
        val preferred = channels.filter { favorites.contains(it.id) }
        if (preferred.isNotEmpty()) channelRow(column,"Favorites",preferred)
        channels.groupBy { it.group }.entries.take(15).forEach { (group,list) ->
            channelRow(column,group,list)
        }
        addDescription(column,"The Your apps row has been removed. Live scores, replays and game scheduling require a separate verified data source; no scores are fabricated.")
    }
    private fun isSports(ch: Channel):Boolean =
        listOf("sport", "espn", "nba", "nfl", "nhl", "mlb", "football", "soccer", "f1", "formula", "sky sport")
            .any { word -> (ch.group+" "+ch.name).lowercase(Locale.ROOT).contains(word) }
    private fun filterChips(column:LinearLayout, names:List<String>, selected:String, handler:(String)->Unit) {
        val scroll=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;isFocusable=false}
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,dp(4),0,dp(9))}
        names.forEach { label ->
            val btn=tvButton(label){handler(label)}
            btn.textSize=13f
            if(label==selected){btn.background=pill(Color.rgb(215,223,255));btn.setTextColor(colorBg)}
            row.addView(btn,LinearLayout.LayoutParams(-2,dp(40)).apply{marginEnd=dp(9)})
        }
        scroll.addView(row)
        column.addView(scroll,LinearLayout.LayoutParams(-1,dp(53)))
    }
    private fun renderSports(column:LinearLayout) {
        addHeading(column,"Sports","Channels and programs from your lineup")
        filterChips(column,listOf("All","NFL","NBA","NHL","MLB","Soccer","Football","Tennis","Other"),sportsFilter) {
            sportsFilter=it;renderScreen()
        }
        val sport=channels.filter(::isSports)
        val filtered = if(sportsFilter=="All" || sportsFilter=="Other") sport else sport.filter {
            (it.name+" "+it.group+" "+it.now).contains(sportsFilter,true) ||
            (sportsFilter=="Soccer" && listOf("premier", "la liga", "serie a", "mls").any { word -> (it.name+" "+it.group).contains(word,true) })
        }
        if(filtered.isNotEmpty())channelRow(column,"Live sports channels",filtered,"${filtered.size} matching")
        else addDescription(column,"No matching sports channels in the current catalog. Connect your M3U/Xtream source or switch filters.","Manage sources") { openSettings() }
        addHeading(column,"Scores and game schedules")
        addDescription(column,"Real-time scores, event cards, feeds, and replays require an authorized sports-data/EPG service. This build does not invent scores or games.")
    }
    private fun renderTeams(column:LinearLayout) {
        addHeading(column,"Teams","Follow your favorites using your TV remote")
        val leagues=linkedMapOf(
            "NFL" to listOf("Cardinals","Falcons","Ravens","Bills","Panthers","Bears","Bengals","Browns","Cowboys","Broncos","Lions","Packers","Texans","Colts","Jaguars","Chiefs","Raiders","Chargers","Rams","Dolphins","Vikings","Patriots","Saints","Giants","Jets","Eagles","Steelers","49ers","Seahawks","Buccaneers","Titans","Commanders"),
            "NBA" to listOf("Celtics","Knicks","Lakers","Warriors","Heat","Bulls","Hawks","Hornets","Mavericks","Cavaliers","Nuggets","Spurs","Thunder"),
            "NHL" to listOf("Hurricanes","Rangers","Bruins","Penguins","Maple Leafs","Oilers","Avalanche","Predators","Capitals","Panthers"))
        for ((league,names) in leagues){
            addHeading(column,league,if(league=="NFL")"Select a team to follow" else "")
            val row=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;isFocusable=false}
            val items=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,dp(4),0,dp(9))}
            names.forEach { name ->
                val id="$league|$name"
                val btn=tvButton((if(followedTeams.contains(id))"★ " else "☆ ")+name){toggleTeam(id);renderScreen()}
                items.addView(btn,LinearLayout.LayoutParams(dp(155),dp(58)).apply{marginEnd=dp(10)})
            }
            row.addView(items)
            column.addView(row,LinearLayout.LayoutParams(-1,dp(77)))
        }
        addDescription(column,"Your team selections are saved locally. Automatic game matching will become available when a verified sports schedule is connected.")
    }
    private fun renderFavorites(column:LinearLayout){
        addHeading(column,"Favorites")
        val saved=channels.filter { favorites.contains(it.id) }
        if(saved.isEmpty()) addDescription(column,"No favorite channels yet. Hold OK on any channel tile to add or remove it.","Browse Home") {tab="Home";renderScreen()}
        else channelRow(column,"Favorite channels",saved)
    }
    private fun toggleFavorite(id:String){
        val selected=favorites.toMutableSet()
        if(!selected.add(id))selected.remove(id)
        preferences.edit().putStringSet("favorite_ids",selected).apply()
        renderScreen()
    }
    private fun toggleTeam(id:String){
        val selected=followedTeams.toMutableSet()
        if(!selected.add(id))selected.remove(id)
        preferences.edit().putStringSet("teams",selected).apply()
    }
    private fun play(ch:Channel){
        preferences.edit().putString("recent_channel",ch.id).apply()
        startActivity(Intent(this,PlayerActivity::class.java).apply {
            putExtra("url",ch.url);putExtra("name",ch.name)
        })
    }
    private fun openGuide() {
        if(channels.isEmpty()){openSettings();return}
        val out=File(cacheDir,"petalix-guide.json")
        try {
            val wrapper=JSONObject().put("channels",JSONArray().apply {
                channels.take(7000).forEach { ch ->
                    put(JSONObject().put("id",ch.id).put("name",ch.name).put("group",ch.group)
                        .put("logo",ch.logo).put("url",ch.url).put("now",ch.now).put("next",ch.next))
                }
            })
            out.writeText(wrapper.toString(),Charsets.UTF_8)
        } catch (_:Exception) { return }
        startActivity(Intent(this,GuideActivity::class.java))
    }
    private fun openSettings(){
        startActivity(Intent(this,MainActivity::class.java).putExtra("native_settings",true))
    }
    override fun onDestroy() {
        imageThreads.shutdownNow()
        super.onDestroy()
    }
    private fun openOnDemand(tab:String){
        startActivity(Intent(this,MainActivity::class.java).putExtra("native_settings",true)
            .putExtra("native_page",tab))
    }
}
