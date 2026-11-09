package com.nuvio.tv.petalix

import android.content.Context
import com.nuvio.tv.BuildConfig

/** Metadata configuration delivered only after this TV's PIN or saved token is authenticated. */
internal object PetalixRuntimeConfig {
    @Volatile private var key: String = BuildConfig.TMDB_API_KEY
    val apiKey: String get() = key
    fun load(context: Context) { key = context.getSharedPreferences("petalix_native_config", Context.MODE_PRIVATE).getString("tmdb", key).orEmpty() }
    fun store(context: Context, value: String) {
        require(value.matches(Regex("[a-fA-F0-9]{32}"))) { "Metadata configuration unavailable. Retry the panel connection." }
        key = value
        check(context.getSharedPreferences("petalix_native_config", Context.MODE_PRIVATE).edit().putString("tmdb", value).commit())
    }
}
