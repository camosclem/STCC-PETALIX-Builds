import pathlib
import tempfile
import unittest
import os
from unittest.mock import patch
import runner
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
        strings=root/"app/src/main/res/values/strings.xml"
        strings.parent.mkdir(parents=True,exist_ok=True)
        strings.write_text('<resources><string name="app_name">Petalix 2 Test</string></resources>')
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
        self.assertIn('<string name="app_name">Petalix</string>',(pathlib.Path(self.tmp.name)/"app/src/main/res/values/strings.xml").read_text())
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

class AuthorizedUi2ClaimTests(unittest.TestCase):
    def claim_with_job(self, value):
        with tempfile.TemporaryDirectory() as directory:
            output = pathlib.Path(directory) / 'github-output'
            environment = {
                'PETALIX_UI2_TEST':'true',
                'REBUILD_BUILD_ID':'a169ba3b-3792-473a-8b3a-b0ade721fdd9',
                'GITHUB_EVENT_NAME':'workflow_dispatch',
                'GITHUB_OUTPUT':str(output),
            }
            with patch.dict(os.environ,environment), patch.object(runner.pathlib.Path,'exists',return_value=False), patch.object(runner,'panel',side_effect=[{'id':'test-job'},{'job':value}]) as panel:
                runner.claim()
                return panel.call_args_list,output.read_text()

    def test_requests_separate_ui2_recipe(self):
        calls,output=self.claim_with_job({'id':'test-job','recipeRevision':'petalix2-fullscreen-test-v1'})
        self.assertEqual(calls[0].args,('rebuild',{'id':'a169ba3b-3792-473a-8b3a-b0ade721fdd9','ui2Test':True}))
        self.assertIn('has_job=true',output)

    def test_refuses_missing_test_job(self):
        with self.assertRaisesRegex(RuntimeError,'did not authorize'):
            self.claim_with_job(None)

    def test_refuses_unrelated_classic_recipe(self):
        with self.assertRaisesRegex(RuntimeError,'different build recipe'):
            self.claim_with_job({'id':'classic-job','recipeRevision':'service-hubs-pengu-panel-v1'})

if __name__=='__main__':unittest.main()
