package com.nuvio.tv.petalix

import androidx.compose.runtime.mutableStateOf

/** Current programme backdrops shared by native shortcut cards and the focus hero. */
internal object PetalixLiveArtwork {
    val images = mutableStateOf<Map<String, String>>(emptyMap())
    fun forCategory(slug: String) = images.value[slug]
        ?: "file:///android_asset/petalix/home-art/$slug.webp"
}
