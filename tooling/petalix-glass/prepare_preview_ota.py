"""Port the streamlined features onto the exact signed Preview OTA source."""
import argparse
import hashlib
from pathlib import Path
import subprocess
import zipfile

from retire_playback_addon import apply as retire_playback_addon

HERE = Path(__file__).resolve().parent
PREVIEW_BUILD = 1795393172
PREVIEW_SOURCE_SHA = "42a53f6d49dd6510f588b538c0bce7e4ae3e45b3d29893fa75b7bc41205720a3"
PREVIEW_CERTIFICATE = "b6809a3733b57474d01a7b77bcddef1241b7f85f35f04142d52ed68071cd1ba1"


RETIRED_PLAYBACK_ERROR = b"Pengu Play is not assigned"


def verify_apk_playback(apk):
    """Reject packaged legacy playback before offering any Preview candidate."""
    with zipfile.ZipFile(apk) as package:
        dex_files = [
            name for name in package.namelist()
            if name.startswith("classes") and name.endswith(".dex") and "/" not in name
        ]
        if not dex_files:
            raise ValueError("Expected an APK containing application DEX files.")
        if any(RETIRED_PLAYBACK_ERROR in package.read(name) for name in dex_files):
            raise ValueError("APK still contains the retired Pengu Play playback error.")


def replace_once(path, old, new):
    text = path.read_text()
    if text.count(old) != 1:
        raise ValueError(f"Expected exactly one matching source block in {path}")
    path.write_text(text.replace(old, new, 1))


def prepare(archive, output, version_code):
    if version_code <= PREVIEW_BUILD:
        raise ValueError("Preview version code must increase.")
    with archive.open("rb") as source:
        if hashlib.file_digest(source, "sha256").hexdigest() != PREVIEW_SOURCE_SHA:
            raise ValueError("Preview source archive checksum does not match the signed release.")
    output.mkdir(parents=True, exist_ok=False)
    with zipfile.ZipFile(archive) as source:
        source.extractall(output)
    root = output / "petalix-source"
    retire_playback_addon(root)

    java = root / "app/src/main/java/com/nuvio/tv"
    guide = java / "livetv/ui/LiveTvViewModel.kt"
    all_channels = "        s.showAllChannelsGroup -> ChannelGroup.ALL\n"
    regular_channels = (
        "        regularForGuide.any { !it.locked && it.count > 0 } ->\n"
        "            regularForGuide.first { !it.locked && it.count > 0 }.id\n"
    )
    replace_once(guide, all_channels + regular_channels, regular_channels + all_channels)
    for patch in ("streamlined-home.patch", "upstream-beta41-dns.patch"):
        subprocess.run(
            ["git", "apply", "--whitespace=error", str(HERE / patch)], cwd=root, check=True
        )

    gradle = root / "app/build.gradle.kts"
    replace_once(gradle, f"versionCode = {PREVIEW_BUILD}", f"versionCode = {version_code}")
    replace_once(gradle, f"panel{PREVIEW_BUILD}", f"panel{version_code}")
    coordinator = (java / "petalix/PetalixManagedCoordinator.kt").read_text()
    updater = (root / "app/src/full/java/com/nuvio/tv/updater/UpdateRepository.kt").read_text()
    if (
        'variant.applicationId.set("com.petalix.iptv.preview")' not in gradle.read_text()
        or '"device-updates-v1"' not in coordinator
        or PREVIEW_CERTIFICATE not in updater
    ):
        raise ValueError("Preview package, OTA capability, or trusted signer changed.")
    stream_repository = java / "data/repository/StreamRepositoryImpl.kt"
    if RETIRED_PLAYBACK_ERROR in stream_repository.read_bytes():
        raise ValueError("Preview source still contains the retired playback fallback.")
    return root


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path, nargs="?")
    parser.add_argument("output", type=Path, nargs="?", help="New folder for the prepared Android source")
    parser.add_argument("--version-code", type=int)
    parser.add_argument("--verify-apk", type=Path)
    args = parser.parse_args()
    if args.verify_apk is not None:
        if args.archive is not None or args.output is not None or args.version_code is not None:
            parser.error("--verify-apk takes only the APK path")
        verify_apk_playback(args.verify_apk)
        print("Retired playback error is absent from the APK DEX files.")
    else:
        if args.archive is None or args.output is None or args.version_code is None:
            parser.error("provide the source archive, output folder, and --version-code")
        print(prepare(args.archive, args.output, args.version_code))
