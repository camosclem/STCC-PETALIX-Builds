package com.openguide.tv

import android.app.Activity
import android.app.AlertDialog
import android.text.format.DateFormat
import java.util.Date
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
import android.widget.EditText
import android.text.InputFilter
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.graphics.Rect
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
    private var lastFocusKey: String? = null
    private var requestedFocusKey: String? = null
    private var localMessage: String = ""
    private var localSearch = ""
    private var localInput: EditText? = null
    private var lastCatalogStamp = ""
    private val locals get() = preferences.getStringSet("local_ids",emptySet()) ?: emptySet()

    private lateinit var contentHost: FrameLayout
    private lateinit var navRow: LinearLayout
    private val favorites get() = preferences.getStringSet("favorite_ids", emptySet()) ?: emptySet()
    private val followedTeams get() = preferences.getStringSet("teams", emptySet()) ?: emptySet()
    private val imageThreads = Executors.newFixedThreadPool(3)
    private val scoreThreads = Executors.newSingleThreadExecutor()
    private val espn by lazy { EspnSportsClient(cacheDir) }
    private var scores: EspnSportsClient.Snapshot? = null
    @Volatile private var scoreRefreshRunning = false
    private var refreshUiOnNextScores = false
    private val imageCache = object: LruCache<String, Bitmap>(24) {}
    private val colorBg = Color.rgb(12, 17, 38)
    private val colorPanel = Color.rgb(36, 46, 78)
    private val colorFocused = Color.rgb(215, 223, 255)
    private val colorMuted = Color.rgb(170, 179, 201)
    private val colorAccent = Color.rgb(74, 180, 252)

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
        if (BuildConfig.DEBUG) scores = espn.loadCache()
        createScreen()
        if (BuildConfig.DEBUG) refreshScores()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected_tab", tab)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() {
        super.onResume()
        if (::contentHost.isInitialized) {
            // Returning from player or guide must not reset the focused tile.
            if (catalogStamp() != lastCatalogStamp) {
                loadCatalog()
                renderScreen()
            }
        }
    }
    /** ESPN is beta-only until rights to distribute a commercial sports feed are confirmed. */
    private fun refreshScores() {
        if (!BuildConfig.DEBUG || scoreRefreshRunning || espn.isFresh()) return
        scoreRefreshRunning = true
        scoreThreads.execute {
            val result = try { espn.refresh() } catch (_: Exception) { null }
            runOnUiThread {
                scoreRefreshRunning = false
                if (!isFinishing && !isDestroyed && result != null) {
                    val firstLoad = scores == null || scores?.games.isNullOrEmpty()
                    scores = result
                    // Don't reset focus while somebody navigates with their TV remote.
                    // Users can explicitly refresh from the Sports tab for subsequent updates.
                    if ((firstLoad || refreshUiOnNextScores) && (tab == "Home" || tab == "Sports")) renderScreen()
                    refreshUiOnNextScores = false
                }
            }
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
    private fun catalogStamp(): String {
        val saved = File(filesDir, "petalix-native-catalog.jsonl")
        val cached = File(cacheDir, "petalix-guide.json")
        val file = if (saved.exists()) saved else cached
        return "${file.lastModified()}:${file.length()}"
    }
    private fun loadCatalog() {
        lastCatalogStamp = catalogStamp()
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
            animate().cancel()
            animate().scaleX(if(focused)1.04f else 1f).scaleY(if(focused)1.04f else 1f).setDuration(110).start()
            if (focused) {
                lastFocusKey = tag as? String
                post { requestRectangleOnScreen(Rect(0,0,width,height),false) }
            }
        }
        setOnClickListener { onClick() }
    }
    private fun createScreen() {
        val frame = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(colorBg) }
        val navScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            isFocusable = false
            setBackgroundColor(Color.rgb(27, 50, 107))
        }
        navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), dp(7), dp(15), dp(7))
        }
        navScroll.addView(navRow, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(58)))
        // Keep Petalix branding visible on TV while tabs scroll independently.
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(27, 50, 107))
        }
        topBar.addView(navScroll, LinearLayout.LayoutParams(0, dp(58), 1f))
        val brand = caption("PETALIX", 15f, colorAccent).apply {
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        topBar.addView(brand, LinearLayout.LayoutParams(dp(107), dp(58)))
        frame.addView(topBar, LinearLayout.LayoutParams(-1, dp(58)))
        contentHost = FrameLayout(this)
        frame.addView(contentHost, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(frame)
        renderScreen()
    }
    private fun renderNavigation() {
        navRow.removeAllViews()
        val mark = caption("P", 21f, colorAccent)
        mark.gravity = Gravity.CENTER_VERTICAL
        navRow.addView(mark, LinearLayout.LayoutParams(dp(37), dp(45)))
        for (label in listOf("Home", "Live", "Sports", "Teams", "Favorites", "My Locals", "Movies", "TV Shows", "Settings")) {
            val button = tvButton(label) {
                when(label) {
                    "Live" -> openGuide()
                    "Settings" -> openSettings()
                    "Movies", "TV Shows" -> openOnDemand(label)
                    else -> {tab = label; requestedFocusKey="nav:$label"; renderScreen()}
                }
            }
            button.textSize = 13f
            button.tag = "nav:$label"
            if (label == tab) {
                button.background = pill(Color.rgb(87, 93, 132))
                button.setTextColor(Color.WHITE)
            }
            navRow.addView(button, LinearLayout.LayoutParams(-2, dp(39)).apply { marginEnd = dp(7) })
        }

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
        val oldFocus = requestedFocusKey ?: (window.decorView.findFocus()?.tag as? String) ?: lastFocusKey
        requestedFocusKey = null
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
            "My Locals" -> renderLocals(content)
            else -> renderHome(content)
        }
        contentHost.post {
            if (!isFinishing && !isDestroyed) {
                val focus = oldFocus?.let { findTagged(window.decorView,it) }
                    ?: findTagged(window.decorView,"nav:$tab")
                if (focus != null && focus.isFocusable) focus.requestFocus()
            }
        }
    }
    private fun findTagged(view: View, key: String): View? {
        if (view.tag == key) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findTagged(view.getChildAt(i),key)?.let { return it }
        }
        return null
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
            tag = "channel:${ch.id}"
            setOnFocusChangeListener { view, focused ->
                background = pill(if(focused) Color.rgb(80, 91, 131) else colorPanel,
                    if(focused) Color.WHITE else Color.rgb(83,94,124))
                animate().cancel()
                animate().scaleX(if(focused)1.035f else 1f)
                    .scaleY(if(focused)1.035f else 1f).setDuration(105).start()
                if(focused) {
                    lastFocusKey = tag as? String
                    view.post { view.requestRectangleOnScreen(Rect(0,0,view.width,view.height),false) }
                }
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
        channelList.take(24).forEach { ch ->
            row.addView(tile(ch),LinearLayout.LayoutParams(dp(225),dp(153)).apply { marginEnd = dp(12) })
        }
        if (channelList.size > 24) {
            row.addView(tvButton("View all ${channelList.size} channels") {
                openGuide(heading)
            },LinearLayout.LayoutParams(dp(222),dp(153)).apply { marginEnd=dp(12) })
        }
        scroller.addView(row)
        column.addView(scroller,LinearLayout.LayoutParams(-1,dp(180)))
    }
    private fun gameRow(column: LinearLayout, label: String, games: List<EspnSportsClient.Game>) {
        if (games.isEmpty()) return
        addHeading(column, label, "ESPN scoreboard beta • scores and schedules")
        val rail = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false;isFocusable=false }
        val items = LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            setPadding(dp(5),dp(8),dp(5),dp(11))
        }
        games.take(32).forEach { game ->
            val card=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL
                gravity=Gravity.CENTER_VERTICAL
                setPadding(dp(13),dp(10),dp(13),dp(8))
                background=pill(colorPanel,Color.rgb(80,94,130))
                isFocusable=true;isClickable=true
                setOnFocusChangeListener { view, focused ->
                    background=pill(if(focused)colorFocused else colorPanel,
                        if(focused)Color.WHITE else Color.rgb(80,94,130))
                    scaleX=if(focused)1.025f else 1f
                    scaleY=if(focused)1.025f else 1f
                    if(focused) {
                    lastFocusKey = tag as? String
                    view.post { view.requestRectangleOnScreen(Rect(0,0,view.width,view.height),false) }
                }
                }
                setOnClickListener { showGameDetails(game) }
            }
            card.tag="game:${game.id}"
            val header=blockText("${game.league}  •  ${game.status}",12f,colorAccent)
            card.addView(header)
            val body=LinearLayout(this).apply{ orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
            val teams=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
            val playing=game.state=="in" || game.state=="post"
            val awayText=game.away.name.ifBlank{game.away.abbreviation} +
                (if(playing && game.away.score.isNotBlank())"  ${game.away.score}" else "")
            val homeText=game.home.name.ifBlank{game.home.abbreviation} +
                (if(playing && game.home.score.isNotBlank())"  ${game.home.score}" else "")
            teams.addView(blockText(awayText,17f).apply{maxLines=1;typeface=Typeface.DEFAULT_BOLD})
            teams.addView(blockText(homeText,17f).apply{maxLines=1;typeface=Typeface.DEFAULT_BOLD})
            body.addView(teams,LinearLayout.LayoutParams(0,dp(76),1f))
            val logo=ImageView(this).apply{scaleType=ImageView.ScaleType.FIT_CENTER}
            val placeholder=blockText(game.home.abbreviation,13f,colorMuted).apply{gravity=Gravity.CENTER}
            val art=FrameLayout(this).apply{
                addView(logo,FrameLayout.LayoutParams(-1,-1))
                addView(placeholder,FrameLayout.LayoutParams(-1,-1))
            }
            body.addView(art,LinearLayout.LayoutParams(dp(45),dp(45)))
            loadLogo(game.home.logo,logo,placeholder)
            card.addView(body)
            card.addView(blockText(if(game.broadcasters.isNotBlank())game.broadcasters else "Game details  •  OK",12f,colorMuted).apply{maxLines=1})
            items.addView(card,LinearLayout.LayoutParams(dp(285),dp(147)).apply{marginEnd=dp(10)})
        }
        rail.addView(items)
        column.addView(rail,LinearLayout.LayoutParams(-1,dp(167)))
    }

    private fun showGameDetails(game: EspnSportsClient.Game) {
        val time = if(game.startMillis>0) DateFormat.format("EEE, MMM d  h:mm a", Date(game.startMillis)).toString()
            else "Time unavailable"
        val body = buildString {
            append(game.name).append("\n\n")
            append(game.status).append("\n")
            append(time).append("\n")
            if(game.broadcasters.isNotBlank())append("Reported networks: ").append(game.broadcasters).append("\n")
            append("\nESPN supplies scores and schedules, not your IPTV stream. ")
            append("Petalix will use only channels available in your connected playlist.")
        }
        AlertDialog.Builder(this)
            .setTitle("${game.league} • Game details")
            .setMessage(body)
            .setPositiveButton("Close",null)
            .show()
    }

    private fun matchingScores(filter: String): List<EspnSportsClient.Game> {
        val all = scores?.games ?: return emptyList()
        if(filter=="All") return all
        if(filter=="Soccer")return all.filter{it.league in setOf("MLS","Premier League","La Liga")}
        if(filter=="Football")return all.filter{it.league=="NFL" || it.league=="NCAAF"}
        return all.filter{it.league==filter}
    }

    private fun renderHome(column: LinearLayout) {
        val currentGames = matchingScores("All").filter { it.state=="in" }
        val nextGames = matchingScores("All").filter { it.state=="pre" }
            .sortedBy{it.startMillis}
        gameRow(column,"Live now",currentGames)
        if (channels.isEmpty()) {
            addDescription(column,"No playlist has been imported on this device yet. Connect your M3U or Xtream Codes source in Settings.","Open Playlist Settings") { openSettings() }
        } else {
            val localChannels=channels.filter { locals.contains(it.id) }
            if (localChannels.isNotEmpty()) channelRow(column,"My Locals",localChannels,"Stations selected by ZIP")
            channelRow(column,"Featured channels",channels.take(60))
            val sport=channels.filter(::isSports)
            if(sport.isNotEmpty())channelRow(column,"Sports",sport,"From your connected playlist")
            val preferred=channels.filter { favorites.contains(it.id) }
            if(preferred.isNotEmpty())channelRow(column,"Favorites",preferred)
            channels.groupBy{it.group}.entries.take(10).forEach{(group,list)->channelRow(column,group,list)}
        }
        gameRow(column,"Coming up",nextGames.take(20))
        if (BuildConfig.DEBUG && scores==null)
            addDescription(column,"Loading scoreboard beta. Game cards show only data returned by ESPN.")
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
            btn.tag="filter:$label"
            if(label==selected){btn.background=pill(Color.rgb(215,223,255));btn.setTextColor(colorBg)}
            row.addView(btn,LinearLayout.LayoutParams(-2,dp(40)).apply{marginEnd=dp(9)})
        }
        scroll.addView(row)
        column.addView(scroll,LinearLayout.LayoutParams(-1,dp(53)))
    }
    private fun renderSports(column:LinearLayout) {
        addHeading(column,"Sports","Games and channels")
        filterChips(column,listOf("All","NFL","NBA","NHL","MLB","NCAAF","NCAAM","Soccer","MLS","Premier League","La Liga"),sportsFilter) {
            sportsFilter=it;requestedFocusKey="filter:$it";renderScreen()
        }
        if (BuildConfig.DEBUG) {
            val games=matchingScores(sportsFilter)
            val live=games.filter{it.state=="in"}.sortedBy{it.startMillis}
            val upcoming=games.filter{it.state=="pre"}.sortedBy{it.startMillis}
            val completed=games.filter{it.state=="post"}.sortedByDescending{it.startMillis}
            gameRow(column,"Live now",live)
            gameRow(column,"Coming up",upcoming.take(30))
            gameRow(column,"Final scores",completed.take(15))
            if(games.isEmpty())addDescription(column,
                if(scoreRefreshRunning)"Loading ESPN scoreboard data..." else "No game data available for this filter. Refresh or choose another league.",
                "Refresh scores") {
                    refreshUiOnNextScores=true
                    scoreThreads.execute {
                        try { File(cacheDir,"petalix-espn-beta.json").delete() } catch(_:Exception) {}
                        runOnUiThread{refreshScores()}
                    }
                }
            val age=scores?.updatedAt ?: 0L
            if(age>0)addDescription(column,
                "Sports data last updated ${DateFormat.format("h:mm a", Date(age))}. ESPN data is for beta testing; video remains from your playlist.",
                "Refresh scores") {
                    refreshUiOnNextScores=true
                    scoreThreads.execute{
                        try { File(cacheDir,"petalix-espn-beta.json").delete() }catch(_:Exception){}
                        runOnUiThread{refreshScores()}
                    }
                }
        } else {
            addDescription(column,"Live scores are disabled in this customer build until licensed sports data is configured.")
        }
        val sport=channels.filter(::isSports)
        val filtered=if(sportsFilter=="All")sport else sport.filter{
            (it.name+" "+it.group+" "+it.now).contains(sportsFilter,true) ||
                (sportsFilter=="Soccer" && listOf("premier","la liga","serie a","mls").any{word->
                    (it.name+" "+it.group).contains(word,true)})
        }
        if(filtered.isNotEmpty())channelRow(column,"Sports channels",filtered,"${filtered.size} matching")
        else if(channels.isNotEmpty())addDescription(column,"No matching sports channel in this playlist group.")
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
                val btn=tvButton((if(followedTeams.contains(id))"★ " else "☆ ")+name){toggleTeam(id);requestedFocusKey="team:$id";renderScreen()}
                btn.tag="team:$id"
                items.addView(btn,LinearLayout.LayoutParams(dp(155),dp(58)).apply{marginEnd=dp(10)})
            }
            row.addView(items)
            column.addView(row,LinearLayout.LayoutParams(-1,dp(77)))
        }
        if (BuildConfig.DEBUG) {
            val following=followedTeams
            val selected=(scores?.games ?: emptyList()).filter { game ->
                following.contains("${game.league}|${game.home.name}") ||
                    following.contains("${game.league}|${game.away.name}")
            }
            gameRow(column,"Games for your teams",selected)
            addDescription(column,"Followed teams are stored on this device. ESPN schedules are beta-only; channel playback comes from your playlist.")
        } else addDescription(column,"Your favorite teams are stored on this device. Customer sports scores require a licensed data feed.")
    }
    private fun renderFavorites(column:LinearLayout){
        addHeading(column,"Favorites")
        val saved=channels.filter { favorites.contains(it.id) }
        if(saved.isEmpty()) addDescription(column,"No favorite channels yet. Hold OK on any channel tile to add or remove it.","Browse Home") {tab="Home";renderScreen()}
        else channelRow(column,"Favorite channels",saved)
    }
    private fun selectedLocalChannels() = channels.filter { locals.contains(it.id) }
    private fun marketForZip(zip:String):LocalStationMatcher.Market? {
        val override=preferences.getString("market_for_$zip", "").orEmpty()
        return LocalStationMatcher.markets.firstOrNull { it.id==override }
            ?: LocalStationMatcher.findMarket(zip)
    }
    private fun saveZipAndFind(entry: String) {
        val zip=entry.trim()
        if(!zip.matches(Regex("^[0-9]{5}$"))){
            localMessage="Enter a valid 5-digit ZIP code."
            requestedFocusKey="locals:zip";renderScreen();return
        }
        preferences.edit().putString("local_zip",zip).apply()
        localMessage=""
        val market=marketForZip(zip)
        if(market==null){
            localMessage="No exact TV market is mapped for $zip yet. Choose your market below, or search your provider's local stations by call sign."
        }else{
            chooseRecommendedLocals(market)
        }
        requestedFocusKey="locals:find"
        renderScreen()
    }
    private fun chooseRecommendedLocals(market:LocalStationMatcher.Market){
        val stations=channels.map { LocalStationMatcher.Station(it.id,it.name,it.group,it.logo) }
        val found=LocalStationMatcher.recommendations(market,stations)
        val ids=found.mapNotNull { it.station?.id }.toSet()
        // Recommendations are suggestions, not forced selections; the viewer uses Add / Add all.
        preferences.edit().putStringSet("suggested_local_ids",ids).apply()
        val matched=found.count{it.station!=null}
        localMessage=if(matched>0)"Found $matched local stations in your imported playlist. Press Add or Add all to save them."
            else "No matching affiliates in your imported playlist. Check its local-channel groups or search by call sign."
    }
    private fun renderLocals(column:LinearLayout){
        addHeading(column,"Local channels","Find local stations by ZIP, then choose which appear in your guide")
        val zip=preferences.getString("local_zip", "").orEmpty()
        val zipRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
        val input=EditText(this).apply {
            tag="locals:zip"
            setSingleLine(true);textSize=19f
            inputType=InputType.TYPE_CLASS_NUMBER
            filters=arrayOf(InputFilter.LengthFilter(5))
            imeOptions=EditorInfo.IME_ACTION_DONE
            setText(zip)
            hint="ZIP code (e.g. 28601)"
            setHintTextColor(colorMuted);setTextColor(Color.WHITE)
            background=pill(colorPanel,Color.rgb(94,108,154))
            setPadding(dp(18),dp(9),dp(18),dp(9))
            setOnEditorActionListener { _,_,_ -> saveZipAndFind(text.toString());true }
            setOnFocusChangeListener { view,focused ->
                background=pill(colorPanel,if(focused)Color.WHITE else Color.rgb(94,108,154))
                if(focused){lastFocusKey="locals:zip";view.post{view.requestRectangleOnScreen(Rect(0,0,view.width,view.height),false)}}
            }
        }
        localInput=input
        zipRow.addView(input,LinearLayout.LayoutParams(dp(278),dp(57)))
        val lookup=tvButton("Find my local stations") { saveZipAndFind(input.text.toString()) }
        lookup.tag="locals:find"
        zipRow.addView(lookup,LinearLayout.LayoutParams(-2,dp(57)).apply {marginStart=dp(12)})
        column.addView(zipRow,LinearLayout.LayoutParams(-1,dp(65)))
        if(localMessage.isNotBlank())addDescription(column,localMessage)
        if(channels.isEmpty()){
            addDescription(column,"First import your M3U or Xtream playlist under Settings. ZIP matching cannot create streams that aren't in your provider's list.","Playlist Settings") {openSettings()}
            return
        }
        val market=marketForZip(zip)
        if(market!=null){
            addHeading(column,"Nearest TV market",market.name)
            addDescription(column,"Stations come from your playlist. Select the stations you want, or Add all. Some ZIP-to-market matches are offline beta hints.")
            val matches=LocalStationMatcher.recommendations(market,channels.map {
                LocalStationMatcher.Station(it.id,it.name,it.group,it.logo) })
            val available = matches.mapNotNull {match ->
                val found=match.station ?: return@mapNotNull null
                val ch=channels.firstOrNull{it.id==found.id} ?: return@mapNotNull null
                match to ch
            }
            if(available.isNotEmpty()){
                val addAll=tvButton("+ Add all ${available.size} stations"){
                    val updated=locals.toMutableSet()
                    available.forEach{updated.add(it.second.id)}
                    preferences.edit().putStringSet("local_ids",updated).apply()
                    requestedFocusKey="locals:addAll";renderScreen()
                }
                addAll.tag="locals:addAll"
                column.addView(addAll,LinearLayout.LayoutParams(dp(270),dp(46)).apply{bottomMargin=dp(9)})
            }
            val group=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
            matches.chunked(3).forEach { items ->
                val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
                items.forEach { match ->
                    val ch=match.station?.let { station -> channels.firstOrNull { it.id==station.id } }
                    val checked=ch!=null && locals.contains(ch.id)
                    val label="${match.network}  •  ${match.callsign}\n"+
                        (if(ch==null)"Not in your playlist" else (if(checked)"✓ Added" else "+ Add")+"  ${ch.name.take(28)}")
                    val action=tvButton(label){
                        if(ch!=null){
                            val saved=locals.toMutableSet()
                            if(!saved.add(ch.id))saved.remove(ch.id)
                            preferences.edit().putStringSet("local_ids",saved).apply()
                            requestedFocusKey="local:${match.network}";renderScreen()
                        }
                    }
                    action.tag="local:${match.network}"
                    action.isEnabled=ch!=null
                    action.textSize=13f
                    action.gravity=Gravity.CENTER_VERTICAL or Gravity.START
                    row.addView(action,LinearLayout.LayoutParams(0,dp(77),1f).apply{marginEnd=dp(10)})
                }
                group.addView(row,LinearLayout.LayoutParams(-1,dp(84)))
            }
            column.addView(group)
        }else if(zip.length==5){
            addDescription(column,"This ZIP isn't in the offline beta lookup. Select a TV market below. The hosted panel will supply exact ZIP-to-market mapping later.")
        }
        addHeading(column,"Change market", "If your local affiliates are different")
        val markets=LocalStationMatcher.markets
        val marketRows=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        markets.chunked(4).forEach {batch->
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            batch.forEach {option->
                val button=tvButton(if(market?.id==option.id)"✓ ${option.name}" else option.name){
                    if(zip.length!=5){localMessage="Enter a ZIP code first.";renderScreen();return@tvButton}
                    preferences.edit().putString("market_for_$zip",option.id).apply()
                    chooseRecommendedLocals(option)
                    requestedFocusKey="market:${option.id}";renderScreen()
                }
                button.tag="market:${option.id}"
                button.textSize=12f
                row.addView(button,LinearLayout.LayoutParams(0,dp(53),1f).apply{marginEnd=dp(6)})
            }
            marketRows.addView(row,LinearLayout.LayoutParams(-1,dp(58)))
        }
        column.addView(marketRows)
        addHeading(column,"Search your playlist", "Station, callsign, city or group")
        val searchRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val search=EditText(this).apply{
            tag="locals:search"
            setSingleLine(true);textSize=17f
            setText(localSearch)
            hint="e.g. WSOC, WBTV, Charlotte"
            setTextColor(Color.WHITE);setHintTextColor(colorMuted)
            inputType=InputType.TYPE_CLASS_TEXT
            imeOptions=EditorInfo.IME_ACTION_SEARCH
            background=pill(colorPanel,Color.rgb(94,108,154))
            setPadding(dp(14),dp(7),dp(14),dp(7))
            setOnEditorActionListener { _,_,_->localSearch=text.toString().trim();requestedFocusKey="locals:search";renderScreen();true }
        }
        searchRow.addView(search,LinearLayout.LayoutParams(dp(320),dp(56)))
        val btn=tvButton("Search") {localSearch=search.text.toString().trim();requestedFocusKey="locals:searchBtn";renderScreen()}
        btn.tag="locals:searchBtn"
        searchRow.addView(btn,LinearLayout.LayoutParams(-2,dp(56)).apply{marginStart=dp(12)})
        column.addView(searchRow)
        val query=localSearch.lowercase(Locale.ROOT)
        if(query.length>=2){
            val results=channels.filter { ch ->
                (ch.name+" "+ch.group).lowercase(Locale.ROOT).contains(query)
            }.take(12)
            if(results.isEmpty())addDescription(column,"No matches in your playlist for $localSearch.")
            else{
                addHeading(column,"Search results","${results.size} shown • maximum 12")
                results.forEach { ch ->
                    val add=tvButton((if(locals.contains(ch.id))"✓ " else "+ ")+ch.name+"  •  "+ch.group){
                        val set=locals.toMutableSet()
                        if(!set.add(ch.id))set.remove(ch.id)
                        preferences.edit().putStringSet("local_ids",set).apply()
                        requestedFocusKey="lookup:${ch.id}";renderScreen()
                    }
                    add.tag="lookup:${ch.id}"
                    column.addView(add,LinearLayout.LayoutParams(-1,dp(52)).apply{bottomMargin=dp(5)})
                }
            }
        }
        val selected=selectedLocalChannels()
        if(selected.isNotEmpty())channelRow(column,"My saved locals",selected,"Shown on Home and at the top of your guide")
    }

    private fun toggleFavorite(id:String){
        val selected=favorites.toMutableSet()
        if(!selected.add(id))selected.remove(id)
        preferences.edit().putStringSet("favorite_ids",selected).apply()
        requestedFocusKey = "channel:$id"
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
            val alternates=FeedMatcher.candidates(FeedMatcher.Feed(ch.name,ch.url),
                channels.map{FeedMatcher.Feed(it.name,it.url)})
            putExtra("feeds_json",JSONArray().apply {
                alternates.forEach{feed ->put(JSONObject().put("name",feed.name).put("url",feed.url))}
            }.toString())
        })
    }
    private fun openGuide(selectedGroup:String?=null) {
        if(channels.isEmpty()){openSettings();return}
        val out=File(cacheDir,"petalix-guide.json")
        try {
            val wrapper=JSONObject().put("channels",JSONArray().apply {
                // A small My Locals group sits at the top without removing the original categories.
                channels.filter { locals.contains(it.id) }.take(12).forEach { ch ->
                    put(JSONObject().put("id",ch.id).put("name",ch.name).put("group","My Locals")
                        .put("logo",ch.logo).put("url",ch.url).put("now",ch.now).put("next",ch.next))
                }
                channels.take(7000).forEach { ch ->
                    put(JSONObject().put("id",ch.id).put("name",ch.name).put("group",ch.group)
                        .put("logo",ch.logo).put("url",ch.url).put("now",ch.now).put("next",ch.next))
                }
            })
            out.writeText(wrapper.toString(),Charsets.UTF_8)
        } catch (_:Exception) { return }
        startActivity(Intent(this,GuideActivity::class.java).apply {
            if(!selectedGroup.isNullOrBlank())putExtra("selected_group",selectedGroup)
        })
    }
    private fun openSettings(){
        startActivity(Intent(this,MainActivity::class.java).putExtra("native_settings",true))
    }
    override fun onDestroy() {
        imageThreads.shutdownNow()
        scoreThreads.shutdownNow()
        super.onDestroy()
    }
    private fun openOnDemand(tab:String){
        startActivity(Intent(this,MainActivity::class.java).putExtra("native_settings",true)
            .putExtra("native_page",tab))
    }
}
