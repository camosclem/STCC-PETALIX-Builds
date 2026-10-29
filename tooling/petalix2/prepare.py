#!/usr/bin/env python3
"""Reproduce the isolated Petalix 2 UI test source from its verified baseline."""
import argparse
import os
import pathlib
import shutil
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "android-build"))
from build import download, unzip
from home_baseline import FILES, baseline_url, verify_archive

parser = argparse.ArgumentParser()
parser.add_argument("destination", type=pathlib.Path)
parser.add_argument("--archive", type=pathlib.Path)
args = parser.parse_args()
work = args.destination.resolve()
if work.exists() and any(work.iterdir()):
    raise SystemExit("Use an empty source destination.")

archive = args.archive or HERE / FILES["petalix"][0]
if not archive.exists():
    download(baseline_url("petalix"), archive)
verify_archive(archive, "petalix")
work.mkdir(parents=True, exist_ok=True)
unzip(archive, work)

# Remove only the abandoned truncated profile-writer temporary file.
(work / "app/src/main/generated/baselineProfiles/.baseline-prof.txt.GDZF89").unlink(missing_ok=True)

modified = HERE / "modified-source"
if not modified.is_dir():
    raise SystemExit("Bundled modified source directory is missing.")
for source in modified.rglob("*"):
    if not source.is_file():
        continue
    target = work / source.relative_to(modified)
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)

(work / "gradlew").chmod(0o755)
properties = []
sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
if sdk:
    properties.append("sdk.dir=" + sdk)
key = os.environ.get("TMDB_API_KEY", "")
if key:
    properties.append("TMDB_API_KEY=" + key)
if properties:
    (work / "local.properties").write_text("\n".join(properties) + "\n")

print("Prepared native Petalix 2 test source at", work)
if not key:
    print("Metadata configuration missing; service catalogs will show the configuration notice.")
print("Build: ./gradlew :app:assembleFullDebug :app:testFullDebugUnitTest --tests 'com.nuvio.tv.petalix.*'")
