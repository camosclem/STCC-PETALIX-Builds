package com.nuvio.tv.livetv.ui

import android.view.KeyEvent as AndroidKey
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.tv.livetv.model.ChannelGroup
import com.nuvio.tv.livetv.model.EpgProgram
import com.nuvio.tv.livetv.model.LiveChannel
import com.nuvio.tv.livetv.model.LiveTvSettings
import com.nuvio.tv.petalix.petalixCleanChannelName

private val StationSurface = Color(0xFF171A20)
private val AiringSurface = Color(0xFF20242B)
private val FutureSurface = Color(0xFF181C23)
private val SelectedSurface = Color(0xFF3C424B)
private val QuietText = Color(0xFFB5BBC4)
private val GuideRule = Color(0xFF30343B)

/** Playback and editing stay in the session. The Petalix guide owns its grid and key handling. */
internal class CableGuideActions(
    val moveRow: (Int) -> Unit,
    val seek: (Long) -> Unit,
    val tune: (Int, PetalixAiring, Boolean) -> Unit,
    val showCategories: () -> Unit,
    val closeCategories: () -> Unit,
    val pickCategory: (ChannelGroup, Boolean) -> Unit,
    val digit: (Int) -> Unit,
    val lastChannel: () -> Unit,
    val openSettings: () -> Unit
)

internal class CableChannelMarks(
    val favorites: Set<String>,
    val reminders: Map<String, Set<Long>>,
    val scheduled: Map<String, Set<Long>>,
    val recording: Map<String, Pair<Long, Long?>>
)

@Composable
private fun airtimes(channel: LiveChannel?, listings: List<EpgProgram>, now: Long, past: Int): PetalixAirtimes =
    remember(channel?.key, channel?.catchup?.days, listings, now / 3_600_000L, past) {
        PetalixAirtimes(if (channel == null) emptyList() else prepareGuidePrograms(channel, listings, now, past))
    }

/** The normal customer guide. Its surfaces fill the viewport. Only the text has a safe inset. */
@Composable
internal fun PetalixCableGuide(
    ui: LiveTvUiState,
    channels: List<LiveChannel>,
    settings: LiveTvSettings,
    listings: Map<String, List<EpgProgram>>,
    marks: CableChannelMarks,
    playingKey: String?,
    now: Long,
    start: Long,
    cursor: Long,
    row: Int,
    categoriesOpen: Boolean,
    playlistNames: Map<String, String>,
    collapsedPlaylists: Set<String>,
    togglePlaylist: (String) -> Unit,
    list: LazyListState,
    gridFocus: FocusRequester,
    categoryFocus: FocusRequester,
    headerFocus: FocusRequester,
    onGridFocus: (Boolean) -> Unit,
    controls: CableGuideActions,
    header: @Composable () -> Unit
) {
    val channel = channels.getOrNull(row)
    val line = airtimes(channel, channel?.let { listings[it.key] }.orEmpty(), now, settings.epgPastHours)
    val duration = guideWindowMs(settings)
    val selected = line.at(cursor)
    var gridFocused by remember { mutableStateOf(false) }
    var heldLeft by remember { mutableStateOf(false) }
    var heldOk by remember { mutableStateOf(false) }

    fun step(steps: Int) {
        val archiveDays = channel?.catchup?.days?.coerceIn(0, 7) ?: 0
        val archiveHours = minOf(archiveDays * 24, settings.epgPastHours.coerceAtLeast(0))
        val earliest = if (archiveHours == 0) now else now - archiveHours * 3_600_000L
        val latest = now + settings.epgFutureDays.coerceAtLeast(0) * 86_400_000L
        val target = line.step(cursor, steps, earliest, latest)
        controls.seek(if (line.at(target).contains(now)) now else target)
    }
    fun select(options: Boolean) {
        if (channel != null) controls.tune(row, selected, options)
        else if (!ui.hasSources && ui.guideReady) controls.openSettings()
        else controls.showCategories()
    }
    fun guideKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        val key = event.key
        val down = event.type == KeyEventType.KeyDown
        val up = event.type == KeyEventType.KeyUp
        val native = event.nativeKeyEvent
        if (key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter) {
            if (down && native.repeatCount == 0) heldOk = false
            if (down && native.repeatCount > 0 && !heldOk && native.eventTime - native.downTime >= LONG_PRESS_MS) {
                heldOk = true
                select(true)
            }
            if (up && !native.isCanceled && !heldOk) select(native.eventTime - native.downTime >= LONG_PRESS_MS)
            return true
        }
        if (key == Key.DirectionLeft) {
            if (down && native.repeatCount == 0) heldLeft = false
            if (down && native.repeatCount > 0 && native.eventTime - native.downTime >= LONG_PRESS_MS) {
                heldLeft = true
                step(-1)
            }
            if (up && !native.isCanceled && !heldLeft) {
                val long = native.eventTime - native.downTime >= LONG_PRESS_MS
                if (!long && (channel == null || selected.contains(now))) controls.showCategories() else step(-1)
            }
            return true
        }
        if (!down) return false
        when (key) {
            Key.DirectionUp -> if (row == 0) headerFocus.requestFocus() else controls.moveRow(-1)
            Key.DirectionDown -> controls.moveRow(1)
            Key.ChannelUp, Key.PageUp -> controls.moveRow(-8)
            Key.ChannelDown, Key.PageDown -> controls.moveRow(8)
            Key.DirectionRight -> step(1)
            Key.MediaFastForward -> step(4)
            Key.MediaRewind -> step(-4)
            Key.Menu, Key.Info -> if (native.repeatCount == 0) select(true)
            else -> when (val code = native.keyCode) {
                in AndroidKey.KEYCODE_0..AndroidKey.KEYCODE_9 -> controls.digit(code - AndroidKey.KEYCODE_0)
                AndroidKey.KEYCODE_LAST_CHANNEL -> controls.lastChannel()
                else -> return false
            }
        }
        return true
    }

    Column(Modifier.fillMaxSize().background(GuideInk)) {
        header()
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val rail = (maxWidth * 0.205f).coerceIn(125.dp, 248.dp)
            val timelineWidth = (maxWidth - rail).coerceAtLeast(1.dp)
            val scale = guideScale(settings)
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(28.dp).background(StationSurface),
                    verticalAlignment = Alignment.CenterVertically) {
                    LiveText(guideDayLabel(start, now), Modifier.width(rail).padding(start = 16.dp),
                        GuideSilver, 12.sp, FontWeight.SemiBold)
                    Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                        for (tick in 0 until (duration / PETALIX_HALF_HOUR).toInt()) {
                            val stamp = start + tick * PETALIX_HALF_HOUR
                            val offset = timelineWidth * ((stamp - start).toFloat() / duration)
                            LiveText(formatClock(stamp, settings.use24HourClock),
                                Modifier.align(Alignment.CenterStart).offset(x = offset).padding(start = 7.dp),
                                QuietText, 12.sp)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f).focusRequester(com.nuvio.tv.LocalContentFocusRequester.current).focusRequester(gridFocus)
                    .onFocusChanged { gridFocused = it.hasFocus; onGridFocus(it.hasFocus) }
                    .onPreviewKeyEvent(::guideKey).focusable()) {
                    if (channels.isEmpty()) {
                        val text = when {
                            !ui.guideReady -> "Loading your channels…"
                            !ui.hasSources -> "Add a playlist in Live TV settings"
                            ui.selectedGroupId == ChannelGroup.FAVORITES -> "Your favorite channels appear here"
                            ui.selectedGroupId == ChannelGroup.RECENT -> "Channels you watch appear here"
                            else -> "No channels in this category"
                        }
                        LiveText(text, Modifier.align(Alignment.Center), QuietText)
                    } else {
                        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(channels, key = { _, ch -> ch.key }) { index, ch ->
                                val schedule = airtimes(ch, listings[ch.key].orEmpty(), now, settings.epgPastHours)
                                CableScheduleRow(ch, schedule, settings, marks, now, start, duration,
                                    rail, timelineWidth, scale, playingKey == ch.key,
                                    selectedAt = cursor.takeIf { row == index && gridFocused && !categoriesOpen },
                                    open = { entry, options -> controls.tune(index, entry, options) })
                            }
                        }
                        if (settings.showNowLine && now in start until start + duration) {
                            Box(Modifier.offset(x = rail + timelineWidth * ((now - start).toFloat() / duration))
                                .fillMaxHeight().width(1.dp).background(GuideGold.copy(alpha = 0.64f)))
                        }
                    }
                }
            }
            if (categoriesOpen) CableCategories(ui.groups, ui.selectedGroupId, settings, categoryFocus,
                playlistNames, collapsedPlaylists, togglePlaylist, controls)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.cableTarget(chosen: Boolean, click: () -> Unit, options: () -> Unit) =
    focusProperties { canFocus = false }.semantics { selected = chosen }
        .combinedClickable(onClick = click, onLongClick = options)

@Composable
private fun CableScheduleRow(
    channel: LiveChannel, line: PetalixAirtimes, settings: LiveTvSettings, marks: CableChannelMarks,
    now: Long, start: Long, duration: Long, rail: Dp, timelineWidth: Dp, scale: Float,
    playing: Boolean, selectedAt: Long?, open: (PetalixAiring, Boolean) -> Unit
) {
    val spans = remember(line, start, duration) { line.visible(start, start + duration) }
    val height = guideRowHeight(settings)
    val name = remember(channel.name) { petalixCleanChannelName(channel.name).ifBlank { channel.name } }
    Row(Modifier.fillMaxWidth().height(height).padding(bottom = 1.dp)) {
        val active = selectedAt != null
        Row(Modifier.width(rail).fillMaxHeight().padding(end = 2.dp)
            .background(if (active) GuideSilver else StationSurface)
            .cableTarget(active, { open(line.at(now), false) }, { open(line.at(now), true) }),
            verticalAlignment = Alignment.CenterVertically) {
            val ink = if (active) GuideInk else GuideSilver
            Box(Modifier.fillMaxHeight().width(3.dp).background(
                if (playing && settings.markPlayingChannel) GuideGold else Color.Transparent))
            Spacer(Modifier.width(10.dp))
            if (settings.showChannelNumbers) LiveText(channel.number.toString(), Modifier.padding(end = 6.dp),
                if (active) GuideInk else QuietText, (11 * scale).sp)
            if (settings.showChannelLogos && !channel.logo.isNullOrBlank()) {
                ChannelLogo(channel.logo, (height - 10.dp).coerceIn(18.dp, 39.dp))
                Spacer(Modifier.width(7.dp))
            }
            if (settings.showChannelNames || channel.logo.isNullOrBlank() || !settings.showChannelLogos) {
                LiveText(name, Modifier.weight(1f), ink, (14 * scale).sp,
                    if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = if (settings.twoLineChannelNames) 2 else 1)
            }
            if (channel.key in marks.favorites) LiveText("★", Modifier.padding(horizontal = 3.dp),
                if (active) GuideInk else GuideGold, 11.sp)
            Spacer(Modifier.width(5.dp))
        }
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            for (span in spans) {
                val left = maxOf(span.start, start)
                val right = minOf(span.end, start + duration)
                val x = timelineWidth * ((left - start).toFloat() / duration)
                val w = timelineWidth * ((right - left).toFloat() / duration)
                val selected = selectedAt != null && span.contains(selectedAt)
                val live = span.contains(now)
                val program = span.program
                val marked = program?.let { titleMarks(it.title) }
                val recording = program != null && (program.startMs in marks.scheduled[channel.key].orEmpty() ||
                    marks.recording[channel.key]?.let { program.stopMs > it.first && program.startMs < (it.second ?: Long.MAX_VALUE) } == true)
                val label = when {
                    recording -> "● "
                    program != null && program.startMs in marks.reminders[channel.key].orEmpty() -> "• "
                    else -> ""
                }
                val fill = when {
                    selected && settings.solidHighlight -> GuideGold
                    selected -> SelectedSurface
                    live -> AiringSurface
                    else -> FutureSurface
                }
                val ink = if (selected && settings.solidHighlight) GuideInk else GuideSilver
                Box(Modifier.offset(x = x).width(w).fillMaxHeight().padding(end = 1.dp)
                    .background(fill).then(if (selected) Modifier.border(1.dp, GuideGold) else Modifier)
                    .clipToBounds().cableTarget(selected, { open(span, false) }, { open(span, true) })) {
                    if (w > 22.dp) Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 8.dp)) {
                        val title = marked?.title?.takeUnless { it == NO_GUIDE_TITLE } ?: "No listing"
                        val episode = program?.episode?.takeIf { settings.showEpisodeNumbers && w > 145.dp }
                        LiveText(label + title + (episode?.let { "  ·  $it" } ?: ""),
                            color = if (program == null && !selected) QuietText else ink,
                            size = (14 * scale).sp, weight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            marquee = selected)
                        if (settings.showProgramTimes && program != null && w > 65.dp)
                            LiveText(formatRange(program.startMs, program.stopMs, settings.use24HourClock),
                                color = if (selected) ink else QuietText, size = (11 * scale).sp)
                    }
                }
            }
        }
    }
}

private data class CableCategory(val key: String, val title: String, val group: ChannelGroup?)

@Composable
private fun CableCategories(
    groups: List<ChannelGroup>, selectedId: String, settings: LiveTvSettings, focus: FocusRequester,
    playlists: Map<String, String>, collapsed: Set<String>, togglePlaylist: (String) -> Unit,
    controls: CableGuideActions
) {
    val entries = remember(groups, playlists, collapsed, settings.groupPlaylistHeadings) {
        val categories = groups.filter { it.id != ChannelGroup.SEARCH }
        fun row(g: ChannelGroup) = CableCategory(g.id, g.title, g)
        if (!settings.groupPlaylistHeadings || categories.mapNotNull { it.sourceId }.distinct().size <= 1)
            categories.map(::row)
        else buildList {
            categories.filter { it.sourceId == null }.forEach { add(row(it)) }
            categories.filter { it.sourceId != null }.groupBy { it.sourceId!! }.forEach { (source, children) ->
                add(CableCategory("playlist:$source", playlists[source] ?: children.first().title, null))
                if (source !in collapsed) children.forEach { add(row(it)) }
            }
        }
    }
    val list = rememberLazyListState()
    var key by remember { mutableStateOf(selectedId) }
    val index = entries.indexOfFirst { it.key == key }.coerceAtLeast(0)
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(index, entries.size) {
        if (entries.isEmpty()) return@LaunchedEffect
        val layout = list.layoutInfo
        if (layout.visibleItemsInfo.none { it.index == index && it.offset >= layout.viewportStartOffset &&
                it.offset + it.size <= layout.viewportEndOffset }) list.scrollToItem(index)
    }
    fun choose(item: CableCategory, options: Boolean) {
        val group = item.group
        if (group == null) togglePlaylist(item.key.removePrefix("playlist:"))
        else controls.pickCategory(group, options)
    }
    Column(Modifier.width(223.dp).fillMaxHeight().background(StationSurface).border(1.dp, GuideRule)
        .focusRequester(focus).onPreviewKeyEvent { e ->
            val native = e.nativeKeyEvent
            val down = e.type == KeyEventType.KeyDown
            when (e.key) {
                Key.DirectionUp, Key.DirectionDown -> {
                    if (down && entries.isNotEmpty()) key = entries[(index + if (e.key == Key.DirectionUp) -1 else 1).coerceIn(entries.indices)].key
                    true
                }
                Key.DirectionRight -> { if (e.type == KeyEventType.KeyUp) controls.closeCategories(); true }
                Key.DirectionLeft -> true
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                    if (down && native.repeatCount == 0) held = false
                    if (down && native.repeatCount > 0 && !held && native.eventTime - native.downTime >= LONG_PRESS_MS) {
                        held = true
                        entries.getOrNull(index)?.let { choose(it, true) }
                    }
                    if (!down && !held && !native.isCanceled) entries.getOrNull(index)?.let {
                        choose(it, native.eventTime - native.downTime >= LONG_PRESS_MS)
                    }
                    true
                }
                Key.Menu -> { if (down && native.repeatCount == 0) entries.getOrNull(index)?.let { choose(it, true) }; true }
                else -> false
            }
        }.focusable()) {
        LiveText("Categories", Modifier.fillMaxWidth().padding(start = 16.dp, top = 7.dp, bottom = 7.dp),
            GuideGold, 15.sp, FontWeight.Bold)
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(entries, key = { _, e -> e.key }) { i, entry ->
                val focused = i == index
                Row(Modifier.fillMaxWidth().height(35.dp)
                    .background(if (focused) GuideGold else StationSurface)
                    .cableTarget(focused, { choose(entry, false) }, { choose(entry, true) })
                    .padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    val ink = if (focused) GuideInk else GuideSilver
                    val prefix = when {
                        entry.group == null -> if (entry.key.removePrefix("playlist:") in collapsed) "▸  " else "▾  "
                        entry.group.locked -> "🔒 "
                        else -> ""
                    }
                    LiveText(prefix + entry.title, Modifier.weight(1f), ink, 14.sp,
                        weight = if (entry.key == selectedId) FontWeight.Bold else FontWeight.Normal)
                    if (settings.showGroupCounts && entry.group != null)
                        LiveText(entry.group.count.toString(), color = ink, size = 12.sp)
                }
            }
        }
    }
}
