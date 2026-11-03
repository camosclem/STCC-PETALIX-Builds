"""Apply the shared animated STCC/Petalix startup presentation."""
from pathlib import Path


def once(text, old, new, label):
    if text.count(old) != 1:
        raise RuntimeError(f"Startup splash patch source changed: {label}")
    return text.replace(old, new)


def apply(work, brand):
    if brand not in {"stcc", "petalix"}:
        raise RuntimeError("Unknown startup splash brand")
    path = Path(work) / "app/src/main/java/com/nuvio/tv/ui/components/StartupSplashScreen.kt"
    text = path.read_text()
    if "petalixStartupPulse" in text:
        return
    text = once(text, "import androidx.compose.runtime.Composable",
        "import androidx.compose.animation.core.RepeatMode\nimport androidx.compose.animation.core.animateFloat\nimport androidx.compose.animation.core.infiniteRepeatable\nimport androidx.compose.animation.core.rememberInfiniteTransition\nimport androidx.compose.animation.core.tween\nimport androidx.compose.foundation.shape.CircleShape\nimport androidx.compose.runtime.Composable\nimport androidx.compose.runtime.getValue",
        "animated startup imports")
    text = once(text, "    val avatarColor = remember(profileColorHex) {",
        """    val isStcc = com.nuvio.tv.livetv.branding.LiveTvBranding.APP_NAME.contains("STCC", ignoreCase = true)
    val brandAccent = if (isStcc) Color(0xFFFF4057) else Color(0xFF35B7FF)
    val petalixStartupPulse = rememberInfiniteTransition(label = "petalixStartupPulse")
    val logoScale by petalixStartupPulse.animateFloat(
        0.975f, 1.025f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "startupLogoScale"
    )
    val ringRotation by petalixStartupPulse.animateFloat(
        0f, 360f,
        animationSpec = infiniteRepeatable(tween(3600)),
        label = "startupRingRotation"
    )
    val avatarColor = remember(profileColorHex) {""",
        "brand animation state")
    text = once(text, "        Column(\n            modifier = Modifier.fillMaxSize(),",
        """        // Keep any customer supplied background while darkening it for readable startup branding.
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xD9000612), Color(0x99030A18), Color(0xF0000308))))
        )
        Column(
            modifier = Modifier.fillMaxSize(),""",
        "cinematic splash overlay")
    old = """            BrandWordmark(
                modifier = Modifier.height(48.dp),
                contentDescription = com.nuvio.tv.livetv.branding.LiveTvBranding.APP_NAME,
                drawableOverride = com.nuvio.tv.livetv.branding.LiveTvBranding.iptvWordmark(
                    brandWordmarkRes ?: NuvioTheme.currentTheme.brandWordmarkResource
                )
            )"""
    new = """            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.size(150.dp)
                        .graphicsLayer { rotationZ = ringRotation; alpha = 0.55f }
                        .background(
                            Brush.radialGradient(listOf(brandAccent.copy(alpha = 0.42f), Color.Transparent)),
                            CircleShape
                        )
                )
                BrandWordmark(
                    modifier = Modifier.height(58.dp).graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                    },
                    contentDescription = com.nuvio.tv.livetv.branding.LiveTvBranding.APP_NAME,
                    drawableOverride = com.nuvio.tv.livetv.branding.LiveTvBranding.iptvWordmark(
                        brandWordmarkRes ?: NuvioTheme.currentTheme.brandWordmarkResource
                    )
                )
            }"""
    text = once(text, old, new, "animated brand wordmark")
    text = once(text, "            LoadingIndicator(modifier = Modifier.size(NuvioTheme.spacing.xxxl))",
        """            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(38.dp).graphicsLayer { rotationZ = ringRotation },
                color = brandAccent,
                trackColor = Color.White.copy(alpha = 0.14f),
                strokeWidth = 3.dp
            )""",
        "brand color startup progress")
    path.write_text(text)
