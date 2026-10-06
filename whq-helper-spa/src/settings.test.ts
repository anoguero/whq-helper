// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { loadSettings } from './settings';

const STORAGE_KEY = 'whq_helper_spa_settings_v1';

function serveCfg(lines: string[]): void {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(lines.join('\n'))));
}

afterEach(() => {
  vi.unstubAllGlobals();
  localStorage.clear();
});

describe('loadSettings table state', () => {
  it('reads id keys and keeps old name keys apart for migration', async () => {
    serveCfg(['table.hag-1.active=false', 'Roleplay\\ Book\\ Monsters\\ -\\ Level\\ 1.active=true']);
    const settings = await loadSettings();
    expect(settings.tableActiveById).toEqual({ 'hag-1': false });
    expect(settings.legacyTableActive).toEqual({ 'Roleplay Book Monsters - Level 1': true });
  });

  it('lets the user choices of an old-format localStorage beat the id defaults of the cfg', async () => {
    serveCfg(['table.hag-1.active=true']);
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ tableActive: { 'Hag Queen - Level 1': false } }));
    const settings = await loadSettings();
    expect(settings.tableActiveById).toEqual({});
    expect(settings.legacyTableActive).toEqual({ 'Hag Queen - Level 1': false });
    expect('tableActive' in settings).toBe(false);
  });

  it('lets a new-format localStorage override the cfg by id', async () => {
    serveCfg(['table.hag-1.active=true', 'table.cot-3.active=true']);
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ tableActiveById: { 'hag-1': false }, legacyTableActive: { orphan: true } }));
    const settings = await loadSettings();
    expect(settings.tableActiveById).toEqual({ 'hag-1': false, 'cot-3': true });
    expect(settings.legacyTableActive).toEqual({ orphan: true });
  });
});
