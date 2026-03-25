#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
NATIVE_DIR="$ROOT_DIR/lib/native/linux-x86_64"
SWT_JAR="$ROOT_DIR/lib/org.eclipse.swt.gtk.linux.x86_64-3.127.0.jar"

mvn -o -q clean compile

java \
  --enable-native-access=ALL-UNNAMED \
  -Djava.library.path="$NATIVE_DIR" \
  -cp "$ROOT_DIR/target/classes:$SWT_JAR" \
  com.whq.app.WhqCardRendererApp
