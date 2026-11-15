package com.nuvio.tv.petalix

import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.CollectionFolder
import com.nuvio.tv.domain.model.PosterShape

internal const val PETALIX_LIVE_SHORTCUT_COLLECTION = "petalix-home-live-categories"
internal data class PetalixHomeShortcut(val slug: String, val title: String, val description: String)
internal val petalixHomeShortcuts = listOf(
    PetalixHomeShortcut("sports", "Sports", "Your sports networks and live coverage."),
    PetalixHomeShortcut("news", "News", "Live news and current affairs."),
    PetalixHomeShortcut("entertainment", "Entertainment", "Browse your entertainment channels."),
    PetalixHomeShortcut("locals", "My Locals", "The local stations selected for your ZIP code.")
)
internal fun petalixHomeShortcut(folderId: String): PetalixHomeShortcut? =
    petalixHomeShortcuts.firstOrNull { folderId == "petalix-home-live-${it.slug}" }

/** Use a real native row so every tile participates in focus, scrolling and hero updates. */
internal fun petalixHomeLiveCollection() = Collection(
    id = PETALIX_LIVE_SHORTCUT_COLLECTION, title = "Live TV", pinToTop = true,
    folders = petalixHomeShortcuts.map { shortcut ->
        val artwork = "file:///android_asset/petalix/home-art/${shortcut.slug}.webp"
        CollectionFolder(id = "petalix-home-live-${shortcut.slug}", title = shortcut.title,
            coverImageUrl = artwork, heroBackdropUrl = artwork,
            tileShape = PosterShape.LANDSCAPE, focusGifEnabled = false)
    }
)
