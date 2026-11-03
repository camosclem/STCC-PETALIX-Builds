"""Restore native Sports access and animate service focus without extra network/video loads."""
from pathlib import Path

def once(text, old, new):
    if text.count(old) != 1:
        raise RuntimeError('UI2 access patch source changed: '+old[:80])
    return text.replace(old,new)

def apply(work):
    root=Path(work)/'app/src/main/java/com/nuvio/tv/petalix/ui2'
    chrome=root/'Petalix2Chrome.kt'
    text=chrome.read_text()
    if 'PetalixTab("Sports"' not in text:
        text=once(text,'    PetalixTab("Search", "search", Icons.Default.Search),','    PetalixTab("Sports", Screen.Sports.route, Icons.Default.SportsSoccer),\n    PetalixTab("Search", "search", Icons.Default.Search),')
        chrome.write_text(text)
    services=root/'Petalix2Services.kt'
    text=services.read_text()
    if 'val serviceFocusScale by' in text:return
    text=once(text,'import androidx.compose.runtime.*','import androidx.compose.runtime.*\nimport androidx.compose.animation.core.*\nimport androidx.compose.ui.focus.onFocusChanged\nimport androidx.compose.ui.graphics.graphicsLayer\nimport androidx.compose.foundation.border\nimport androidx.compose.foundation.shape.RoundedCornerShape')
    old='    PetalixTile(modifier.width(112.dp).height(50.dp).semantics { contentDescription=service.name },PetalixBlue,onClick) {'
    new='''    var serviceFocused by remember { mutableStateOf(false) }
    val serviceFocusScale by animateFloatAsState(if(serviceFocused)1.06f else 1f, tween(180), label="serviceFocusScale")
    val serviceGlow = if(serviceFocused) {
        val pulse=rememberInfiniteTransition(label="serviceGlow")
        val alpha by pulse.animateFloat(.35f,.95f,infiniteRepeatable(tween(1100),RepeatMode.Reverse),label="serviceGlowAlpha")
        alpha
    } else 0f
    PetalixTile(modifier.width(112.dp).height(50.dp)
        .graphicsLayer { scaleX=serviceFocusScale;scaleY=serviceFocusScale }
        .onFocusChanged { serviceFocused=it.hasFocus }
        .border(2.dp,service.accent.copy(alpha=serviceGlow),RoundedCornerShape(8.dp))
        .semantics { contentDescription=service.name },service.accent,onClick) {'''
    services.write_text(once(text,old,new))
