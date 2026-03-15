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
- Migración automática desde `data/whq-cards.db` si existe una base SQLite legacy y todavía no se ha creado el XML.
- Dependencias de terceros mínimas:
  - SWT,
  - sqlite-jdbc solo para migración legacy desde `whq-cards.db`.

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
  -cp "target/classes:./lib/org.eclipse.swt.gtk.linux.x86_64-3.127.0.jar:./lib/sqlite-jdbc-3.49.1.0.jar" \
  com.whq.app.WhqCardRendererApp
```

Si no tienes acceso a internet, usa modo offline:

```bash
./run-maven-offline.sh
```

El proyecto ya incluye los JAR necesarios en `lib/` para evitar descargas de dependencias.

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

Si el XML no existe al arrancar, la app intenta migrarlo desde `data/whq-cards.db`. Si tampoco hay datos legacy, crea un conjunto de ejemplo.

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
