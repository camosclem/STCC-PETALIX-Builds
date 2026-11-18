"""Original branded animation fills the service card; its resting artwork remains unchanged."""
from pathlib import Path
from shutil import copy2
from home_visual_owner_patch import replace
HERE = Path(__file__).resolve().parent

def apply(root):
    source = root / 'app/src/main/java/com/nuvio/tv'
    copy2(HERE/'PetalixServiceTileMotion.kt', source/'petalix/PetalixServiceTileMotion.kt')
    cards = source/'ui/screens/home/ModernHomeRows.kt'
    # Stop redundant captions beneath the service marks; the card artwork supplies its identity.
    text = cards.read_text()
    start = text.index('                    if (serviceTagline != null &&')
    end = text.index('                    if (shouldPlayTrailerInCard)', start)
    cards.write_text(text[:start] + text[end:])
    hero = source/'ui/screens/home/PetalixCinemaHero.kt'
    replace(hero, 'val description = preview.description?.takeIf { it.isNotBlank() } ?: when {',
            'val description = if (service) null else preview.description?.takeIf { it.isNotBlank() } ?: when {')
    replace(hero, '        service -> "Discover movies and series in this collection."\n', '')
