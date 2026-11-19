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


# Use the localized label when possible. English fallbacks for upstream-specific copy
# keep the destination and payment recipient clear in locales that name the upstream app.
UPSTREAM_COPY = {
    'licenses_attributions_nuvio_title': 'Upstream Android TV project',
    'supporters_contributors_title': 'Upstream contributors',
    'supporters_contributors_subtitle': 'Meet the people who support the upstream TV and mobile projects.',
    'supporters_contributors_donate_copy': 'Donations here support development and infrastructure for the upstream open-source project.',
    'supporters_contributors_donate_button': 'Support the upstream project',
    'supporters_contributors_qr_subtitle': 'Open the link on your phone to support the upstream project.',
    'supporter_membership_title': 'Upstream supporter membership',
    'supporter_membership_description': 'Membership supports infrastructure and development for the upstream project.',
    'supporter_membership_connected_description': 'No active upstream membership was found on this Patreon account. View the available options or refresh after changing your membership.',
    'supporters_since': 'Supporting the upstream project since %1$s',
    'supporters_since_unknown': 'Upstream project supporter',
    'sponsors_detail_copy': 'These sponsors support development of the upstream project.',
    'custom_server_error_official_active': 'The official server is already selected.',
    'custom_server_error_official_available': 'Close this dialog and choose Use official server to switch back.',
    'collections_file_description': 'Reads your saved collections file from Downloads.',
}


def brand_string(match, default_locale):
    opening, name, value, closing = match.groups()
    if 'copyright' in name or 'source_code' in name:
        return match.group()
    if name == 'support_nuvio_name':
        return opening + '@string/contributors_tab' + closing
    if name in UPSTREAM_COPY:
        if default_locale:
            return opening + UPSTREAM_COPY[name] + closing
        if re.search(r'nuvio|novio|petalix', value, re.I):
            return ''
        return match.group()
    parts = MARKUP.split(value)
    for i in range(0, len(parts), 2):
        parts[i] = BRAND.sub(lambda m: 'PETALIX' if m.group().isupper() else 'Petalix', parts[i])
    return opening + ''.join(parts) + closing


def apply(root):
    subprocess.run(['git', 'apply', '--whitespace=error', str(HERE / 'cinematic-guide.patch')], cwd=root, check=True)
    subprocess.run(['git', 'apply', '--whitespace=error', str(HERE / 'guide-ready.patch')], cwd=root, check=True)
    subprocess.run(['git', 'apply', '--whitespace=error', str(HERE / 'petalix-navigation.patch')], cwd=root, check=True)
    subprocess.run(['git', 'apply', '--whitespace=error', str(HERE / 'petalix-cable.patch')], cwd=root, check=True)
    source = root / 'app/src'
    copy2(HERE / 'PetalixNavigationShell.kt', source / 'main/java/com/nuvio/tv/PetalixNavigationShell.kt')
    copy2(HERE / 'PetalixRemoteGuide.kt', source / 'main/java/com/nuvio/tv/PetalixRemoteGuide.kt')
    remote_assets = source / 'main/assets/petalix/remote-guide'
    remote_assets.mkdir(parents=True, exist_ok=True)
    copy2(HERE / 'remote-guide/remote.html', remote_assets / 'remote.html')
    ui = source / 'main/java/com/nuvio/tv/livetv/ui'
    for name in ('PetalixGuideHero.kt', 'PetalixCableGuide.kt', 'PetalixAirtimes.kt'):
        copy2(HERE / name, ui / name)
    copy2(HERE / 'PetalixCableTimelineTest.kt', source / 'test/java/com/nuvio/tv/petalix/PetalixCableTimelineTest.kt')
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
        branded = STRING.sub(lambda match: brand_string(match, path.parent.name == 'values'), original)
        if path.relative_to(source).as_posix() == 'main/res/values/strings.xml':
            branded = branded.replace('Reset it from your Petalix account on Petalix website.',
                                      'Use the account website to reset it.')
        if branded != original:
            path.write_text(branded)
