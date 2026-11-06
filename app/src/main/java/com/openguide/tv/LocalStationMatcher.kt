package com.openguide.tv

import java.util.Locale

/**
 * Offline, deterministic affiliate finder for private beta.
 * No inference from a generic network's national stream. The ZIP must identify a
 * known local TV market (or the viewer must select one). A station must actually
 * appear in the customer's own catalog before we claim it is watchable.
 *
 * The panel's exact ZIP-to-market database supersedes these limited test hints
 * after the PIN activation backend is deployed.
 */
object LocalStationMatcher {
    data class Market(
        val id: String, val name: String, val calls: Map<String, String>,
        val cityHints: List<String>, val exactZips: Set<String> = emptySet(),
        val centerPrefixes: Set<String> = emptySet()
    )
    data class Station(val id: String, val name: String, val group: String, val logo: String = "")
    data class Match(val network: String, val callsign: String, val station: Station?)
    val networks = listOf("ABC", "CBS", "NBC", "FOX")
    val markets = listOf(
        Market("charlotte", "Charlotte / Hickory, NC", mapOf("ABC" to "WSOC", "CBS" to "WBTV", "NBC" to "WCNC", "FOX" to "WJZY"), listOf("charlotte", "hickory", "belmont"), setOf("28601", "28602"), setOf("282")),
        Market("raleigh", "Raleigh / Durham, NC", mapOf("ABC" to "WTVD", "CBS" to "WNCN", "NBC" to "WRAL", "FOX" to "WRAZ"), listOf("raleigh", "durham"), centerPrefixes=setOf("276", "277")),
        Market("greensboro", "Greensboro / Winston-Salem, NC", mapOf("ABC" to "WXLV", "CBS" to "WFMY", "NBC" to "WXII", "FOX" to "WGHP"), listOf("greensboro", "winston-salem"), centerPrefixes=setOf("274", "271")),
        Market("asheville", "Greenville / Asheville, SC-NC", mapOf("ABC" to "WLOS", "CBS" to "WSPA", "NBC" to "WYFF", "FOX" to "WHNS"), listOf("asheville", "greenville sc", "spartanburg"), centerPrefixes=setOf("288")),
        Market("wilmington", "Wilmington, NC", mapOf("ABC" to "WWAY", "CBS" to "WWAY", "NBC" to "WECT", "FOX" to "WSFX"), listOf("wilmington nc"), centerPrefixes=setOf("284")),
        Market("atlanta", "Atlanta, GA", mapOf("ABC" to "WSB", "CBS" to "WANF", "NBC" to "WXIA", "FOX" to "WAGA"), listOf("atlanta"), centerPrefixes=setOf("303")),
        Market("newyork", "New York, NY", mapOf("ABC" to "WABC", "CBS" to "WCBS", "NBC" to "WNBC", "FOX" to "WNYW"), listOf("new york", "nyc"), centerPrefixes=setOf("100", "101")),
        Market("washington", "Washington, DC", mapOf("ABC" to "WJLA", "CBS" to "WUSA", "NBC" to "WRC", "FOX" to "WTTG"), listOf("washington dc"), centerPrefixes=setOf("200")),
        Market("chicago", "Chicago, IL", mapOf("ABC" to "WLS", "CBS" to "WBBM", "NBC" to "WMAQ", "FOX" to "WFLD"), listOf("chicago"), centerPrefixes=setOf("606")),
        Market("dallas", "Dallas / Fort Worth, TX", mapOf("ABC" to "WFAA", "CBS" to "KTVT", "NBC" to "KXAS", "FOX" to "KDFW"), listOf("dallas", "fort worth"), centerPrefixes=setOf("752", "761")),
        Market("houston", "Houston, TX", mapOf("ABC" to "KTRK", "CBS" to "KHOU", "NBC" to "KPRC", "FOX" to "KRIV"), listOf("houston"), centerPrefixes=setOf("770")),
        Market("losangeles", "Los Angeles, CA", mapOf("ABC" to "KABC", "CBS" to "KCBS", "NBC" to "KNBC", "FOX" to "KTTV"), listOf("los angeles"), centerPrefixes=setOf("900"))
    )
    private val tokenRegexCache = HashMap<String, Regex>()
    private fun hasToken(text: String, token: String): Boolean {
        val matcher = tokenRegexCache.getOrPut(token) { Regex("(^|[^A-Z0-9])" + Regex.escape(token) + "([^A-Z0-9]|$)") }
        return matcher.containsMatchIn(text)
    }
    fun findMarket(zip: String): Market? {
        if (!Regex("^[0-9]{5}$").matches(zip)) return null
        return markets.firstOrNull { zip in it.exactZips }
            ?: markets.firstOrNull { zip.take(3) in it.centerPrefixes }
    }
    fun recommendations(market: Market, stations: List<Station>): List<Match> {
        val used = HashSet<String>()
        return networks.map { network ->
            val sign = market.calls.getValue(network)
            val option = stations.asSequence().mapNotNull { station ->
                val text = (station.name + " " + station.group).uppercase(Locale.US)
                val callScore = if (hasToken(text, sign)) 200 else 0
                val geo = if (market.cityHints.any { text.lowercase(Locale.US).contains(it) }) 40 else 0
                val type = if (hasToken(text, network)) 15 else 0
                // Avoid falsely labeling national ABC/CBS/etc channels as local stations.
                if (callScore == 0 && (geo == 0 || type == 0)) return@mapNotNull null
                val hd = if (hasToken(text, "HD")) 4 else 0
                val score = callScore + geo + type + hd
                Triple(station, score, callScore > 0)
            }.filter { it.first.id !in used }
                .sortedWith(compareByDescending<Triple<Station, Int, Boolean>> { it.second }.thenBy { it.first.name })
                .firstOrNull()?.first
            if (option != null) used.add(option.id)
            Match(network, sign, option)
        }
    }
}
