#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
BIN_DIR="$ROOT_DIR/bin"
LIB_DIR="$ROOT_DIR/lib"

SWT_JAR="$LIB_DIR/org.eclipse.swt.gtk.linux.x86_64-3.127.0.jar"
SQLITE_JAR="$LIB_DIR/sqlite-jdbc-3.49.1.0.jar"

if [[ ! -f "$SWT_JAR" ]]; then
  echo "No se encontró SWT jar: $SWT_JAR" >&2
  exit 1
fi

if [[ ! -f "$SQLITE_JAR" ]]; then
  echo "No se encontró sqlite-jdbc jar: $SQLITE_JAR" >&2
  exit 1
fi

rm -rf "$BIN_DIR"
mkdir -p "$BIN_DIR"

CLASSPATH="$SWT_JAR:$SQLITE_JAR"
NATIVE_DIR="$LIB_DIR/native/linux-x86_64"

javac --release 25 -cp "$CLASSPATH" -d "$BIN_DIR" $(find "$ROOT_DIR/src" -name '*.java' | sort)
java \
  --enable-native-access=ALL-UNNAMED \
  -Djava.library.path="$NATIVE_DIR" \
  -cp "$BIN_DIR:$CLASSPATH" \
  com.whq.app.WhqCardRendererApp
