// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { event, group, monster, table, tableRef } from '../testing/fixtures';
import type { UserMonsterData, UserRuleData } from './types';
import {
  normalizeSpecialRuleLinks,
  parseEventIdsFromTableXml,
  parseTableMetadata,
  parseTableName,
  serializeDungeonCard,
  serializeEventTableXml,
  serializeMonster,
  serializeRule,
  serializeSpecial,
  serializeTableFromModel
} from './xml';

function parse(xml: string): Document {
  const doc = new DOMParser().parseFromString(xml, 'text/xml');
  expect(doc.querySelector('parsererror')).toBeNull();
  return doc;
}

function userMonster(overrides: Partial<UserMonsterData> = {}): UserMonsterData {
  return {
    id: 'sample-toad',
    name: 'Cellar Toad',
    plural: 'Cellar Toads',
    factions: [],
    move: '3',
    weaponskill: '3',
    ballisticskill: '-',
    strength: '3',
    toughness: '4',
    wounds: '6',
    initiative: '1',
    attacks: '2',
    gold: '40',
    armor: '-',
    damage: '1D6',
    special: '',
    specialLinks: {},
    magicType: '',
    magicLevel: 0,
    ...overrides
  } as UserMonsterData;
}

describe('normalizeSpecialRuleLinks', () => {
  it('accepts plain text and full links, trims them and drops incomplete ones', () => {
    expect(
      normalizeSpecialRuleLinks({
        ' fear ': ' Fear ',
        breath: { text: 'Breath', parameter: '', parameters: [' 3 ', '', '2D6'] },
        empty: '  ',
        '': 'No key'
      })
    ).toEqual({
      fear: { text: 'Fear', parameter: '', parameters: [] },
      breath: { text: 'Breath', parameter: '3', parameters: ['3', '2D6'] }
    });
  });
});

describe('event tables', () => {
  it('round-trips name, kind and event ids, escaping special characters', () => {
    const xml = serializeEventTableXml('Tom & "Jerry"', 'travel', ['a<b', 'c']);
    expect(parseTableName(xml)).toEqual({ name: 'Tom & "Jerry"', kind: 'travel' });
    expect(parseTableMetadata(xml)).toEqual(parseTableName(xml));
    expect(parseEventIdsFromTableXml(xml)).toEqual(['a<b', 'c']);
  });

  it('omits the kind of dungeon tables and reads unknown kinds as dungeon', () => {
    expect(serializeEventTableXml('t', 'dungeon', [])).not.toContain('kind=');
    expect(parseTableName('<tables><table name="t" kind="weird"/></tables>')?.kind).toBe('dungeon');
  });

  it('returns null for malformed XML or a missing table', () => {
    expect(parseTableName('<tables><table')).toBeNull();
    expect(parseTableName('<tables/>')).toBeNull();
  });
});

describe('serializeSpecial', () => {
  it('is empty when there is nothing special', () => {
    expect(serializeSpecial('  ', {}, '', 0, '')).toBe('');
  });

  it('writes text, rule links with a single parameter and magic', () => {
    const doc = parse(
      serializeSpecial(
        'Leader',
        { fear: { text: 'Fear', parameter: '6', parameters: ['6'] }, ambush: { text: 'Ambush', parameter: '', parameters: [] } },
        'orc-magic',
        -2,
        ''
      )
    );
    expect(doc.querySelector('text')?.textContent).toBe('Leader');
    expect(Array.from(doc.querySelectorAll('rule')).map((r) => [r.getAttribute('id'), r.getAttribute('param')])).toEqual([
      ['fear', '6'],
      ['ambush', null]
    ]);
    expect(doc.querySelector('magic')?.getAttribute('level')).toBe('0');
  });
});

describe('serializeMonster', () => {
  it('writes every statistic and the factions, and nests the special section', () => {
    const doc = parse(serializeMonster(userMonster({ factions: ['orcs', 'chaos'], special: 'Sticky tongue' })));
    const node = doc.documentElement;
    expect(node.getAttribute('factions')).toBe('orcs chaos');
    for (const [tag, value] of Object.entries({ wounds: '6', move: '3', damage: '1D6', armor: '-' })) {
      expect(node.querySelector(tag)?.textContent).toBe(value);
    }
    expect(node.querySelector('special text')?.textContent).toBe('Sticky tongue');
  });

  it('leaves out factions and special when there are none', () => {
    const xml = serializeMonster(userMonster());
    expect(xml).not.toContain('factions=');
    expect(xml).not.toContain('<special>');
  });
});

describe('serializeRule', () => {
  it('writes the optional parameter attributes only when present', () => {
    const base: UserRuleData = {
      id: 'r1',
      name: 'Breath',
      type: 'rule',
      text: 'Breathes fire',
      parameterName: '',
      parameterNames: [],
      parameterFormat: ''
    } as UserRuleData;
    expect(serializeRule(base)).toBe('  <rule id="r1" name="Breath">Breathes fire</rule>');
    const withParameters = parse(serializeRule({ ...base, type: 'magic', parameterNames: ['Range', 'Damage'], parameterFormat: '{name} {0}' }));
    expect(withParameters.documentElement.tagName).toBe('magic');
    expect(withParameters.documentElement.getAttribute('parameterNames')).toBe('Range, Damage');
    expect(withParameters.documentElement.getAttribute('parameterFormat')).toBe('{name} {0}');
  });
});

describe('serializeDungeonCard', () => {
  it('clamps the copy count and escapes the texts', () => {
    const doc = parse(
      serializeDungeonCard({
        id: 7,
        name: 'Hall <A>',
        type: 'CORRIDOR',
        environment: 'Sample',
        copyCount: -1,
        enabled: false,
        descriptionText: 'Dark & damp',
        rulesText: '',
        tileImagePath: 'resources/tiles/hall.png'
      } as never)
    );
    const card = doc.documentElement;
    expect(card.getAttribute('name')).toBe('Hall <A>');
    expect(card.getAttribute('copyCount')).toBe('0');
    expect(card.getAttribute('enabled')).toBe('false');
    expect(card.querySelector('description')?.textContent).toBe('Dark & damp');
  });
});

describe('serializeTableFromModel', () => {
  it('writes monsters, groups, table references and events', () => {
    const model = table('Mixed', {
      kind: 'treasure',
      kindRaw: 'treasure',
      monsters: [
        monster('orc', { min: 2, max: 4, level: 3, ambiences: ['orcs'] }),
        group(2, [monster('a', { level: 2 }), monster('b', { level: 2 })]),
        tableRef('Other', { level: 5, targetLevel: 1, times: 2 })
      ],
      events: [event('gold')]
    });
    const tableNode = parse(serializeTableFromModel(model)).querySelector('table')!;
    expect(tableNode.getAttribute('kind')).toBe('treasure');
    const orc = tableNode.querySelector(':scope > monster')!;
    expect([orc.getAttribute('number'), orc.getAttribute('level'), orc.getAttribute('ambiences')]).toEqual(['2-4', '3', 'orcs']);
    expect(tableNode.querySelectorAll('group > monster')).toHaveLength(2);
    const ref = tableNode.querySelector('tableRef')!;
    expect([ref.getAttribute('name'), ref.getAttribute('targetLevel'), ref.getAttribute('times')]).toEqual(['Other', '1', '2']);
    expect(tableNode.querySelector('event')?.getAttribute('id')).toBe('gold');
  });
});
