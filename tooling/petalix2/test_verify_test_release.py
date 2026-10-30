import pathlib
import tempfile
import unittest
import zipfile
from unittest.mock import patch
from verify_test_release import inspect, parse_badging, parse_certificate

class TestReleaseValidation(unittest.TestCase):
    def test_badging(self):
        self.assertEqual(parse_badging("package: name='com.petalix.iptv.ui2test' versionCode='200002' versionName='2.0.0-ui-test.2'\n"), ('com.petalix.iptv.ui2test', 200002, '2.0.0-ui-test.2'))

    def test_signer(self):
        cert = '5e649d8e34d6b1f77b45094b1c5a289bb1ed6e9b28df83e2f3741abd3991d0d2'
        self.assertEqual(parse_certificate('Signer #1 certificate SHA-256 digest: '+cert+'\n'), cert)
        with self.assertRaises(ValueError):
            parse_certificate('')

    def test_refuse_modified_apk_before_signature_check(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = pathlib.Path(tmp) / 'example.apk'
            with zipfile.ZipFile(p, 'w') as z:
                z.writestr('AndroidManifest.xml', 'dummy')
            with patch('verify_test_release.run') as mock_run:
                with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
                    inspect(p, 'aapt', 'apksigner', {'sha256': '0'*64})
                mock_run.assert_not_called()

if __name__ == '__main__':
    unittest.main()
