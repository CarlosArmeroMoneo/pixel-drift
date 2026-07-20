#!/usr/bin/env python3
"""Fast, dependency-free checks for public repository hygiene."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit


ROOT = Path(__file__).resolve().parents[1]
PUBLIC_CERTIFICATE = Path("security/PixelDrift-release-certificate.pem")
FORBIDDEN_SUFFIXES = {".apk", ".jks", ".keystore", ".p12", ".key"}
FORBIDDEN_NAME_PARTS = {"signing-credentials", "signing-password", "keystore-password"}
PRIVATE_KEY_MARKERS = tuple(
    b"-----BEGIN " + (key_type + b" " if key_type else b"") + b"PRIVATE KEY-----"
    for key_type in (b"", b"ENCRYPTED", b"RSA", b"EC")
)
MARKDOWN_LINK = re.compile(r"!?\[[^\]]*\]\(([^)]+)\)")


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


def main() -> int:
    files = repository_files()
    failures = check_sensitive_files(files) + check_markdown_links(files)
    if failures:
        for failure in failures:
            print(f"ERROR: {failure}", file=sys.stderr)
        return 1
    print(f"Repository hygiene passed for {len(files)} tracked files.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
