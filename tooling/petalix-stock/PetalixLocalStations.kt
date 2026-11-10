package com.nuvio.tv.petalix

import android.content.Context
import com.nuvio.tv.livetv.model.LiveChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.*

internal class PetalixLocalStations(private val context: Context, private val http: OkHttpClient) {
    data class Result(val location: String, val nearby: List<LiveChannel>, val available: List<LiveChannel>)
    private data class Station(val call: String, val city: String, val state: String, val miles: Double)

    suspend fun lookup(zip: String, channels: suspend () -> List<LiveChannel>): Result = withContext(Dispatchers.IO) {
        require(zip.matches(Regex("[0-9]{5}"))) { "Enter a five-digit ZIP code." }
        val request = Request.Builder().url("https://api.zippopotam.us/us/$zip").build()
        val place = http.newCall(request).execute().use { response ->
            check(response.code != 404) { "ZIP code not found." }
            check(response.isSuccessful) { "ZIP lookup is unavailable. Please try again." }
            JSONObject(requireNotNull(response.body).string()).getJSONArray("places").getJSONObject(0)
        }
        val lat = place.getDouble("latitude")
        val lon = place.getDouble("longitude")
        val city = place.getString("place name")
        val state = place.getString("state abbreviation")
        val directory = context.assets.open("petalix/local-stations.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONArray("stations")
        }
        val stations = (0 until directory.length()).map { index ->
            val entry = directory.getJSONObject(index)
            Station(
                canonical(entry.getString("call")),
                entry.getString("city"),
                entry.getString("state"),
                distance(lat, lon, entry.getDouble("lat"), entry.getDouble("lon"))
            )
        }.sortedBy { it.miles }.distinctBy { it.call }
        val byCall = stations.associateBy { it.call }
        val nearbyMarkets = stations.filter { it.miles <= 90 }
            .groupBy { it.city.lowercase() to it.state.lowercase() }
            .mapValues { (_, market) -> market.first().miles }
        val ranked = channels().mapNotNull { channel ->
            val label = listOfNotNull(channel.name, channel.tvgName, channel.tvgId).joinToString(" ")
            val station = callToken.findAll(label).map { canonical(it.value) }
                .firstNotNullOfOrNull { byCall[it] }
            val isLocal = station != null || localGroup.containsMatchIn(channel.group)
            if (!isLocal) return@mapNotNull null
            val namedDistance = nearbyMarkets.entries.firstOrNull { (market, _) ->
                val (marketCity, marketState) = market
                val parts = channel.name.split('|').map { it.trim().lowercase() }
                marketCity in parts && marketState in parts
            }?.value
            channel to (station?.miles ?: namedDistance ?: Double.POSITIVE_INFINITY)
        }.distinctBy { it.first.key }.sortedWith(compareBy({ it.second }, { it.first.name }))
        Result("$city, $state", ranked.filter { it.second <= 90 }.map { it.first }, ranked.map { it.first })
    }

    private fun canonical(call: String) = call.uppercase().replace(callSuffix, "")

    private fun distance(lat: Double, lon: Double, stationLat: Double, stationLon: Double): Double {
        val a = sin(Math.toRadians(stationLat - lat) / 2).pow(2) +
            cos(Math.toRadians(lat)) * cos(Math.toRadians(stationLat)) *
            sin(Math.toRadians(stationLon - lon) / 2).pow(2)
        return 3958.7613 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    private companion object {
        val callToken = Regex("(?i)(?<![A-Z0-9])[WK][A-Z]{2,3}(?:[- .]?(?:TV|DT|LD|CD|LP))?(?![A-Z])")
        val callSuffix = Regex("[- .](TV|DT|LD|CD|LP)$")
        val localGroup = Regex("(?i)\\blocals?\\b|\\baffiliates?\\b")
    }
}
