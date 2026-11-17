package com.nuvio.tv.petalix

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Actual media bytes per media duration, never connection speed or the channel's label. */
internal object PetalixStreamMeasurements {
    private var preferences: SharedPreferences? = null
    private val observed = MutableStateFlow<Map<String, Long>>(emptyMap())
    val bitrates = observed.asStateFlow()

    @Synchronized fun initialize(context: Context) {
        if (preferences != null) return
        preferences = context.applicationContext.getSharedPreferences("petalix_stream_measurements", Context.MODE_PRIVATE)
        val saved = runCatching { JSONObject(preferences!!.getString("bitrates", "{}")!!) }.getOrDefault(JSONObject())
        observed.value = saved.keys().asSequence().associateWith { saved.optLong(it) }.filterValues { it > 0 }
    }

    @Synchronized fun record(channelKey: String, bytes: Long, durationMs: Long) {
        if (bytes <= 0 || durationMs !in 500..120_000) return
        val sample = bytes * 8_000L / durationMs
        if (sample !in 64_000..200_000_000) return
        val previous = observed.value[channelKey]
        val average = if (previous == null) sample else (previous * 3 + sample) / 4
        if (previous != null && kotlin.math.abs(average - previous) < previous / 20) return
        observed.value = observed.value + (channelKey to average)
        preferences?.edit()?.putString("bitrates", JSONObject(observed.value).toString())?.apply()
    }
}
