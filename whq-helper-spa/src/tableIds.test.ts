import { afterEach, describe, expect, it, vi } from 'vitest';
import { findReferencedTable, migrateLegacyTableActive, tableIdFromName } from './tableIds';
import { repository, settings, table } from './testing/fixtures';

afterEach(() => {
  vi.restoreAllMocks();
});

describe('tableIdFromName', () => {
  it('builds the same slug as Java and the validator', () => {
    expect(tableIdFromName('Hall of the Hag Queen Monsters - Level 1')).toBe('hall-of-the-hag-queen-monsters-level-1');
    expect(tableIdFromName('lm - Town Events')).toBe('lm-town-events');
    expect(tableIdFromName('  Tesoros de Salá Ñ! ')).toBe('tesoros-de-sala-n');
    expect(tableIdFromName(' -- ')).toBe('table');
  });
});

describe('findReferencedTable', () => {
  it('finds the table by stable id first and by visible name otherwise', () => {
    const renamed = table('Minions (renamed)', { id: 'minions' });
    const legacy = table('Old Name', { id: 'old-name' });
    const tables = repository([renamed, legacy]).tables;
    expect(findReferencedTable(tables, 'minions')).toBe(renamed);
    expect(findReferencedTable(tables, ' minions ')).toBe(renamed);
    expect(findReferencedTable(tables, 'Old Name')).toBe(legacy);
    expect(findReferencedTable(tables, 'missing')).toBeUndefined();
    expect(findReferencedTable(tables, '  ')).toBeUndefined();
  });

  it('prefers the id when a reference matches one table by id and another by name', () => {
    const byId = table('Something', { id: 'ogres' });
    const byName = table('ogres', { id: 'other' });
    expect(findReferencedTable(repository([byName, byId]).tables, 'ogres')).toBe(byId);
  });
});

describe('migrateLegacyTableActive', () => {
  it('converts name keys to id keys keeping their value', () => {
    const repo = repository([table('Hag Queen - Level 1', { id: 'hag-1' }), table('Catacombs 3', { id: 'cot-3' })]);
    const config = settings({ legacyTableActive: { 'Hag Queen - Level 1': false, 'Catacombs 3': true } });
    migrateLegacyTableActive(repo, config);
    expect(config.tableActiveById).toEqual({ 'hag-1': false, 'cot-3': true });
    expect(config.legacyTableActive).toEqual({});
  });

  it('keeps and reports an orphan name key', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const config = settings({ legacyTableActive: { 'gpt-events': true } });
    expect(migrateLegacyTableActive(repository([]), config)).toEqual(['gpt-events']);
    expect(config.legacyTableActive).toEqual({ 'gpt-events': true });
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('gpt-events'));
  });

  it('lets an existing id key win over a leftover name key', () => {
    const repo = repository([table('Hag Queen - Level 1', { id: 'hag-1' })]);
    const config = settings({ tableActiveById: { 'hag-1': false }, legacyTableActive: { 'Hag Queen - Level 1': true } });
    migrateLegacyTableActive(repo, config);
    expect(config.tableActiveById).toEqual({ 'hag-1': false });
  });

  it('resolves a user table without id by the slug of its name', () => {
    const repo = repository([table('My Monsters')]);
    const config = settings({ legacyTableActive: { 'My Monsters': false } });
    migrateLegacyTableActive(repo, config);
    expect(config.tableActiveById).toEqual({ 'my-monsters': false });
  });
});
