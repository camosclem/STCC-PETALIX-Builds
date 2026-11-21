"""Install the guide port into the fully prepared Petalix owner-test source."""
from pathlib import Path
from shutil import copy2, rmtree
import re
import subprocess
import sys

HERE = Path(__file__).resolve().parent

def install_sports_device_test(root, device_tests):
    relative = Path("com/nuvio/tv/livetv/sports/SportsMatcherPlaybackTest.kt")
    original = (root / "app/src/test/java" / relative).read_text()
    start = original.index("import org.robolectric.")
    end = original.index("class SportsMatcherPlaybackTest", start)
    source = original[:start] + """import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

@RunWith(AndroidJUnit4::class)
""" + original[end:]
    old = '''        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("livetv_guide2.db")'''
    new = '''        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val context = object : ContextWrapper(target) {
            override fun getDatabasePath(name: String) = target.getDatabasePath("petalix-instrumentation-" + name)
        }
        android.database.sqlite.SQLiteDatabase.deleteDatabase(context.getDatabasePath("livetv_guide2.db"))'''
    if source.count(old) != 1:
        raise ValueError("Sports matcher test fixture changed")
    source = re.sub(r"@Test fun `([^`]+)`", lambda m: "@Test fun " + m[1].replace(" ", "_"), source)
    dest = device_tests / relative
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(source.replace(old, new))

def prepare(root, *, panel=False):
    java = root / "app/src/main/java/com/nuvio/tv"
    gradle = root / "app/build.gradle.kts"
    text = gradle.read_text()
    package = "com.petalix.iptv.preview" if panel else "com.petalix.iptv.hometest"
    version = "1.1.0-petalix.panel" if panel else "1.2.0-petalix.owner-test."
    if f'variant.applicationId.set("{package}")' not in text or version not in text:
        raise ValueError("Expected the prepared Petalix Android TV source")
    for patch in ["guide.patch", "sports-and-playback.patch"]:
        subprocess.run(["git", "apply", "--whitespace=error", str(HERE / patch)], cwd=root, check=True)
    for name in ("PetalixGlassSurface.kt", "PetalixGlassCategories.kt", "PetalixGuideMetrics.kt"):
        copy2(HERE / name, java / "livetv/ui" / name)
    subprocess.run(["git", "apply", "--whitespace=error", str(HERE / "polish.patch")], cwd=root, check=True)
    subprocess.run(["git", "apply", "--whitespace=error", str(HERE / "artwork.patch")], cwd=root, check=True)
    rmtree(root / "app/src/main/assets/petalix/remote-guide")
    tests = root / "app/src/test/java/com/nuvio/tv/petalix"
    for name in ("PetalixGlassGuideTest.kt", "PetalixSportsUpdateTest.kt", "PetalixArtworkTest.kt"):
        copy2(HERE / name, tests / name)
    device_tests = root / "app/src/petalixGuideAndroidTest/java"
    ui_tests = device_tests / "com/nuvio/tv/petalix"
    ui_tests.mkdir(parents=True, exist_ok=True)
    copy2(HERE / "PetalixGlassRemoteTest.kt", ui_tests)
    install_sports_device_test(root, device_tests)
    gradle.write_text(text + """
if (providers.gradleProperty("petalixTvTest").isPresent) {
    android.splits.abi.isEnable = false
    android.defaultConfig.ndk.abiFilters.add("x86_64")
    android.sourceSets.getByName("androidTest").java.setSrcDirs(listOf("src/petalixGuideAndroidTest/java"))
} else if (providers.gradleProperty("petalixOwnerArm").isPresent) {
    android.splits.abi.isEnable = false
    android.defaultConfig.ndk.abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
}
tasks.withType<Test>().configureEach {
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
}
""")
    copy2(HERE / "NOTICE.txt", root / "app/src/main/assets/petalix/guide-notice.txt")
    sys.path.insert(0, str(HERE.parent / "petalix-stock"))
    from upgrade_121 import apply as apply_beta_2_1
    apply_beta_2_1(root)
    subprocess.run(["git", "apply", "--whitespace=error", str(HERE / "landscape-home.patch")], cwd=root, check=True)
    subprocess.run(["git", "apply", "--whitespace=error", str(HERE / "shield-guide.patch")], cwd=root, check=True)
    subprocess.run(["git", "apply", "--whitespace=error", str(HERE / "pinned-preview-and-copy.patch")], cwd=root, check=True)
    subprocess.run(["git", "apply", "--whitespace=error", str(HERE / "home-row-and-scroll.patch")], cwd=root, check=True)
    subprocess.run(["git", "apply", "--whitespace=error", str(HERE / "home-catalog-navigation.patch")], cwd=root, check=True)

if __name__ == "__main__":
    prepare(Path(sys.argv[1]).resolve())
