package com.nuvio.tv.petalix

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.livetv.data.LiveTvRepository
import com.nuvio.tv.ui.screens.collection.FolderDetailUiState
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

const val PETALIX_SERVICES_ROUTE = "petalix_services"
const val PETALIX_SERVICES_COLLECTION = "collection-streaming-services-only"

@EntryPoint @InstallIn(SingletonComponent::class)
interface PetalixUiDependencies {
    fun collections(): CollectionsDataStore
    fun liveTv(): LiveTvRepository
}

private val background = Color(0xFF090E18)
private val accent = Color(0xFFEB3B80)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Action(label: String, selected: Boolean = false, enabled: Boolean = true,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier, colors = ButtonDefaults.colors(
        containerColor = if (selected) Color.White else Color.White.copy(alpha = .10f),
        contentColor = if (selected) background else Color.White,
        focusedContainerColor = Color.White, focusedContentColor = background
    )) { Text(label, fontSize = 13.sp) }
}

@Composable
private fun BrandHeader(title: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(com.nuvio.tv.R.drawable.app_logo_wordmark, "Petalix",
            Modifier.width(132.dp).height(42.dp), contentScale = ContentScale.Fit)
        Spacer(Modifier.width(24.dp))
        Text(title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PetalixEmptyHome(onLiveTv: () -> Unit, onServices: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val dependencies = remember(context) { EntryPointAccessors.fromApplication(context.applicationContext, PetalixUiDependencies::class.java) }
    val channels by dependencies.liveTv().channels.collectAsStateWithLifecycle()
    val status by dependencies.liveTv().status.collectAsStateWithLifecycle()
    val liveFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        liveFocus.requestFocus()
    }
    Column(Modifier.fillMaxSize().background(background).padding(36.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        BrandHeader("Welcome to Petalix")
        Text("Live TV, Sports and Streaming Services", color = Color.White, fontSize = 22.sp)
        Text(when {
            channels.isNotEmpty() -> "${channels.size} live channels ready"
            status.error != null -> "Your playlist needs attention. Open Live TV to see its status or retry the update."
            else -> "Open Live TV for your playlist and guide. Streaming Services opens your movie and series catalogs."
        }, color = Color.LightGray, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Action("Live TV", modifier = Modifier.focusRequester(liveFocus), onClick = onLiveTv)
            Action("Streaming Services", onClick = onServices)
            Action("Settings", onClick = onSettings)
        }
    }
}

/** Service tiles are local configuration; this menu never waits for a metadata network request. */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun PetalixServicesScreen(onLiveTv: () -> Unit, onSettings: () -> Unit, onFolder: (String, String) -> Unit) {
    val context = LocalContext.current
    val dependencies = remember(context) { EntryPointAccessors.fromApplication(context.applicationContext, PetalixUiDependencies::class.java) }
    val collections by dependencies.collections().collections.collectAsStateWithLifecycle(initialValue = emptyList())
    val serviceCollection = collections.firstOrNull { it.id == PETALIX_SERVICES_COLLECTION }
    val folders = serviceCollection?.folders.orEmpty()
    val liveFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        liveFocus.requestFocus()
    }
    Column(Modifier.fillMaxSize().background(background).padding(horizontal = 30.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BrandHeader("Streaming Services")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Action("Live TV", modifier = Modifier.focusRequester(liveFocus), onClick = onLiveTv)
            Action("Settings", onClick = onSettings)
        }
        Text("Choose a service", color = Color.LightGray, fontSize = 15.sp)
        LazyVerticalGrid(columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f).fillMaxWidth().focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)) {
            items(folders, key = { it.id }) { folder ->
                Card(onClick = { onFolder(PETALIX_SERVICES_COLLECTION, folder.id) },
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF152033)), contentAlignment = Alignment.Center) {
                        AsyncImage(folder.coverImageUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .9f)))))
                        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Bottom) {
                            Text(folder.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Movies & TV Shows", color = Color.LightGray, fontSize = 11.sp)
                        }
                    }
                }
            }
            if (folders.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Your services are not available yet. Open Settings to check your Petalix connection, or use Live TV while the catalog becomes available.",
                    color = Color.LightGray, fontSize = 15.sp)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Catalog availability: JustWatch via TMDB. Playback uses your configured sources.", color = Color.Gray, fontSize = 11.sp)
            }
        }
    }
}

/** Arvio-style spotlight, pill tabs and landscape grid; data and playback use native Nuvio routes. */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun PetalixServiceHub(state: FolderDetailUiState, onDetail: (String, String, String) -> Unit,
    onBack: () -> Unit, onRetry: () -> Unit, onMore: (Int) -> Unit) {
    var media by rememberSaveable(state.folder?.id) { mutableStateOf("movie") }
    var section by rememberSaveable(state.folder?.id, media) { mutableStateOf<String?>(null) }
    var focused by remember(state.folder?.id) { mutableStateOf<MetaPreview?>(null) }
    val moviesFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        moviesFocus.requestFocus()
    }
    val tabs = state.tabs.withIndex().filter { !it.value.isAllTab && it.value.rawType == media }
    val selectedTabs = tabs.filter { section == null || it.value.label == section }
    val titles = selectedTabs.flatMap { it.value.catalogRow?.items.orEmpty() }
        .filterNot { it.id.startsWith("__placeholder_") }.distinctBy { "${it.apiType}:${it.id}" }
    val spotlight = focused?.takeIf { f -> titles.any { it.id == f.id && it.apiType == f.apiType } } ?: titles.firstOrNull()
    val busy = state.isLoading || selectedTabs.any { it.value.isLoading || it.value.catalogRow?.isLoading == true }
    val failed = selectedTabs.any { it.value.error != null }
    val missingService = !state.isLoading && state.folder == null
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(background)) {
        AsyncImage(spotlight?.backdropUrl ?: state.folder?.heroBackdropUrl, null,
            Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(background, background.copy(alpha = .88f), background.copy(alpha = .35f)))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, background))))
        Column(Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Action("‹ Services", onClick = onBack)
                AsyncImage(state.folder?.titleLogoUrl ?: state.folder?.coverImageUrl, null,
                    Modifier.width(112.dp).height(40.dp), contentScale = ContentScale.Fit)
                Text(state.folder?.title ?: "Streaming Services", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.fillMaxWidth().height(124.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(spotlight?.name ?: state.folder?.title.orEmpty(), color = Color.White, fontSize = 29.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                Text(spotlight?.description ?: "Browse movies and series from this service.", color = Color.LightGray,
                    fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(.76f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Action("Movies", selected = media == "movie", modifier = Modifier.focusRequester(moviesFocus)) { media = "movie"; focused = null }
                Action("TV Shows", selected = media == "series") { media = "series"; focused = null }
                if (busy) Text("Loading…", color = accent, fontSize = 13.sp)
                if (failed || missingService) Action("Retry", enabled = !busy, onClick = onRetry)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 3.dp)) {
                item { Action("All", selected = section == null) { section = null } }
                items(tabs.map { it.value.label }.distinct(), key = { it }) { title ->
                    Action(title, selected = section == title) { section = title; focused = null }
                }
            }
            key(state.folder?.id, media, section) {
                LazyVerticalGrid(columns = GridCells.Fixed(4),
                    modifier = Modifier.weight(1f).fillMaxWidth().focusRestorer(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)) {
                    if (titles.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(when {
                            busy -> "Loading this service’s catalog…"
                            missingService -> "This service could not be found. Retry or return to Streaming Services."
                            failed -> "The service catalog could not load. Check your connection and retry."
                            tabs.isEmpty() -> "This service has no catalog for this selection yet."
                            else -> "No titles available for this selection."
                        }, color = Color.LightGray, fontSize = 14.sp)
                    }
                    items(titles, key = { "${it.apiType}:${it.id}" }) { title ->
                        Card(onClick = { onDetail(title.id, title.apiType, title.sourceAddonBaseUrl.orEmpty()) },
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f).onFocusChanged { if (it.hasFocus) focused = title }) {
                            Box(Modifier.fillMaxSize().background(Color(0xFF152033))) {
                                AsyncImage(title.backdropUrl ?: title.poster, title.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))))
                                Text(title.name, Modifier.align(Alignment.BottomStart).padding(9.dp), color = Color.White,
                                    fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (selectedTabs.any { it.value.catalogRow?.hasMore == true }) Action("Load more", enabled = !busy) {
                                selectedTabs.filter { it.value.catalogRow?.hasMore == true }.forEach { onMore(it.index) }
                            }
                            if (titles.isEmpty() && !failed && !missingService) Action("Retry", enabled = !busy, onClick = onRetry)
                        }
                    }
                }
            }
        }
    }
}
