@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.petalix

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.nuvio.tv.ui.components.BrandWordmark
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames

/** A focused welcome after activation, with one clear action for the TV remote. */
@Composable
fun PetalixWelcomeScreen(onContinue: () -> Unit, onBackPress: () -> Unit) {
    BackHandler(onBack = onBackPress)
    val continueFocus = remember { FocusRequester() }
    Box(
        modifier = Modifier.fillMaxSize().background(Brush.linearGradient(
            listOf(Color(0xFF14213A), Color(0xFF111521), Color(0xFF090D15))
        )),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 64.dp, vertical = 36.dp).widthIn(max = 680.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BrandWordmark(
                contentDescription = "Petalix",
                modifier = Modifier.height(32.dp).widthIn(max = 180.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "Make yourself at home.",
                color = Color.White, fontSize = 40.sp, lineHeight = 48.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Live TV, movies and shows. All in one place.",
                color = Color(0xFFB9C5D8), fontSize = 18.sp, lineHeight = 26.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(34.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.focusRequester(continueFocus),
                colors = ButtonDefaults.colors(
                    containerColor = Color(0xFF28364E), contentColor = Color.White,
                    focusedContainerColor = Color.White, focusedContentColor = Color(0xFF101722)
                ),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 13.dp)
            ) {
                Text("Continue to Petalix", fontSize = 17.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
    LaunchedEffect(Unit) { continueFocus.requestFocusAfterFrames(frames = 3) }
}
