package com.nuvio.tv.petalix

import com.nuvio.tv.livetv.model.ChannelGroup
import com.nuvio.tv.livetv.model.CustomGroup
import com.nuvio.tv.livetv.model.LiveChannel
import com.nuvio.tv.livetv.model.LiveTvSettings
import com.nuvio.tv.livetv.model.LiveUserState
import com.nuvio.tv.livetv.ui.*
import org.junit.Assert.*
import org.junit.Test

class PetalixGlassGuideTest {
    private val locals = ChannelGroup(PETALIX_ZIP_LOCALS, "My Locals", 4)
    private val sports = ChannelGroup("provider:sports", "Sports", 12, sourceId = "playlist")
    private val favorites = ChannelGroup(ChannelGroup.FAVORITES, "Favorites", 8)
    private val recent = ChannelGroup(ChannelGroup.RECENT, "Recently watched", 3)

    @Test fun guideKeepsRealChannelGroupsAndTheSettingsFooter() {
        val saved = glassCategories(listOf(sports, locals, recent, favorites), false, emptyMap(), emptySet())
        assertEquals(listOf(favorites, recent), saved.take(2).map { it.group })
        assertEquals(listOf(sports, locals), saved.mapNotNull { it.group }.drop(2))
        assertSame(locals, saved.single { it.title == "My Locals" }.group)
        assertSame(sports, saved.single { it.title == "Sports" }.group)
        assertFalse(saved.any { it.title == "Sports Hub" })
        val fresh = glassCategories(emptyList(), false, emptyMap(), emptySet())
        assertEquals(listOf("Guide options"), fresh.map { it.title })
    }

    @Test fun favoriteLocalAffiliatesAppearInMyLocalsUntilExplicitZipChoicesAreSaved() {
        val abc = channel("abc", "WXYZ ABC", "USA Locals")
        val nbc = channel("nbc", "WCNC NBC", "Charlotte affiliates")
        val espn = channel("espn", "ESPN", "Sports")
        val channels = listOf(abc, nbc, espn)
        val user = LiveUserState(favorites = listOf("abc", "espn"))
        val guide = buildUi(channels, user, LiveTvSettings(), PETALIX_ZIP_LOCALS, "", true)
        assertEquals(listOf("abc"), guide.channels.map { it.key })
        assertEquals(1, guide.groups.single { it.id == PETALIX_ZIP_LOCALS }.count)
        val saved = user.copy(customGroups = listOf(CustomGroup(PETALIX_ZIP_LOCALS, "My Locals", listOf("nbc"))))
        val picked = buildUi(channels, saved, LiveTvSettings(), PETALIX_ZIP_LOCALS, "", true)
        assertEquals(listOf("nbc"), picked.channels.map { it.key })
    }

    @Test fun hidingEventGroupsLeavesSportsEligibleWhileIndividualHidesStillApply() {
        for (league in listOf("NFL", "NHL", "NBA")) {
            for (group in listOf(league, "$league Games", "$league Teams")) {
                val event = channel(league, "$league 01", group)
                val hiddenGroup = setOf(event.groupId)
                assertFalse(hiddenFromSportMatching(event, emptySet(), hiddenGroup))
                assertTrue(hiddenFromSportMatching(event, setOf(event.key), hiddenGroup))
            }
        }
        val ordinary = channel("espn", "ESPN", "Sports")
        assertTrue(hiddenFromSportMatching(ordinary, emptySet(), setOf(ordinary.groupId)))
    }

    private fun channel(key: String, name: String, group: String) =
        LiveChannel(key, "fixture", "Fixture", name, null, null, null, group, group, 1, "")

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
