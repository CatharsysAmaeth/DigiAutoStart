#!/usr/bin/env python3
"""Recover validated historical DAdA Debug APKs from private Git history into the private archive."""
import base64
import hashlib
import json
import os
import pathlib
import subprocess
import tempfile

PRIVATE_REPO = os.environ["DADA_PRIVATE_REPOSITORY"]
TOKEN = os.environ["DADA_PRIVATE_TOKEN"]

# These refs are immutable Git commits in the private repository, not public workflow refs.
HISTORICAL_BUILDS = [
    {
        "ref": "ae524fed4fe71f272354215a7756abcb649eafab",
        "versionName": "0.6.15",
        "versionCode": 26,
    },
]

def gh_api(endpoint, *, output=None):
    env = os.environ.copy()
    env["GH_TOKEN"] = TOKEN
    command = ["gh", "api", endpoint]
    if output is None:
        return subprocess.check_output(command, env=env)
    with open(output, "wb") as stream:
        subprocess.run(command, env=env, check=True, stdout=stream)

for build in HISTORICAL_BUILDS:
    ref = build["ref"]
    version_file = json.loads(gh_api(
        f"repos/{PRIVATE_REPO}/contents/apk/version.json?ref={ref}"
    ))
    metadata = json.loads(base64.b64decode(
        "".join(version_file["content"].split()), validate=True
    ).decode("utf-8"))
    if metadata.get("versionName") != build["versionName"] or int(metadata.get("versionCode", -1)) != build["versionCode"]:
        raise SystemExit(f"Historical metadata mismatch at {ref}; refusing to archive.")
    expected_sha = metadata.get("sha256", "")
    archive_name = (
        f"DigiAutoStart-debug-{build['versionName']}-{build['versionCode']}-recovered.apk"
    )
    archive_path = f"apk/archive/{archive_name}"
    env = os.environ.copy()
    env["GH_TOKEN"] = TOKEN
    exists = subprocess.run(
        ["gh", "api", f"repos/{PRIVATE_REPO}/contents/{archive_path}?ref=main"],
        env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
    ).returncode == 0
    if exists:
        print(f"Already archived: {archive_path}")
        continue

    with tempfile.TemporaryDirectory(prefix="dada-debug-recovery-") as tmp:
        apk_path = pathlib.Path(tmp) / "historical-debug.apk"
        encoded_path = pathlib.Path(tmp) / "apk-content.json"
        gh_api(
            f"repos/{PRIVATE_REPO}/contents/apk/DigiAutoStart-debug.apk?ref={ref}",
            output=encoded_path,
        )
        content = json.loads(encoded_path.read_text(encoding="utf-8"))["content"]
        apk_path.write_bytes(base64.b64decode("".join(content.split()), validate=True))
        actual_sha = hashlib.sha256(apk_path.read_bytes()).hexdigest()
        if actual_sha != expected_sha:
            raise SystemExit(
                f"Historical APK SHA-256 mismatch for {build['versionName']}; refusing to archive."
            )
        encoded = base64.b64encode(apk_path.read_bytes()).decode("ascii")
        body = json.dumps({
            "message": f"archive: recover Debug {build['versionName']} ({build['versionCode']})",
            "content": encoded,
            "branch": "main",
        })
        subprocess.run(
            ["gh", "api", "--method", "PUT",
             f"repos/{PRIVATE_REPO}/contents/{archive_path}", "--input", "-"],
            input=body.encode(), env=env, check=True, stdout=subprocess.DEVNULL,
        )

        archive_metadata = {
            "versionName": build["versionName"],
            "versionCode": build["versionCode"],
            "sha256": actual_sha,
            "recoveredFromCommit": ref,
            "apk": archive_path,
        }
        meta_path = archive_path + ".json"
        meta_body = json.dumps({
            "message": f"archive metadata: recovered Debug {build['versionName']} ({build['versionCode']})",
            "content": base64.b64encode(
                json.dumps(archive_metadata, indent=2).encode()
            ).decode("ascii"),
            "branch": "main",
        })
        subprocess.run(
            ["gh", "api", "--method", "PUT",
             f"repos/{PRIVATE_REPO}/contents/{meta_path}", "--input", "-"],
            input=meta_body.encode(), env=env, check=True, stdout=subprocess.DEVNULL,
        )
        print(f"Recovered and SHA-256-verified Debug {build['versionName']} ({build['versionCode']}).")
