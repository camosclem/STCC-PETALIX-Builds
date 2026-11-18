package com.nuvio.tv.livetv.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.livetv.model.ChannelGroup
import com.nuvio.tv.livetv.model.EpgProgram
import com.nuvio.tv.livetv.model.LiveChannel
import com.nuvio.tv.livetv.model.LiveTvSettings

internal val LocalPetalixGuide = staticCompositionLocalOf { false }
internal val GuideInk = Color(0xFF091124)
internal val GuidePanel = Color(0xFF151F34)
internal val GuideGold = Color(0xFFEAC680)
internal val GuideSilver = Color(0xFFE7ECF5)
private val guideMuted = Color(0xFFB9C4D7)

internal fun Modifier.petalixPreviewFade(base: Color): Modifier = drawWithCache {
    val clear = base.copy(alpha = 0f)
    val side = Brush.horizontalGradient(listOf(base, clear), endX = size.width * 0.25f)
    val bottom = Brush.verticalGradient(listOf(clear, base), startY = size.height * 0.72f)
    onDrawWithContent {
        drawContent()
        drawRect(side)
        drawRect(bottom)
    }
}

@Composable
private fun GuideCommand(
    label: String,
    onClick: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier.onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown) {
                    onDown()
                    true
                } else false
            }
            .background(if (focused) GuideSilver else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        LiveText(label, size = 13.sp, color = if (focused) GuideInk else GuideSilver, weight = FontWeight.Medium)
    }
}

@Composable
internal fun PetalixCategoryPicker(
    groups: List<ChannelGroup>,
    selected: String,
    onDismiss: () -> Unit,
    onPick: (ChannelGroup) -> Unit
) {
    if (groups.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val first = remember { FocusRequester() }
    val initial = groups.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(350.dp).background(GuidePanel, RoundedCornerShape(14.dp)).padding(16.dp)) {
            LiveText("Categories", color = Color.White, size = 21.sp, weight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val state = rememberLazyListState(initialFirstVisibleItemIndex = initial)
            LazyColumn(Modifier.heightIn(max = 300.dp), state = state) {
                items(groups, key = { it.id }) { group ->
                    val target = group.id == groups[initial].id
                    LiveFocusRow(modifier = Modifier.fillMaxWidth().then(if (target) Modifier.focusRequester(first) else Modifier),
                        selected = group.id == selected, onClick = { onPick(group) }) { focused ->
                        LiveText(group.title, Modifier.weight(1f), focusedTextColor(focused), 15.sp)
                        if (group.locked) LiveText("PIN", color = focusedTextColor(focused), size = 11.sp)
                        if (group.id == selected) LiveText("✓", color = focusedTextColor(focused), size = 14.sp)
                    }
                }
            }
            LaunchedEffect(groups[initial].id) {
                state.scrollToItem(initial)
                withFrameNanos { }
                runCatching { first.requestFocus() }
            }
        }
    }
}

private fun categoryPhoto(title: String?): String? {
    val file = when (title?.trim()?.lowercase()) {
        "sports", "sports networks" -> "sports"
        "news", "local news" -> "news"
        "entertainment" -> "entertainment"
        "my locals", "locals" -> "locals"
        "24/7", "24/7 channels", "24/7 tv" -> "around-clock"
        "westerns" -> "westerns"
        else -> return null
    }
    return "file:///android_asset/petalix/home-art/live/$file.jpg"
}

@Composable
private fun HeroBackdrop(image: String?, fallback: String?, base: Color, playing: Boolean) {
    var failed by remember(image) { mutableStateOf(false) }
    val model = if (failed) fallback else image ?: fallback
    if (model == null) return
    AsyncImage(model, null, Modifier.fillMaxSize().drawWithCache {
        val clear = base.copy(alpha = 0f)
        val veil = if (playing) Brush.horizontalGradient(
            0f to base.copy(alpha = 0.80f), 0.30f to base.copy(alpha = 0.55f), 0.51f to base, 1f to base)
            else Brush.horizontalGradient(0f to base.copy(alpha = 0.90f), 0.46f to base.copy(alpha = 0.67f), 0.82f to clear)
        val floor = Brush.verticalGradient(0f to clear, 0.64f to clear, 1f to base)
        onDrawWithContent { drawContent(); drawRect(veil); drawRect(floor) }
    }, alignment = Alignment.Center, contentScale = ContentScale.Crop,
        onError = { if (model != fallback) failed = true })
}

@Composable
internal fun PetalixGuideHeader(
    channel: LiveChannel?,
    program: EpgProgram?,
    start: Long?,
    stop: Long?,
    poster: String?,
    playlist: String?,
    settings: LiveTvSettings,
    now: Long,
    group: String,
    playing: Boolean,
    onBounds: (androidx.compose.ui.geometry.Rect?) -> Unit,
    onCategories: () -> Unit,
    onCycle: (Int) -> Unit,
    onMenu: () -> Unit,
    onHeaderFocus: (Boolean) -> Unit,
    onDown: () -> Unit,
    categoryFocus: FocusRequester,
    menuFocus: FocusRequester
) {
    val compact = settings.smallHeader
    val hidden = remember(settings.guideInfoHidden) { BarPart.parse(settings.guideInfoHidden) }
    val base = if (settings.guideBackdrop == "auto") GuideInk else backdropBase(resolveBackdrop(settings.guideBackdrop))
    Box(Modifier.fillMaxWidth().onFocusChanged { onHeaderFocus(it.hasFocus) }.focusGroup()
        .padding(horizontal = 28.dp, vertical = 5.dp)) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.petalix_guide_wordmark), "Petalix", Modifier.size(86.dp, 27.dp))
            Spacer(Modifier.width(9.dp))
            LiveText("Live TV", color = GuideGold, size = 14.sp, weight = FontWeight.Bold)
        }
        var focused by remember { mutableStateOf(false) }
        val ink = if (focused) GuideInk else GuideSilver
        Row(Modifier.align(Alignment.Center).widthIn(max = 240.dp).focusRequester(categoryFocus)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        if (event.nativeKeyEvent.repeatCount == 0) onCycle(if (event.key == Key.DirectionLeft) -1 else 1)
                        true
                    }
                    Key.DirectionDown -> { onDown(); true }
                    Key.DirectionUp -> {
                        if (event.nativeKeyEvent.repeatCount == 0) menuFocus.requestFocus()
                        true
                    }
                    else -> false
                }
            }
            .background(if (focused) GuideSilver else GuidePanel, RoundedCornerShape(18.dp))
            .clickable(onClickLabel = "Open categories", onClick = onCategories)
            .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            LiveText("‹", color = ink, size = 17.sp)
            Column(Modifier.weight(1f, fill = false), horizontalAlignment = Alignment.CenterHorizontally) {
                LiveText("Categories", color = ink.copy(alpha = 0.74f), size = 10.sp)
                LiveText(group, color = ink, size = 15.sp, weight = FontWeight.SemiBold)
            }
            LiveText("›", color = ink, size = 17.sp)
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            GuideCommand("Guide menu", onMenu, { categoryFocus.requestFocus() }, Modifier.focusRequester(menuFocus))
            Spacer(Modifier.width(10.dp))
            LiveText(formatClock(now, settings.use24HourClock), size = 13.sp, color = GuideSilver)
        }
    }
    if (settings.showProgramDetails || settings.showPreview) {
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val height = if (compact) (screenHeight * 0.23f).coerceAtMost(142.dp)
            else (screenHeight * 0.37f).coerceAtMost(242.dp)
        Box(Modifier.fillMaxWidth().height(height)) {
            val hasVideo = playing && settings.showPreview
            val details = if (settings.showProgramDetails && channel != null) rememberShowDetails(channel.key, program) else null
            val about = if (settings.showProgramDetails && channel != null) rememberChannelAbout(channel, details) else null
            if (settings.showPosters && settings.showProgramDetails) {
                val image = about?.backdropUrl ?: details?.icon ?: poster
                HeroBackdrop(image, categoryPhoto(group) ?: categoryPhoto(channel?.group), base, hasVideo)
            }
            if (hasVideo) {
                Box(Modifier.align(Alignment.CenterEnd).fillMaxWidth(if (settings.showProgramDetails) 0.49f else 0.76f)
                    .fillMaxHeight().onGloballyPositioned { onBounds(it.boundsInRoot()) })
                DisposableEffect(Unit) { onDispose { onBounds(null) } }
            } else if (settings.showPreview && !settings.showProgramDetails) {
                LiveText("Select a channel to preview", Modifier.align(Alignment.Center), guideMuted, 13.sp)
            }
            if (settings.showProgramDetails && channel != null) {
                val onNow = start != null && stop != null && now >= start && now < stop
                Column(
                    Modifier.fillMaxWidth(if (hasVideo || settings.showPosters) 0.54f else 0.86f)
                        .align(Alignment.CenterStart).padding(start = 54.dp, end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)
                ) {
                    if (GuideInfoPart.CHANNEL !in hidden || GuideInfoPart.TAGS !in hidden) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            if (GuideInfoPart.CHANNEL !in hidden) {
                                if (settings.showChannelLogos && !compact && !channel.logo.isNullOrBlank()) ChannelLogo(channel.logo, 25.dp)
                                LiveText(buildString {
                                    if (settings.showChannelNumbers) append("${channel.number}  ")
                                    append(channel.name)
                                    playlist?.let { append("  ·  $it") }
                                }, Modifier.weight(1f, fill = false), guideMuted, 13.sp, FontWeight.Medium)
                            }
                            if (GuideInfoPart.TAGS !in hidden && onNow) LiveText("LIVE", color = GuideGold, size = 11.sp, weight = FontWeight.Bold)
                            if (GuideInfoPart.TAGS !in hidden && channel.catchup != null) LiveText("Catch-up", color = guideMuted, size = 11.sp)
                        }
                    }
                    if (GuideInfoPart.TITLE !in hidden) {
                        LiveText(details?.title?.let { titleMarks(it).title } ?: channel.name,
                            size = if (compact) 23.sp else 30.sp, color = Color.White, weight = FontWeight.Bold,
                            maxLines = if (compact) 1 else 2, lineHeight = if (compact) 25.sp else 33.sp)
                    }
                    val meta = buildList {
                        if (GuideInfoPart.TIME !in hidden && start != null && stop != null)
                            add(formatDayClock(start, now, settings.use24HourClock) + " – " + formatClock(stop, settings.use24HourClock))
                        if (GuideInfoPart.LEFT !in hidden && onNow && stop != null) add(minutesLeftLabel(stop, now))
                        if (settings.showEpisodeNumbers) details?.episode?.let { add(it) }
                        if (GuideInfoPart.CATEGORY !in hidden) details?.category?.let { add(it) }
                    }.joinToString("  ·  ")
                    if (meta.isNotEmpty()) LiveText(meta, color = guideMuted, size = 12.sp)
                    if (GuideInfoPart.PROGRESS !in hidden && onNow && start != null && stop != null) {
                        ProgressBar((now - start).toFloat() / (stop - start), Modifier.width(165.dp), GuideGold, GuidePanel)
                    }
                    if (GuideInfoPart.DESCRIPTION !in hidden) {
                        val description = about?.description?.takeIf { it.isNotBlank() } ?: details?.description
                        if (!description.isNullOrBlank()) LiveText(description, color = Color.White.copy(alpha = 0.84f), size = 13.sp,
                            maxLines = if (compact) 1 else 3, lineHeight = 18.sp)
                    }
                }
            }
        }
    }
}
