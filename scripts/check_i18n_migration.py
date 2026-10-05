#!/usr/bin/env python3
"""Comprueba que shared/data/i18n/ui-{es,en}.xml contiene, sin perdidas, los textos de interfaz que
vivian en I18n.java e i18n.ts antes de la Fase 10.B.

Reconstruye los mapas originales desde git (compila el I18n.java antiguo y ejecuta el i18n.ts
antiguo) y compara clave a clave, en ambos idiomas, aplicando solo las transformaciones decididas:
placeholders de Java a {nombre}, sufijos {error}/{path} en lugar de espacios finales, y la
resolucion de las divergencias entre apps.

Requiere git, un JDK (javac/java) y Node 22+ (--experimental-strip-types).
Uso: python3 scripts/check_i18n_migration.py [revision]   (por defecto 7eddee3)
"""
from __future__ import annotations

import json
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REVISION = "7eddee3"
JAVA_I18N = "WhqHelperApp/src/com/whq/app/i18n"
SPA_SRC = "whq-helper-spa/src"

# Claves de Java con String.format: nombre de cada placeholder, en orden de aparicion.
JAVA_PLACEHOLDERS = {
    "dashboard.overview.settings.body": ["mode", "eventProbability", "treasureProbability", "tables", "language"],
    "dashboard.stats.eventProbability": ["value"],
    "dashboard.stats.language": ["language"],
    "dashboard.stats.mode": ["mode"],
    "dashboard.stats.party": ["party"],
    "dashboard.stats.treasureProbability": ["value"],
    "dialog.adventureSimulator.info.emptyPile": ["pile"],
    "dialog.adventureSimulator.info.notEnoughToSplit": ["pile"],
    "dialog.adventureSimulator.multiPileStatus": ["piles", "cards"],
    "dialog.adventureSimulator.pileLabel": ["pile", "cards"],
    "dialog.adventureSimulator.selectedCard": ["name", "pile"],
    "dialog.adventureSimulator.singlePileStatus": ["count"],
    "dialog.tileConfig.bulkUpdated": ["count", "environment"],
    "simulator.addCardsDone": ["count", "pile"],
    "simulator.addCardsUnavailable": ["max"],
    "simulator.objectiveMonstersDifficulty": ["difficulty"],
}

# Claves de Java que terminaban en espacio y se concatenaban con un valor: ahora llevan placeholder.
JAVA_SUFFIXES = {
    "dialog.newDungeon.error.loadAdventures": "error",
    "dialog.newDungeon.error.loadEnvironments": "error",
    "dialog.newDungeon.error.loadObjectiveRooms": "error",
    "dialog.newSettlement.error.loadLocations": "error",
    "dialog.party.error.loadWarriors": "error",
    "dialog.warriorCounters.error.load": "error",
    "dialog.warriorCounters.error.missingImage": "path",
}

# Divergencias entre apps en claves comunes: que app manda.
WINNER = {
    "card.treasure": "java",          # bug de la SPA: ES decia TREASURE CARD
    "card.noTreasure": "java",        # bug de la SPA: ES en ingles
    "deck.window.subtitle": "java",
    "button.finishAdventure": "spa",
}


def run(command: list[str], cwd: Path | None = None) -> str:
    return subprocess.run(command, cwd=cwd, check=True, capture_output=True, text=True).stdout


def git_show(revision: str, path: str) -> str:
    return run(["git", "show", f"{revision}:{path}"], cwd=ROOT)


def java_maps(revision: str, work: Path) -> dict[str, dict[str, str]]:
    source_dir = work / "java" / "com" / "whq" / "app" / "i18n"
    source_dir.mkdir(parents=True)
    for name in ("I18n.java", "Language.java"):
        (source_dir / name).write_text(git_show(revision, f"{JAVA_I18N}/{name}"), encoding="utf-8")
    classes = work / "classes"
    run(["javac", "-d", str(classes), str(source_dir / "I18n.java"), str(source_dir / "Language.java")])
    dumper = work / "Dump.java"
    dumper.write_text(
        """
import java.lang.reflect.Field;
import java.util.*;
public class Dump {
  static String q(String s) {
    StringBuilder b = new StringBuilder("\\"");
    for (char c : s.toCharArray()) {
      if (c == '"' || c == '\\\\') b.append('\\\\').append(c);
      else if (c < 0x20) b.append(String.format("\\\\u%04x", (int) c));
      else b.append(c);
    }
    return b.append('"').toString();
  }
  @SuppressWarnings("unchecked")
  public static void main(String[] a) throws Exception {
    Class<?> c = Class.forName("com.whq.app.i18n.I18n");
    StringBuilder out = new StringBuilder("{");
    for (String lang : List.of("ES", "EN")) {
      Field f = c.getDeclaredField(lang);
      f.setAccessible(true);
      Map<String, String> m = (Map<String, String>) f.get(null);
      out.append(lang.equals("ES") ? "" : ",").append(q(lang)).append(":{");
      boolean first = true;
      for (var e : m.entrySet()) {
        if (!first) out.append(",");
        first = false;
        out.append(q(e.getKey())).append(":").append(q(e.getValue()));
      }
      out.append("}");
    }
    System.out.print(out.append("}"));
  }
}
""",
        encoding="utf-8",
    )
    return json.loads(run(["java", "-cp", str(classes), str(dumper)]))


def spa_maps(revision: str, work: Path) -> dict[str, dict[str, str]]:
    spa = work / "spa"
    spa.mkdir()
    source = git_show(revision, f"{SPA_SRC}/i18n.ts")
    source = source.replace("from './types'", "from './types.ts'")
    source = re.sub(r"^const (ES|EN):", r"export const \1:", source, flags=re.M)
    (spa / "i18n.ts").write_text(source, encoding="utf-8")
    (spa / "types.ts").write_text(git_show(revision, f"{SPA_SRC}/types.ts"), encoding="utf-8")
    script = f"import('{(spa / 'i18n.ts').as_posix()}').then(m => process.stdout.write(JSON.stringify({{ES: m.ES, EN: m.EN}})))"
    return json.loads(run(["node", "--experimental-strip-types", "--no-warnings", "-e", script]))


def convert_java(key: str, value: str) -> str:
    if key in JAVA_PLACEHOLDERS:
        names = iter(JAVA_PLACEHOLDERS[key])
        value = re.sub(r"%%|%[sd]", lambda m: "%" if m.group(0) == "%%" else "{" + next(names) + "}", value)
    if key in JAVA_SUFFIXES:
        value = value + "{" + JAVA_SUFFIXES[key] + "}"
    return value


def load_ui(language: str) -> dict[str, str]:
    path = ROOT / "shared" / "data" / "i18n" / f"ui-{language.lower()}.xml"
    return {
        entry.get("key", "").strip(): (entry.text or "").strip()
        for entry in ET.parse(path).getroot().iter("entry")
    }


def main() -> int:
    revision = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_REVISION
    for tool in ("git", "javac", "java", "node"):
        if shutil.which(tool) is None:
            print(f"Falta {tool} en el PATH.", file=sys.stderr)
            return 2

    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        java = java_maps(revision, work)
        spa = spa_maps(revision, work)

    errors: list[str] = []
    checked = 0
    for language in ("ES", "EN"):
        ui = load_ui(language)
        expected: dict[str, str] = {}
        for key, value in spa[language].items():
            expected[key] = value
        for key, value in java[language].items():
            converted = convert_java(key, value)
            if key in spa[language] and converted != spa[language][key]:
                winner = WINNER.get(key)
                if winner is None:
                    errors.append(f"{language} {key}: divergencia sin resolver")
                    continue
                if winner == "spa":
                    continue
            expected[key] = converted

        for key, value in expected.items():
            checked += 1
            if key not in ui:
                errors.append(f"{language} {key}: falta en ui-{language.lower()}.xml")
            elif ui[key] != value:
                errors.append(f"{language} {key}: {ui[key]!r} != {value!r}")

    if errors:
        print("La migracion de i18n pierde o altera textos:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(
        f"OK: {checked} textos comprobados (Java {len(java['ES'])} + SPA {len(spa['ES'])} claves por idioma, "
        f"claves actuales {len(load_ui('ES'))})."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
