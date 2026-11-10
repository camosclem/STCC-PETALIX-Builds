"""Refresh native Live TV surfaces without replacing its guide or focus graph."""
from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    source = path.read_text()
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"Guide source changed: {path.name}: {old[:65]!r} ({count})")
    path.write_text(source.replace(old, new))


def apply(root: Path) -> None:
    guide = root / "app/src/main/java/com/nuvio/tv/livetv/ui"
    components = guide / "LiveTvComponents.kt"
    replace_once(components, '''    val c = NuvioTheme.colors.Background
    val alpha = when (LocalGuideDarkness.current) { "solid" -> 0.94f; "standard" -> 0.50f; else -> 0.66f }
    return (if (c.luminance() < 0.003f) Color(0xFF0E0E12) else c).copy(alpha = alpha)''', '''    val base = androidx.compose.ui.graphics.lerp(
        NuvioTheme.colors.BackgroundElevated, Color(0xFF21334B), 0.24f
    )
    val alpha = when (LocalGuideDarkness.current) { "solid" -> 0.98f; "standard" -> 0.84f; else -> 0.92f }
    return base.copy(alpha = alpha)''')
    replace_once(components, '''    val c = NuvioTheme.colors.Background
    val base = if (c.luminance() < 0.003f) Color(0xFF0E0E12) else c
    val alpha = when (LocalGuideDarkness.current) { "solid" -> 0.96f; "standard" -> 0.58f; else -> 0.72f }
    return androidx.compose.ui.graphics.lerp(base, Color.White, 0.10f).copy(alpha = alpha)''', '''    val base = androidx.compose.ui.graphics.lerp(
        NuvioTheme.colors.BackgroundElevated, NuvioTheme.colors.Secondary, 0.13f
    )
    val alpha = when (LocalGuideDarkness.current) { "solid" -> 0.99f; "standard" -> 0.91f; else -> 0.96f }
    return androidx.compose.ui.graphics.lerp(base, Color.White, 0.07f).copy(alpha = alpha)''')
    replace_once(components, 'internal val GuideGlassEdge = Color.White.copy(alpha = 0.10f)',
                 'internal val GuideGlassEdge = Color.White.copy(alpha = 0.14f)')
    replace_once(components, '''        LiveCellColors(NuvioTheme.colors.FocusBackground, NuvioTheme.colors.FocusRing, NuvioTheme.colors.TextPrimary)''',
                 '''        LiveCellColors(
            androidx.compose.ui.graphics.lerp(NuvioTheme.colors.FocusBackground, NuvioTheme.colors.Secondary, 0.12f),
            NuvioTheme.colors.FocusRing, NuvioTheme.colors.TextPrimary
        )''')
    replace_once(components, 'return LiveCorners.radius.collectAsState().value.dp',
                 'return LiveCorners.radius.collectAsState().value.coerceIn(16, 24).dp')

    strip = guide / "GuideRowStrip.kt"
    replace_once(strip, 'title = base.copy(fontSize = titleSize, fontWeight = FontWeight.Normal)',
                 'title = base.copy(fontSize = titleSize, fontWeight = FontWeight.Medium)')
    replace_once(strip, 'item.text.copy(alpha = 0.42f)', 'item.text.copy(alpha = 0.62f)')

    screen = guide / "LiveTvGuideScreen.kt"
    replace_once(screen, 'color = colors.text.copy(alpha = if (focused) 0.6f else 0.42f)',
                 'color = colors.text.copy(alpha = if (focused) 0.82f else 0.62f)')
