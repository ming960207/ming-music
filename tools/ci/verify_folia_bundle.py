"""Reject an APK with a missing/stale offline Folia renderer before publishing."""
import argparse
from pathlib import Path
from zipfile import ZipFile


def verify(apk: Path, bundle: Path) -> None:
    expected = bundle.read_bytes()
    with ZipFile(apk) as archive:
        assets = [name for name in archive.namelist() if name.endswith('/folia-stage.html')]
        if len(assets) != 1:
            raise ValueError(f'Expected one packaged Folia renderer, found {len(assets)}')
        if archive.read(assets[0]) != expected:
            raise ValueError('APK contains a stale or modified Folia renderer')
    print(f'Verified exact offline Folia renderer in APK ({len(expected)} bytes)')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--bundle', type=Path, default=Path('shared/src/commonMain/composeResources/files/folia-stage.html'))
    args = parser.parse_args()
    verify(args.apk, args.bundle)
