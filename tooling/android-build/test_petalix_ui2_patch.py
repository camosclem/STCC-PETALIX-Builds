import pathlib
import tempfile
import unittest
from petalix_ui2_patch import patch_authorized_identity, AUTHORIZED_PACKAGE

class AuthorizedUi2Tests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root=pathlib.Path(self.tmp.name)
        self.gradle=root/"app/build.gradle.kts"
        self.gradle.parent.mkdir(parents=True)
        self.gradle.write_text(
            'android.defaultConfig.versionCode = 200002\n'
            'android.defaultConfig.versionName = "2.0.0-ui-test.2"\n'
            'androidComponents.onVariants { variant -> variant.applicationId.set("com.petalix.iptv.ui2test") }\n'
        )
        self.manifest=root/"app/src/main/AndroidManifest.xml"
        self.manifest.parent.mkdir(parents=True)
        self.manifest.write_text('<application android:label="Petalix 2 Test"/>')
        self.manager=root/"app/src/main/java/com/nuvio/tv/petalix/ManagedCoordinator.kt"
        self.manager.parent.mkdir(parents=True)
        self.manager.write_text(
            '.put("packageName",BuildConfig.APPLICATION_ID)\n'
            'post("activate",report().put("code",pin),null)\n'
        )
        self.policy=root/"app/src/main/java/com/nuvio/tv/petalix/PanelConnectionPolicy.kt"
        self.policy.write_text('petalix-control.caroliinaconsultant.chatgpt.site')
    def test_restores_original_authorized_identity_and_pin(self):
        pkg=patch_authorized_identity(self.tmp.name,1793236988,'1.1.0-petalix.panel1793236988')
        self.assertEqual(pkg,AUTHORIZED_PACKAGE)
        value=self.gradle.read_text()
        self.assertIn('com.petalix.iptv.preview',value)
        self.assertNotIn('com.petalix.iptv.ui2test',value)
        self.assertIn('android.defaultConfig.versionCode = 1793236988',value)
        self.assertIn('android:label="Petalix"',self.manifest.read_text())
        self.assertIn('post("activate"',self.manager.read_text())
    def test_refuses_incorrect_version(self):
        with self.assertRaises(RuntimeError):
            patch_authorized_identity(self.tmp.name,200002,"2.0.0-ui-test.2")
    def test_refuses_missing_activation_binding(self):
        self.manager.write_text("disabled")
        with self.assertRaisesRegex(RuntimeError,"PIN activation"):
            patch_authorized_identity(self.tmp.name,1793236988,'1.1.0-petalix.panel1793236988')
    def test_refuses_unexpected_package(self):
        self.gradle.write_text(self.gradle.read_text().replace(
            'com.petalix.iptv.ui2test','com.somebody.else'))
        with self.assertRaisesRegex(RuntimeError,"application ID"):
            patch_authorized_identity(self.tmp.name,1793236988,'1.1.0-petalix.panel1793236988')

if __name__=='__main__':unittest.main()
