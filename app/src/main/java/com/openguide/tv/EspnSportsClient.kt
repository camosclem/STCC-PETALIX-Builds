package com.openguide.tv

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** ESPN public-site scoreboards: beta testing only, not a licensed commercial API. */
internal class EspnSportsClient(private val folder: File) {
    data class Game(
        val league: String, val title: String, val state: String, val status: String,
        val home: String, val away: String, val homeScore: String, val awayScore: String,
        val network: String
    )
    private val cache = File(folder, "espn-scores-beta.json")
    private val sources = linkedMapOf(
        "NFL" to "football/nfl", "NBA" to "basketball/nba",
        "NHL" to "hockey/nhl", "MLB" to "baseball/mlb",
        "NCAAF" to "football/college-football",
        "NCAAM" to "basketball/mens-college-basketball",
        "MLS" to "soccer/usa.1", "Premier League" to "soccer/eng.1",
        "La Liga" to "soccer/esp.1"
    )
    fun cached():List<Game> = try {
        parse(JSONObject(cache.readText()).optJSONArray("games") ?: JSONArray())
    } catch (_:Exception) { emptyList() }
    fun fresh():Boolean = cache.exists() &&
        System.currentTimeMillis() - cache.lastModified() < 180000L
    fun fetch():List<Game> {
        if(fresh()) return cached()
        val games = JSONArray()
        for((league, path) in sources) {
            try {
                val uri = "https://site.api.espn.com/apis/site/v2/sports/" + path + "/scoreboard"
                val connection = URL(uri).openConnection() as HttpURLConnection
                connection.connectTimeout=6000
                connection.readTimeout=8000
                connection.setRequestProperty("Accept","application/json")
                val response = try {
                    if(connection.responseCode !in 200..299) error("Scoreboard status")
                    connection.inputStream.use { stream ->
                        val buffer=java.io.ByteArrayOutputStream()
                        val bytes=ByteArray(8192)
                        while(true) {
                            val count=stream.read(bytes)
                            if(count<0) break
                            if(buffer.size()+count>3*1024*1024) error("Oversized score response")
                            buffer.write(bytes,0,count)
                        }
                        JSONObject(buffer.toString("UTF-8"))
                    }
                } finally { connection.disconnect() }
                val events=response.optJSONArray("events") ?: JSONArray()
                for(i in 0 until minOf(events.length(),32)) {
                    val event=events.optJSONObject(i) ?: continue
                    val competition=event.optJSONArray("competitions")?.optJSONObject(0) ?: continue
                    val competitors=competition.optJSONArray("competitors") ?: continue
                    var home:JSONObject?=null
                    var away:JSONObject?=null
                    for(j in 0 until competitors.length()) {
                        val side=competitors.optJSONObject(j) ?: continue
                        if(side.optString("homeAway")=="home") home=side
                        if(side.optString("homeAway")=="away") away=side
                    }
                    if(home==null || away==null)continue
                    val type=event.optJSONObject("status")?.optJSONObject("type") ?: JSONObject()
                    val nets=mutableListOf<String>()
                    val broadcasts=competition.optJSONArray("broadcasts") ?: JSONArray()
                    for(j in 0 until broadcasts.length()){
                        val names=broadcasts.optJSONObject(j)?.optJSONArray("names") ?: continue
                        for(k in 0 until names.length()){
                            val name=names.optString(k)
                            if(name.isNotBlank() && name.length<70) nets.add(name)
                        }
                    }
                    games.put(JSONObject()
                        .put("league",league).put("title",event.optString("name").take(120))
                        .put("state",type.optString("state"))
                        .put("status",type.optString("shortDetail").take(100))
                        .put("home",home.optJSONObject("team")?.optString("name") ?: "")
                        .put("away",away.optJSONObject("team")?.optString("name") ?: "")
                        .put("homeScore",home.optString("score"))
                        .put("awayScore",away.optString("score"))
                        .put("network",nets.distinct().joinToString(", ").take(120)))
                }
            } catch(_:Exception) { /* Preserve other leagues and cached data on outage */ }
        }
        val parsed=parse(games)
        if(parsed.isEmpty())return cached()
        try{cache.writeText(JSONObject().put("games",games).toString())}catch(_:Exception){}
        return parsed
    }
    private fun parse(items:JSONArray):List<Game>{
        val result=ArrayList<Game>()
        for(i in 0 until minOf(items.length(),250)){
            val v=items.optJSONObject(i) ?: continue
            result.add(Game(v.optString("league"),v.optString("title"),v.optString("state"),
                v.optString("status"),v.optString("home"),v.optString("away"),
                v.optString("homeScore"),v.optString("awayScore"),v.optString("network")))
        }
        return result
    }
}