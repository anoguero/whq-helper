import { cpSync, createReadStream, existsSync, statSync } from 'node:fs';
import { extname, isAbsolute, join, normalize, resolve, sep } from 'node:path';
import type { Plugin } from 'vite';

// Rutas que pertenecen al paquete de contenido (las mismas que en la app de escritorio).
const CONTENT_PREFIXES = ['/data/', '/resources/', '/branding/', '/content-manifest.json'];
// Lo que se copia a dist/: solo lo que la SPA sirve (el paquete puede traer mas, como docs/).
const CONTENT_ENTRIES = ['data', 'resources', 'branding', 'content-manifest.json'];

const CONTENT_TYPES: Record<string, string> = {
  '.json': 'application/json',
  '.xml': 'application/xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.svg': 'image/svg+xml',
  '.ttf': 'font/ttf',
  '.otf': 'font/otf',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2'
};

/**
 * Directorio del paquete de contenido: WHQ_CONTENT_HOME (relativo al directorio desde el que se
 * lanza Vite) o, por defecto, whq-content junto al repositorio, como en la app de escritorio.
 */
export function resolveContentDir(env: string | undefined, spaDir: string): string {
  if (env && env.trim()) {
    return isAbsolute(env.trim()) ? env.trim() : resolve(process.cwd(), env.trim());
  }
  return resolve(spaDir, '../../whq-content');
}

function isContentPath(url: string): boolean {
  return CONTENT_PREFIXES.some((prefix) => url === prefix || url.startsWith(prefix));
}

/**
 * Sirve el paquete de contenido junto a publicDir (shared/, solo lo propio de la aplicacion) y lo
 * copia a dist/ al construir. Sin paquete la SPA se genera igual y muestra el aviso de contenido
 * ausente; en desarrollo, una ruta de contenido que no existe da 404 en lugar del index.html.
 */
export function whqContentPlugin(contentDir: string, publicDir: string): Plugin {
  const available = existsSync(contentDir) && statSync(contentDir).isDirectory();
  let outDir = '';
  return {
    name: 'whq-content',
    configResolved(config) {
      outDir = resolve(config.root, config.build.outDir);
      const where = available ? `desde ${contentDir}` : `no encontrado en ${contentDir}: la SPA mostrara el aviso`;
      config.logger.info(`[whq-content] paquete de contenido ${where}`);
    },
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const url = decodeURIComponent((req.url ?? '').split('?')[0] ?? '');
        if (!isContentPath(url)) {
          next();
          return;
        }
        const relative = normalize(url).replace(/^[/\\]+/, '');
        const file = join(contentDir, relative);
        if (available && file.startsWith(contentDir + sep) && existsSync(file) && statSync(file).isFile()) {
          res.setHeader('Content-Type', CONTENT_TYPES[extname(file).toLowerCase()] ?? 'application/octet-stream');
          createReadStream(file).pipe(res);
          return;
        }
        if (existsSync(join(publicDir, relative))) {
          next();
          return;
        }
        res.statusCode = 404;
        res.end();
      });
    },
    closeBundle() {
      if (!available || !outDir) {
        return;
      }
      for (const entry of CONTENT_ENTRIES) {
        const source = join(contentDir, entry);
        if (existsSync(source)) {
          cpSync(source, join(outDir, entry), { recursive: true });
        }
      }
    }
  };
}
