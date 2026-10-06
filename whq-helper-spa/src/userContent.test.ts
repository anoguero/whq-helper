// @vitest-environment happy-dom
import { afterEach, describe, expect, it } from 'vitest';
import {
  buildUserContentXmlDocuments,
  deleteUserContentItem,
  loadUserContentItems,
  upsertUserContentItem
} from './userContent';
import { createDefaultEvent, createDefaultMonster } from './userContent/defaults';
import type { UserContentItem } from './userContent/types';
import { parseEventIdsFromTableXml } from './userContent/xml';

function newItem(partial: Pick<UserContentItem, 'kind' | 'data'> & { uid: string }): UserContentItem {
  return { mode: 'new', title: '', updatedAt: '', ...partial } as UserContentItem;
}

function document(path: string): string | undefined {
  return buildUserContentXmlDocuments().find((entry) => entry.path === path)?.xml;
}

afterEach(() => {
  localStorage.clear();
});

describe('user content storage and XML documents', () => {
  it('stores a normalized monster and exposes it as a monsters document', () => {
    upsertUserContentItem(newItem({ uid: 'm1', kind: 'monster', data: { ...createDefaultMonster(), name: 'Rust Golem' } }));

    expect(loadUserContentItems().map((item) => item.data)).toMatchObject([{ id: 'userdefined-monster-rust-golem' }]);
    const xml = document('/userdefined/monsters/userdefined-monsters.xml');
    const doc = new DOMParser().parseFromString(xml ?? '', 'text/xml');
    expect(doc.querySelector('parsererror')).toBeNull();
    expect(doc.querySelector('monster')?.getAttribute('id')).toBe('userdefined-monster-rust-golem');
  });

  it('keeps a managed treasure table in step with the new treasures', () => {
    upsertUserContentItem(newItem({ uid: 't1', kind: 'treasure', data: { ...createDefaultEvent('treasure'), name: 'Gold Cup' } }));
    upsertUserContentItem(newItem({ uid: 't2', kind: 'treasure', data: { ...createDefaultEvent('treasure'), name: 'Silver Ring' } }));

    const managed = loadUserContentItems().find((item) => item.kind === 'table');
    expect(managed?.data).toMatchObject({ name: 'userdefined-treasure', kind: 'treasure' });
    expect(parseEventIdsFromTableXml((managed?.data as { xml: string }).xml)).toEqual([
      'userdefined-treasure-gold-cup',
      'userdefined-treasure-silver-ring'
    ]);

    deleteUserContentItem('t1');
    const afterOne = loadUserContentItems().find((item) => item.kind === 'table');
    expect(parseEventIdsFromTableXml((afterOne?.data as { xml: string }).xml)).toEqual(['userdefined-treasure-silver-ring']);

    deleteUserContentItem('t2');
    expect(loadUserContentItems()).toEqual([]);
  });

  it('builds no documents without user content', () => {
    expect(buildUserContentXmlDocuments()).toEqual([]);
  });
});
