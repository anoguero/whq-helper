# whq-helper

Ayuda de mesa para **Warhammer Quest (1995)**. El repositorio contiene dos aplicaciones con la misma funcionalidad: una de escritorio y una SPA.

## Se distribuye sin contenido de juego

Las aplicaciones **no incluyen contenido de juego**: ni los textos de los libros, ni cartas, losetas, contadores o tipografías. Ese material no es de este proyecto y cada usuario lo aporta desde sus propios materiales, como un **paquete de contenido** aparte.

Sin paquete, las dos aplicaciones arrancan igual, vacías, y muestran un aviso que explica que falta el contenido y dónde colocarlo.

Para ver la aplicación funcionando sin un paquete propio, el repositorio trae un **contenido de ejemplo inventado** en `shared/sample/`: tres monstruos, una tabla y una carta de mazmorra.

```bash
WHQ_CONTENT_HOME=shared/sample  ...   # ver "Arrancar cada aplicación"
```

## Estructura

```
whq-helper/                 ← este repositorio
├─ shared/                  ← lo propio de la aplicación, común a las dos apps
│  ├─ data/xml/**/*.xsd     esquemas del formato del contenido
│  ├─ data/i18n/ui-*.xml    textos de la interfaz
│  ├─ branding/             logo de la aplicación
│  ├─ sample/               contenido de ejemplo inventado (un paquete de contenido mínimo)
│  └─ settings.cfg          configuración por defecto de la SPA
├─ WhqHelperApp/            aplicación de escritorio (Java 25 + SWT)
├─ whq-helper-spa/          SPA (TypeScript + Vite)
└─ scripts/
   └─ validate_shared_data.py

whq-content/                ← paquete de contenido, fuera del repositorio
├─ data/
│  ├─ xml/                  monstruos, eventos, tablas, reglas, cartas…
│  ├─ i18n/                 content-es.xml, content-en.xml
│  ├─ graphics/
│  └─ fonts/
├─ resources/               losetas, contadores, plantillas de carta, imágenes de la interfaz
└─ content-manifest.json    XML que carga la SPA
```

## Dónde se busca el paquete de contenido

Las dos aplicaciones usan la misma variable de entorno, **`WHQ_CONTENT_HOME`**.

**Escritorio**, en este orden:

1. `-Dwhq.content.home=<ruta>` o `WHQ_CONTENT_HOME=<ruta>`.
2. `<app>/content`, junto a `shared/`, en una instalación.
3. `whq-content/` junto al repositorio (`<repo>/../whq-content`), en desarrollo.

**SPA:** al ejecutar `npm run dev` o `npm run build`, `WHQ_CONTENT_HOME` (relativa al directorio desde el que se lanza) o, por defecto, `<repo>/../whq-content`.
- En desarrollo, Vite sirve el paquete junto a `shared/`.
- Al construir, copia a `dist/` sus `data/`, `resources/` y `content-manifest.json`.

Esa variable va aparte de `WHQ_SHARED_HOME` porque son cosas distintas. `shared/` es parte de la aplicación y cambia con el código: sus esquemas validan cualquier paquete. El contenido es de cada usuario.

## Formato del paquete de contenido

Un paquete es un directorio con la estructura de arriba. Cada XML de `data/xml/<categoría>/` debe validar contra el esquema de la misma categoría en `shared/data/xml/`:

| Directorio del paquete | Esquema |
|---|---|
| `data/xml/monsters/` | `monsters/whq-monster-schema.xsd` |
| `data/xml/tables/` | `tables/whq-tables-schema.xsd` |
| `data/xml/rules/` | `rules/whq-rules-schema.xsd` |
| `data/xml/events/`, `travel/`, `settlement/` | `events/whq-events-schema.xsd` |
| `data/xml/dungeon/dungeon-cards.xml` | `dungeon/whq-dungeon-cards-schema.xsd` |
| `data/xml/dungeon/room-references.xml` (opcional) | `dungeon/whq-room-references-schema.xsd` |
| `data/xml/adventures/` | `adventures/whq-adventures-schema.xsd` |
| `data/xml/locations/` | `locations/whq-locations-schema.xsd` |
| `data/xml/warriors/` | `warriors/whq-warriors-schema.xsd` |

Además:
- **Rutas:** las rutas de imágenes de los XML (por ejemplo, `tileImagePath`) son relativas a la raíz del paquete.
- **Ids de tabla:** cada tabla lleva un `id` estable, el slug de su nombre. La configuración de qué tablas están activas se guarda por ese id.
- **Traducciones:** `data/i18n/content-{es,en}.xml` traduce el contenido; sin ellas se muestra el texto del XML.
- **Manifiesto:** `content-manifest.json` lista los XML que carga la SPA, con rutas absolutas desde la raíz del paquete.
- **Paquete incompleto:** si falta algún directorio, la aplicación carga lo que hay y avisa de lo que falta.

`shared/sample/` es un ejemplo completo de este formato.

## Arrancar cada aplicación

```bash
# Escritorio (JDK 25, Maven 3.9+)
cd WhqHelperApp
mvn -q compile
java --enable-native-access=ALL-UNNAMED \
  -Djava.library.path=./lib/native/linux-x86_64 \
  -cp "target/classes:./lib/org.eclipse.swt.gtk.linux.x86_64-3.127.0.jar" \
  com.whq.app.WhqCardRendererApp
# con el contenido de ejemplo: añade -Dwhq.content.home=../shared/sample
mvn -o test

# SPA
cd whq-helper-spa
npm install
npm run dev      # http://localhost:5173
WHQ_CONTENT_HOME=../shared/sample npm run dev   # con el contenido de ejemplo
npm test
npm run build
```

Los tests que comprueban el contenido real se saltan si no encuentran el paquete, por ejemplo en CI. Los de `shared/sample/` se ejecutan siempre.

## Validación

```bash
python3 scripts/validate_shared_data.py
```

**Siempre comprueba:**
- que los esquemas son XML bien formados;
- que en `shared/` no hay contenido de juego (fuera de `sample/` solo puede haber esquemas, textos de interfaz, el logo y `settings.cfg`);
- que las claves de interfaz están en los dos idiomas;
- que no hay ficheros versionados duplicados.

**Sobre `shared/sample/` y, si lo encuentra, sobre el paquete de contenido:**
- el manifiesto, las losetas, los ids de tabla y las referencias a cartas;
- la validación de cada XML contra su esquema (con `xmllint`, si está instalado).

Se ejecuta en CI (`.github/workflows/validate-shared-data.yml`), allí sin paquete.

## Contenido no versionado

- El paquete de contenido (`../whq-content/`, o `whq-content/` o `content/` si se coloca dentro del repositorio, que están ignorados).
- `WhqHelperApp/data/**/userdefined-*`: contenido creado por el usuario.
