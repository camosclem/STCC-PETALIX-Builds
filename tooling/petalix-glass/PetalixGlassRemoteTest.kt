package com.nuvio.tv.petalix

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.livetv.model.*
import com.nuvio.tv.livetv.ui.*
import com.nuvio.tv.ui.theme.NuvioTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PetalixGlassRemoteTest {
    @get:Rule val compose = createComposeRule()
    private var lastAction: GuideAction? = null
    private var tuned: String? = null
    private val names = listOf("NBC News NOW", "ABC", "CBS", "ESPN", "FOX", "NBC", "PBS")
    private val shows = listOf("Hallie Jackson NOW", "Local News", "Evening News", "SportsCenter", "The Game", "Nightly News", "Nature")
    private val start = 12 * 3_600_000L
    private val now = start + 12 * 60_000L

    @Test fun guideOpensWithoutTabsAndTheRemoteReachesCategoriesAndSports() {
        val channels = names.mapIndexed { i, name ->
            LiveChannel(key = "ch$i", sourceId = "test", sourceName = "Test", name = name,
                tvgId = null, tvgName = null, logo = null, groupId = "news", group = "News", number = i + 1, url = "")
        }
        val epg = channels.mapIndexed { i, ch -> ch.key to listOf(
            EpgProgram(start, start + 3_600_000, shows[i], if (i == 0) "Breaking news and developing stories from around the country." else null),
            EpgProgram(start + 3_600_000, start + 7_200_000, listOf("Top Story", "World News", "CBS Reports", "Live Coverage", "Postgame", "Tonight", "NOVA")[i])
        ) }.toMap()
        val groups = listOf(ChannelGroup(ChannelGroup.ALL, "All channels", 7),
            ChannelGroup(ChannelGroup.FAVORITES, "Favorites", 3), ChannelGroup(ChannelGroup.RECENT, "Recently watched", 5),
            ChannelGroup(PETALIX_ZIP_LOCALS, "My Locals", 4), ChannelGroup("news", "News", 3),
            ChannelGroup("sports", "Sports", 1))
        val settings = LiveTvSettings(showChannelLogos = false)
        compose.setContent {
            val gridFocus = remember { FocusRequester() }
            val railFocus = remember { FocusRequester() }
            var rail by remember { mutableStateOf(false) }
            var row by remember { mutableIntStateOf(0) }
            var cursor by remember { mutableLongStateOf(now) }
            var group by remember { mutableStateOf(groups.first()) }
            val ui = LiveTvUiState(guideReady = true, groups = groups, selectedGroupId = group.id, channels = channels)
            NuvioTheme {
                CompositionLocalProvider(LocalContentFocusRequester provides remember { FocusRequester() }) {
                    Box(Modifier.fillMaxSize().glassGuideBackground()) {
                        PetalixGlassGuide(ui, channels, settings, epg,
                            CableChannelMarks(emptySet(), emptyMap(), emptyMap(), emptyMap()), null, now, start,
                            cursor, row, rail, rememberLazyListState(), gridFocus, {}, CableGuideActions(
                                moveRow = { row = (row + it).coerceIn(channels.indices) }, seek = { cursor = it },
                                tune = { index, _, _ -> tuned = channels[index].key }, showCategories = { rail = true },
                                digit = {}, lastChannel = {}, openSettings = {}
                            )) { modifier ->
                            val ch = channels[row]
                            PetalixGlassHero(ch, epg.getValue(ch.key).first(), "file:///android_asset/petalix/home-art/live/news.jpg",
                                group.title, settings, now, false, {}, { rail = true }, modifier)
                        }
                        if (rail) PetalixGlassCategories(ui, settings, railFocus, emptyMap(), emptySet(), {},
                            { g, _ -> group = g; rail = false }, { lastAction = it; rail = false }, { rail = false }, null)
                    }
                    LaunchedEffect(rail) { if (!rail) { withFrameNanos { }; gridFocus.requestFocus() } }
                }
            }
        }
        compose.onNodeWithText("Movies").assertDoesNotExist()
        compose.onNodeWithText("Series").assertDoesNotExist()
        compose.onNodeWithText("Live TV", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Breaking news and developing stories from around the country.").assertIsDisplayed()
        capture("Petalix-Guide.png")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithText("Categories", substring = false).assertIsDisplayed()
        compose.onNodeWithText("Recently Watched").assertIsDisplayed()
        capture("Petalix-Categories.png")
        compose.onNodeWithText("My Locals").performClick()
        compose.onNodeWithText("◀  My Locals").assertIsDisplayed()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(channels[1].key, tuned) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithText("Sports Hub").performClick()
        compose.runOnIdle { assertEquals(GuideAction.SPORTS, lastAction) }
    }

    private fun capture(name: String) {
        val dir = System.getenv("PETALIX_GUIDE_CAPTURES") ?: return
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
