#!/usr/bin/env python3
"""Check the test's identity and that native UI/launcher code was preserved."""
import argparse
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path


def check(root):
    def upstream(path):
        return subprocess.check_output(['git', 'show', 'HEAD:' + path], cwd=root, text=True)

    source = 'app/src/main/java/com/nuvio/tv/'
    count = 0
    for directory in ('ui/screens/home', 'ui/components', 'core/launcher', 'livetv/ui', 'livetv/sports', 'core/profile'):
        for path in (root / source / directory).rglob('*.kt'):
            relative = path.relative_to(root).as_posix()
            before = upstream(relative)
            # The authenticated runtime metadata key is the only change allowed in native UI files.
            expected = re.sub(r'(?:com\.nuvio\.tv\.)?BuildConfig\.TMDB_API_KEY',
                              'com.nuvio.tv.petalix.PetalixRuntimeConfig.apiKey', before)
            expected = re.sub(r'private val (\w+) = com.nuvio.tv.petalix.PetalixRuntimeConfig.apiKey',
                              r'private val \1 get() = com.nuvio.tv.petalix.PetalixRuntimeConfig.apiKey', expected)
            assert path.read_text() == expected, f'Unexpected native UI change: {relative}'
            count += 1
    assert count > 150
    android = '{http://schemas.android.com/apk/res/android}'
    manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
    original = ET.fromstring(upstream('app/src/main/AndroidManifest.xml'))
    app = manifest.find('application')
    old_app = original.find('application')
    for tag in ('service', 'receiver'):
        assert [ET.tostring(n) for n in app.findall(tag)] == [ET.tostring(n) for n in old_app.findall(tag)]
    activity = next(n for n in app.findall('activity') if n.get(android + 'name') == '.MainActivity')
    old_activity = next(n for n in old_app.findall('activity') if n.get(android + 'name') == '.MainActivity')
    assert ET.tostring(activity) == ET.tostring(old_activity), 'Native Home/button intent handling changed'
    gradle = (root / 'app/build.gradle.kts').read_text()
    assert gradle.count('variant.applicationId.set("com.petalix.iptv.stocktest2")') == 2
    assert 'applicationId = "com.petalix.iptv.stocktest2"' in gradle
    assert not (root / source / 'petalix/ui2').exists()
    assert not (root / source / 'petalix/PetalixHomeServices.kt').exists()
    assert 'PetalixWelcomeScreen' not in (root / source / 'ui/screens/account/AuthQrSignInScreen.kt').read_text()
    assert 'remote-control-v1' in (root / source / 'petalix/PetalixManagedCoordinator.kt').read_text()
    print(f'Native integrity passed: {count} UI/launcher/profile files, original Home intents, boot receivers and services; isolated test package.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('root', type=Path)
    check(parser.parse_args().root.resolve())
