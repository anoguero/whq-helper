# WHQ Helper App (SWT)

Aplicación Java + SWT para renderizar cartas de mazmorra estilo **Warhammer Quest (1995)** a partir de datos cargados desde XML.

## Qué incluye

- UI SWT con dos paneles:
  - lista de habitaciones/cartas disponibles,
  - visor de renderizado de carta.
- Renderizado de carta sobre plantilla (`shared/resources/dungeon-card-template.png`):
  - nombre,
  - texto descriptivo,
  - texto de reglas,
  - imagen de tile,
  - banda inferior con tipo (`DUNGEON ROOM`, `OBJECTIVE ROOM`, `CORRIDOR`, `SPECIAL`).
- Repositorio XML de cartas de mazmorra (`shared/data/xml/dungeon/dungeon-cards.xml`) como fuente principal de mantenimiento.
- Nuevo campo de datos `environment` en cada carta (por defecto: `The Old World`).
- Dependencias de terceros mínimas:
  - SWT.

## Estructura

- `src/com/whq/app/WhqCardRendererApp.java`: entrada principal.
- `src/com/whq/app/ui/AppWindow.java`: ventana SWT.
- `src/com/whq/app/render/CardRenderer.java`: motor de render.
- `src/com/whq/app/storage/XmlDungeonCardStore.java`: acceso a cartas de mazmorra en XML.
- `src/com/whq/app/io/CardCsvService.java`: import/export CSV.
- `src/com/whq/app/model/*`: modelos de dominio.
- `src/com/whq/app/AppPaths.java`: resolución del app home, del shared home y del directorio escribible.
- `../shared/`: contenido base compartido con la SPA (XML, imágenes, fuentes, i18n). Ver el `README.md` de la raíz.
- `data/xml/**/userdefined-*.xml`: contenido creado por el usuario (no versionado).

## Contenido base y contenido del usuario

La app distingue dos ubicaciones:

| Tipo | Dónde | Permisos |
|---|---|---|
| Contenido base (XML, imágenes, fuentes, i18n) | shared home | Solo lectura |
| Contenido del usuario (`userdefined-*`), `settings.cfg` | directorio escribible | Lectura y escritura |

El shared home se resuelve en este orden:

1. `-Dwhq.shared.home=<ruta>` o la variable de entorno `WHQ_SHARED_HOME`.
2. `<appHome>/shared`: builds empaquetadas con `jpackage`.
3. `<appHome>/../shared`: desarrollo, ejecutando desde `WhqHelperApp/`.
4. `<appHome>`: compatibilidad con instalaciones antiguas que llevan `data/` y `resources/` dentro.

El directorio escribible es el app home si se puede escribir en él; si no, `%APPDATA%/WHQ Helper` (Windows), `~/Library/Application Support/WHQ Helper` (macOS) o `$XDG_DATA_HOME/whq-helper` / `~/.local/share/whq-helper` (Linux). Se puede forzar con `-Dwhq.user.home` o `WHQ_USER_HOME`. Al prepararlo solo se copian `settings.cfg`, los `userdefined-*` y `lib/`: el contenido base se lee siempre del shared home y nunca queda una copia obsoleta.

Las traducciones del contenido del usuario se guardan en `data/i18n/userdefined-content-{es,en}.xml` del directorio escribible cuando el `content-*.xml` base no se puede escribir (instalación empaquetada). En desarrollo, el editor de contenido sigue escribiendo directamente en `shared/`.

## Ejecutar

Requisitos:

- JDK 25.
- Maven 3.9+.

Comandos:

```bash
mvn -q clean compile
java --enable-native-access=ALL-UNNAMED \
  -Djava.library.path=./lib/native/linux-x86_64 \
  -cp "target/classes:./lib/org.eclipse.swt.gtk.linux.x86_64-3.127.0.jar" \
  com.whq.app.WhqCardRendererApp
```

Si no tienes acceso a internet, usa modo offline:

```bash
./run-maven-offline.sh
```

El proyecto ya incluye los JAR necesarios en `lib/` para evitar descargas de dependencias.

## Empaquetado para Windows

La opción recomendada para este proyecto es generar un bundle de aplicación y, sobre ese bundle, crear el ejecutable con `jpackage`.

Motivo:

- la app SWT lee el contenido base de `shared/` y guarda el del usuario en disco (`userdefined-*`, `settings.cfg`),
- el contenido XML no debe quedar enterrado dentro del JAR si quieres seguir editándolo, hacer backups o permitir contenido de usuario,
- `jpackage` genera un `.exe` o `.msi` con runtime de Java incluido, sin pedir Java preinstalado al usuario final.

### Preparación

Añade el JAR de SWT para Windows en:

- `lib/org.eclipse.swt.win32.win32.x86_64-3.127.0.jar`

No hace falta copiar DLL de SWT aparte en esta estrategia: el JAR de SWT ya contiene sus binarios nativos.

### Generar el bundle de entrada para Windows

Desde `WhqHelperApp/`:

```bash
mvn -Pwindows-dist -DskipTests package
```

Esto genera:

- `target/windows-input/`: directorio listo para `jpackage`

El contenido de `target/windows-input/` queda así:

```text
windows-input/
  whq-helper-app-1.0.0.jar
  lib/
    org.eclipse.swt.win32.win32.x86_64-3.127.0.jar
  shared/
    data/
      xml/
      graphics/
      fonts/
      i18n/
    resources/
    branding/
```

### Generar `.exe` o `.msi`

Este paso debe ejecutarse en Windows con JDK 25+:

```powershell
./scripts/package-windows.ps1
```

Opciones:

```powershell
./scripts/package-windows.ps1 -Type app-image
./scripts/package-windows.ps1 -Type exe
./scripts/package-windows.ps1 -Type msi
```

Salida:

- `target/windows-package/`

### Generación automática en GitHub Actions

El repositorio incluye el workflow:

- `.github/workflows/build-windows-exe.yml`

Comportamiento:

- se ejecuta en cada `push` a `main`,
- usa un runner Windows,
- descarga en CI el JAR `org.eclipse.swt.win32.win32.x86_64-3.127.0.jar`,
- ejecuta `./scripts/package-windows.ps1 -Type exe`,
- publica el `.exe` como artefacto del workflow.

Esto te permite desarrollar en Linux y delegar el empaquetado final de Windows a GitHub.

## Empaquetado para Linux

`jpackage` no genera `AppImage` directamente. Para Linux, el flujo correcto es:

1. generar una `app-image` con `jpackage`,
2. convertir esa imagen a `AppImage` con `appimagetool`.

### Preparación

Asegúrate de tener disponible:

- `lib/org.eclipse.swt.gtk.linux.x86_64-3.127.0.jar`
- `appimagetool`

### Generar `app-image` o `AppImage`

Desde `WhqHelperApp/`:

```bash
./scripts/package-linux.sh app-image
./scripts/package-linux.sh appimage
```

Salida:

- `target/linux-package/WHQ Helper/`: app-image de `jpackage`
- `target/linux-package/*.AppImage`: ejecutable portable para Linux

### Generación automática en GitHub Actions

El repositorio incluye:

- `.github/workflows/build-linux-appimage.yml`

Comportamiento:

- se ejecuta en cada `push` a `main`,
- usa un runner Linux,
- descarga el JAR SWT de Linux y `appimagetool`,
- genera el `AppImage`,
- publica el `AppImage` como artefacto.

## Empaquetado para macOS

Para macOS, `jpackage` sí puede generar directamente la aplicación `.app`. También he dejado el script listo para `dmg` o `pkg`, aunque la salida más útil para probar es la `.app`.

### Preparación

Asegúrate de tener disponible el JAR SWT de la arquitectura de destino:

- Intel: `lib/org.eclipse.swt.cocoa.macosx.x86_64-3.127.0.jar`
- Apple Silicon: `lib/org.eclipse.swt.cocoa.macosx.aarch64-3.127.0.jar`

### Generar `.app`, `.dmg` o `.pkg`

Desde `WhqHelperApp/` y ejecutando en macOS:

```bash
./scripts/package-macos.sh app-image
./scripts/package-macos.sh dmg
./scripts/package-macos.sh pkg
```

Salida:

- `target/macos-package/`

El script detecta la arquitectura del host y selecciona el JAR SWT correspondiente. También genera el icono `.icns` a partir de `shared/branding/logo.png`.

### Generación automática en GitHub Actions

El repositorio incluye:

- `.github/workflows/build-macos-app.yml`

Comportamiento:

- se ejecuta en cada `push` a `main`,
- construye dos variantes: Intel (`macos-15-intel`) y Apple Silicon (`macos-15`),
- descarga en CI el JAR SWT correcto para cada arquitectura,
- ejecuta `./scripts/package-macos.sh app-image`,
- publica cada `.app` como artefacto independiente.

Nota:

- las `.app` generadas en CI no quedan firmadas ni notarizadas; para distribución pública en macOS necesitarás `codesign` y notarización de Apple.

### Publicación de releases

El repositorio incluye además:

- `.github/workflows/release-desktop-apps.yml`

Comportamiento:

- se ejecuta al hacer `push` de un tag con formato `v*`,
- construye y publica en una GitHub Release:
- Windows `EXE`
- Linux `AppImage`
- macOS `DMG` Intel y Apple Silicon

Flujo recomendado:

```bash
git tag v1.0.0
git push origin v1.0.0
```

La release quedará publicada en:

- `https://github.com/<owner>/<repo>/releases/latest`

## Cómo quedan los XML en el ejecutable

Los XML no se empaquetan dentro del JAR principal. Los perfiles `*-dist` de Maven copian `../shared/` como `shared/` dentro del bundle de entrada (sin `userdefined-*`, `*.bak` ni las losetas borrador `resources/tiles/{ad,at}/`), y `jpackage` lo deja dentro del área `app/` de la imagen generada.

En tiempo de ejecución la aplicación resuelve su base por la ubicación real del JAR empaquetado y encuentra `shared/` a su lado. Implicación práctica:

- el contenido base es de solo lectura y se actualiza con cada versión instalada,
- el contenido del usuario (`userdefined-*.xml`, sus traducciones y `settings.cfg`) vive en el directorio escribible y sobrevive a las actualizaciones,
- no necesitas descomprimir nada en cada arranque.

Alternativa sin Maven:

```bash
./run-offline.sh
```

## Datos de cartas

Fichero maestro:

- `shared/data/xml/dungeon/dungeon-cards.xml`

Cada carta guarda:

- `id`, `name`, `type`, `environment`, `copyCount`, `enabled`
- `description`, `rules`, `tileImagePath`

Si el XML no existe al arrancar, la app crea un conjunto de ejemplo.

## Personalización

- Para usar tus propias cartas, crea cartas desde el editor (se guardan en `data/xml/dungeon/userdefined-dungeon-cards.xml`) o importa desde CSV.
- `tile_image_path` debe ser una ruta relativa al shared home (por ejemplo `resources/tiles/mi-tile.png`), relativa al directorio escribible si la imagen es tuya, o absoluta.
- El renderer intenta usar fuentes con look clásico (`Cinzel/Trajan/Georgia/Trebuchet`) y cae a fuentes del sistema si no están instaladas.

## Import / Export CSV

Desde el menú `Contenido` en la app:

- `Importar cartas desde CSV...`: añade cartas del CSV al catálogo XML.
- `Exportar todas las cartas a CSV...`: exporta todas las cartas.
- `Exportar grupo del entorno seleccionado...`: exporta solo cartas del mismo `environment` que la carta seleccionada.

Cabecera CSV esperada:

- `name,type,environment,description_text,rules_text,tile_image_path`

## Notas sobre fuentes

Si quieres máxima fidelidad visual, instala localmente fuentes similares a los ejemplos (display serif para títulos + serif itálica para flavor text + sans para reglas). El código ya usa fallback automático.
