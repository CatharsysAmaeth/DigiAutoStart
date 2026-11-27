#!/usr/bin/env python3
"""Fetch, validate, redact and privately store the newest unencrypted DAdA diagnostic ZIP."""
import base64
import json
import os
import pathlib
import re
import subprocess
import zipfile

PRIVATE_REPO = os.environ["DADA_PRIVATE_REPOSITORY"]
TOKEN = os.environ["DADA_PRIVATE_TOKEN"]
WORK = pathlib.Path(os.environ.get("RUNNER_TEMP", "/tmp")) / "dada-reports"
WORK.mkdir(parents=True, exist_ok=True)
MAX_ARCHIVE = 12 * 1024 * 1024
MAX_MEMBER = 12 * 1024 * 1024

def gh_api(endpoint):
    env = os.environ.copy()
    env["GH_TOKEN"] = TOKEN
    return subprocess.check_output(["gh", "api", endpoint], env=env)

listing = json.loads(gh_api(f"repos/{PRIVATE_REPO}/contents/reports?ref=main"))
candidates = sorted(
    item["name"] for item in listing
    if item.get("type") == "file"
    and item["name"].endswith(".zip")
    and not item["name"].endswith("-encrypted.zip")
)
if not candidates:
    raise SystemExit("No unencrypted diagnostic ZIP found in private reports/.")
archive_name = candidates[-1]
encoded = json.loads(gh_api(
    f"repos/{PRIVATE_REPO}/contents/reports/{archive_name}?ref=main"
))["content"]
encoded = "".join(encoded.split())  # GitHub Contents API wraps base64 with whitespace.\narchive_bytes = base64.b64decode(encoded, validate=True)
if len(archive_bytes) > MAX_ARCHIVE:
    raise SystemExit("Report archive exceeds the 12 MiB safety limit.")
archive_path = WORK / archive_name
archive_path.write_bytes(archive_bytes)

with zipfile.ZipFile(archive_path) as zf:
    bad = zf.testzip()
    if bad:
        raise SystemExit(f"ZIP CRC validation failed for member: {bad}")
    names = set(zf.namelist())
    allowed = {"events.jsonl", "device-summary.txt"}
    payload = {}
    for name in allowed:
        if name in names:
            info = zf.getinfo(name)
            if info.file_size > MAX_MEMBER:
                raise SystemExit(f"{name} exceeds the safety limit.")
            payload[name] = zf.read(name).decode("utf-8", errors="replace")

patterns = [
    (re.compile(r"(?i)gh[pousr]_[A-Za-z0-9_]{20,}"), "[REDACTED_GITHUB_TOKEN]"),
    (re.compile(r"(?i)Bearer\s+[A-Za-z0-9._~+/-]+=*"), "Bearer [REDACTED]"),
    (re.compile(r'(?i)("(?:password|passwd|pin|otp|token|authorization|cookie|session)"\s*:\s*")[^"]*(")'), r"\1[REDACTED]\2"),
    (re.compile(r"(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}"), "[REDACTED_EMAIL]"),
]

def scrub(value):
    text = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False, indent=2)
    for pattern, replacement in patterns:
        text = pattern.sub(replacement, text)
    return text

events = []
for line in payload.get("events.jsonl", "").splitlines():
    if not line.strip():
        continue
    try:
        events.append(json.loads(line))
    except json.JSONDecodeError:
        events.append({"parse_error": "Invalid JSONL line", "line_preview": scrub(line[:500])})

out = WORK / (pathlib.Path(archive_name).stem + ".md")
parts = [
    f"# Parsed diagnostic report: {archive_name}", "",
    f"- Archive size: {len(archive_bytes)} bytes",
    f"- ZIP members: {', '.join(sorted(names))}",
    f"- Events parsed: {len(events)}",
    "- ZIP password: none",
    "- Content is best-effort redacted; review as potentially sensitive.", "",
    "## Device summary", "", "~~~text",
    scrub(payload.get("device-summary.txt", "(missing)")), "~~~", "",
    "## Events (JSON)", "", "~~~json", scrub(events), "~~~", "",
]
out.write_text("\n".join(parts), encoding="utf-8")
print(f"Validated and parsed {archive_name}: {len(events)} events.")

content_b64 = base64.b64encode(out.read_bytes()).decode("ascii")
body = json.dumps({
    "message": f"diagnostics: parse {archive_name}",
    "content": content_b64,
    "branch": "main",
})
env = os.environ.copy()
env["GH_TOKEN"] = TOKEN
endpoint = f"repos/{PRIVATE_REPO}/contents/reports/parsed/{out.name}"
try:
    existing = json.loads(subprocess.check_output(
        ["gh", "api", endpoint + "?ref=main"], env=env, stderr=subprocess.DEVNULL
    ))
    body_obj = json.loads(body)
    body_obj["sha"] = existing["sha"]
    body = json.dumps(body_obj)
except subprocess.CalledProcessError:
    pass
subprocess.run(
    ["gh", "api", "--method", "PUT", endpoint, "--input", "-"],
    input=body.encode(), env=env, check=True, stdout=subprocess.DEVNULL,
)
print(f"Saved parsed report privately as reports/parsed/{out.name}.")
