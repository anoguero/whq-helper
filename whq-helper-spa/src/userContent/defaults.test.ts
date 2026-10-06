// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { monster, table } from '../testing/fixtures';
import type { Rule, SettlementLocation } from '../types';
import {
  createDefaultEvent,
  createDefaultLocation,
  createDefaultMonster,
  createDefaultTable,
  mapLocationToUserData,
  mapRuleToUserData,
  mapTableToUserData
} from './defaults';
import { parseTableName, serializeMonster } from './xml';

describe('default items', () => {
  it('marks only treasure kinds as treasure', () => {
    expect(createDefaultEvent('treasure').treasure).toBe(true);
    expect(createDefaultEvent('objectiveTreasure').treasure).toBe(true);
    expect(createDefaultEvent('dungeonEvent').treasure).toBe(false);
  });

  it('builds a default table whose XML matches its name and kind', () => {
    const data = createDefaultTable();
    expect(parseTableName(data.xml)).toEqual({ name: data.name, kind: data.kind });
  });

  it('builds a default monster that serializes to well-formed XML', () => {
    const doc = new DOMParser().parseFromString(serializeMonster(createDefaultMonster()), 'text/xml');
    expect(doc.querySelector('parsererror')).toBeNull();
  });

  it('starts locations available in cities and open to every visitor', () => {
    expect(createDefaultLocation()).toMatchObject({ availableTypes: ['city'], visitors: ['all'] });
  });
});

describe('mapping base content to user data', () => {
  it('maps a table to its serialized XML, keeping name and kind', () => {
    const data = mapTableToUserData(table('Base Table', { kind: 'travel', kindRaw: 'travel', monsters: [monster('orc')] }));
    expect(data).toMatchObject({ name: 'Base Table', kind: 'travel' });
    expect(parseTableName(data.xml)).toEqual({ name: 'Base Table', kind: 'travel' });
  });

  it('maps any non-magic rule type to rule', () => {
    const rule: Rule = { id: 'r', name: 'R', text: 't', type: 'special', parameterName: '', parameterNames: [], parameterFormat: '' };
    expect(mapRuleToUserData(rule).type).toBe('rule');
    expect(mapRuleToUserData({ ...rule, type: 'magic' }).type).toBe('magic');
  });

  it('copies the lists of a location instead of sharing them', () => {
    const location: SettlementLocation = {
      id: 'l',
      name: 'Inn',
      availableTypes: ['town'],
      description: '',
      visitors: ['all'],
      rules: ''
    };
    const data = mapLocationToUserData(location);
    data.visitors.push('elf');
    data.availableTypes.push('city');
    expect(location.visitors).toEqual(['all']);
    expect(location.availableTypes).toEqual(['town']);
  });
});
