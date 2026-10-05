#!/usr/bin/env python3
from __future__ import annotations

import shutil
import sys
from pathlib import Path


def is_shared_file(path: Path) -> bool:
    name = path.name
    return (
        path.is_file()
        and not name.endswith(".bak")
        and not name.startswith("userdefined-")
        and path.suffix != ".md"
    )


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: sync_shared_data.py <java-data-dir> <spa-data-dir>", file=sys.stderr)
        return 2

    java_data = Path(sys.argv[1]).resolve()
    spa_data = Path(sys.argv[2]).resolve()
    if not java_data.is_dir():
        print(f"Java data directory does not exist: {java_data}", file=sys.stderr)
        return 1
    spa_data.mkdir(parents=True, exist_ok=True)

    copied = 0
    for source in sorted(java_data.rglob("*")):
        if not is_shared_file(source):
            continue
        relative = source.relative_to(java_data)
        target = spa_data / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        copied += 1

    print(f"Synced {copied} shared data files from {java_data} to {spa_data}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
