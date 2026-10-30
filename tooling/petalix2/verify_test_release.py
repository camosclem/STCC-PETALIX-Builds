#!/usr/bin/env python3
"""Verify a pinned Petalix 2 test APK; do not authorize devices or deploy production."""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import sys
import zipfile

EXPECTED = {
    'sha256': 'bb0b89f52cc6f5685a414597dafc87e3db7385e851a37c998e85ffbce49a4d5d',
    'package': 'com.petalix.iptv.ui2test',
    'version_code': 200002,
    'version_name': '2.0.0-ui-test.2',
    'certificate': '5e649d8e34d6b1f77b45094b1c5a289bb1ed6e9b28df83e2f3741abd3991d0d2',
}

def run(cmd):
    return subprocess.run(cmd, check=True, capture_output=True, text=True).stdout

def parse_badging(raw):
    match = re.search(r"(?m)^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", raw)
    if not match:
        raise ValueError('Could not read Android package metadata from aapt')
    return match.group(1), int(match.group(2)), match.group(3)

def parse_certificate(raw):
    certs = re.findall(r"(?im)^.*?Signer\s*#\d+\s+certificate SHA-256 digest:\s*([0-9a-f:]{64,95})\s*$", raw)
    result = {c.replace(':', '').lower() for c in certs}
    if len(result) != 1:
        raise ValueError('Expected exactly one unique APK signing certificate')
    return result.pop()

def inspect(apk, aapt, apksigner, expected=EXPECTED):
    if not zipfile.is_zipfile(apk):
        raise ValueError('File is not an APK ZIP archive')
    with apk.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    if digest != expected['sha256']:
        raise ValueError('APK checksum mismatch; refusing a different build')
    package, code, name = parse_badging(run([aapt, 'dump', 'badging', str(apk)]))
    if (package, code, name) != (expected['package'], expected['version_code'], expected['version_name']):
        raise ValueError('APK package or version does not match pinned Test 2')
    signature = run([apksigner, 'verify', '--verbose', '--print-certs', str(apk)])
    cert = parse_certificate(signature)
    if cert != expected['certificate']:
        raise ValueError('APK signer mismatch; refusing an unknown certificate')
    if not re.search(r'Verified using v2 scheme[^:]*:\s*true', signature, re.I):
        raise ValueError('APK v2 signing verification not confirmed')
    return {
        'brand': 'petalix', 'channel': 'test', 'testOnly': True,
        'versionName': name, 'versionCode': code, 'packageName': package,
        'sha256': digest, 'certificateSha256': cert, 'sizeBytes': apk.stat().st_size,
        'sourceBranch': 'test/petalix-2-fullscreen',
        'sourceCommit': '7f716eab5c80b156f26ad238762f683b807591f4',
        'activationStatus': 'blocked-package-not-authorized',
        'physicalDeviceActivationVerified': False,
        'physicalDevicePlaybackVerified': False,
        'customerRolloutApproved': False,
        'releaseNotes': 'Isolated debug-signed Test 2. Backend currently rejects test package. Not approved for customers.'
    }

def main():
    p = argparse.ArgumentParser()
    p.add_argument('--apk', required=True, type=pathlib.Path)
    p.add_argument('--aapt', required=True)
    p.add_argument('--apksigner', required=True)
    p.add_argument('--output', required=True, type=pathlib.Path)
    args = p.parse_args()
    try:
        result = inspect(args.apk, args.aapt, args.apksigner)
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        print(f'FAILED: {exc}', file=sys.stderr)
        raise SystemExit(1)
    args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print('Verified identity, checksum and signing certificate. NOT ACTIVATED.')

if __name__ == '__main__':
    main()
