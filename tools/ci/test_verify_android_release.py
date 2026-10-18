import unittest
from unittest.mock import patch

from verify_android_release import inspect, validate_upgrade


class UpgradeTests(unittest.TestCase):
    def setUp(self):
        self.current = dict(package="org.feeluown.mobile", versionCode=100027,
                            versionName="0.1.0-alpha.27", signerSha256="a" * 64,
                            debuggable=False)
        self.previous = dict(self.current, versionCode=100026)

    def test_same_identity_and_increasing_version_pass(self):
        validate_upgrade(self.current, "a" * 64, 100026, self.previous)

    def test_rejects_signature_change(self):
        with self.assertRaisesRegex(ValueError, "signer"):
            validate_upgrade(self.current, "b" * 64, 100026)
        with self.assertRaisesRegex(ValueError, "signer"):
            validate_upgrade(self.current, "a" * 64, 100026,
                             dict(self.previous, signerSha256="b" * 64))

    def test_rejects_non_increasing_version(self):
        for previous in (100027, 100028):
            with self.assertRaisesRegex(ValueError, "versionCode"):
                validate_upgrade(self.current, "a" * 64, previous)

    def test_rejects_package_change_and_debug(self):
        for changes in (dict(package="other.app"), dict(debuggable=True)):
            with self.assertRaises(ValueError):
                validate_upgrade(dict(self.current, **changes), "a" * 64, 100026)

    def test_reads_current_and_older_sdk_signer_formats(self):
        badging = "package: name='org.feeluown.mobile' versionCode='100027' versionName='0.1.0-alpha.27'"
        for label in ("Signer #1", "Signer #1:", "V2 Signer:", "V3 Signer:"):
            with self.subTest(label=label), patch('verify_android_release.run', side_effect=[
                badging, f"Number of signers: 1\n{label} certificate SHA-256 digest: {'a' * 64}\n",
            ]):
                self.assertEqual(inspect('app.apk', 'aapt', 'apksigner'), self.current)

    def test_rejects_multiple_signers(self):
        with patch('verify_android_release.run', side_effect=[
            "package: name='org.feeluown.mobile' versionCode='100027' versionName='0.1.0-alpha.27'",
            f"Number of signers: 2\nSigner #1 certificate SHA-256 digest: {'a' * 64}\n",
        ]), self.assertRaisesRegex(ValueError, 'exactly one'):
            inspect('app.apk', 'aapt', 'apksigner')


if __name__ == "__main__":
    unittest.main()
