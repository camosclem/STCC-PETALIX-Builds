import pathlib,tempfile,unittest
from branding_patch import apply

class BrandingTest(unittest.TestCase):
    def test_visible_copy_preserves_integration_contracts(self):
        for brand,label in [('petalix','Petalix'),('stcc','STCC')]:
            with tempfile.TemporaryDirectory() as directory:
                root=pathlib.Path(directory)
                xml=root/'app/src/main/res/values/strings.xml';xml.parent.mkdir(parents=True)
                xml.write_text('<resources><string name="app_name">Nuvio TV</string><string name="welcome">Welcome to Nuvio</string><string name="licenses_attribution">Based on Nuvio</string></resources>')
                kotlin=root/'app/src/main/java/com/nuvio/tv/ui/screens/Screen.kt';kotlin.parent.mkdir(parents=True)
                kotlin.write_text('package com.nuvio.tv.ui\nText("Nuvio settings")\nval link="https://nuvio.tv/support"\n')
                data=root/'app/src/main/java/com/nuvio/tv/data/Store.kt';data.parent.mkdir(parents=True)
                data.write_text('val key="Nuvio"')
                apply(root,brand)
                self.assertIn('Welcome to '+label,xml.read_text())
                self.assertIn('Based on Nuvio',xml.read_text())
                self.assertIn('Text("'+label+' settings")',kotlin.read_text())
                self.assertIn('package com.nuvio.tv.ui',kotlin.read_text())
                self.assertIn('https://nuvio.tv/support',kotlin.read_text())
                self.assertEqual(data.read_text(),'val key="Nuvio"')
                before=kotlin.read_text();apply(root,brand);self.assertEqual(before,kotlin.read_text())

if __name__=='__main__':unittest.main()
