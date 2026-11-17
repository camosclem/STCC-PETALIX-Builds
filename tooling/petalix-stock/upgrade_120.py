"""Port the prepared Petalix customizations onto the pinned official 1.2.0-beta.1.4 release.

Merge each changed upstream file against its exact 13.2 base. Resolutions are
explicit and checked, so a future upstream change cannot silently discard code.
"""
from pathlib import Path
import json
import subprocess
import tempfile

HERE = Path(__file__).resolve().parent
BASE = '6e18ff250d93c0c1bac64bda75f49b035027ab99'
TAG = '1.2.0-beta.1.4'
PIN = '2d898cd8be38cd81a087604cd9dc974a06cf9a00'

def git(root, *args):
    return subprocess.check_output(['git', *args], cwd=root)

def apply(root):
    root = Path(root)
    try:
        revision = git(root, 'rev-parse', TAG).decode().strip()
    except subprocess.CalledProcessError:
        subprocess.run(['git', 'fetch', '--depth=1', 'origin', 'tag', TAG], cwd=root, check=True)
        revision = git(root, 'rev-parse', TAG).decode().strip()
    if revision != PIN or git(root, 'rev-parse', 'HEAD').decode().strip() != BASE:
        raise ValueError('Unexpected source revision while porting the official update')
    resolutions_path = HERE/'upstream-120-resolutions.json'
    resolutions = json.loads(resolutions_path.read_text()) if resolutions_path.exists() else {}
    conflicts = []
    changed = git(root, 'diff', '--no-renames', '--name-status', BASE, PIN).decode().splitlines()
    for line in changed:
        status, relative = line.split('\t', 1)
        path = root/relative
        before = git(root, 'show', BASE+':'+relative) if status != 'A' else None
        after = git(root, 'show', PIN+':'+relative) if status != 'D' else None
        ours = path.read_bytes() if path.exists() else None
        if ours == before:
            if after is None:
                path.unlink(missing_ok=True)
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(after)
        elif ours == after:
            continue
        elif before is None or after is None or ours is None:
            raise ValueError('Upstream file addition/removal collides with Petalix: '+relative)
        elif b'\0' in before+after+ours:
            # App branding is deliberately retained when upstream changes its launcher images.
            if not relative.startswith('app/src/') or '/res/' not in relative:
                raise ValueError('Unexpected binary merge: '+relative)
        else:
            with tempfile.TemporaryDirectory() as temporary:
                files = [Path(temporary)/name for name in ('ours','base','theirs')]
                for file, data in zip(files, (ours,before,after)):
                    file.write_bytes(data)
                merged = subprocess.run(['git','merge-file','-p','-L','PETALIX','-L','13.2','-L',TAG,
                                         *map(str,files)], capture_output=True)
            if merged.returncode < 0 or merged.returncode >= 128:
                raise RuntimeError('Could not merge '+relative+': '+merged.stderr.decode())
            text = merged.stdout.decode().replace('\r\n', '\n')
            for resolution in resolutions.get(relative, []):
                if text.count(resolution['old']) != resolution.get('count', 1):
                    raise ValueError('Upstream resolution anchor changed: '+relative)
                text = text.replace(resolution['old'], resolution['new'])
            path.write_text(text)
            if '<<<<<<< PETALIX' in text:
                conflicts.append(relative)
    if conflicts:
        raise ValueError('Unresolved official-update merges: '+', '.join(conflicts))
    report = root/'app/src/main/assets/petalix/upstream-release.json'
    report.write_text(json.dumps({'tag':TAG,'sha':PIN,'customizationBase':BASE}, indent=2)+'\n')
    return {'upstream':TAG,'sha':PIN,'portedFiles':len(changed)}

if __name__ == '__main__':
    import sys
    print(json.dumps(apply(Path(sys.argv[1]))))
