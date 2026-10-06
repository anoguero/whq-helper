// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { isContentPackageAvailable, loadContentManifest } from './content';

function serve(body: string, init: ResponseInit): void {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(body, init)));
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('loadContentManifest', () => {
  it('loads the manifest of a content package', async () => {
    serve(JSON.stringify({ xmlFiles: ['/data/xml/tables/a.xml'] }), { headers: { 'content-type': 'application/json' } });
    expect(await loadContentManifest()).toEqual({ xmlFiles: ['/data/xml/tables/a.xml'] });
    expect(isContentPackageAvailable()).toBe(true);
  });

  it('falls back to an empty manifest when there is no content package', async () => {
    serve('', { status: 404 });
    expect(await loadContentManifest()).toEqual({ xmlFiles: [] });
    expect(isContentPackageAvailable()).toBe(false);
  });

  it('does not take the index.html of an SPA fallback for a manifest', async () => {
    serve('<!doctype html><html></html>', { headers: { 'content-type': 'text/html' } });
    expect(await loadContentManifest()).toEqual({ xmlFiles: [] });
    expect(isContentPackageAvailable()).toBe(false);
  });
});
