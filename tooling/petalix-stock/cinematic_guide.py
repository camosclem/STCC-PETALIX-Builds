"""Install the Petalix guide on the prepared owner-test source."""
import re
import subprocess
from pathlib import Path
from shutil import copy2

from PIL import Image

HERE = Path(__file__).resolve().parent
STRING = re.compile(r'(<string\b[^>]*\bname="([^"]+)"[^>]*>)(.*?)(</string>)', re.S)
# The delimiters protect domains, deep links, file names and resource references.
# Android resource markup and translator attributes stay byte-for-byte intact.
BRAND = re.compile(r'(?<![a-z0-9_./:@#-])(?:nuvio|novio)(?:\s*\+\s*IPTV|\s*TV)?(?![.:/]\w|-collections\.json)', re.I)
MARKUP = re.compile(r'(<[^>]+>)')


def brand_string(match):
    opening, name, value, closing = match.groups()
    if name.startswith('licenses_attributions_') or 'copyright' in name or 'source_code' in name:
        return match.group()
    # This section credits and funds the external project. Its recipient must stay accurate.
    if name == 'support_nuvio_name':
        return opening + '@string/contributors_tab' + closing
    if name.startswith(('supporters_', 'supporter_membership_', 'sponsors_')):
        return opening + re.sub('Petalix', 'Nuvio', value, flags=re.I) + closing
    parts = MARKUP.split(value)
    for i in range(0, len(parts), 2):
        parts[i] = BRAND.sub(lambda m: 'PETALIX' if m.group().isupper() else 'Petalix', parts[i])
    return opening + ''.join(parts) + closing


def apply(root):
    subprocess.run(['git', 'apply', '--whitespace=error', str(HERE / 'cinematic-guide.patch')], cwd=root, check=True)
    subprocess.run(['git', 'apply', '--whitespace=error', str(HERE / 'guide-ready.patch')], cwd=root, check=True)
    source = root / 'app/src'
    ui = source / 'main/java/com/nuvio/tv/livetv/ui'
    copy2(HERE / 'PetalixGuideHero.kt', ui / 'PetalixGuideHero.kt')
    copy2(HERE / 'PetalixHomeNavigation.kt', source / 'main/java/com/nuvio/tv/ui/screens/home/PetalixHomeNavigation.kt')
    # Prepare already installs the real Petalix artwork. Trim transparent margins for the toolbar.
    drawables = source / 'main/res/drawable-nodpi'
    with Image.open(drawables / 'app_logo_wordmark_gold.png') as wordmark:
        image = wordmark.convert('RGBA')
        bounds = image.getchannel('A').getbbox()
        if not bounds:
            raise ValueError('Empty Petalix wordmark')
        image.crop(bounds).save(drawables / 'petalix_guide_wordmark.png')
    for path in source.glob('*/res/values*/*.xml'):
        original = path.read_text()
        branded = STRING.sub(brand_string, original)
        if path.relative_to(source).as_posix() == 'main/res/values/strings.xml':
            branded = branded.replace('Reset it from your Petalix account on Petalix website.',
                                      'Use the account website to reset it.')
        if branded != original:
            path.write_text(branded)
