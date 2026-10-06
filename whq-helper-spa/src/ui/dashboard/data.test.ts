import { describe, expect, it } from 'vitest';
import { serializeEventOnlyTable, serializeMonsterOnlyTable, summarizeMonsterCount } from './data';
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
