#!/usr/bin/env python3
"""Bundle the exact original Petalix 8.3 v14 service clips and static logos.

Existing verified app assets/cache entries are used first. CI downloads missing
clips from their pinned original URLs and checks both length and SHA-256 before
installing them. A verified v14 corresponding-source archive can supply the same
assets without a network request. No synthetic sounds are generated.
"""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import tempfile
import time
from urllib.parse import urlparse
from urllib.request import Request, urlopen
import zipfile


HERE = Path(__file__).resolve().parent
ORIGINAL = HERE.parent / "android-build"
LEGACY_SOURCE_SHA256 = "88beb6fe08b948b3909c1cfe88e2474010738e715a75f0fb450bda862e8998f9"
LEGACY_SOURCE_TAG = "panel-7f824415-e623-4fed-a898-89550ef3c693"
LEGACY_MEDIA_MANIFEST_SHA256 = "98b91003e40a3fe7d449480bbcc2b275a2d72c706f03967843ea9eee8de714a4"
LEGACY_SOURCE_PREFIX = "petalix-source/app/src/main/"
SERVICE_IDS = frozenset({"netflix", "prime", "disney", "hulu", "max", "paramount", "apple", "peacock"})
LEGACY_LOGO_SHA256 = {
    "apple": "18bf0481ac24479ebdce3037ef7d4c9c3dbb00efdb534e11fb83dcb4de167d81",
    "disney": "ad0ea07cb6627c268dae721d9647019e6e3ce1b51c8f4ba8ec8ddf4618546140",
    "hulu": "6bf705502216ffd2ad58721bcb1b76b93d3a2a31995dfa919ef34a847d038e1f",
    "max": "7788b831491963a43c9f4b2b5b7a1ebbbab6df3a97fc4bf3f9638554b8b1fe6c",
    "netflix": "07a84017b30822b0e7d078161f468abd3e344796f8060cf96cb4bd83eaae4cfc",
    "paramount": "1e1f18603ea29b0f1373edf581749c9c41802505b62aaecd99fb01ddfc9c4a74",
    "peacock": "5d7134f70e44896a3cd445b3c8355d0f49140d733f99c4a4c148e8dd999ce864",
    "prime": "bbab516b6309959c5c2040385fe606c3ab7e394778590bf30114063cb09abe33"
}
MAX_CLIP_BYTES = 12_000_000


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _valid_file(path: Path, entry: dict) -> bool:
    return path.is_file() and path.stat().st_size == entry["size"] and _sha256(path) == entry["sha256"]


def _validate_bytes(data: bytes, entry: dict, service_id: str) -> None:
    if len(data) != entry["size"] or hashlib.sha256(data).hexdigest() != entry["sha256"]:
        raise ValueError("Original service media integrity check failed: " + service_id)


def _atomic_write(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, prefix="." + path.name + ".", suffix=".part", delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(data)
        temporary.replace(path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def _download(entry: dict, service_id: str) -> bytes:
    last_error = None
    for attempt in range(3):
        try:
            request = Request(entry["url"], headers={"User-Agent": "Petalix-Original-Service-Media/1"})
            with urlopen(request, timeout=45) as response:
                data = response.read(entry["size"] + 1)
            _validate_bytes(data, entry, service_id)
            return data
        except (OSError, ValueError) as error:
            last_error = error
            if attempt < 2:
                time.sleep(attempt + 1)
    raise RuntimeError("Could not retrieve verified original service media: " + service_id) from last_error


def prepare(upstream: Path, *, cache_dir: Path | None = None,
            source_archive: Path | None = None, offline: bool = False) -> dict:
    root = Path(upstream).resolve()
    if not (root / "app").is_dir():
        raise ValueError("Expected an Android source checkout with an app directory")
    manifest_path = ORIGINAL / "service-media-manifest.json"
    manifest_bytes = manifest_path.read_bytes()
    if hashlib.sha256(manifest_bytes).hexdigest() != LEGACY_MEDIA_MANIFEST_SHA256:
        raise ValueError("The original Petalix 8.3 v14 service media manifest changed")
    manifest = json.loads(manifest_bytes)
    entries = manifest["media"]
    if set(entries) != SERVICE_IDS:
        raise ValueError("The original eight-service media manifest changed")
    for service_id, entry in entries.items():
        parsed = urlparse(entry["url"])
        if (parsed.scheme != "https" or parsed.hostname != "raw.githubusercontent.com"
                or parsed.username or parsed.password or parsed.query or parsed.fragment
                or not re.search(r"/[0-9a-f]{40}/", parsed.path)
                or not re.fullmatch(r"[0-9a-f]{64}", entry["sha256"])
                or not 0 < entry["size"] <= MAX_CLIP_BYTES or entry.get("hasAudio") is not True):
            raise ValueError("Unpinned original service media entry: " + service_id)

    source_bytes = {}
    if source_archive is not None:
        source_archive = Path(source_archive)
        if _sha256(source_archive) != LEGACY_SOURCE_SHA256:
            raise ValueError("The supplied source archive is not the verified Petalix 8.3 v14 source")
        with zipfile.ZipFile(source_archive) as archive:
            legacy_manifest = archive.read(LEGACY_SOURCE_PREFIX + "assets/petalix2/service-media-manifest.json")
            if json.loads(legacy_manifest) != manifest:
                raise ValueError("The source archive and original service media manifest differ")
            for service_id, entry in entries.items():
                data = archive.read(LEGACY_SOURCE_PREFIX + "assets/petalix2/service-media/" + service_id + ".mp4")
                _validate_bytes(data, entry, service_id)
                source_bytes[service_id] = data

    media_dir = root / "app/src/main/assets/petalix2/service-media"
    cache = Path(cache_dir).resolve() if cache_dir is not None else None

    def install_clip(item: tuple[str, dict]) -> tuple[str, str]:
        service_id, entry = item
        target = media_dir / (service_id + ".mp4")
        if _valid_file(target, entry):
            return service_id, "bundled"
        cached = cache / (entry["sha256"] + ".mp4") if cache is not None else None
        if cached is not None and _valid_file(cached, entry):
            data, origin = cached.read_bytes(), "cache"
        elif service_id in source_bytes:
            data, origin = source_bytes[service_id], "verified-v14-source"
        elif offline:
            raise FileNotFoundError("Verified original service media is not available offline: " + service_id)
        else:
            data, origin = _download(entry, service_id), "pinned-source"
        _validate_bytes(data, entry, service_id)
        _atomic_write(target, data)
        if cached is not None and not _valid_file(cached, entry):
            _atomic_write(cached, data)
        return service_id, origin

    with ThreadPoolExecutor(max_workers=4) as executor:
        origins = dict(executor.map(install_clip, entries.items()))

    logos = {}
    logo_dir = root / "app/src/main/assets/petalix2/service-logos"
    logo_dir.mkdir(parents=True, exist_ok=True)
    for service_id in sorted(SERVICE_IDS):
        source = ORIGINAL / "service-logos" / (service_id + ".svg")
        data = source.read_bytes()
        if hashlib.sha256(data).hexdigest() != LEGACY_LOGO_SHA256[service_id]:
            raise ValueError("The original Petalix 8.3 v14 service logo changed: " + service_id)
        if source_archive is not None:
            with zipfile.ZipFile(source_archive) as archive:
                original_logo = archive.read(LEGACY_SOURCE_PREFIX + "assets/petalix2/service-logos/" + service_id + ".svg")
            if data != original_logo:
                raise ValueError("The bundled logo differs from verified Petalix 8.3 v14: " + service_id)
        target = logo_dir / (service_id + ".svg")
        _atomic_write(target, data)
        logos[service_id] = {"assetPath": "petalix2/service-logos/" + target.name,
                             "size": len(data), "sha256": hashlib.sha256(data).hexdigest()}

    assets = root / "app/src/main/assets"
    # Current Apple TV styling is a code-native vector derived from the bundled
    # mark. Apple's press-only JPG package is not redistributed in this app.
    apple_asset = assets / "petalix/apple-current.svg"
    apple_asset.parent.mkdir(parents=True, exist_ok=True)
    _atomic_write(apple_asset, (HERE / "apple-current.svg").read_bytes())
    _atomic_write(assets / "petalix2/service-media-manifest.json", manifest_bytes)
    bundle = {
        "sourceReleaseTag": LEGACY_SOURCE_TAG,
        "sourceArchiveSha256": LEGACY_SOURCE_SHA256,
        "media": {service_id: {**entry, "assetPath": "petalix2/service-media/" + service_id + ".mp4"}
                  for service_id, entry in entries.items()},
        "logos": logos,
        "focusDelayMs": 350,
        "volume": 0.8,
        "loop": False,
        "syntheticServiceSounds": False
    }
    _atomic_write(assets / "petalix2/service-media-bundle.json", (json.dumps(bundle, indent=2) + "\n").encode())
    licenses = assets / "licenses"
    licenses.mkdir(parents=True, exist_ok=True)
    for name in ("ARVIO-LICENSE.txt", "ARVIO-NOTICE.txt"):
        shutil.copyfile(ORIGINAL / name, licenses / name)

    layout = root / "app/src/main/res/layout/petalix_service_media_tile.xml"
    _atomic_write(layout, b'''<androidx.media3.ui.PlayerView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:background="@android:color/transparent" android:focusable="false"
    android:focusableInTouchMode="false" app:use_controller="false"
    app:surface_type="texture_view" />\n''')
    kotlin = root / "app/src/main/java/com/nuvio/tv/petalix/PetalixServiceMedia.kt"
    kotlin.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(HERE / "PetalixServiceMedia.kt", kotlin)
    return {"originalServiceClips": len(entries), "originalStaticLogos": len(logos),
            "totalClipBytes": sum(entry["size"] for entry in entries.values()),
            "origins": origins, "syntheticServiceSounds": False,
            "sourceReleaseTag": LEGACY_SOURCE_TAG}


def apply(upstream: Path) -> dict:
    """Entry point used by the stock prepare pipeline; optional local offline inputs."""
    return prepare(
        upstream,
        cache_dir=os.environ.get("PETALIX_MEDIA_CACHE") or None,
        source_archive=os.environ.get("PETALIX_MEDIA_SOURCE_ARCHIVE") or None,
        offline=os.environ.get("PETALIX_MEDIA_OFFLINE") == "1"
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upstream", required=True, type=Path)
    parser.add_argument("--cache-dir", type=Path)
    parser.add_argument("--source-archive", type=Path)
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args()
    print(json.dumps(prepare(args.upstream, cache_dir=args.cache_dir,
                             source_archive=args.source_archive, offline=args.offline)))
