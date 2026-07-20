#!/usr/bin/env python3
"""Fast, dependency-free checks for public repository and Google Play hygiene."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit


ROOT = Path(__file__).resolve().parents[1]
PUBLIC_CERTIFICATE = Path("security/PixelDrift-release-certificate.pem")
FORBIDDEN_SUFFIXES = {".apk", ".aab", ".jks", ".keystore", ".p12", ".key"}
FORBIDDEN_NAME_PARTS = {"signing-credentials", "signing-password", "keystore-password"}
PRIVATE_KEY_MARKERS = tuple(
    b"-----BEGIN " + (key_type + b" " if key_type else b"") + b"PRIVATE KEY-----"
    for key_type in (b"", b"ENCRYPTED", b"RSA", b"EC")
)
MARKDOWN_LINK = re.compile(r"!?\[[^\]]*\]\(([^)]+)\)")
PLAY_METADATA_ROOT = Path("fastlane/metadata/android")
PLAY_REQUIRED_LOCALES = {"en-US", "es-ES"}
PLAY_FIELD_LIMITS = {
    "title.txt": 30,
    "short_description.txt": 80,
    "full_description.txt": 4_000,
}
PRIVACY_URL = "https://github.com/CarlosArmeroMoneo/pixel-drift/blob/main/PRIVACY.md"


def repository_files() -> list[Path]:
    completed = subprocess.run(
        ["git", "ls-files", "-z"],
        cwd=ROOT,
        check=True,
        stdout=subprocess.PIPE,
    )
    return [Path(item.decode("utf-8")) for item in completed.stdout.split(b"\0") if item]


def check_sensitive_files(files: list[Path]) -> list[str]:
    failures: list[str] = []
    for relative in files:
        lowered = relative.name.lower()
        if relative.suffix.lower() in FORBIDDEN_SUFFIXES:
            failures.append(f"forbidden release or secret file is tracked: {relative}")
        if any(part in lowered for part in FORBIDDEN_NAME_PARTS):
            failures.append(f"credential-like filename is tracked: {relative}")
        if relative.suffix.lower() == ".pem" and relative != PUBLIC_CERTIFICATE:
            failures.append(f"only the pinned public release certificate may be tracked: {relative}")

        absolute = ROOT / relative
        if absolute.is_file() and absolute.stat().st_size <= 1_048_576:
            data = absolute.read_bytes()
            if any(marker in data for marker in PRIVATE_KEY_MARKERS):
                failures.append(f"private-key material is tracked: {relative}")
    return failures


def local_link_target(markdown: Path, raw_target: str) -> Path | None:
    target = raw_target.strip().split(maxsplit=1)[0].strip("<>")
    parsed = urlsplit(target)
    if parsed.scheme or target.startswith("#"):
        return None
    path = unquote(parsed.path)
    if not path:
        return None
    return (markdown.parent / path).resolve()


def check_markdown_links(files: list[Path]) -> list[str]:
    failures: list[str] = []
    for relative in files:
        if relative.suffix.lower() != ".md":
            continue
        markdown = ROOT / relative
        for raw_target in MARKDOWN_LINK.findall(markdown.read_text(encoding="utf-8")):
            target = local_link_target(markdown, raw_target)
            if target is None:
                continue
            try:
                target.relative_to(ROOT)
            except ValueError:
                failures.append(f"link escapes repository in {relative}: {raw_target}")
                continue
            if not target.exists():
                failures.append(f"broken local link in {relative}: {raw_target}")
    return failures


def version_code() -> int | None:
    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    match = re.search(r"^\s*versionCode\s*=\s*(\d+)\s*$", gradle, flags=re.MULTILINE)
    return int(match.group(1)) if match else None


def check_play_metadata() -> list[str]:
    failures: list[str] = []
    root = ROOT / PLAY_METADATA_ROOT
    if not root.is_dir():
        return [f"missing Google Play metadata directory: {PLAY_METADATA_ROOT}"]

    locales = {path.name for path in root.iterdir() if path.is_dir()}
    for locale in sorted(PLAY_REQUIRED_LOCALES - locales):
        failures.append(f"missing required Google Play locale: {locale}")

    current_version_code = version_code()
    if current_version_code is None:
        failures.append("could not parse versionCode from app/build.gradle.kts")
        return failures

    for locale in sorted(locales):
        locale_root = root / locale
        for filename, limit in PLAY_FIELD_LIMITS.items():
            path = locale_root / filename
            relative = path.relative_to(ROOT)
            if not path.is_file():
                failures.append(f"missing Google Play metadata field: {relative}")
                continue
            text = path.read_text(encoding="utf-8").strip()
            if not text:
                failures.append(f"empty Google Play metadata field: {relative}")
            elif len(text) > limit:
                failures.append(
                    f"Google Play metadata exceeds {limit} characters: {relative} ({len(text)})"
                )

        notes = locale_root / "changelogs" / f"{current_version_code}.txt"
        relative_notes = notes.relative_to(ROOT)
        if not notes.is_file():
            failures.append(
                f"missing release notes for versionCode {current_version_code}: {relative_notes}"
            )
        else:
            text = notes.read_text(encoding="utf-8").strip()
            if not text:
                failures.append(f"empty Google Play release notes: {relative_notes}")
            elif len(text) > 500:
                failures.append(
                    f"Google Play release notes exceed 500 characters: "
                    f"{relative_notes} ({len(text)})"
                )
    return failures


def check_play_policy_contract() -> list[str]:
    failures: list[str] = []
    manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    strings = (ROOT / "app/src/main/res/values/strings.xml").read_text(encoding="utf-8")
    layout = (ROOT / "app/src/main/res/layout/activity_main.xml").read_text(encoding="utf-8")

    if "android.permission.INTERNET" in manifest:
        failures.append("Data safety contract forbids android.permission.INTERNET")
    if PRIVACY_URL not in strings:
        failures.append("the public privacy policy URL is missing from app strings")
    if "@string/privacy_policy_link" not in layout:
        failures.append("the privacy policy is not exposed from the app's main UI")
    if not (ROOT / "PRIVACY.md").is_file():
        failures.append("PRIVACY.md is missing")
    return failures


def main() -> int:
    files = repository_files()
    failures = (
        check_sensitive_files(files)
        + check_markdown_links(files)
        + check_play_metadata()
        + check_play_policy_contract()
    )
    if failures:
        for failure in failures:
            print(f"ERROR: {failure}", file=sys.stderr)
        return 1
    print(f"Repository and Google Play hygiene passed for {len(files)} tracked files.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
