"""Install the guide port into the fully prepared Petalix owner-test source."""
from pathlib import Path
from shutil import copy2
import subprocess
import sys

HERE = Path(__file__).resolve().parent

def prepare(root):
    java = root / "app/src/main/java/com/nuvio/tv"
    gradle = root / "app/build.gradle.kts"
    text = gradle.read_text()
    if 'variant.applicationId.set("com.petalix.iptv.hometest")' not in text or '1.2.0-petalix.owner-test.' not in text:
        raise ValueError("Expected the prepared Petalix 1795087269 owner-test base")
    subprocess.run(["git", "apply", "--whitespace=error", str(HERE / "guide.patch")], cwd=root, check=True)
    for name in ("PetalixGlassSurface.kt", "PetalixGlassCategories.kt", "PetalixGuideMetrics.kt"):
        copy2(HERE / name, java / "livetv/ui" / name)
    tests = root / "app/src/test/java/com/nuvio/tv/petalix"
    for name in ("PetalixGlassGuideTest.kt", "PetalixGlassRemoteTest.kt"):
        copy2(HERE / name, tests / name)
    gradle.write_text(text + """
android.testOptions.unitTests.isIncludeAndroidResources = true
dependencies {
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
""")
    remote = root / "app/src/main/assets/petalix/remote-guide/remote.html"
    help_text = remote.read_text()
    for old, new in (
        ("From categories, return to channels.", "In categories, open the highlighted group."),
        ("Move up one channel. At the first channel, press Up to reach Guide options.", "Move up one channel or category. Press Left from the current listing for categories and Guide options."),
        ("From another time, return to now. From the current listing, open categories. From categories, open the Petalix menu.", "Close categories, return from another time to now, or leave the guide for the previous screen."),
    ):
        if help_text.count(old) != 1:
            raise ValueError("Unexpected remote tutorial copy")
        help_text = help_text.replace(old, new)
    remote.write_text(help_text)
    copy2(HERE / "NOTICE.txt", root / "app/src/main/assets/petalix/guide-notice.txt")

if __name__ == "__main__":
    prepare(Path(sys.argv[1]).resolve())
