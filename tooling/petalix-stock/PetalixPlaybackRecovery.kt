@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.petalix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.livetv.ui.LiveText

@Composable
internal fun PetalixPlaybackRecovery(
    reason: String, onRetry: () -> Unit, onGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(2) { withFrameNanos { } }
        runCatching { retryFocus.requestFocus() }
    }
    Column(
        modifier.widthIn(max = 760.dp).clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.88f)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LiveText("Channel unavailable", size = 22.sp, color = Color.White)
        LiveText(reason, size = 15.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 4)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onRetry, modifier = Modifier.focusRequester(retryFocus)) { Text("Retry") }
            Button(onClick = onGuide) { Text("Return to Guide") }
        }
        LiveText("Back returns to the guide. Channel + / − tries another channel.",
            size = 13.sp, color = Color.White.copy(alpha = 0.7f))
    }
}
