import { defineConfig } from 'vite';
import { resolve } from 'node:path';
import { resolveContentDir, whqContentPlugin } from './content-plugin';

// shared/ trae solo lo propio de la aplicacion (i18n de interfaz, esquemas, ejemplo); el contenido de
// juego es un paquete aparte (WHQ_CONTENT_HOME, por defecto ../../whq-content), que el plugin sirve
// en dev y copia a dist/ en build.
const publicDir = resolve(__dirname, '../shared');
const contentDir = resolveContentDir(process.env.WHQ_CONTENT_HOME, __dirname);

export default defineConfig({
  publicDir,
  plugins: [whqContentPlugin(contentDir, publicDir)],
  server: {
    port: 5173,
    fs: { allow: [resolve(__dirname, '..'), contentDir] }
  }
});
