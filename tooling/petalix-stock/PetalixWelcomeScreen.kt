@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.petalix

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.nuvio.tv.core.qr.QrCodeGenerator
import com.nuvio.tv.ui.components.BrandWordmark
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames

/** Contact and payment QRs are local and never depend on an account-login service. */
@Composable
fun PetalixWelcomeScreen(onContinue: () -> Unit, onBackPress: () -> Unit) {
    BackHandler(onBack = onBackPress)
    var selected by rememberSaveable { mutableStateOf(0) }
    val contacts = remember {
        listOf(
            Triple("Text", "sms:+12522043258", "Scan to text Petalix"),
            Triple("Venmo", "https://venmo.com/u/Petalix", "Scan to pay with Venmo"),
            Triple("Cash App", "https://cash.app/\$Petalix", "Scan to pay with Cash App")
        )
    }
    val contact = contacts[selected]
    val qr = remember(contact.second) { QrCodeGenerator.generate(contact.second, 768).asImageBitmap() }
    val continueFocus = remember { FocusRequester() }
    val colors = ButtonDefaults.colors(
        containerColor = Color(0xFF303A4D), contentColor = Color.White,
        focusedContainerColor = Color.White, focusedContentColor = Color.Black
    )
    Row(Modifier.fillMaxSize().background(Brush.linearGradient(
        listOf(Color(0xFF21113B), Color(0xFF0A060F), Color.Black)
    ))) {
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(horizontal = 56.dp),
            verticalArrangement = Arrangement.Center
        ) {
            BrandWordmark(contentDescription = "Petalix", modifier = Modifier.height(60.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.height(32.dp))
            Text("Watch your library,\nanywhere", color = Color.White, fontSize = 40.sp,
                lineHeight = 45.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(
            Modifier.width(460.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.022f)).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            Text("Need help? Text 252-204-3258", color = Color.White, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                contacts.forEachIndexed { index, option ->
                    Button(onClick = { selected = index }, colors = colors) { Text(option.first) }
                }
            }
            Image(qr, contentDescription = contact.third,
                modifier = Modifier.size(192.dp).background(Color.White), contentScale = ContentScale.Fit)
            Text(contact.third, color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
            Button(onClick = onContinue, colors = colors, modifier = Modifier.focusRequester(continueFocus)) {
                Text("Continue to Petalix")
            }
        }
    }
    LaunchedEffect(Unit) { continueFocus.requestFocusAfterFrames(frames = 3) }
}
