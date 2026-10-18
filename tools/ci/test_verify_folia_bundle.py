import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

from verify_folia_bundle import verify


class FoliaBundleTest(unittest.TestCase):
    def test_rejects_missing_and_stale_assets_and_accepts_exact_bundle(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / 'test.apk'
            bundle = Path(directory) / 'folia-stage.html'
            bundle.write_bytes(b'current offline renderer')
            for content in (None, b'stale renderer'):
                with ZipFile(apk, 'w') as archive:
                    if content is not None:
                        archive.writestr('assets/composeResources/test/files/folia-stage.html', content)
                with self.assertRaises(ValueError):
                    verify(apk, bundle)
            with ZipFile(apk, 'w') as archive:
                archive.writestr('assets/composeResources/test/files/folia-stage.html', bundle.read_bytes())
            verify(apk, bundle)


if __name__ == '__main__':
    unittest.main()
