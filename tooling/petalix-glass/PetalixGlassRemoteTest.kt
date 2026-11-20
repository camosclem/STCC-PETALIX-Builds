package com.nuvio.tv.petalix

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.livetv.model.*
import com.nuvio.tv.livetv.ui.*
import com.nuvio.tv.ui.navigation.Screen
import com.nuvio.tv.ui.navigation.exitPetalixSports
import com.nuvio.tv.ui.theme.NuvioTheme
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PetalixGlassRemoteTest {
    @get:Rule val compose = createComposeRule()
    private var tuned: String? = null
    private val names = listOf("NBC News NOW", "ABC", "CBS", "ESPN", "FOX", "NBC", "PBS")
    private val shows = listOf("Hallie Jackson NOW", "Local News", "Evening News", "SportsCenter", "The Game", "Nightly News", "Nature")
    private val start = 12 * 3_600_000L
    private val now = start + 12 * 60_000L

    @Test fun guideOpensWithoutTabsAndTheRemoteReachesCategoriesAndLocals() {
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
                            { g, _ -> group = g; rail = false }, { rail = false }, { rail = false })
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
        compose.onNodeWithText("Sports Hub").assertDoesNotExist()
        press(Key.DirectionUp)
        capture("Petalix-Categories.png")
        press(Key.DirectionCenter)
        compose.onNodeWithText("◀  My Locals").assertIsDisplayed()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.runOnIdle { assertEquals(channels[1].key, tuned) }
    }

    @Test fun netflixDetailsShowOneTitleAndOneSynopsis() {
        val service = checkNotNull(com.nuvio.tv.petalix.ui2.PetalixServices.find("netflix"))
        val title = "Never Surrender"
        val synopsis = "The crew makes one last attempt to reach safety."
        val meta = com.nuvio.tv.domain.model.Meta(
            id = "sample", type = com.nuvio.tv.domain.model.ContentType.MOVIE,
            name = title, poster = null, posterShape = com.nuvio.tv.domain.model.PosterShape.POSTER,
            background = "file:///android_asset/petalix/home-art/live/news.jpg", logo = null,
            description = synopsis, releaseInfo = "2026", imdbRating = 7.6f,
            genres = listOf("Drama"), runtime = "109 min", director = emptyList(),
            cast = listOf("Taylor James"), videos = emptyList(), country = null, awards = null,
            language = null, links = emptyList(),
        )
        compose.setContent {
            NuvioTheme {
                com.nuvio.tv.petalix.ui2.PetalixServiceDetails(service,
                    com.nuvio.tv.ui.screens.detail.MetaDetailsUiState(meta = meta, tmdbRating = 8.0f),
                    playLabel = "Play", canPlay = true, onPlay = {}, onEpisode = {}, onSeason = {},
                    onLibrary = {}, onBack = {}, returnSeason = null, returnEpisode = null, onReturnConsumed = {})
            }
        }
        compose.onAllNodesWithText(title).assertCountEquals(1)
        compose.onAllNodesWithText(synopsis).assertCountEquals(1)
        compose.onNodeWithText("About Never Surrender").assertDoesNotExist()
        compose.onNodeWithText("Play").assertIsDisplayed()
        capture("Petalix-Service-Details.png")
    }

    @Test fun backFromSportsReturnsToHome() {
        sportsNavigation(startRoute = Screen.Home.route)
        compose.onNodeWithText("Open Sports Hub").performClick()
        compose.onNodeWithText("Sports Hub open").assertIsDisplayed()
        back()
        compose.onNodeWithText("Open Sports Hub").assertIsDisplayed()
    }

    @Test fun backFromSportsAtStartupOpensHome() {
        sportsNavigation(startRoute = Screen.Sports.route)
        compose.onNodeWithText("Sports Hub open").assertIsDisplayed()
        back()
        compose.onNodeWithText("Petalix Home").assertIsDisplayed()
    }

    @Test fun guideSettingsChangeTheSavedOptionsAndDismissWithBack() {
        var settings by mutableStateOf(LiveTvSettings(showProgramDetails = true))
        compose.setContent {
            var showing by remember { mutableStateOf(true) }
            NuvioTheme {
                Box(Modifier.fillMaxSize().glassGuideBackground()) {
                    if (showing) GuideGearPanel(settings, { settings = it(settings) }, {}, { showing = false })
                }
            }
        }
        compose.onNodeWithText("Guide settings").assertIsDisplayed()
        capture("Petalix-Guide-Settings.png")
        compose.onNodeWithText("Show info panel").performClick()
        compose.runOnIdle { assertFalse(settings.showProgramDetails) }
        back()
        compose.onNodeWithText("Guide settings").assertDoesNotExist()
    }

    @Test fun playBarShowsChannelAndNextProgram() {
        val channel = LiveChannel(key = "sports", sourceId = "test", sourceName = "Test", name = "Petalix Sports FHD",
            tvgId = null, tvgName = null, logo = null, groupId = "sports", group = "Sports", number = 12, url = "")
        compose.setContent {
            NuvioTheme {
                Box(Modifier.fillMaxSize().glassGuideBackground()) {
                    LiveTvPlayerPanel(channel = channel, mode = PanelMode.LIVE, full = true, title = "Game Night",
                        meta = "", description = "Today’s live broadcast", poster = null, showPosters = false,
                        timeline = PanelTimeline(start, start + 3_600_000, now, leftLabel = "12:00", rightLabel = "1:00",
                            status = "LIVE", statusLive = true),
                        paused = false, controls = panelControls(PanelMode.LIVE, false, false, sports = true),
                        focused = PanelControl.MORE, captionsOn = false, audioLabel = "Default", sizeLabel = "Fit",
                        sleepLabel = null, clock = "12:12 PM", resolution = null, showNumber = true,
                        nextTitle = "Postgame", modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
                }
            }
        }
        compose.onNodeWithText("Game Night").assertIsDisplayed()
        compose.onNodeWithText("Postgame").assertIsDisplayed()
        compose.onNodeWithText("Petalix Sports").assertIsDisplayed()
        capture("Petalix-Play-Bar.png")
    }

    private fun sportsNavigation(startRoute: String) {
        compose.setContent {
            val nav = rememberNavController()
            NavHost(nav, startDestination = startRoute) {
                composable(Screen.Home.route) {
                    Text("Petalix Home")
                    Button(onClick = { nav.navigate(Screen.Sports.route) }) { Text("Open Sports Hub") }
                }
                composable(Screen.Sports.route) {
                    BackHandler { nav.exitPetalixSports() }
                    Text("Sports Hub open")
                }
            }
        }
    }

    private fun back() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        check(bitmap.width > bitmap.height) { "Expected landscape, got ${bitmap.width} x ${bitmap.height}" }
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PetalixVerification")
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        checkNotNull(resolver.openOutputStream(uri)).use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
