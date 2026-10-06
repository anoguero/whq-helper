# WHQ Helper SPA (TypeScript)

Migración completa de `WhqHelperApp` a una SPA en TypeScript pensada para despliegue sencillo en hosting estático.

## Funcionalidad migrada

- Mazo de eventos completo (Mazmorra, Asentamiento, Viaje, Tesoro, Tesoro Objetivo).
- Carga de contenido XML original (`rules`, `events`, `travel`, `settlement`, `tables`, `monsters`).
- Modo simulación `tabla` / `mazo`.
- Probabilidades de evento y tesoro oro.
- Activación de tablas.
- Render de cartas de evento, tesoro y monstruo.
- Biblioteca de cartas de mazmorra.
- Render de carta de mazmorra sobre template.
- Mantenimiento de cartas (copias, habilitada, tile path, borrado).
- Importación CSV.
- Exportación CSV global y por entorno seleccionado.
- Editor de contenido XML (reglas/eventos/monstruos/tablas) con recarga de mazos.
- Nueva Mazmorra:
  - selección de entorno,
  - sala objetivo,
  - misión,
  - ambientación,
  - tamaño de mazo y número de habitaciones,
  - simulador de mazo con montones, división e histórico.

## Persistencia

Como es una SPA para hosting estático, no escribe en disco del servidor.

- Configuración: `localStorage`
- Estado de tablas activas: `localStorage`
- Cambios de cartas de mazmorra / importaciones CSV: `localStorage`
- Overrides de XML editados: `localStorage`
- Los XML del paquete de contenido se usan como base de lectura inicial.

## Contenido

La SPA no tiene carpeta `public/`. Se sirve desde dos sitios, con las mismas rutas en desarrollo y en `dist/`:

- `publicDir` es `../shared`, lo propio de la aplicación: `/settings.cfg` (configuración por defecto), `/data/i18n/ui-*.xml` (textos de interfaz) y `/branding/logo.png` (favicon e icono).
- El paquete de contenido de juego (`WHQ_CONTENT_HOME`, por defecto `../../whq-content`) lo sirve el plugin de `content-plugin.ts` en `npm run dev` y lo copia a `dist/` en `npm run build`:
  - `/content-manifest.json`: lista de XML que carga la SPA.
  - `/data/xml/...`, `/data/graphics/...`, `/data/fonts/...`, `/data/i18n/content-*.xml`
  - `/resources/...`: plantillas, tiles, UI y contadores.

Sin paquete, la SPA se genera igual y muestra un aviso de contenido ausente. Para probarla con el contenido de ejemplo: `WHQ_CONTENT_HOME=../shared/sample npm run dev`.

## Ejecutar

```bash
npm install
npm run dev
```

## Build

```bash
npm run build
```

## Estructura principal

- `src/main.ts`: app SPA, UI y flujos.
- `src/content.ts`: carga/parsing XML del sistema de eventos.
- `src/deck.ts`: lógica de construcción y robo de mazos/tablas.
- `src/dungeonStore.ts`: store de cartas de mazmorra + CSV + aventuras.
- `src/dungeonRenderer.ts`: render de cartas de mazmorra.
- `src/render.ts`: render de cartas de evento/tesoro/monstruo.
