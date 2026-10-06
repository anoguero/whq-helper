#!/usr/bin/env python3
"""Guard shared/ and the game content package.

The applications ship without game content: shared/ only holds what belongs to
the application (XSD schemas, UI translations, settings and the invented
sample/ content), and the game content is an external package (WHQ_CONTENT_HOME,
by default ../whq-content next to the repository).

Always checked: the schemas, that no game content creeps back into shared/,
the UI translations and duplicated tracked files. Checked on sample/ and, when
present, on the content package: manifest, tiles, table ids, room references and
validation of every XML against its schema (with xmllint, when installed).
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SHARED = ROOT / "shared"
SAMPLE = SHARED / "sample"
SCHEMAS = SHARED / "data" / "xml"
MANIFEST = "content-manifest.json"
DUNGEON_CARDS = Path("data/xml/dungeon/dungeon-cards.xml")
ROOM_REFERENCES = Path("data/xml/dungeon/room-references.xml")
ADVENTURES = Path("data/xml/adventures/original-objective-room-adventures.xml")
TABLES_DIR = Path("data/xml/tables")
# Lo unico que puede haber en shared/ fuera de sample/: lo propio de la aplicacion.
SHARED_ALLOWED = (
    re.compile(r"\.gitignore"),
    re.compile(r"settings\.cfg"),
    re.compile(r"data/i18n/ui-(es|en)\.xml"),
    re.compile(r"data/xml/[\w-]+/[\w-]+\.xsd"),
    re.compile(r"icons/whq-helper\.(png|ico)"),
    re.compile(r"sample/.+"),
)
TABLE_ID = re.compile(r"[a-z0-9]+(-[a-z0-9]+)*")
UI_TRANSLATIONS = {language: SHARED / "data" / "i18n" / f"ui-{language}.xml" for language in ("es", "en")}
JAVA_SOURCES = ROOT / "WhqHelperApp" / "src" / "main" / "java"
SPA_SOURCES = ROOT / "whq-helper-spa" / "src"
# Claves literales: I18n.t("clave"...) en Java, t(lang, 'clave') / tf(lang, 'clave', ...) en la SPA.
# Las claves construidas en tiempo de ejecucion ("prefijo." + x, `prefijo.${x}`) no se pueden comprobar.
JAVA_UI_KEY = re.compile(r'I18n\.t\(\s*"([^"]+)"')
SPA_UI_KEY = re.compile(r"\btf?\(\s*[\w.]+\s*,\s*'([^']+)'")
SPA_CSS = ROOT / "whq-helper-spa" / "src" / "styles.css"
FORBIDDEN_COPIES = (
    ROOT / "whq-helper-spa" / "public" / "data",
    ROOT / "whq-helper-spa" / "public" / "resources",
    ROOT / "WhqHelperApp" / "resources",
)


def relative(path: Path) -> str:
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)


def content_home() -> Path | None:
    """Paquete de contenido: WHQ_CONTENT_HOME o ../whq-content junto al repositorio; None si no hay."""
    configured = os.environ.get("WHQ_CONTENT_HOME", "").strip()
    path = Path(configured).expanduser().resolve() if configured else ROOT.parent / "whq-content"
    return path if path.is_dir() else None


def validate_schemas(errors: list[str]) -> None:
    schemas = sorted(SCHEMAS.rglob("*.xsd"))
    if not schemas:
        errors.append(f"No XSD schemas found under {relative(SCHEMAS)}")
    for schema in schemas:
        try:
            root = ET.parse(schema).getroot()
        except ET.ParseError as error:
            errors.append(f"Schema is not well-formed XML: {relative(schema)} ({error})")
            continue
        if not root.tag.endswith("}schema"):
            errors.append(f"Not an XML schema: {relative(schema)}")


def validate_no_content_in_shared(errors: list[str]) -> None:
    for path in sorted(SHARED.rglob("*")):
        if not path.is_file():
            continue
        name = path.relative_to(SHARED).as_posix()
        if name.startswith(("data/xml/", "data/i18n/")) and "userdefined-" in name:
            continue
        if not any(pattern.fullmatch(name) for pattern in SHARED_ALLOWED):
            errors.append(f"Game content must not live in shared/ (it belongs to the content package): {relative(path)}")


# Viaje y asentamiento usan el esquema de eventos (como XmlContentService.validateTravelFile).
SCHEMA_DIRECTORY_ALIASES = {"travel": "events", "settlement": "events"}


def schema_for(xml: Path, root: Path) -> Path | None:
    category = xml.parent.relative_to(root / "data" / "xml").as_posix()
    directory = SCHEMAS / SCHEMA_DIRECTORY_ALIASES.get(category, category)
    candidates = sorted(directory.glob("*.xsd"))
    if xml.name == ROOM_REFERENCES.name:
        candidates = [path for path in candidates if "room-references" in path.name]
    elif len(candidates) > 1:
        candidates = [path for path in candidates if "room-references" not in path.name]
    return candidates[0] if candidates else None


def validate_against_schemas(errors: list[str], root: Path) -> None:
    xmllint = shutil.which("xmllint")
    if xmllint is None:
        print(f"Warning: xmllint not installed; skipping schema validation of {relative(root)}.", file=sys.stderr)
        return
    for xml in sorted((root / "data" / "xml").rglob("*.xml")):
        if xml.name.startswith("userdefined-"):
            continue
        schema = schema_for(xml, root)
        if schema is None:
            errors.append(f"No schema for content file: {relative(xml)}")
            continue
        result = subprocess.run(
            [xmllint, "--noout", "--schema", str(schema), str(xml)],
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            detail = result.stderr.strip().splitlines()[0] if result.stderr.strip() else "invalid"
            errors.append(f"Does not validate against {relative(schema)}: {relative(xml)} ({detail})")


def validate_no_copies(errors: list[str]) -> None:
    for path in FORBIDDEN_COPIES:
        if path.exists():
            errors.append(f"Copy of shared content must not exist: {relative(path)}")


def validate_manifest(errors: list[str], root: Path) -> None:
    manifest_path = root / MANIFEST
    if not manifest_path.is_file():
        errors.append(f"Missing manifest: {relative(manifest_path)}")
        return
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    for raw_path in manifest.get("xmlFiles", []):
        path = str(raw_path)
        if not path.startswith("/"):
            errors.append(f"Manifest path must be absolute from the content root: {path}")
            continue
        if not (root / path.lstrip("/")).is_file():
            errors.append(f"Manifest of {relative(root)} references missing file: {path}")


def validate_tile_paths(errors: list[str], root: Path) -> None:
    cards = root / DUNGEON_CARDS
    if not cards.is_file():
        errors.append(f"Missing dungeon cards: {relative(cards)}")
        return
    for element in ET.parse(cards).iter("tileImagePath"):
        path = (element.text or "").strip()
        if path and not (root / path).is_file():
            errors.append(f"Dungeon card in {relative(root)} references missing tile: {path}")


def validate_room_references(errors: list[str], root: Path) -> None:
    # Opcional en un paquete: si lo trae, cada referencia apunta a una carta existente.
    references = root / ROOM_REFERENCES
    if not references.is_file() or not (root / DUNGEON_CARDS).is_file():
        return
    card_ids = {card.get("id") for card in ET.parse(root / DUNGEON_CARDS).iter("card")}
    seen: set[str] = set()
    for reference in ET.parse(references).iter("reference"):
        card_id = (reference.get("cardId") or "").strip()
        if card_id not in card_ids:
            errors.append(f"Room reference points to missing dungeon card: cardId={card_id}")
        if card_id in seen:
            errors.append(f"Duplicate room reference: cardId={card_id}")
        seen.add(card_id)


def validate_adventure_rooms(errors: list[str], root: Path) -> None:
    # Opcional en un paquete: si trae aventuras, cada sala objetivo apunta a una carta existente.
    adventures = root / ADVENTURES
    if not adventures.is_file() or not (root / DUNGEON_CARDS).is_file():
        return
    card_ids = {card.get("id") for card in ET.parse(root / DUNGEON_CARDS).iter("card")}
    for room in ET.parse(adventures).iter("objectiveRoom"):
        card_id = (room.get("cardId") or "").strip()
        if not card_id:
            errors.append(f"Objective room without cardId in adventures: {room.get('name')}")
        elif card_id not in card_ids:
            errors.append(f"Adventure objective room points to missing dungeon card: cardId={card_id} ({room.get('name')})")


def validate_table_ids(errors: list[str], root: Path) -> None:
    # El estado activo de cada tabla se guarda por id (table.<id>.active): debe existir y ser unico
    # en el paquete. El esquema lo deja opcional por los ficheros antiguos del usuario.
    seen: dict[str, str] = {}
    for path in sorted((root / TABLES_DIR).glob("*.xml")):
        for table in ET.parse(path).getroot().iter("table"):
            table_id = (table.get("id") or "").strip()
            name = table.get("name")
            if not table_id:
                errors.append(f"Table without id in {relative(path)}: {name}")
            elif not TABLE_ID.fullmatch(table_id):
                errors.append(f"Invalid table id in {relative(path)}: {table_id} ({name})")
            elif table_id in seen:
                errors.append(f"Duplicate table id: {table_id} ({seen[table_id]} and {relative(path)})")
            else:
                seen[table_id] = relative(path)


def ui_keys(path: Path) -> set[str]:
    return {(entry.get("key") or "").strip() for entry in ET.parse(path).getroot().iter("entry")}


def validate_ui_translations(errors: list[str]) -> None:
    missing = [path for path in UI_TRANSLATIONS.values() if not path.is_file()]
    for path in missing:
        errors.append(f"Missing UI translations: {relative(path)}")
    if missing:
        return

    keys = {language: ui_keys(path) for language, path in UI_TRANSLATIONS.items()}
    for key in sorted(keys["es"] ^ keys["en"]):
        errors.append(f"UI translation key not in both ui-es.xml and ui-en.xml: {key}")

    used: dict[str, set[str]] = {}
    for sources, pattern, suffix in ((JAVA_SOURCES, JAVA_UI_KEY, ".java"), (SPA_SOURCES, SPA_UI_KEY, ".ts")):
        for path in sources.rglob(f"*{suffix}"):
            if "test" in path.relative_to(sources).parts:
                continue
            for key in pattern.findall(path.read_text(encoding="utf-8")):
                if not key.endswith("."):
                    used.setdefault(key, set()).add(relative(path))
    for key in sorted(set(used) - keys["es"]):
        errors.append(f"UI translation key used in code but missing: {key} ({', '.join(sorted(used[key]))})")


def validate_css_font_urls(errors: list[str], content: Path | None) -> None:
    # Las fuentes son contenido: si faltan, la SPA usa las del sistema. Solo se avisa.
    if not SPA_CSS.is_file():
        errors.append(f"Missing SPA CSS: {relative(SPA_CSS)}")
        return
    if content is None:
        return
    css = SPA_CSS.read_text(encoding="utf-8")
    for url in re.findall(r"url\(['\"]?([^'\")]+)['\"]?\)", css):
        if url.startswith("/data/fonts/") and not (content / url.lstrip("/")).is_file():
            print(f"Warning: CSS references a font the content package does not have: {url}", file=sys.stderr)


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
    validate_schemas(errors)
    validate_no_content_in_shared(errors)
    validate_ui_translations(errors)
    validate_no_duplicates(errors)

    content = content_home()
    roots = [SAMPLE] + ([content] if content else [])
    for root in roots:
        validate_manifest(errors, root)
        validate_tile_paths(errors, root)
        validate_room_references(errors, root)
        validate_adventure_rooms(errors, root)
        validate_table_ids(errors, root)
        validate_against_schemas(errors, root)
    validate_css_font_urls(errors, content)
    where = f"content package {content}" if content else "no content package (only sample/)"
    print(f"Checked shared/ and {where}.")

    if errors:
        print("Shared data validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print("Shared data validation passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
