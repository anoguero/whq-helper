#!/usr/bin/env python3
"""Guard shared/ as the single source of game content.

Fails when a copy of the shared content reappears, when the manifest or the
dungeon cards reference missing files, or when two tracked files in the repo
have identical content.
"""
from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SHARED = ROOT / "shared"
MANIFEST = SHARED / "content-manifest.json"
DUNGEON_CARDS = SHARED / "data" / "xml" / "dungeon" / "dungeon-cards.xml"
SPA_CSS = ROOT / "whq-helper-spa" / "src" / "styles.css"
FORBIDDEN_COPIES = (
    ROOT / "whq-helper-spa" / "public" / "data",
    ROOT / "whq-helper-spa" / "public" / "resources",
    ROOT / "WhqHelperApp" / "resources",
)


def relative(path: Path) -> str:
    return str(path.relative_to(ROOT))


def validate_no_copies(errors: list[str]) -> None:
    for path in FORBIDDEN_COPIES:
        if path.exists():
            errors.append(f"Copy of shared content must not exist: {relative(path)}")


def validate_manifest(errors: list[str]) -> None:
    if not MANIFEST.is_file():
        errors.append(f"Missing manifest: {relative(MANIFEST)}")
        return
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    for raw_path in manifest.get("xmlFiles", []):
        path = str(raw_path)
        if not path.startswith("/"):
            errors.append(f"Manifest path must be absolute from shared root: {path}")
            continue
        if not (SHARED / path.lstrip("/")).is_file():
            errors.append(f"Manifest references missing file: {path}")


def validate_tile_paths(errors: list[str]) -> None:
    if not DUNGEON_CARDS.is_file():
        errors.append(f"Missing dungeon cards: {relative(DUNGEON_CARDS)}")
        return
    for element in ET.parse(DUNGEON_CARDS).iter("tileImagePath"):
        path = (element.text or "").strip()
        if path and not (SHARED / path).is_file():
            errors.append(f"Dungeon card references missing tile: {path}")


def validate_css_font_urls(errors: list[str]) -> None:
    if not SPA_CSS.is_file():
        errors.append(f"Missing SPA CSS: {relative(SPA_CSS)}")
        return
    css = SPA_CSS.read_text(encoding="utf-8")
    for url in re.findall(r"url\(['\"]?([^'\")]+)['\"]?\)", css):
        if not url.startswith("/data/fonts/"):
            continue
        if not (SHARED / url.lstrip("/")).is_file():
            errors.append(f"CSS references missing font: {url}")


def tracked_files() -> list[Path] | None:
    """Ficheros versionados, o None si no hay repositorio git (p. ej. desde un tarball)."""
    try:
        output = subprocess.run(
            ["git", "ls-files", "-z"],
            cwd=ROOT,
            check=True,
            capture_output=True,
        ).stdout.decode("utf-8")
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None
    return [ROOT / name for name in output.split("\0") if name]


def validate_no_duplicates(errors: list[str]) -> None:
    files = tracked_files()
    if files is None:
        print(
            "Warning: no git repository found; skipping duplicate content check.",
            file=sys.stderr,
        )
        return

    by_digest: dict[str, list[Path]] = defaultdict(list)
    for path in files:
        if path.is_file() and path.stat().st_size > 0:
            by_digest[hashlib.sha256(path.read_bytes()).hexdigest()].append(path)
    for paths in by_digest.values():
        if len(paths) > 1:
            names = ", ".join(sorted(relative(path) for path in paths))
            errors.append(f"Duplicate content: {names}")


def main() -> int:
    errors: list[str] = []
    validate_no_copies(errors)
    validate_manifest(errors)
    validate_tile_paths(errors)
    validate_css_font_urls(errors)
    validate_no_duplicates(errors)

    if errors:
        print("Shared data validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print("Shared data validation passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
