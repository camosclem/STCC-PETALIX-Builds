"""Apply the Nuvio + IPTV beta.2.1 delta to the prepared Petalix source."""
import json
from pathlib import Path
import re
import subprocess
from tempfile import TemporaryDirectory

HERE = Path(__file__).resolve().parent
BASE = '2d898cd8be38cd81a087604cd9dc974a06cf9a00'
TAG = '1.2.0-beta.2.1'
PIN = 'f3fcb2aebd1b44a02f63f56715712fbdee11e5f6'


def git(root, *args):
    return subprocess.check_output(['git', *args], cwd=root)


# Android locale strings are independent resources. Keep Petalix's existing copy where it
# changed a resource, alongside upstream translations and new resources from this release.
LOCALE_STRING = re.compile(rb'(?m)^[ \t]*<string(?=[ \t])[^>]*\bname="([^"]+)"[^>]*>.*?</string>[ \t]*\n?', re.S)


def merge_locale(before, ours, after):
    before, ours, after = (text.replace(b'\r\n', b'\n') for text in (before, ours, after))
    old = {m[1]: m[0] for m in LOCALE_STRING.finditer(before)}
    local = {m[1]: m[0] for m in LOCALE_STRING.finditer(ours)}
    incoming = {m[1] for m in LOCALE_STRING.finditer(after)}
    merged = LOCALE_STRING.sub(lambda m: local.get(m[1], b'') if local.get(m[1]) != old.get(m[1]) else m[0], after)
    inserted = b''.join(value for name, value in local.items() if name not in old and name not in incoming)
    if inserted:
        merged = merged.replace(b'</resources>', inserted + b'</resources>', 1)
    return merged


def merge(root):
    report = root / 'app/src/main/assets/petalix/upstream-release.json'
    if json.loads(report.read_text()).get('sha') != BASE:
        raise ValueError('The beta.2.1 update requires the prepared beta.1.4 source.')
    if subprocess.run(['git', 'show-ref', '--verify', '--quiet', f'refs/tags/{TAG}'], cwd=root).returncode:
        subprocess.run(['git', 'fetch', '--depth=1', 'origin', 'tag', TAG], cwd=root, check=True)
    if git(root, 'rev-parse', f'refs/tags/{TAG}^{{commit}}').decode().strip() != PIN:
        raise ValueError('The beta.2.1 tag does not match its verified revision.')

    conflicts = []
    changed = git(root, 'diff', '--no-renames', '--name-status', BASE, PIN).decode().splitlines()
    with TemporaryDirectory() as folder:
        files = [Path(folder) / name for name in ('petalix', 'beta.1.4', 'beta.2.1')]
        for line in changed:
            status, relative = line.split('\t', 1)
            path = root / relative
            before = git(root, 'show', f'{BASE}:{relative}') if status != 'A' else None
            after = git(root, 'show', f'{PIN}:{relative}') if status != 'D' else None
            ours = path.read_bytes() if path.is_file() else None
            if ours == after:
                continue
            if ours == before:
                if after is None:
                    path.unlink()
                else:
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(after)
                continue
            if after is None or ours is None:
                raise ValueError(f'An upstream addition or deletion overlaps Petalix: {relative}')
            if b'\0' in (before or b'') + after + ours:
                if relative.startswith('app/src/') and '/res/' in relative:
                    continue  # Keep Petalix launcher artwork.
                raise ValueError(f'A binary source change overlaps Petalix: {relative}')
            if relative in ('app/src/main/res/values-ar/strings.xml', 'app/src/main/res/values-bg/strings.xml'):
                path.write_bytes(merge_locale(before, ours, after))
                continue
            for file, data in zip(files, (ours, before or b'', after)):
                file.write_bytes(data)
            result = subprocess.run(
                ['git', 'merge-file', '-p', '--diff3', '-L', 'PETALIX', '-L', 'BETA1', '-L', 'BETA2',
                 *map(str, files)], capture_output=True,
            )
            if result.returncode not in range(128) or result.stderr:
                raise RuntimeError(f'Could not merge {relative}: {result.stderr.decode()}')
            text = result.stdout.replace(b'\r\n', b'\n')
            path.write_bytes(text)
            if b'<<<<<<< PETALIX\n' in text:
                conflicts.append(relative)
    return conflicts


def apply(root):
    conflicts = merge(root)
    subprocess.run(
        ['git', 'apply', '--whitespace=error', str(HERE / 'upstream-121-resolutions.patch')],
        cwd=root, check=True,
    )
    remaining = [path for path in conflicts if b'<<<<<<< PETALIX\n' in (root / path).read_bytes()]
    if remaining:
        raise ValueError('Unresolved upstream merges: ' + ', '.join(remaining))
    report = root / 'app/src/main/assets/petalix/upstream-release.json'
    stamp = json.loads(report.read_text())
    stamp.update(tag=TAG, sha=PIN)
    report.write_text(json.dumps(stamp, indent=2) + '\n')
