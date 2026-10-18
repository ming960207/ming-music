"""Verify real APK identity before allowing publication (Python standard library only)."""
import argparse
import json
import re
import subprocess
from pathlib import Path


def validate_upgrade(current, expected_signer, minimum_code, previous=None):
    if not re.fullmatch(r"[0-9a-f]{64}", expected_signer):
        raise ValueError("Expected signer must be a lowercase SHA-256 fingerprint")
    if current["package"] != "org.feeluown.mobile":
        raise ValueError("Unexpected applicationId")
    if current["debuggable"]:
        raise ValueError("A release APK must not be debuggable")
    if current["signerSha256"] != expected_signer:
        raise ValueError("APK signer differs from the pinned release certificate")
    if current["versionCode"] <= minimum_code:
        raise ValueError("versionCode must increase beyond the release baseline")
    if previous:
        if previous["package"] != current["package"]:
            raise ValueError("Previous APK applicationId differs")
        if previous["signerSha256"] != current["signerSha256"]:
            raise ValueError("Previous APK signer differs; in-place upgrade is impossible")
        if previous["versionCode"] >= current["versionCode"]:
            raise ValueError("versionCode must increase beyond the previous APK")


def run(tool, *args):
    # Invoke the SDK JAR directly on Windows so paths never pass through cmd.
    command = [str(tool), *map(str, args)]
    if str(tool).lower().endswith(".bat"):
        command = ["java", "-jar", str(Path(tool).parent / "lib" / "apksigner.jar"), *map(str, args)]
    return subprocess.check_output(command, text=True, encoding="utf-8", errors="replace")


def inspect(apk, aapt, apksigner):
    badging = run(aapt, "dump", "badging", apk)
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    if not package:
        raise ValueError("Cannot read APK package metadata")
    certificates = run(apksigner, "verify", "--verbose", "--print-certs", apk)
    signers = set(re.findall(r"^(?:Signer #\d+:?|V[234](?:\.\d+)? Signer):? certificate SHA-256 digest: ([0-9a-fA-F]{64})$", certificates, re.M))
    if len(signers) != 1 or "Number of signers: 1" not in certificates:
        raise ValueError("Expected exactly one verified APK signer")
    return dict(package=package[1], versionCode=int(package[2]), versionName=package[3],
                signerSha256=next(iter(signers)).lower(), debuggable="application-debuggable" in badging)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--aapt", required=True)
    parser.add_argument("--apksigner", required=True)
    parser.add_argument("--policy", type=Path, default=Path("android-release.json"))
    parser.add_argument("--previous-apk", type=Path)
    parser.add_argument("--version-name", required=True)
    args = parser.parse_args()
    policy = json.loads(args.policy.read_text(encoding="utf-8"))
    current = inspect(args.apk, args.aapt, args.apksigner)
    previous = inspect(args.previous_apk, args.aapt, args.apksigner) if args.previous_apk else None
    validate_upgrade(current, policy["signerSha256"], policy["minimumVersionCode"], previous)
    if current["versionName"] != args.version_name:
        raise ValueError("APK versionName differs from the requested release")
    print(json.dumps(current, indent=2))


if __name__ == "__main__":
    main()
