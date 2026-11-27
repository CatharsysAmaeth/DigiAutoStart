#!/usr/bin/env python3
"""Validate private DAdA reports, preserve raw APK evidence privately, and decompile Hermes bundles."""
import base64
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import zipfile
from urllib.parse import quote

PRIVATE_REPO = os.environ["DADA_PRIVATE_REPOSITORY"]
TOKEN = os.environ["DADA_PRIVATE_TOKEN"]
WORK = pathlib.Path(os.environ.get("RUNNER_TEMP", "/tmp")) / "dada-reports"
WORK.mkdir(parents=True, exist_ok=True)
MAX_ARCHIVE = 48 * 1024 * 1024
MAX_MEMBER = 24 * 1024 * 1024
MAX_EVIDENCE_TOTAL = 32 * 1024 * 1024
MAX_ANALYSIS_FILE = 24 * 1024 * 1024

def gh_env():
    env = os.environ.copy()
    env["GH_TOKEN"] = TOKEN
    return env

def gh_api(endpoint):
    return subprocess.check_output(["gh", "api", endpoint], env=gh_env())

def gh_api_raw(endpoint):
    return subprocess.check_output(
        ["gh", "api", "-H", "Accept: application/vnd.github.raw", endpoint],
        env=gh_env(),
    )

def put_private_file(path, data, message):
    if len(data) > MAX_ANALYSIS_FILE:
        raise ValueError(f"Private output exceeds size limit: {path}")
    encoded = base64.b64encode(data).decode("ascii")
    endpoint = f"repos/{PRIVATE_REPO}/contents/{quote(path, safe='/')}"
    body = {"message": message, "content": encoded, "branch": "main"}
    try:
        existing = json.loads(gh_api(endpoint + "?ref=main"))
        if existing.get("sha"):
            body["sha"] = existing["sha"]
    except subprocess.CalledProcessError:
        pass
    subprocess.run(
        ["gh", "api", "--method", "PUT", endpoint, "--input", "-"],
        input=json.dumps(body).encode("utf-8"),
        env=gh_env(),
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
    )

def scrub(value):
    text = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False, indent=2)
    patterns = [
        (re.compile(r"(?i)gh[pousr]_[A-Za-z0-9_]{20,}"), "[REDACTED_GITHUB_TOKEN]"),
        (re.compile(r"(?i)Bearer\s+[A-Za-z0-9._~+/-]+=*"), "Bearer [REDACTED]"),
        (re.compile(r'(?i)("(?:password|passwd|pin|otp|token|authorization|cookie|session)"\s*:\s*")[^"]*(")'), r"\1[REDACTED]\2"),
        (re.compile(r"(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}"), "[REDACTED_EMAIL]"),
    ]
    for pattern, replacement in patterns:
        text = pattern.sub(replacement, text)
    return text

def run_hermes(bundle_path, report_stem):
    status = []
    output_dir = WORK / (report_stem + "-hermes")
    output_dir.mkdir(parents=True, exist_ok=True)
    venv = WORK / (report_stem + "-hermes-venv")
    create_venv = subprocess.run(
        ["python3", "-m", "venv", str(venv)],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
        timeout=60,
    )
    install = create_venv
    if create_venv.returncode == 0:
        install = subprocess.run(
            [str(venv / "bin" / "pip"), "install", "--quiet", "hermes-dec==0.1.8"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
            timeout=180,
        )
    if install.returncode != 0:
        status.append("hermes_install=failed")
        detail = (install.stderr or b"").decode("utf-8", errors="replace")[-2000:]
        put_private_file(
            f"reports/analysis/{report_stem}-hermes-status.txt",
            ("hermes-dec 0.1.8 installation failed\n" + detail).encode(),
            f"analysis: Hermes installation status for {report_stem}",
        )
        return status

    env = os.environ.copy()
    env["PATH"] = str(venv / "bin") + os.pathsep + env.get("PATH", "")
    commands = [
        ("hbc-file-parser", [str(bundle_path)], output_dir / "header.txt", True),
        ("hbc-disassembler", [str(bundle_path), str(output_dir / "bundle.hasm")], output_dir / "disassembler.stdout.txt", False),
        ("hbc-decompiler", [str(bundle_path), str(output_dir / "bundle.js")], output_dir / "decompiler.stdout.txt", False),
    ]
    for command, args, output_path, capture_stdout in commands:
        executable = shutil.which(command, path=env["PATH"])
        if not executable:
            status.append(f"{command}=not_found")
            continue
        try:
            with output_path.open("wb") as stdout_file:
                result = subprocess.run(
                    [executable] + args,
                    stdout=stdout_file if capture_stdout else subprocess.DEVNULL,
                    stderr=subprocess.PIPE,
                    env=env,
                    timeout=240,
                )
            status.append(f"{command}=exit_{result.returncode}")
            if result.returncode == 0 and output_path.exists() and output_path.stat().st_size:
                data = output_path.read_bytes()
                if len(data) <= MAX_ANALYSIS_FILE:
                    suffix = output_path.suffix or ".txt"
                    private_name = f"reports/analysis/{report_stem}-hermes-{command}{suffix}"
                    put_private_file(private_name, data, f"analysis: Hermes {command} output for {report_stem}")
            if result.stderr:
                err = result.stderr.decode("utf-8", errors="replace")[-4000:]
                put_private_file(
                    f"reports/analysis/{report_stem}-{command}-stderr.txt",
                    err.encode(),
                    f"analysis: Hermes {command} diagnostics for {report_stem}",
                )
        except subprocess.TimeoutExpired:
            status.append(f"{command}=timeout")
    return status

listing = json.loads(gh_api(f"repos/{PRIVATE_REPO}/contents/reports?ref=main"))
candidates = sorted(
    item["name"] for item in listing
    if item.get("type") == "file"
    and item["name"].endswith(".zip")
    and not item["name"].endswith("-encrypted.zip")
)
if not candidates:
    raise SystemExit("No unencrypted diagnostic ZIP found in private reports/.")
requested = [name.strip() for name in os.environ.get("DADA_REPORT_NAMES", "").split(",") if name.strip()]
archive_names = requested if requested else candidates[-2:]
if not archive_names:
    raise SystemExit("No report selected.")

for archive_name in archive_names:
    endpoint = f"repos/{PRIVATE_REPO}/contents/reports/{quote(archive_name, safe='')}?ref=main"
    archive_bytes = gh_api_raw(endpoint)
    if len(archive_bytes) > MAX_ARCHIVE:
        raise SystemExit(f"Report archive exceeds the {MAX_ARCHIVE // (1024 * 1024)} MiB safety limit.")
    archive_path = WORK / archive_name
    archive_path.write_bytes(archive_bytes)
    stem = pathlib.Path(archive_name).stem

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

        evidence_records = []
        bundle_local = None
        evidence_total = 0
        evidence_members = sorted(name for name in names if name.startswith("evidence/") and not name.endswith("/"))
        for member in evidence_members:
            relative = pathlib.PurePosixPath(member[len("evidence/"):])
            if not relative.parts or any(part in ("", ".", "..") for part in relative.parts):
                continue
            info = zf.getinfo(member)
            if info.file_size <= 0 or info.file_size > MAX_MEMBER:
                evidence_records.append(f"skipped={member};bytes={info.file_size};reason=member_limit")
                continue
            evidence_total += info.file_size
            if evidence_total > MAX_EVIDENCE_TOTAL:
                raise SystemExit("Evidence exceeds the 32 MiB total safety limit.")
            data = zf.read(member)
            digest = hashlib.sha256(data).hexdigest()
            private_path = f"reports/evidence/{stem}/" + relative.as_posix()
            put_private_file(private_path, data, f"evidence: preserve {stem} {relative.name}")
            evidence_records.append(f"stored={private_path};bytes={len(data)};sha256={digest}")
            if relative.name == "index.android.bundle":
                bundle_local = WORK / (stem + "-index.android.bundle")
                bundle_local.write_bytes(data)

    events = []
    for line in payload.get("events.jsonl", "").splitlines():
        if not line.strip():
            continue
        try:
            events.append(json.loads(line))
        except json.JSONDecodeError:
            events.append({"parse_error": "Invalid JSONL line", "line_preview": scrub(line[:500])})

    hermes_status = []
    if bundle_local and bundle_local.exists():
        hermes_status = run_hermes(bundle_local, stem)

    out = WORK / (stem + ".md")
    parts = [
        f"# Parsed diagnostic report: {archive_name}", "",
        f"- Archive size: {len(archive_bytes)} bytes",
        f"- ZIP members: {', '.join(sorted(names))}",
        f"- Events parsed: {len(events)}",
        f"- Raw evidence files preserved privately: {len(evidence_records)}",
        "- ZIP password: none",
        "- Content is best-effort redacted; review as potentially sensitive.", "",
        "## Device summary", "", "~~~text",
        scrub(payload.get("device-summary.txt", "(missing)")), "~~~", "",
        "## Private raw evidence", "",
        *[f"- {scrub(record)}" for record in evidence_records],
        "",
        "## Hermes analysis status", "",
        *([f"- {item}" for item in hermes_status] if hermes_status else ["- No Hermes bundle found in this report."]),
        "",
        "## Events (JSON)", "", "~~~json", scrub(events), "~~~", "",
    ]
    out.write_text("\n".join(parts), encoding="utf-8")
    print(f"Validated {archive_name}: {len(events)} events; {len(evidence_records)} raw evidence files preserved privately; Hermes statuses={','.join(hermes_status) or 'none'}.")

    put_private_file(
        f"reports/parsed/{out.name}",
        out.read_bytes(),
        f"diagnostics: parse {archive_name}",
    )

# Raw APK evidence and all decompilation outputs are written only to the private repository.
