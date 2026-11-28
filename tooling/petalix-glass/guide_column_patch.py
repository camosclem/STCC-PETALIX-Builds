"""Size the channel rail for the fields actually visible in the glass guide."""
from pathlib import Path


def apply(root: Path):
    ui = root / "app/src/main/java/com/nuvio/tv/livetv/ui"
    guide = ui / "PetalixCableGuide.kt"
    old = "val labelWidth = (maxWidth * .21f).coerceIn(minOf(maxWidth, 94.dp), minOf(maxWidth, 245.dp))"
    source = guide.read_text()
    if source.count(old) != 1:
        raise ValueError("Expected the glass guide's shared channel/timeline width")
    guide.write_text(source.replace(old, "val labelWidth = glassChannelColumnWidth(settings, maxWidth)"))

    surface = ui / "PetalixGlassSurface.kt"
    source = surface.read_text()
    old = """            .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {"""
    new = """            .padding(horizontal = if (settings.showChannelLogos && !settings.showChannelNames) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp,
                if (settings.showChannelLogos && !settings.showChannelNames) Alignment.CenterHorizontally else Alignment.Start)) {"""
    if source.count(old) != 1:
        raise ValueError("Expected the glass guide's channel cell padding")
    surface.write_text(source.replace(old, new))
