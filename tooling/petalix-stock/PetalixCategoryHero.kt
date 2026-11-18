package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.ui.util.StableList

internal fun petalixCategoryHero(base: ModernHeroSceneState, category: String?): ModernHeroSceneState {
    val art = when (category) {
        "Sports" -> "sports"
        "News" -> "news"
        "Entertainment" -> "entertainment"
        "My Locals" -> "locals"
        "24/7 Channels" -> "around-clock"
        "Westerns" -> "westerns"
        else -> return base
    }
    val backdrop = "file:///android_asset/petalix/home-art/$art.webp"
    return base.copy(
        heroBackdrop = backdrop,
        preview = HeroPreview(title = category.orEmpty(), logo = null, description = null,
            contentTypeText = "Live TV", yearText = null, imdbText = null,
            genres = StableList(), poster = null, backdrop = backdrop, imageUrl = backdrop),
        enrichmentActive = false, shouldPlayTrailer = false, trailerFirstFrameRendered = false,
        trailerUrl = null, trailerAudioUrl = null, trailerPlaybackKey = null, fullScreenBackdrop = false
    )
}
