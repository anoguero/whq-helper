#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
JAVA_DATA = ROOT / "WhqHelperApp" / "data"
SPA_PUBLIC = ROOT / "whq-helper-spa" / "public"
SPA_DATA = SPA_PUBLIC / "data"
MANIFEST = SPA_PUBLIC / "content-manifest.json"
SPA_CSS = ROOT / "whq-helper-spa" / "src" / "styles.css"


def is_shared_file(path: Path) -> bool:
    name = path.name
    return (
        path.is_file()
        and not name.endswith(".bak")
        and not name.startswith("userdefined-")
        and path.suffix != ".md"
    )


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def collect(root: Path) -> dict[str, Path]:
    return {
        str(path.relative_to(root)): path
        for path in root.rglob("*")
        if is_shared_file(path)
    }


def validate_manifest(errors: list[str]) -> None:
    if not MANIFEST.is_file():
        errors.append(f"Missing manifest: {MANIFEST.relative_to(ROOT)}")
        return
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    for raw_path in manifest.get("xmlFiles", []):
        path = str(raw_path)
        if not path.startswith("/"):
            errors.append(f"Manifest path must be absolute from public root: {path}")
            continue
        resolved = SPA_PUBLIC / path.lstrip("/")
        if not resolved.is_file():
            errors.append(f"Manifest references missing file: {path}")


def validate_css_font_urls(errors: list[str]) -> None:
    if not SPA_CSS.is_file():
        errors.append(f"Missing SPA CSS: {SPA_CSS.relative_to(ROOT)}")
        return
    css = SPA_CSS.read_text(encoding="utf-8")
    for url in re.findall(r"url\\(['\"]?([^'\")]+)['\"]?\\)", css):
        if not url.startswith("/data/fonts/"):
            continue
        resolved = SPA_PUBLIC / url.lstrip("/")
        if not resolved.is_file():
            errors.append(f"CSS references missing font: {url}")


def validate_shared_data(errors: list[str]) -> None:
    java_files = collect(JAVA_DATA)
    spa_files = collect(SPA_DATA)
    only_java = sorted(set(java_files) - set(spa_files))
    only_spa = sorted(set(spa_files) - set(java_files))
    different = sorted(
        relative
        for relative in set(java_files) & set(spa_files)
        if digest(java_files[relative]) != digest(spa_files[relative])
    )

    for relative in only_java:
        errors.append(f"Shared data exists only in Java: {relative}")
    for relative in only_spa:
        errors.append(f"Shared data exists only in SPA: {relative}")
    for relative in different:
        errors.append(f"Shared data differs between Java and SPA: {relative}")


def main() -> int:
    errors: list[str] = []
    validate_manifest(errors)
    validate_css_font_urls(errors)
    validate_shared_data(errors)

    if errors:
        print("Shared data validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print("Shared data validation passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
