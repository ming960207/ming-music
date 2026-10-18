"""Fetch the highest versionCode published for this app across both channels."""
import json
import os
import subprocess


def gh(*args):
    return subprocess.check_output(["gh", *args], text=True, encoding="utf-8")


repo = os.environ["RELEASE_REPO"]
pages = json.loads(gh("api", "--paginate", "--slurp", f"repos/{repo}/releases?per_page=100"))
releases = [r for page in pages for r in page
            if not r["draft"] and r["tag_name"].startswith("ming-music-v")]
previous = []
for release in releases:
    manifest = json.loads(gh("release", "download", release["tag_name"], "--repo", repo,
                             "--pattern", "ming-music-update.json", "--output", "-"))
    previous.append((int(manifest["versionCode"]), release["tag_name"]))
if previous:
    tag = max(previous)[1]
    subprocess.run(["gh", "release", "download", tag, "--repo", repo,
                    "--pattern", "*.apk", "--dir", "signing/previous"], check=True)
else:
    print("First permanent release: checking pinned certificate and migration version floor.")
