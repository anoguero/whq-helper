import { describe, expect, it } from 'vitest';
import {
  buildSpecialRuleText,
  escapeHtml,
  formatRuleLinks,
  inferRuleParameters,
  joinCsv,
  ruleParameterLabels,
  treasureUsersFromFlags,
  treasureUsersToFlags
} from './formatting';

describe('escapeHtml', () => {
  it('escapes the five HTML-sensitive characters', () => {
    expect(escapeHtml(`<a href="x">Tom & Jerry's</a>`)).toBe(
      '&lt;a href=&quot;x&quot;&gt;Tom &amp; Jerry&#39;s&lt;/a&gt;'
    );
  });

  it('escapes the ampersand first, so entities are not double-escaped by later passes', () => {
    expect(escapeHtml('&lt;')).toBe('&amp;lt;');
  });
});

describe('joinCsv', () => {
  it('joins with comma and space', () => {
    expect(joinCsv(['a', 'b', 'c'])).toBe('a, b, c');
    expect(joinCsv([])).toBe('');
  });
});

describe('formatRuleLinks', () => {
  it('formats one line per link with its id and parameter', () => {
    expect(
      formatRuleLinks({
        fear: { text: 'Fear', parameter: ' 4 ' },
        ambush: { text: 'Ambush', parameter: '' }
      })
    ).toBe('Fear (fear) [4]\nAmbush (ambush)');
  });

  it('lists every parameter when there is more than one', () => {
    const links = { breath: { text: 'Breath', parameter: '1', parameters: ['1', '2D6'] } };
    expect(formatRuleLinks(links)).toBe('Breath (breath) [1 | 2D6]');
  });
});

describe('buildSpecialRuleText', () => {
  it('returns the bare name when every parameter is empty', () => {
    expect(buildSpecialRuleText(' Fear ', ['', ' '])).toBe('Fear');
  });

  it('appends the first parameter when there is no format', () => {
    expect(buildSpecialRuleText('Fear', ['4'])).toBe('Fear 4');
  });

  it('fills {name}, {param} and positional placeholders from the format', () => {
    expect(buildSpecialRuleText('Breath', ['3', '2D6'], '{name} ({param}) - {1} wounds')).toBe('Breath (3) - 2D6 wounds');
  });
});

describe('inferRuleParameters', () => {
  it('inverts buildSpecialRuleText for a format with several placeholders', () => {
    const format = '{name} ({0}) - {1} wounds';
    const text = buildSpecialRuleText('Breath', ['3', '2D6'], format);
    expect(inferRuleParameters('Breath', text, format)).toEqual(['3', '2D6']);
  });

  it('falls back to the text after the name, case-insensitively', () => {
    expect(inferRuleParameters('Fear', 'fear 4')).toEqual(['4']);
  });

  it('returns nothing when the text does not start with the name', () => {
    expect(inferRuleParameters('Fear', 'Terror 4')).toEqual([]);
    expect(inferRuleParameters('', 'Fear 4')).toEqual([]);
  });
});

describe('ruleParameterLabels', () => {
  it('prefers parameterNames over parameterName', () => {
    expect(ruleParameterLabels({ parameterName: 'X', parameterNames: ['A', 'B'] })).toEqual(['A', 'B']);
    expect(ruleParameterLabels({ parameterName: 'X', parameterNames: [] })).toEqual(['X']);
    expect(ruleParameterLabels({ parameterName: '', parameterNames: [] })).toEqual([]);
    expect(ruleParameterLabels(undefined)).toEqual([]);
  });
});

describe('treasure users', () => {
  it('converts to flags case-insensitively and back in canonical order', () => {
    const flags = treasureUsersToFlags(' wb ');
    expect(flags).toEqual({ B: true, D: false, E: false, W: true });
    expect(treasureUsersFromFlags(flags)).toBe('BW');
    expect(treasureUsersFromFlags(treasureUsersToFlags('EWDB'))).toBe('BDEW');
  });
});
