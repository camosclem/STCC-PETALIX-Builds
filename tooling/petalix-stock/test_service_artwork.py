"""The build must not depend on a live artwork host or silently accept corrupt assets."""
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from service_artwork_patch import HERE, install_service_artwork


class ServiceArtworkTest(unittest.TestCase):
    def test_real_bundle_installs_all_services_without_network(self):
        with tempfile.TemporaryDirectory() as temporary, patch('urllib.request.urlopen', side_effect=AssertionError('No build-time artwork downloads')):
            root=Path(temporary)
            entries=install_service_artwork(root)
            self.assertEqual(8,len(entries))
            for entry in entries.values():
                installed=root/'app/src/main/assets/petalix/service-art-v1'/entry['filename']
                self.assertEqual(entry['sha256'],hashlib.sha256(installed.read_bytes()).hexdigest())

    def test_corrupt_artwork_is_rejected_before_install(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary)
            bundle=root/'bundle';(bundle/'service-art').mkdir(parents=True)
            entry=next(iter(json.loads((HERE/'service-artwork.json').read_text()).values()))
            (bundle/'service-artwork.json').write_text(json.dumps({'service':entry}))
            (bundle/'service-art'/entry['filename']).write_bytes(b'corrupt image')
            with self.assertRaisesRegex(ValueError,'checksum mismatch'):
                install_service_artwork(root/'output',bundle=bundle)
            self.assertFalse((root/'output/app/src/main/assets/petalix/service-art-v1'/entry['filename']).exists())


if __name__=='__main__':unittest.main()
