package com.nuvio.tv.petalix

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.nuvio.tv.LocalOpenSidebar
import com.nuvio.tv.LocalSidebarExpanded

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun PetalixHomeHeader(
    selectedType: String?, onBrowse: (String?) -> Unit,
    onLiveTv: () -> Unit, onSports: () -> Unit, onMyList: () -> Unit
) {
    val openSidebar = LocalOpenSidebar.current
    val sidebarOpen = LocalSidebarExpanded.current
    BackHandler(enabled = selectedType != null && !sidebarOpen) { onBrowse(null) }
    val tabs = listOf("Home", "Live TV", "Movies", "TV Shows", "Sports", "My List")
    val selected = when (selectedType) { "movie" -> "Movies"; "series" -> "TV Shows"; else -> "Home" }
    Row(
        Modifier.fillMaxWidth().background(Color(0xE5090D15))
            .horizontalScroll(rememberScrollState()).padding(start = 86.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            Button(
                onClick = {
                    when (title) {
                        "Home" -> onBrowse(null)
                        "Movies" -> onBrowse("movie")
                        "TV Shows" -> onBrowse("series")
                        "Live TV" -> onLiveTv()
                        "Sports" -> onSports()
                        "My List" -> onMyList()
                    }
                },
                modifier = Modifier.onPreviewKeyEvent {
                    if (index == 0 && it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft) {
                        openSidebar(); true
                    } else false
                },
                colors = ButtonDefaults.colors(containerColor = if (title == selected) Color(0xFF394B65) else Color.Transparent)
            ) { Text(title) }
        }
    }
}
