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
- Los XML originales en `public/data/xml` se usan como base de lectura inicial.

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
