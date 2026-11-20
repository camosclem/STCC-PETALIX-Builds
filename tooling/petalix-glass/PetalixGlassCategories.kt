// SPDX-License-Identifier: GPL-3.0-only
// Adapted from the category rail in IMD TV v0.5.2.
package com.nuvio.tv.livetv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.tv.R
import com.nuvio.tv.livetv.model.ChannelGroup
import com.nuvio.tv.livetv.model.LiveTvSettings
import com.nuvio.tv.petalix.PETALIX_ZIP_LOCALS

private const val GUIDE_OPTIONS_KEY = "action:options"
internal data class GuideEntry(
    val key: String, val title: String, val group: ChannelGroup? = null,
    val playlist: String? = null,
)

internal fun glassCategories(
    groups: List<ChannelGroup>, headings: Boolean, playlists: Map<String, String>, collapsed: Set<String>,
): List<GuideEntry> {
    val visible = groups.filter { it.id != ChannelGroup.SEARCH }
    fun entry(g: ChannelGroup) = GuideEntry(g.id, when (g.id) {
        ChannelGroup.FAVORITES -> "★  Favorites"
        ChannelGroup.RECENT -> "Recently Watched"
        PETALIX_ZIP_LOCALS -> "My Locals"
        else -> g.title
    }, group = g)
    val pinned = listOf(ChannelGroup.FAVORITES, ChannelGroup.RECENT, PETALIX_ZIP_LOCALS)
    val rest = visible.filter { it.id !in pinned }
    return buildList {
        for (id in pinned) visible.firstOrNull { it.id == id }?.let { add(entry(it)) }
        if (headings && rest.mapNotNull { it.sourceId }.distinct().size > 1) {
            rest.filter { it.sourceId == null }.forEach { add(entry(it)) }
            for ((source, children) in rest.filter { it.sourceId != null }.groupBy { it.sourceId!! }) {
                val prefix = if (source in collapsed) "▸  " else "▾  "
                add(GuideEntry("playlist:$source", prefix + (playlists[source] ?: children.first().title), playlist = source))
                if (source !in collapsed) children.forEach { add(entry(it)) }
            }
        } else rest.forEach { add(entry(it)) }
        add(GuideEntry(GUIDE_OPTIONS_KEY, "Guide options"))
    }
}

@Composable
internal fun PetalixGlassCategories(
    ui: LiveTvUiState, settings: LiveTvSettings, focus: FocusRequester,
    playlists: Map<String, String>, collapsed: Set<String>, togglePlaylist: (String) -> Unit,
    onPick: (ChannelGroup, Boolean) -> Unit, onOptions: () -> Unit, onClose: () -> Unit,
) {
    val entries = remember(ui.groups, playlists, collapsed, settings.groupPlaylistHeadings) {
        glassCategories(ui.groups, settings.groupPlaylistHeadings, playlists, collapsed)
    }
    var key by remember { mutableStateOf(ui.selectedGroupId) }
    val index = entries.indexOfFirst { it.key == key }.coerceAtLeast(0)
    val list = rememberLazyListState(index)
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { withFrameNanos { }; focus.requestFocus() }
    LaunchedEffect(index, entries.size) {
        val layout = list.layoutInfo
        if (layout.visibleItemsInfo.none { it.index == index && it.offset >= layout.viewportStartOffset &&
                it.offset + it.size <= layout.viewportEndOffset }) list.scrollToItem(index)
    }
    fun choose(e: GuideEntry, options: Boolean) {
        when {
            e.group != null -> onPick(e.group, options)
            e.playlist != null -> togglePlaylist(e.playlist)
            e.key == GUIDE_OPTIONS_KEY && !options -> onOptions()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().pointerInput(onClose) { detectTapGestures { onClose() } })
        val panelWidth = (maxWidth * .271f).coerceIn(minOf(maxWidth, 184.dp), minOf(maxWidth, 320.dp))
        Column(Modifier.fillMaxHeight().width(panelWidth)
            .shadow(20.dp).background(GlassPanel)
            .focusRequester(focus).onPreviewKeyEvent { e ->
                val down = e.type == KeyEventType.KeyDown
                val keyEvent = e.nativeKeyEvent
                when (e.key) {
                    Key.DirectionUp, Key.DirectionDown -> {
                        if (down) key = entries[(index + if (e.key == Key.DirectionUp) -1 else 1).coerceIn(entries.indices)].key
                        true
                    }
                    Key.DirectionLeft -> { if (!down) onClose(); true }
                    Key.DirectionRight -> { if (!down) choose(entries[index], false); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (down && keyEvent.repeatCount == 0) held = false
                        if (down && keyEvent.repeatCount > 0 && !held && keyEvent.eventTime - keyEvent.downTime >= LONG_PRESS_MS) {
                            held = true
                            choose(entries[index], true)
                        }
                        if (!down && !held && !keyEvent.isCanceled) choose(entries[index], keyEvent.eventTime - keyEvent.downTime >= LONG_PRESS_MS)
                        true
                    }
                    Key.Menu, Key.Info -> { if (down && keyEvent.repeatCount == 0) choose(entries[index], true); true }
                    else -> false
                }
            }.focusable().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
                LiveText("Categories", Modifier.weight(1f), GlassMuted, 13.sp, FontWeight.SemiBold)
                Image(painterResource(R.drawable.petalix_guide_wordmark), "Petalix", Modifier.size(76.dp, 28.dp))
            }
            LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxSize()) {
                itemsIndexed(entries, key = { _, e -> e.key }) { i, e ->
                    val selected = index == i
                    val ink = if (selected) GlassInk else Color.White
                    Column {
                        if (e.key == GUIDE_OPTIONS_KEY) {
                            Spacer(Modifier.height(7.dp))
                            Box(Modifier.fillMaxWidth().height(1.dp).background(GuideSilver.copy(alpha = .24f)))
                            Spacer(Modifier.height(5.dp))
                        }
                        Row(Modifier.fillMaxWidth().height(34.dp)
                            .background(if (selected) GlassFocus else Color.Transparent, RoundedCornerShape(5.dp))
                            .cableTarget(selected, { choose(e, false) }, { choose(e, true) })
                            .padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            LiveText((if (e.group?.locked == true) "🔒 " else "") + e.title, Modifier.weight(1f), ink, 14.sp)
                            if (settings.showGroupCounts && e.group != null)
                                LiveText(e.group.count.toString(), color = if (selected) ink else GlassMuted, size = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
