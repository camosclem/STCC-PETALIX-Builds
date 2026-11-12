package com.nuvio.tv.petalix

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.livetv.home.LocalLiveTvHomeActions

private val artworkByService = mapOf(
    "folder-WFGY1XPD" to "netflix",
    "folder-8ZBZOMW9" to "disney",
    "folder-LN9SPMQN" to "hulu",
    "folder-AT8KYHZO" to "prime",
    "folder-petalix-britbox" to "britbox"
)

internal fun petalixServiceArtworkUri(folderId: String): String? =
    artworkByService[folderId]?.let(::homeArtworkUri)

private fun homeArtworkUri(name: String) = "file:///android_asset/petalix/home-art/$name.webp"

private val categories = listOf(
    "Sports" to "sports", "News" to "news", "Entertainment" to "entertainment", "My Locals" to "locals"
)

@Composable
internal fun PetalixHomeCategories() {
    val actions = LocalLiveTvHomeActions.current
    if (!actions.onHome) return
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Live TV", Modifier.padding(start = 52.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        LazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(horizontal = 52.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(categories, key = { it.first }) { (title, artwork) ->
                var focused by remember { mutableStateOf(false) }
                val shape = RoundedCornerShape(9.dp)
                Box(
                    Modifier.width(252.dp).height(70.dp)
                        .onFocusChanged { focused = it.isFocused }
                        .clip(shape).background(Color(0xFF102742))
                        .border(if (focused) 2.dp else 1.dp, if (focused) Color(0xFF50A7FF) else Color(0xFF263348), shape)
                        .clickable { actions.openCategory(title) }
                ) {
                    if (artwork != null) {
                        AsyncImage(homeArtworkUri(artwork), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    }
                    Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
                        0f to Color(0xEC050C1D), .66f to Color(0x44050C1D), 1f to Color.Transparent
                    )))
                    Text(title, Modifier.align(Alignment.CenterStart).padding(horizontal = 15.dp),
                        color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
