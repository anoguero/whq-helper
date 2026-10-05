import { defineConfig } from 'vite';
import { resolve } from 'node:path';

export default defineConfig({
  // shared/ es la fuente unica de contenido: Vite la sirve en dev y la copia a dist/ en build.
  publicDir: resolve(__dirname, '../shared'),
  server: {
    port: 5173,
    fs: { allow: [resolve(__dirname, '..')] }
  }
});
