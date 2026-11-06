package com.openguide.tv

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Beta-only ESPN scoreboard adapter. It uses publicly reachable, undocumented web endpoints.
 * There is no API key embedded or extracted from another application. For commercial releases,
 * use a licensed provider unless reuse rights are confirmed.
 */
internal class EspnSportsClient(private val cacheDir: File) {
    data class Side(
        val name: String,
        val abbreviation: String,
        val score: String,
        val logo: String,
        val homeAway: String
    )
    data class Game(
        val id: String,
        val league: String,
        val name: String,
        val startMillis: Long,
        val state: String,
        val status: String,
        val summary: String,
        val home: Side,
        val away: Side,
        val broadcasters: String
    )
    data class Snapshot(val games: List<Game>, val updatedAt: Long, val errors: Int)

    private val leagues = linkedMapOf(
        "NFL" to "football/nfl",
        "NBA" to "basketball/nba",
        "NHL" to "hockey/nhl",
        "MLB" to "baseball/mlb",
        "NCAAF" to "football/college-football",
        "NCAAM" to "basketball/mens-college-basketball",
        "MLS" to "soccer/usa.1",
        "Premier League" to "soccer/eng.1",
        "La Liga" to "soccer/esp.1"
    )
    private val cacheFile = File(cacheDir, "petalix-espn-beta.json")
    private val maxAgeMillis = 3L * 60_000 // 3 minutes: avoid hammering upstream
    private val maxResponseBytes = 2 * 1024 * 1024

    fun loadCache(): Snapshot? = try {
        if (!cacheFile.exists()) null else parseSnapshot(JSONObject(cacheFile.readText(Charsets.UTF_8)))
    } catch (_: Exception) { null }

    fun isFresh(): Boolean = loadCache()?.let { System.currentTimeMillis() - it.updatedAt < maxAgeMillis } ?: false

    /** Call this from a background thread. Never call on the Android main thread. */
    fun refresh(): Snapshot {
        val previous = loadCache()
        if (previous != null && isFresh()) return previous
        val items = JSONArray()
        var errors = 0
        // Scoreboard is used for current round/day. Some sports may be out of season.
        for ((label, path) in leagues) {
            try {
                val response = download("https://site.api.espn.com/apis/site/v2/sports/$path/scoreboard")
                val events = JSONObject(response).optJSONArray("events") ?: JSONArray()
                for (i in 0 until minOf(events.length(), 30)) {
                    val game = parseGame(label, events.optJSONObject(i) ?: continue) ?: continue
                    items.put(game)
                }
            } catch (_: Exception) { errors++ }
        }
        if (items.length() == 0 && previous != null) return previous.copy(errors = errors)
        val root = JSONObject().put("updated_at", System.currentTimeMillis())
            .put("errors", errors).put("games", items)
        try {
            cacheFile.parentFile?.mkdirs()
            val temp = File(cacheDir,"petalix-espn-beta.tmp")
            temp.writeText(root.toString(), Charsets.UTF_8)
            if (!temp.renameTo(cacheFile)) {
                cacheFile.writeText(root.toString(), Charsets.UTF_8)
                temp.delete()
            }
        } catch (_: Exception) { /* still return fetched data */ }
        return parseSnapshot(root)
    }

    private fun download(address: String): String {
        val conn = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 8000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "PetalixTV-Beta/0.6 (Android TV)")
        }
        return try {
            if (conn.responseCode !in 200..299) error("Scores HTTP ${conn.responseCode}")
            val buffer = java.io.ByteArrayOutputStream()
            conn.inputStream.use { source ->
                val block = ByteArray(8192)
                while (true) {
                    val count = source.read(block)
                    if (count < 0) break
                    if (buffer.size() + count > maxResponseBytes) error("Scores response exceeds limit")
                    buffer.write(block, 0, count)
                }
            }
            buffer.toString(Charsets.UTF_8.name())
        } finally { conn.disconnect() }
    }

    private fun parseGame(league: String, event: JSONObject): JSONObject? {
        val competition = event.optJSONArray("competitions")?.optJSONObject(0) ?: return null
        val competitors = competition.optJSONArray("competitors") ?: return null
        var home: JSONObject? = null
        var away: JSONObject? = null
        for (i in 0 until competitors.length()) {
            val c = competitors.optJSONObject(i) ?: continue
            if (c.optString("homeAway") == "home") home = c
            if (c.optString("homeAway") == "away") away = c
        }
        if (home == null || away == null) return null
        val status = event.optJSONObject("status") ?: JSONObject()
        val statusType = status.optJSONObject("type") ?: JSONObject()
        val broadcasterSet = linkedSetOf<String>()
        val broadcasts = competition.optJSONArray("broadcasts") ?: JSONArray()
        for (i in 0 until broadcasts.length()) {
            val b = broadcasts.optJSONObject(i) ?: continue
            val names = b.optJSONArray("names") ?: JSONArray()
            for (ix in 0 until names.length()) {
                val name = names.optString(ix).take(50)
                if (name.isNotBlank()) broadcasterSet.add(name)
            }
        }
        return JSONObject().put("id", event.optString("id").take(32))
            .put("league", league)
            .put("name", event.optString("name").take(150))
            .put("date", event.optString("date").take(50))
            .put("state", statusType.optString("state", "pre").take(12))
            .put("status", statusType.optString("shortDetail", "Scheduled").take(90))
            .put("summary", statusType.optString("detail", "").take(120))
            .put("home", side(home))
            .put("away", side(away))
            .put("broadcasters", broadcasterSet.joinToString(", ").take(160))
    }

    private fun side(competitor: JSONObject): JSONObject {
        val team = competitor.optJSONObject("team") ?: JSONObject()
        return JSONObject()
            .put("name", team.optString("name").take(70))
            .put("abbr", team.optString("abbreviation").take(12))
            .put("score", competitor.optString("score").take(10))
            .put("logo", team.optString("logo").take(450))
            .put("homeAway", competitor.optString("homeAway").take(8))
    }

    private fun parseSnapshot(root: JSONObject): Snapshot {
        val array = root.optJSONArray("games") ?: JSONArray()
        val games = ArrayList<Game>()
        for (i in 0 until minOf(array.length(), 250)) {
            val item = array.optJSONObject(i) ?: continue
            val home = item.optJSONObject("home") ?: continue
            val away = item.optJSONObject("away") ?: continue
            fun readSide(s: JSONObject) = Side(
                s.optString("name"), s.optString("abbr"), s.optString("score"),
                s.optString("logo"), s.optString("homeAway")
            )
            val date = item.optString("date")
            val timestamp = try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mmX", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }.parse(date)?.time ?: 0L
            } catch (_: Exception) { 0L }
            games.add(Game(
                item.optString("id"), item.optString("league"), item.optString("name"),
                timestamp, item.optString("state"), item.optString("status"),
                item.optString("summary"), readSide(home), readSide(away),
                item.optString("broadcasters")
            ))
        }
        return Snapshot(games, root.optLong("updated_at"), root.optInt("errors"))
    }
}
