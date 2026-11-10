package com.nuvio.tv.petalix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

private data class WalkthroughPage(val title: String, val body: String)
private val pages = listOf(
    WalkthroughPage("Use your remote", "Use the arrows to move between tiles. Press OK to open a tile or play a channel. Press Back to return, or press Left from the first tile to open the side menu."),
    WalkthroughPage("Find something to watch", "On Home, open a streaming service or browse the movie and show rows below it. Open Live TV from the side menu, then use the arrows to move through the guide and OK to watch."),
    WalkthroughPage("Make it yours", "In Live TV, hold OK on a channel to add it to Favorites. To choose local stations by ZIP, open Settings, then Live TV and Local stations. You can find this guide again under Settings > About.")
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PetalixWalkthrough(onDismiss: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val nextFocus = remember { FocusRequester() }
    LaunchedEffect(page) { runCatching { nextFocus.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier.fillMaxWidth(0.72f)
                .background(Color(0xFF20374D), RoundedCornerShape(20.dp))
                .padding(horizontal = 36.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("Petalix · Quick guide", color = Color(0xFFB7DDE9), fontWeight = FontWeight.Bold)
            Text(pages[page].title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(pages[page].body, color = Color(0xFFE5EFF4))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(onClick = { if (page < pages.lastIndex) page++ else onDismiss() }, modifier = Modifier.focusRequester(nextFocus)) {
                    Text(if (page == pages.lastIndex) "Done" else "Next")
                }
                if (page > 0) Button(onClick = { page-- }) { Text("Back") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onDismiss) { Text("Skip") }
            }
            Text("${page + 1} of ${pages.size} · Use your remote's OK and Back buttons", color = Color(0xFFB7DDE9))
        }
    }
}
