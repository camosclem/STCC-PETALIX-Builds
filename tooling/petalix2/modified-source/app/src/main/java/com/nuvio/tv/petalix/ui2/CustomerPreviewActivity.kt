package com.nuvio.tv.petalix.ui2

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Text

/**
 * UI-only walkthrough while the Petalix Control server refuses a distinct test package.
 * Does not bypass activation, grant subscriptions, read playlists or supply playback.
 */
class CustomerPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContent { PetalixPreview() }
    }
}

@Composable
private fun PetalixPreview() {
    var tab by rememberSaveable { mutableStateOf("Home") }
    var serviceId by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    val service = PetalixServices.find(serviceId)
    BackHandler(service != null || tab != "Home") {
        if(service != null) serviceId = "" else tab = "Home"
    }
    Column(Modifier.fillMaxSize().background(PetalixBackground)) {
        Row(Modifier.fillMaxWidth().height(60.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF0C2440), PetalixBackground)))
            .padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            PetalixReferenceLogo(Modifier.width(170.dp).height(44.dp))
            Spacer(Modifier.width(6.dp))
            listOf("Home", "Live TV", "Movies", "TV Shows", "Search", "Settings").forEach { item ->
                PetalixAction(item, selected = tab == item && service == null,
                    onClick = { tab = item; serviceId = "" })
            }
            Spacer(Modifier.weight(1f))
            Text("UI PREVIEW", color = Color(0xFF8DD9FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        if(service != null) {
            ServiceWalkthrough(service, onBack = { serviceId = "" })
        } else when(tab) {
            "Home" -> PreviewHome(onService = { serviceId = it }, onSection = { tab = it }, onExplore = { tab = "Browse" })
            "Browse" -> {
                Column(Modifier.fillMaxSize().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("Explore Streaming Services", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                        color = Color.White, modifier = Modifier.padding(horizontal = 26.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 26.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(PetalixServices.all, key = { it.id }) { s ->
                            ServiceTile(s, onClick = { serviceId = s.id })
                        }
                    }
                    Text("Select a service to preview its interface. Sample layouts only: no paid streams.",
                        color = Color(0xFFD6E2ED), modifier = Modifier.padding(horizontal = 26.dp))
                }
            }
            "Live TV" -> PreviewLiveTv()
            "Movies", "TV Shows" -> PreviewLibrary(tab)
            "Search" -> {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Browse streaming services", color = Color.White, fontSize = 25.sp)
                    androidx.compose.material3.OutlinedTextField(query, { query = it }, singleLine = true,
                        label = { androidx.compose.material3.Text("Search service names") })
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(PetalixServices.all.filter { it.name.contains(query, ignoreCase = true) }, key = { it.id }) { s ->
                            ServiceTile(s, onClick = { serviceId = s.id })
                        }
                    }
                    Text("This is a UI-only walkthrough. Movie title search requires activated catalog data.",
                        color = Color(0xFFB8C8D9), fontSize = 13.sp)
                }
            }
            else -> {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Petalix 2.0 visual preview", color = Color.White, fontSize = 25.sp)
                    Text("Sample tiles only. This app does not access customer credentials, subscriptions, or live streams.",
                        color = Color(0xFFB8C8D9), fontSize = 14.sp)
                    PetalixAction("Explore the design", onClick = { tab = "Browse" })
                }
            }
        }
    }
}

@Composable
private fun PreviewHome(onService: (String) -> Unit, onSection: (String) -> Unit, onExplore: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(174.dp).background(
                Brush.horizontalGradient(listOf(PetalixBackground, Color(0xFF123B64), PetalixBackground))
            )) {
                Column(Modifier.align(Alignment.CenterStart).padding(start = 34.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("PETALIX 2.0", color = PetalixBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Your entertainment. Your way.", color = Color.White,
                        fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Explore the full-screen design and streaming-service interfaces.",
                        color = Color(0xFFD6E2ED), fontSize = 13.sp)
                    PetalixAction("Enter UI preview", onClick = onExplore)
                }
                Text("DESIGN PREVIEW · NO VIDEO PLAYBACK", color = Color(0xFFB1DAFF),
                    fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp))
            }
        }
        item {
            PetalixSection("Live TV", actionLabel = "See All", onAction = { onSection("Live TV") }) {
                PreviewCategories { onSection("Live TV") }
            }
        }
        item {
            PetalixSection("Streaming Services") {
                LazyRow(contentPadding = PaddingValues(horizontal = 26.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(PetalixServices.all, key = { it.id }) { s ->
                        ServiceTile(s, onClick = { onService(s.id) })
                    }
                }
            }
        }
        item { SampleCards("Popular Movies · sample layout", PetalixBlue) }
        item { SampleCards("TV Shows · sample layout", Color(0xFF739CEC)) }
    }
}

@Composable
private fun PreviewCategories(onClick: () -> Unit) {
    val groups = listOf("News", "Sports", "Entertainment", "Kids", "Local TV", "Lifestyle", "Documentaries")
    LazyRow(contentPadding = PaddingValues(horizontal = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        items(groups.indices.toList()) { i ->
            PetalixTile(Modifier.width(160.dp).height(68.dp), onClick = onClick) {
                CategoryArtwork(i, Modifier.fillMaxSize())
                Text(groups[i], color = Color.White, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun SampleCards(title: String, accent: Color) {
    PetalixSection(title) {
        LazyRow(contentPadding = PaddingValues(horizontal = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items((1..7).toList()) { n ->
                PetalixTile(Modifier.width(158.dp).height(90.dp), accent = accent, onClick = {}) {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.27f), Color(0xFF081220))
                    )))
                    Text("Sample tile " + n, color = Color.White, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
                }
            }
        }
    }
}

@Composable
private fun ServiceWalkthrough(service: PetalixService, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().background(service.background),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                PetalixAction("Back", accent = service.accent, onClick = onBack)
                Spacer(Modifier.width(18.dp))
                Text(service.name, color = service.accent, fontWeight = FontWeight.Black, fontSize = 26.sp)
                Spacer(Modifier.weight(1f))
                Text("SAMPLE CATALOG ONLY", color = Color(0xFFBECFE1), fontSize = 12.sp)
            }
        }
        item {
            Box(Modifier.fillMaxWidth().height(if(service.style == "apple") 235.dp else 195.dp)
                .background(Brush.horizontalGradient(
                    listOf(service.background, service.accent.copy(alpha = 0.27f), service.background)
                ))) {
                Column(Modifier.align(Alignment.CenterStart).padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("EXPLORE " + service.name.uppercase(), color = service.accent,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(service.name + "-inspired browsing", color = Color.White,
                        fontWeight = FontWeight.Bold, fontSize = 30.sp)
                    Text("Browsing layout sample, not a playable subscription catalog.",
                        color = Color(0xFFC4D4E5), fontSize = 13.sp)
                }
            }
        }
        items(listOf("Featured", "Popular Series", "New Movies") + service.collections.map { it.first }) { heading ->
            SampleCards(heading, service.accent)
        }
    }
}

@Composable
private fun PreviewLiveTv() {
    Column(Modifier.fillMaxSize().padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Live TV categories", color = Color.White, fontSize = 26.sp,
            modifier = Modifier.padding(horizontal = 26.dp))
        PreviewCategories {}
        Text("Live TV guide and playback require a verified customer activation.",
            color = Color(0xFFB8C8D9), modifier = Modifier.padding(horizontal = 26.dp))
    }
}

@Composable
private fun PreviewLibrary(title: String) {
    Column(Modifier.fillMaxSize().padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(title, color = Color.White, fontSize = 25.sp,
            modifier = Modifier.padding(horizontal = 26.dp))
        SampleCards("Featured " + title, PetalixBlue)
        SampleCards("Browse by genre", Color(0xFF7C91CF))
        Text("Sample interface only. Real titles and playback require activation.",
            color = Color(0xFFB8C8D9), modifier = Modifier.padding(horizontal = 26.dp))
    }
}
