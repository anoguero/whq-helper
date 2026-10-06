// @vitest-environment happy-dom
import { readFileSync } from 'node:fs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { loadUiTranslations } from '../../i18n';
import { appState } from '../../state';
import { repository, settings, table } from '../../testing/fixtures';
import { upsertUserContentItem } from '../../userContent';
import type { UserTableItem } from '../../userContent/types';
import { tableIdConflictMessage } from './tableEditor';

function tableXml(name: string, id?: string): string {
  const idAttr = id ? ` id="${id}"` : '';
  return `<?xml version="1.0"?>\n<tables>\n  <table${idAttr} name="${name}">\n    <event id="e" />\n  </table>\n</tables>`;
}

function tableItem(uid: string, mode: UserTableItem['mode'], name: string): UserTableItem {
  return {
    uid,
    kind: 'table',
    mode,
    title: name,
    updatedAt: '2026-10-06T00:00:00.000Z',
    data: { name, kind: 'dungeon', xml: tableXml(name) }
  };
}

beforeEach(() => {
  localStorage.clear();
  appState.settings = settings();
  appState.repository = repository([
    table('Catacombs Monsters', { id: 'catacombs-monsters' }),
    table('userdefined-Foo', { id: 'userdefined-foo' })
  ]);
});

describe('tableIdConflictMessage', () => {
  it('rejects a new table whose name gives the id of another table', () => {
    // El fallo: no se comprobaba y dos tablas acababan con el mismo id.
    const draft = tableItem('new-1', 'new', 'userdefined foo!');
    expect(tableIdConflictMessage(tableXml('userdefined foo!'), draft)).not.toBeNull();
    expect(tableIdConflictMessage(tableXml('userdefined-Bar'), draft)).toBeNull();
  });

  it('rejects renaming a user table to a name whose id belongs to a base table', () => {
    const saved = tableItem('u-1', 'new', 'userdefined-Foo');
    upsertUserContentItem(saved);
    expect(tableIdConflictMessage(tableXml('Catacombs  Monsters'), saved)).not.toBeNull();
  });

  it('rejects an explicit id attribute that another table already uses', () => {
    const draft = tableItem('new-2', 'new', 'userdefined-Other');
    expect(tableIdConflictMessage(tableXml('userdefined-Other', 'catacombs-monsters'), draft)).not.toBeNull();
  });

  it('does not count the table being edited as a conflict', () => {
    const saved = tableItem('u-1', 'new', 'userdefined-Foo');
    upsertUserContentItem(saved);
    expect(tableIdConflictMessage(tableXml('userdefined-Foo'), saved)).toBeNull();
    expect(tableIdConflictMessage(tableXml('userdefined - foo'), saved)).toBeNull();
  });

  it('does not count the base table that an unsaved modification replaces', () => {
    const draft = tableItem('mod-1', 'modified', 'Catacombs Monsters');
    expect(tableIdConflictMessage(tableXml('Catacombs Monsters'), draft)).toBeNull();
  });

  it('names the id and the table that already uses it', async () => {
    const uiXml = (language: string): string =>
      readFileSync(`${process.cwd()}/../shared/data/i18n/ui-${language}.xml`, 'utf8');
    vi.stubGlobal('fetch', async (url: string) => new Response(uiXml(url.endsWith('ui-en.xml') ? 'en' : 'es')));
    await loadUiTranslations();
    vi.unstubAllGlobals();
    appState.settings = settings({ language: 'ES' });
    const message = tableIdConflictMessage(tableXml('userdefined foo'), tableItem('new-3', 'new', 'x'));
    expect(message).toBe(
      'El id «userdefined-foo» de esta tabla ya lo usa la tabla «userdefined-Foo». Cambia el nombre de la tabla.'
    );
  });
});
