package com.nuvio.tv.petalix

import com.nuvio.tv.livetv.model.ChannelGroup
import com.nuvio.tv.livetv.ui.*
import org.junit.Assert.*
import org.junit.Test

class PetalixGlassGuideTest {
    private val locals = ChannelGroup(PETALIX_ZIP_LOCALS, "My Locals", 4)
    private val sports = ChannelGroup("provider:sports", "Sports", 12, sourceId = "playlist")
    private val favorites = ChannelGroup(ChannelGroup.FAVORITES, "Favorites", 8)
    private val recent = ChannelGroup(ChannelGroup.RECENT, "Recently watched", 3)

    @Test fun guideKeepsTheSportsDestinationDistinctFromPlaylistNetworksAndZipLocals() {
        val saved = glassCategories(listOf(sports, locals, recent, favorites), false, emptyMap(), emptySet())
        assertEquals(listOf(favorites, recent), saved.take(2).map { it.group })
        assertSame(locals, saved.single { it.title == "My Locals" }.group)
        assertSame(sports, saved.single { it.title == "Sports" }.group)
        assertEquals(GuideAction.SPORTS, saved.single { it.title == "Sports Hub" }.action)
        val fresh = glassCategories(emptyList(), false, emptyMap(), emptySet())
        assertEquals(GuideAction.SET_UP_LOCALS, fresh.single { it.title == "My Locals" }.action)
    }

    @Test fun aClosedPlaylistRemainsClosedAndLockedGroupsRetainTheirIdentity() {
        val other = sports.copy(id = "other", sourceId = "other", locked = true)
        val visible = glassCategories(listOf(sports, other), true, mapOf("playlist" to "Service"), setOf("playlist"))
        assertFalse(visible.any { it.group?.id == sports.id })
        assertSame(other, visible.single { it.group?.id == other.id }.group)
        assertEquals(visible.size, visible.map { it.key }.distinct().size)
    }

    @Test fun okPreviewsThenOpensFullscreenUsingTheSameChannelKey() {
        assertEquals(OkAction.PREVIEW, okAction(false, null, "abc"))
        assertEquals(OkAction.PREVIEW, okAction(false, "nbc", "abc"))
        assertEquals(OkAction.FULL_SCREEN, okAction(false, "abc", "abc"))
        assertEquals(OkAction.FULL_SCREEN, okAction(true, null, "abc"))
        for (height in listOf(239, 331, 591)) {
            val row = guideRowPx(height, 6, 3)
            assertTrue(6 * row + 5 * 3 <= height)
            assertTrue(height - (6 * row + 5 * 3) < 6)
        }
    }
}
