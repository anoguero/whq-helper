// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { loadContentManifest } from '../content';
import { renderMissingContentNotice } from './contentNotice';

beforeEach(() => {
  document.body.innerHTML = '<div id="app"><main>app</main></div>';
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('renderMissingContentNotice', () => {
  it('shows the notice above the application when there is no content package', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('', { status: 404 })));
    await loadContentManifest();

    renderMissingContentNotice('ES');

    const notice = document.querySelector('#app > .content-notice');
    expect(notice).not.toBeNull();
    expect(notice?.getAttribute('role')).toBe('status');
    expect(document.querySelector('#app main')).not.toBeNull();
  });

  it('shows nothing when the content package is there', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('{"xmlFiles":[]}', { headers: { 'content-type': 'application/json' } })));
    await loadContentManifest();

    renderMissingContentNotice('ES');

    expect(document.querySelector('.content-notice')).toBeNull();
  });
});
