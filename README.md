# whq-helper

Ayuda de mesa para **Warhammer Quest (1995)**. El repositorio contiene dos aplicaciones con la misma funcionalidad y un único contenido de juego compartido.

## Estructura

```
whq-helper/
├─ shared/                  ← fuente única del contenido de juego
│  ├─ data/
│  │  ├─ xml/               monstruos, eventos, tablas, reglas, localizaciones, guerreros…
│  │  ├─ graphics/
│  │  ├─ fonts/
│  │  └─ i18n/
│  ├─ resources/            tiles, ui, warrior_counters, plantillas de carta
│  ├─ branding/             logo.png, logo.ico, logo.icns
│  ├─ content-manifest.json XML que carga la SPA
│  └─ settings.cfg          configuración por defecto de la SPA
├─ docs/                    reglas de salas transcritas y notas de extracción
├─ WhqHelperApp/            aplicación de escritorio (Java 25 + SWT)
├─ whq-helper-spa/          SPA (TypeScript + Vite)
└─ scripts/
   └─ validate_shared_data.py
```

`shared/` es la **única** copia del contenido. Ninguna aplicación tiene una copia propia ni hay scripts de sincronización: las dos leen directamente de `shared/`.

- **WhqHelperApp** resuelve `shared/` como *shared home* (en desarrollo, `WhqHelperApp/../shared`; empaquetada, `<app>/shared`) y lo trata como contenido de solo lectura. Lo que crea el usuario (`userdefined-*.xml`) y `settings.cfg` se guardan aparte, en el directorio escribible de la app. Detalles en `WhqHelperApp/README.md`.
- **whq-helper-spa** usa `shared/` como `publicDir` de Vite. El contenido del usuario se guarda en `localStorage`.

## Arrancar cada aplicación

```bash
# Escritorio (JDK 25, Maven 3.9+)
cd WhqHelperApp
mvn -q compile
java --enable-native-access=ALL-UNNAMED \
  -Djava.library.path=./lib/native/linux-x86_64 \
  -cp "target/classes:./lib/org.eclipse.swt.gtk.linux.x86_64-3.127.0.jar" \
  com.whq.app.WhqCardRendererApp
mvn -o test

# SPA
cd whq-helper-spa
npm install
npm run dev      # http://localhost:5173
npm run build    # dist/ incluye data/, resources/ y branding/
```

## Validación del contenido

```bash
python3 scripts/validate_shared_data.py
```

Falla si reaparece una copia del contenido (`whq-helper-spa/public/data`, `whq-helper-spa/public/resources`, `WhqHelperApp/resources`), si `shared/content-manifest.json` o algún `tileImagePath` de `dungeon-cards.xml` apuntan a un fichero inexistente, si la CSS de la SPA referencia una fuente que no existe o si dos ficheros versionados tienen el mismo contenido. Se ejecuta en CI (`.github/workflows/validate-shared-data.yml`).

## Contenido no versionado

- `shared/resources/tiles/{ad,at}/`: losetas en borrador, ignoradas por git y excluidas del empaquetado de escritorio.
- `docs/drafts/`: volcados de OCR sin revisar.
- `WhqHelperApp/data/**/userdefined-*`: contenido del usuario.
