// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { createDefaultEvent, createDefaultMonster, createDefaultTable } from './defaults';
import type { UserContentItem } from './types';
import { normalizeUserContentItem, slugify } from './normalize';

function item<T extends UserContentItem>(partial: Omit<T, 'uid' | 'title' | 'updatedAt'> & Partial<T>): T {
  return { uid: 'u1', title: '', updatedAt: '2026-01-01T00:00:00.000Z', ...partial } as T;
}

describe('slugify', () => {
  it('lowercases, turns & into and, and joins words with dashes', () => {
    expect(slugify('  Tom & Jerry: The Movie!  ')).toBe('tom-and-jerry-the-movie');
    expect(slugify('***')).toBe('');
  });
});

describe('normalizeUserContentItem', () => {
  it('gives new events an id with the prefix of their kind, from the name', () => {
    const normalized = normalizeUserContentItem(
      item({ kind: 'travelEvent', mode: 'new', data: { ...createDefaultEvent('travelEvent'), name: ' Rock Slide ' } })
    );
    expect(normalized.data).toMatchObject({ id: 'userdefined-travel-rock-slide', name: 'Rock Slide' });
    expect(normalized.title).toBe('Rock Slide');
  });

  it('does not duplicate the prefix of an id that already has it', () => {
    const normalized = normalizeUserContentItem(
      item({ kind: 'dungeonEvent', mode: 'new', data: { ...createDefaultEvent('dungeonEvent'), id: 'userdefined-event-ambush' } })
    );
    expect(normalized.data).toMatchObject({ id: 'userdefined-event-ambush' });
  });

  it('keeps the id of a modified item and marks treasures as treasure', () => {
    const normalized = normalizeUserContentItem(
      item({ kind: 'treasure', mode: 'modified', data: { ...createDefaultEvent('dungeonEvent'), id: ' base-sword ', treasure: false } })
    );
    expect(normalized.data).toMatchObject({ id: 'base-sword', treasure: true });
  });

  it('trims monster statistics and clamps the magic level', () => {
    const normalized = normalizeUserContentItem(
      item({
        kind: 'monster',
        mode: 'new',
        data: { ...createDefaultMonster(), name: 'Rust Golem', wounds: ' 14 ', factions: [' orcs ', ''], magicLevel: -3 }
      })
    );
    expect(normalized.data).toMatchObject({
      id: 'userdefined-monster-rust-golem',
      wounds: '14',
      factions: ['orcs'],
      magicLevel: 0
    });
  });

  it('takes the name and kind of a table from its XML', () => {
    const xml = '<tables><table name="My Travel Table" kind="travel"><event id="e"/></table></tables>';
    const normalized = normalizeUserContentItem(item({ kind: 'table', mode: 'new', data: { ...createDefaultTable(), name: 'x', xml } }));
    expect(normalized.data).toMatchObject({ name: 'My Travel Table', kind: 'travel' });
    expect(normalized.title).toBe('My Travel Table');
  });

  it('keeps the date of an item and stamps one when it has none', () => {
    const withDate = normalizeUserContentItem(item({ kind: 'monster', mode: 'new', data: createDefaultMonster() }));
    expect(withDate.updatedAt).toBe('2026-01-01T00:00:00.000Z');
    const withoutDate = normalizeUserContentItem(item({ kind: 'monster', mode: 'new', updatedAt: '', data: createDefaultMonster() }));
    expect(Number.isNaN(Date.parse(withoutDate.updatedAt))).toBe(false);
  });
});
