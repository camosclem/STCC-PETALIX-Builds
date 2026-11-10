"""Bundle the BritBox mnemonic shown on Sibling Rivalry's official project page."""
from pathlib import Path
import base64
import hashlib
import json
import shutil
import subprocess

HERE = Path(__file__).resolve().parent
SOURCE = "https://www.siblingrivalry.com/studio/work/britbox"
SHA256 = "a03d02530e3c38fcffea1d4ebf69a7bc06e3e785dcc8c685eae1151c7ab871f8"


def apply(root):
    target = Path(root) / "app/src/main/assets/petalix/britbox-intro"
    target.mkdir(parents=True, exist_ok=True)
    clip = base64.b64decode((HERE / "britbox-official-mnemonic.mp4.b64").read_text().strip(), validate=True)
    if hashlib.sha256(clip).hexdigest() != SHA256:
        raise ValueError("BritBox source clip changed")
    (target / "intro.mp4").write_bytes(clip)
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        raise RuntimeError("FFmpeg is required for the BritBox fallback image")
    font = next((p for p in [Path('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'),
                            Path('/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf')]
                 if p.exists()), None)
    if not font:
        raise RuntimeError("A sans-serif font is required for the BritBox fallback")
    text = f"drawtext=fontfile={font}:text='britbox':fontcolor=0xB3E8F8:fontsize=108:x=(w-tw)/2:y=(h-th)/2"
    subprocess.run([ffmpeg, '-hide_banner', '-loglevel', 'error', '-y', '-f', 'lavfi', '-i',
                    'color=c=0x557EAB:s=960x540:r=30:d=1', '-vf', text, '-frames:v', '1',
                    '-threads', '1', str(target / 'logo.png')], check=True)
    record = {"officialBritBoxIdent": True, "source": SOURCE, "sourceSha256": SHA256,
              "hasAudio": True, "durationSeconds": 5.055,
              "assets": {p.name: {"sha256": hashlib.sha256(p.read_bytes()).hexdigest(), "size": p.stat().st_size}
                         for p in [target / 'logo.png', target / 'intro.mp4']}}
    (target / 'manifest.json').write_text(json.dumps(record, separators=(',', ':')))
    return record
