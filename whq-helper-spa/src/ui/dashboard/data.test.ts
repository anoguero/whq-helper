// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import {
  parseEventOnlyTable,
  parseMonsterOnlyTable,
  serializeEventOnlyTable,
  serializeMonsterOnlyTable,
  summarizeMonsterCount
} from './data';
import { group, monster } from '../../testing/fixtures';

describe('serializeEventOnlyTable', () => {
  it('omits the kind attribute for dungeon tables and escapes names and ids', () => {
    expect(serializeEventOnlyTable('Mine & Yours', 'dungeon', ['a"b'])).toBe(
      [
        '<?xml version="1.0"?>',
        '<tables>',
        '  <table name="Mine &amp; Yours">',
        '    <event id="a&quot;b" />',
        '  </table>',
        '</tables>'
      ].join('\n')
    );
  });

  it('writes the kind for travel and settlement tables', () => {
    expect(serializeEventOnlyTable('t', 'travel', [])).toContain('<table name="t" kind="travel">');
    expect(serializeEventOnlyTable('t', 'settlement', [])).toContain('<table name="t" kind="settlement">');
  });
});

describe('serializeMonsterOnlyTable', () => {
  it('writes monsters, ranges, ambiences, groups and specials', () => {
    const xml = serializeMonsterOnlyTable('Monsters', [
      monster('orc', { min: 2, max: 4, level: 3, ambiences: ['Caves', 'Crypt'] }),
      group(5, [
        monster('shaman', {
          level: 5,
          special: 'Leader',
          specialLinks: { fear: { text: 'Fear', parameter: '6', parameters: ['6'] } },
          magicType: 'orc-magic',
          magicLevel: 2,
          appendSpecials: false
        })
      ])
    ]);
    expect(xml).toBe(
      [
        '<?xml version="1.0"?>',
        '<tables>',
        '  <table name="Monsters">',
        '    <monster id="orc" number="2-4" level="3" ambiences="Caves Crypt">',
        '    </monster>',
        '    <group level="5">',
        '      <monster id="shaman" number="1" level="5">',
        '        <special append="false">',
        '          <text>Leader</text>',
        '          <rule id="fear" param="6">Fear</rule>',
        '          <magic id="orc-magic" level="2" />',
        '        </special>',
        '      </monster>',
        '    </group>',
        '  </table>',
        '</tables>'
      ].join('\n')
    );
  });
});

describe('summarizeMonsterCount', () => {
  it('collapses equal bounds', () => {
    expect(summarizeMonsterCount(3, 3)).toBe('3');
    expect(summarizeMonsterCount(1, 6)).toBe('1-6');
  });
});

describe('parseEventOnlyTable', () => {
  it('round-trips with serializeEventOnlyTable', () => {
    for (const kind of ['dungeon', 'travel', 'settlement'] as const) {
      expect(parseEventOnlyTable(serializeEventOnlyTable('A & B', kind, ['e1', 'e2']))).toEqual({
        name: 'A & B',
        kind,
        eventIds: ['e1', 'e2']
      });
    }
  });

  it('treats a missing or unknown kind as dungeon', () => {
    const xml = '<tables><table name="t" kind="weird"><event id="e" /></table></tables>';
    expect(parseEventOnlyTable(xml)?.kind).toBe('dungeon');
  });

  it('rejects tables with anything other than events, and malformed XML', () => {
    expect(parseEventOnlyTable('<tables><table name="t"><monster id="orc" /></table></tables>')).toBeNull();
    expect(parseEventOnlyTable('<tables><table name="t">')).toBeNull();
    expect(parseEventOnlyTable('<tables></tables>')).toBeNull();
  });
});

describe('parseMonsterOnlyTable', () => {
  it('round-trips with serializeMonsterOnlyTable', () => {
    const entries = [
      monster('orc', { min: 2, max: 4, level: 3, ambiences: ['Caves', 'Crypt'] }),
      group(5, [
        monster('shaman', {
          level: 5,
          special: 'Leader',
          specialLinks: { fear: { text: 'Fear', parameter: '6', parameters: ['6'] } },
          magicType: 'orc-magic',
          magicLevel: 2,
          appendSpecials: false
        }),
        monster('boy', { level: 5 })
      ])
    ];
    expect(parseMonsterOnlyTable(serializeMonsterOnlyTable('Monsters', entries))).toEqual({ name: 'Monsters', entries });
  });

  it('clamps levels to 1-10 and group members inherit the group level', () => {
    const xml = [
      '<tables><table name="t">',
      '<monster id="a" number="1" level="15" />',
      '<group level="0"><monster id="b" number="2" /></group>',
      '</table></tables>'
    ].join('');
    const parsed = parseMonsterOnlyTable(xml);
    expect(parsed?.entries).toEqual([monster('a', { level: 10 }), group(1, [monster('b', { level: 1, min: 2, max: 2 })])]);
  });

  it('rejects events, table references and groups with non-monster members', () => {
    expect(parseMonsterOnlyTable('<tables><table name="t"><event id="e" /></table></tables>')).toBeNull();
    expect(parseMonsterOnlyTable('<tables><table name="t"><tableRef table="x" /></table></tables>')).toBeNull();
    expect(parseMonsterOnlyTable('<tables><table name="t"><group level="1"><event id="e" /></group></table></tables>')).toBeNull();
  });
});
