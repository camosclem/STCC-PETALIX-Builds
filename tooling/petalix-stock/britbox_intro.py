"""Generate Petalix's own BritBox tile animation and original short audio cue.
This is a custom Petalix treatment, not an official BritBox ident or recording.
"""
from pathlib import Path
import hashlib,json,shutil,subprocess

def apply(root):
    target=Path(root)/'app/src/main/assets/petalix/britbox-intro'
    target.mkdir(parents=True,exist_ok=True)
    ffmpeg=shutil.which('ffmpeg')
    if not ffmpeg:raise RuntimeError('FFmpeg is required to build the BritBox intro')
    font=next((p for p in [Path('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'),Path('/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf')] if p.exists()),None)
    if not font:raise RuntimeError('A bundled system sans-serif font is required')
    text=f"drawtext=fontfile={font}:text='BritBox':fontcolor=white:fontsize=110:x=(w-tw)/2:y=(h-th)/2"
    base=[ffmpeg,'-hide_banner','-loglevel','error','-y']
    subprocess.run(base+['-f','lavfi','-i','color=c=0x111A48:s=960x540:r=30:d=2.2','-vf',text,'-frames:v','1','-threads','1',str(target/'logo.png')],check=True)
    # A quiet original two-note cue; no audio from another service is reused.
    audio='aevalsrc=0.11*sin(2*PI*523.25*t)*exp(-4*t)+0.085*sin(2*PI*783.99*t)*exp(-4*max(t-0.16\\,0))*gte(t\\,0.16):s=44100:d=2.2'
    filters=text+",fade=t=in:st=0:d=0.35,fade=t=out:st=1.8:d=0.4"
    subprocess.run(base+['-f','lavfi','-i','color=c=0x111A48:s=960x540:r=30:d=2.2','-f','lavfi','-i',audio,'-vf',filters,'-af','afade=t=in:d=0.025,afade=t=out:st=1.5:d=0.7','-c:v','libx264','-profile:v','baseline','-pix_fmt','yuv420p','-crf','24','-c:a','aac','-b:a','96k','-shortest','-movflags','+faststart',str(target/'intro.mp4')],check=True)
    record={'customPetalixIntro':True,'officialBritBoxIdent':False,'hasAudio':True,'durationSeconds':2.2,'assets':{p.name:{'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'size':p.stat().st_size} for p in [target/'logo.png',target/'intro.mp4']}}
    (target/'manifest.json').write_text(json.dumps(record,separators=(',',':')))
    return record
