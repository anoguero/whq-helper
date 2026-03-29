#!/usr/bin/env bash

set -euo pipefail

TYPE="${1:-app-image}"

if [[ "$TYPE" != "app-image" && "$TYPE" != "dmg" && "$TYPE" != "pkg" ]]; then
  echo "Uso: ./scripts/package-macos.sh [app-image|dmg|pkg]" >&2
  exit 1
fi

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
POM_PATH="$PROJECT_ROOT/pom.xml"

ARTIFACT_ID="$(sed -n 's:.*<artifactId>\(.*\)</artifactId>.*:\1:p' "$POM_PATH" | head -n 1)"
VERSION="$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' "$POM_PATH" | head -n 1)"
MAIN_JAR="$ARTIFACT_ID-$VERSION.jar"
MACOS_INPUT_DIR="$PROJECT_ROOT/target/macos-input"
OUTPUT_DIR="$PROJECT_ROOT/target/macos-package"
SOURCE_ICON="$PROJECT_ROOT/resources/logo.png"
ICONSET_DIR="$PROJECT_ROOT/target/macos-icon/whq-helper.iconset"
MACOS_ICON="$PROJECT_ROOT/target/macos-icon/whq-helper.icns"

if [[ ! -f "$SOURCE_ICON" ]]; then
  echo "Falta el icono base PNG de la aplicación: $SOURCE_ICON" >&2
  exit 1
fi

if [[ "${SWT_PLATFORM_ID:-}" != "" ]]; then
  SWT_PLATFORM_ID_VALUE="$SWT_PLATFORM_ID"
else
  case "$(uname -m)" in
    x86_64) SWT_PLATFORM_ID_VALUE="cocoa.macosx.x86_64" ;;
    arm64|aarch64) SWT_PLATFORM_ID_VALUE="cocoa.macosx.aarch64" ;;
    *)
      echo "Arquitectura macOS no soportada: $(uname -m)" >&2
      exit 1
      ;;
  esac
fi

MACOS_SWT_JAR="$PROJECT_ROOT/lib/org.eclipse.swt.$SWT_PLATFORM_ID_VALUE-3.127.0.jar"
if [[ ! -f "$MACOS_SWT_JAR" ]]; then
  echo "Falta el JAR de SWT para macOS: $MACOS_SWT_JAR" >&2
  exit 1
fi

mkdir -p "$(dirname "$MACOS_ICON")"
rm -rf "$ICONSET_DIR"
mkdir -p "$ICONSET_DIR"

sips -z 16 16 "$SOURCE_ICON" --out "$ICONSET_DIR/icon_16x16.png" >/dev/null
sips -z 32 32 "$SOURCE_ICON" --out "$ICONSET_DIR/icon_16x16@2x.png" >/dev/null
sips -z 32 32 "$SOURCE_ICON" --out "$ICONSET_DIR/icon_32x32.png" >/dev/null
sips -z 64 64 "$SOURCE_ICON" --out "$ICONSET_DIR/icon_32x32@2x.png" >/dev/null
sips -z 128 128 "$SOURCE_ICON" --out "$ICONSET_DIR/icon_128x128.png" >/dev/null
sips -z 256 256 "$SOURCE_ICON" --out "$ICONSET_DIR/icon_128x128@2x.png" >/dev/null
sips -z 256 256 "$SOURCE_ICON" --out "$ICONSET_DIR/icon_256x256.png" >/dev/null
sips -z 512 512 "$SOURCE_ICON" --out "$ICONSET_DIR/icon_256x256@2x.png" >/dev/null
sips -z 512 512 "$SOURCE_ICON" --out "$ICONSET_DIR/icon_512x512.png" >/dev/null
cp "$SOURCE_ICON" "$ICONSET_DIR/icon_512x512@2x.png"

iconutil -c icns "$ICONSET_DIR" -o "$MACOS_ICON"

pushd "$PROJECT_ROOT" >/dev/null

mvn -q -Pmacos-dist -DskipTests -Dswt.platform.id="$SWT_PLATFORM_ID_VALUE" clean package

MAIN_JAR_PATH="$MACOS_INPUT_DIR/$MAIN_JAR"
if [[ ! -f "$MAIN_JAR_PATH" ]]; then
  echo "No se ha generado el bundle de entrada para macOS: $MAIN_JAR_PATH" >&2
  popd >/dev/null
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

jpackage \
  --type "$TYPE" \
  --input "$MACOS_INPUT_DIR" \
  --dest "$OUTPUT_DIR" \
  --name "WHQ Helper" \
  --main-jar "$MAIN_JAR" \
  --main-class "com.whq.app.WhqCardRendererApp" \
  --app-version "$VERSION" \
  --vendor "WHQ Helper" \
  --icon "$MACOS_ICON" \
  --java-options "--enable-native-access=ALL-UNNAMED" \
  --mac-package-identifier "com.whq.helper" \
  --mac-package-name "WHQ Helper"

popd >/dev/null
