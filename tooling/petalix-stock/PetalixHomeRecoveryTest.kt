package com.nuvio.tv.petalix

import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.CollectionFolder
import com.nuvio.tv.livetv.ondemand.OnDemandStatus
import com.nuvio.tv.ui.screens.home.buildCollectionFolderItem
import org.junit.Assert.*
import org.junit.Test

class PetalixHomeRecoveryTest {
    @Test fun liveShortcutsUseDistinctNativeFocusAndBackdropMetadata() {
        val collection = petalixHomeLiveCollection()
        val cards = collection.folders.map { buildCollectionFolderItem(collection, it) }
        assertEquals(listOf("Sports", "News", "Entertainment", "My Locals"), cards.map { it.heroPreview.title })
        assertEquals(4, cards.map { it.key }.toSet().size)
        assertEquals(4, cards.map { it.heroPreview.backdrop }.toSet().size)
        assertTrue(cards.all { !it.heroPreview.description.isNullOrBlank() })
        assertTrue(collection.folders.all { petalixHomeShortcut(it.id)?.title == it.title })
        assertNull(petalixHomeShortcut("unrelated-folder"))
    }

    @Test fun hiddenServiceTileLabelDoesNotBlankItsHeroTitle() {
        val folder = CollectionFolder(
            id = "prime", title = "Prime Video", hideTitle = true,
            coverImageUrl = "file:///prime-tile.webp", heroBackdropUrl = "file:///prime-hero.webp"
        )
        val card = buildCollectionFolderItem(Collection(id = PETALIX_SERVICES_COLLECTION, title = "Streaming Services"), folder)
        assertEquals("", card.title)
        assertEquals("Prime Video", card.heroPreview.title)
        assertEquals("file:///prime-hero.webp", card.heroPreview.backdrop)
        val custom = buildCollectionFolderItem(Collection(id = "owner-custom", title = "Custom"), folder)
        assertEquals("", custom.heroPreview.title)
    }

    @Test fun finishingVodImportRetainsTheActualProviderFailure() {
        val failed = OnDemandStatus(true, "Provider returned HTTP 503")
        assertEquals(OnDemandStatus(false, failed.message), finishPetalixVodLoad(failed))
        assertEquals(OnDemandStatus(false), finishPetalixVodLoad(OnDemandStatus(true)))
    }

    @Test fun av1RecoveryOnlyHandlesSoftwareDecoderInitializationFailure() {
        assertTrue(petalixSoftwareAv1Recovery(true, "Libgav1VideoRenderer error [ERROR_CODE_DECODER_INIT_FAILED]"))
        assertFalse(petalixSoftwareAv1Recovery(false, "Libgav1VideoRenderer network timeout"))
        assertFalse(petalixSoftwareAv1Recovery(true, "MediaCodecVideoRenderer AVC initialization failed"))
        assertFalse(petalixSoftwareAv1Recovery(true, "Audio decoder initialization failed"))
    }
    @Test fun sportsRematchesRenamedEventsWithoutChangingTheirFavoriteIdentity() {
        val original = com.nuvio.tv.livetv.model.LiveChannel(
            key = "petalix-managed|stream:123", sourceId = "petalix-managed", sourceName = "Petalix",
            name = "NFL Sunday Ticket 01", tvgId = null, tvgName = null, logo = null,
            groupId = "sports", group = "NFL Sunday Ticket", number = 1,
            url = "https://provider.example/live/123.ts"
        )
        val today = original.copy(name = "NFL: Chiefs vs Falcons @ Nov 15 13:00")
        assertEquals(original.key, today.key)
        assertNotEquals(petalixSportsChannelSignature(listOf(original)), petalixSportsChannelSignature(listOf(today)))
        assertNotEquals(petalixSportsChannelSignature(listOf(today)), petalixSportsChannelSignature(listOf(today.copy(group = "NFL Games"))))
        assertEquals(petalixSportsChannelSignature(listOf(today)), petalixSportsChannelSignature(listOf(today.copy(logo = "https://art.example/new.png"))))
    }

}
