#!/usr/bin/env bash

set -euo pipefail

TYPE="${1:-appimage}"

if [[ "$TYPE" != "app-image" && "$TYPE" != "appimage" ]]; then
  echo "Uso: ./scripts/package-linux.sh [app-image|appimage]" >&2
  exit 1
fi

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
POM_PATH="$PROJECT_ROOT/pom.xml"

ARTIFACT_ID="$(sed -n 's:.*<artifactId>\(.*\)</artifactId>.*:\1:p' "$POM_PATH" | head -n 1)"
VERSION="$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' "$POM_PATH" | head -n 1)"
MAIN_JAR="$ARTIFACT_ID-$VERSION.jar"
LINUX_INPUT_DIR="$PROJECT_ROOT/target/linux-input"
OUTPUT_DIR="$PROJECT_ROOT/target/linux-package"
LINUX_SWT_JAR="$PROJECT_ROOT/lib/org.eclipse.swt.gtk.linux.x86_64-3.127.0.jar"
LINUX_ICON="$PROJECT_ROOT/resources/logo.png"

if [[ ! -f "$LINUX_SWT_JAR" ]]; then
  echo "Falta el JAR de SWT para Linux: $LINUX_SWT_JAR" >&2
  exit 1
fi

if [[ ! -f "$LINUX_ICON" ]]; then
  echo "Falta el icono Linux de la aplicación: $LINUX_ICON" >&2
  exit 1
fi

pushd "$PROJECT_ROOT" >/dev/null

mvn -q -Plinux-dist -DskipTests clean package

MAIN_JAR_PATH="$LINUX_INPUT_DIR/$MAIN_JAR"
if [[ ! -f "$MAIN_JAR_PATH" ]]; then
  echo "No se ha generado el bundle de entrada para Linux: $MAIN_JAR_PATH" >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

jpackage \
  --type app-image \
  --input "$LINUX_INPUT_DIR" \
  --dest "$OUTPUT_DIR" \
  --name "WHQ Helper" \
  --main-jar "$MAIN_JAR" \
  --main-class "com.whq.app.WhqCardRendererApp" \
  --app-version "$VERSION" \
  --vendor "WHQ Helper" \
  --icon "$LINUX_ICON" \
  --java-options "--enable-native-access=ALL-UNNAMED"

if [[ "$TYPE" == "app-image" ]]; then
  popd >/dev/null
  exit 0
fi

APPIMAGETOOL_BIN="${APPIMAGETOOL:-appimagetool}"
if [[ ! -x "$APPIMAGETOOL_BIN" ]]; then
  echo "No se encuentra appimagetool. Define APPIMAGETOOL con su ruta o instálalo." >&2
  popd >/dev/null
  exit 1
fi

APP_IMAGE_DIR="$OUTPUT_DIR/WHQ Helper"
if [[ ! -d "$APP_IMAGE_DIR" ]]; then
  echo "jpackage no ha generado la app-image Linux esperada: $APP_IMAGE_DIR" >&2
  popd >/dev/null
  exit 1
fi

APPDIR="$OUTPUT_DIR/WHQHelper.AppDir"
rm -rf "$APPDIR"
mkdir -p "$APPDIR/usr"
cp -a "$APP_IMAGE_DIR/." "$APPDIR/usr/"

cat > "$APPDIR/AppRun" <<'EOF'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/usr/bin/WHQ Helper" "$@"
EOF
chmod +x "$APPDIR/AppRun"

cp "$LINUX_ICON" "$APPDIR/whq-helper.png"
ln -sf "whq-helper.png" "$APPDIR/.DirIcon"

cat > "$APPDIR/whq-helper.desktop" <<'EOF'
[Desktop Entry]
Type=Application
Name=WHQ Helper
Exec=WHQ Helper
Icon=whq-helper
Categories=Game;
Terminal=false
EOF

ARCH="$(uname -m)"
APPIMAGE_OUTPUT="$OUTPUT_DIR/WHQ-Helper-$VERSION-$ARCH.AppImage"
rm -f "$APPIMAGE_OUTPUT"

APPIMAGE_EXTRACT_AND_RUN=1 "$APPIMAGETOOL_BIN" "$APPDIR" "$APPIMAGE_OUTPUT"

popd >/dev/null
