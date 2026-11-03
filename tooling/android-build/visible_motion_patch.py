"""Make the requested motion and sound visible in the actual Petalix UI2 routes."""
from pathlib import Path
import math
import shutil
import struct
import wave


def once(text, old, new, label):
    if text.count(old) != 1:
        raise RuntimeError('Petalix motion integration changed: ' + label)
    return text.replace(old, new)


def apply(work):
    work = Path(work)
    source = work / 'app/src/main/java/com/nuvio/tv'
    ui = source / 'petalix/ui2'
    home = ui / 'Petalix2Home.kt'
    text = home.read_text()
    text = once(text,
        'fun PetalixTile(modifier:Modifier=Modifier,accent:Color=PetalixBlue,onClick:()->Unit,content:@Composable BoxScope.()->Unit)',
        'fun PetalixTile(modifier:Modifier=Modifier,accent:Color=PetalixBlue,onClick:()->Unit,onFocus:(Boolean)->Unit={},content:@Composable BoxScope.()->Unit)',
        'shared focus callback signature')
    text = once(text, '.onFocusChanged { focused=it.isFocused }.clip(RoundedCornerShape(8.dp))',
        '.onFocusChanged { focused=it.hasFocus;onFocus(it.hasFocus) }.clip(RoundedCornerShape(8.dp))',
        'single source of tile focus')
    home.write_text(text)
    services = ui / 'Petalix2Services.kt'
    text = services.read_text()
    start = text.index('@Composable\nfun ServiceTile(')
    end = text.index('data class PetalixServiceState', start)
    text = text[:start] + '''@Composable
fun ServiceTile(service:PetalixService,modifier:Modifier=Modifier,onClick:()->Unit) {
    MotionServiceTile(service,modifier,onClick)
}

''' + text[end:]
    services.write_text(text)
    shutil.copyfile(Path(__file__).with_name('PetalixServiceMotion.kt'), ui / 'PetalixServiceMotion.kt')
    shutil.copyfile(Path(__file__).with_name('PetalixStartupScreen.kt'), source / 'ui/components/StartupSplashScreen.kt')
    activity = source / 'MainActivity.kt'
    text = activity.read_text()
    text = once(text, '                    val showSplash = shouldShowStartupSplash(', '''                    // A short launch reveal is independent of fast Home completion and saved splash settings.
                    var petalixIntroFinished by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(1800)
                        petalixIntroFinished = true
                    }
                    val showSplash = !petalixIntroFinished || shouldShowStartupSplash(''',
        'visible launch intro')
    activity.write_text(text)
    raw = work / 'app/src/main/res/raw'
    raw.mkdir(parents=True, exist_ok=True)
    # These are newly synthesized cues, not audio from any streaming service.
    chords = {
        'netflix': (196, 293.66, 392), 'prime': (261.63, 329.63, 523.25),
        'hulu': (220, 349.23, 440), 'disney': (293.66, 440, 587.33),
        'max': (174.61, 261.63, 349.23), 'paramount': (246.94, 369.99, 493.88),
        'apple': (329.63, 493.88, 659.25), 'peacock': (233.08, 349.23, 466.16),
        'tubi': (207.65, 311.13, 415.30), 'pluto': (185, 277.18, 369.99),
        'startup': (196, 293.66, 392, 587.33),
    }
    for name, notes in chords.items():
        rate = 24000
        duration = 0.65 if name == 'startup' else 0.38
        samples = []
        for i in range(int(rate * duration)):
            t = i / rate
            value = 0.0
            for index, freq in enumerate(notes):
                age = t - index * 0.035
                if age >= 0:
                    envelope = min(age / 0.015, 1.0) * math.exp(-age * 7)
                    value += envelope * math.sin(2 * math.pi * freq * age)
            fade = min((duration - t) / 0.04, 1.0)
            samples.append(struct.pack('<h', int(max(-1, min(1, value / len(notes) * fade * 0.7)) * 32767)))
        with wave.open(str(raw / ('petalix_cue_' + name + '.wav')), 'wb') as target:
            target.setnchannels(1)
            target.setsampwidth(2)
            target.setframerate(rate)
            target.writeframes(b''.join(samples))
