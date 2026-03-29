# WHQ Helper App (SWT)

Aplicación Java + SWT para renderizar cartas de mazmorra estilo **Warhammer Quest (1995)** a partir de datos cargados desde XML.

## Qué incluye

- UI SWT con dos paneles:
  - lista de habitaciones/cartas disponibles,
  - visor de renderizado de carta.
- Renderizado de carta sobre plantilla (`resources/dungeon-card-template.png`):
  - nombre,
  - texto descriptivo,
  - texto de reglas,
  - imagen de tile,
  - banda inferior con tipo (`DUNGEON ROOM`, `OBJECTIVE ROOM`, `CORRIDOR`, `SPECIAL`).
- Repositorio XML de cartas de mazmorra (`data/xml/dungeon/dungeon-cards.xml`) como fuente principal de mantenimiento.
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
- `resources/tiles/*`: tiles de ejemplo.
- `data/xml/dungeon/dungeon-cards.xml`: catálogo maestro de cartas de mazmorra.

## Ejecutar

Requisitos:

- JDK 17.
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

- la app SWT usa datos y recursos editables en disco (`data/`, `resources/`, `settings.cfg`),
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
  data/
    xml/
    graphics/
    fonts/
  resources/
    ...
```

### Generar `.exe` o `.msi`

Este paso debe ejecutarse en Windows con JDK 17+:

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

El script detecta la arquitectura del host y selecciona el JAR SWT correspondiente. También genera el icono `.icns` a partir de `resources/logo.png`.

### Generación automática en GitHub Actions

El repositorio incluye:

- `.github/workflows/build-macos-app.yml`

Comportamiento:

- se ejecuta en cada `push` a `main`,
- construye dos variantes: Intel (`macos-13`) y Apple Silicon (`macos-14`),
- descarga en CI el JAR SWT correcto para cada arquitectura,
- ejecuta `./scripts/package-macos.sh app-image`,
- publica cada `.app` como artefacto independiente.

Nota:

- las `.app` generadas en CI no quedan firmadas ni notarizadas; para distribución pública en macOS necesitarás `codesign` y notarización de Apple.

## Cómo quedan los XML en el ejecutable

Los XML no se empaquetan dentro del JAR principal.

Se copian como ficheros normales dentro del bundle de aplicación:

- `data/xml/monsters/*.xml`
- `data/xml/events/*.xml`
- `data/xml/travel/*.xml`
- `data/xml/settlement/*.xml`
- `data/xml/tables/*.xml`
- `data/xml/dungeon/*.xml`

Cuando `jpackage` genera la imagen Windows, esos ficheros quedan junto al resto del contenido de la app dentro del área `app/` del paquete generado. En tiempo de ejecución la aplicación resuelve su base no por directorio de trabajo, sino por la ubicación real del JAR empaquetado.

Implicación práctica:

- el ejecutable puede leer y escribir los XML,
- puedes distribuir XML de serie en la instalación,
- los ficheros `userdefined-*.xml` y `*.bak` siguen funcionando,
- no necesitas descomprimir nada en cada arranque.

Si quisieras un diseño todavía más limpio para despliegue, puedes separar:

- XML base en el bundle instalado,
- XML editables del usuario en `%APPDATA%/WHQ Helper/`

Pero eso requeriría adaptar el código de carga/guardado. La configuración actual mantiene el comportamiento existente y es la opción más segura para este proyecto.

Alternativa sin Maven:

```bash
./run-offline.sh
```

## Datos de cartas

Fichero maestro:

- `data/xml/dungeon/dungeon-cards.xml`

Cada carta guarda:

- `id`, `name`, `type`, `environment`, `copyCount`, `enabled`
- `description`, `rules`, `tileImagePath`

Si el XML no existe al arrancar, la app crea un conjunto de ejemplo.

## Personalización

- Para usar tus propias cartas, edita `data/xml/dungeon/dungeon-cards.xml` o importa desde CSV.
- `tile_image_path` debe apuntar a una ruta válida relativa al root del proyecto (por ejemplo `resources/tiles/mi-tile.png`).
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
