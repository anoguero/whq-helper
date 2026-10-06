import { afterEach, describe, expect, it, vi } from 'vitest';
import { applyTableActiveState, buildDecks, getMonsterNumber, toHitRollNeeded } from './deck';
import type { DrawEntry, EventList } from './types';
import { event, group, monster, repository, settings, table, tableRef } from './testing/fixtures';

afterEach(() => {
  vi.restoreAllMocks();
});

function drawMany(list: EventList, times: number): Array<DrawEntry | null> {
  return Array.from({ length: times }, () => list.draw());
}

function ids(entries: Array<DrawEntry | null>): string[] {
  return entries.map((entry) => (entry && 'id' in entry ? entry.id : String(entry?.kind ?? null)));
}

describe('toHitRollNeeded', () => {
  it.each([
    [4, 4, 4],
    [4, 3, 3],
    [10, 4, 2],
    [3, 1, 2],
    [2, 1, 3],
    [2, 5, 5],
    [2, 7, 6]
  ])('monster WS %i against target WS %i needs %i', (monsterWs, targetWs, expected) => {
    expect(toHitRollNeeded(monsterWs, targetWs)).toBe(expected);
  });
});

describe('getMonsterNumber', () => {
  it('scales a fixed number by party size over four, never below one', () => {
    expect(getMonsterNumber(monster('orc', { min: 4, max: 4 }), 4)).toBe(4);
    expect(getMonsterNumber(monster('orc', { min: 4, max: 4 }), 2)).toBe(2);
    expect(getMonsterNumber(monster('orc', { min: 1, max: 1 }), 1)).toBe(1);
    expect(getMonsterNumber(monster('orc', { min: 4, max: 4 }), 0)).toBe(1);
  });

  it('keeps zero as zero', () => {
    expect(getMonsterNumber(monster('orc', { min: 0, max: 0 }), 6)).toBe(0);
  });

  it('rolls within the min-max range', () => {
    vi.spyOn(Math, 'random').mockReturnValue(0.999);
    expect(getMonsterNumber(monster('orc', { min: 2, max: 6 }), 4)).toBe(6);
    vi.spyOn(Math, 'random').mockReturnValue(0);
    expect(getMonsterNumber(monster('orc', { min: 2, max: 6 }), 4)).toBe(2);
  });
});

describe('applyTableActiveState', () => {
  it('applies the state configured by table id and defaults to active', () => {
    const repo = repository([table('A', { active: false }), table('B', { active: true }), table('C', { active: false })]);
    applyTableActiveState(repo, settings({ tableActiveById: { a: true, b: false } }));
    expect(repo.tables.get('A')?.active).toBe(true);
    expect(repo.tables.get('B')?.active).toBe(false);
    expect(repo.tables.get('C')?.active).toBe(true);
  });

  it('keeps the state of a renamed table, because it is stored by id', () => {
    const config = settings({ tableActiveById: { 'hag-1': false } });
    const renamed = repository([table('Hag Queen Monsters (Level 1)', { id: 'hag-1' })]);
    applyTableActiveState(renamed, config);
    expect(renamed.tables.get('Hag Queen Monsters (Level 1)')?.active).toBe(false);
  });

  it('migrates state saved by visible name before applying it', () => {
    const repo = repository([table('Hag Queen - Level 1', { id: 'hag-1' })]);
    const config = settings({ legacyTableActive: { 'Hag Queen - Level 1': false } });
    applyTableActiveState(repo, config);
    expect(repo.tables.get('Hag Queen - Level 1')?.active).toBe(false);
    expect(config.tableActiveById).toEqual({ 'hag-1': false });
    expect(config.legacyTableActive).toEqual({});
  });
});

describe('buildDecks in simulated-deck mode', () => {
  it('routes entries by table kind and skips inactive tables', () => {
    const repo = repository([
      table('dungeon', { monsters: [monster('orc'), monster('goblin')], events: [event('ev')] }),
      table('travel', { kind: 'travel', events: [event('road')] }),
      table('settlement', { kind: 'settlement', events: [event('inn')] }),
      table('off', { active: false, monsters: [monster('troll')] })
    ]);
    const decks = buildDecks(repo, settings({ simulateDeck: true }));
    expect(decks.dungeon.size()).toBe(3);
    expect(decks.travel.size()).toBe(1);
    expect(decks.settlement.size()).toBe(1);
    expect(ids(drawMany(decks.dungeon, 3)).sort()).toEqual(['ev', 'goblin', 'orc']);
  });

  it('splits treasure into dungeon and objective decks by the -objective- id marker', () => {
    const repo = repository([
      table('treasure', { kind: 'treasure', events: [event('rpb-treasure-sword'), event('rpb-objective-crown')] })
    ]);
    const decks = buildDecks(repo, settings({ simulateDeck: true }));
    expect(ids(drawMany(decks.treasure, 1))).toEqual(['rpb-treasure-sword']);
    expect(ids(drawMany(decks.objectiveTreasure, 1))).toEqual(['rpb-objective-crown']);
  });

  it('adds dungeon gold to the treasure deck when it exists but no active table carries it', () => {
    const repo = repository([table('treasure', { kind: 'treasure', events: [event('sword')] })], ['rpb-treasure-dungeon-gold']);
    const decks = buildDecks(repo, settings({ simulateDeck: true }));
    expect(ids(drawMany(decks.treasure, 2)).sort()).toEqual(['rpb-treasure-dungeon-gold', 'sword']);
  });

  it('reshuffles the discard pile once the draw pile is exhausted', () => {
    const repo = repository([table('dungeon', { monsters: [monster('orc'), monster('goblin')] })]);
    const decks = buildDecks(repo, settings({ simulateDeck: true }));
    const firstRound = ids(drawMany(decks.dungeon, 2)).sort();
    const secondRound = ids(drawMany(decks.dungeon, 2)).sort();
    expect(firstRound).toEqual(['goblin', 'orc']);
    expect(secondRound).toEqual(['goblin', 'orc']);
  });
});

describe('buildDecks in table mode', () => {
  it('returns null from an empty table', () => {
    const decks = buildDecks(repository([]), settings());
    expect(decks.dungeon.draw()).toBeNull();
  });

  it('only draws monsters of the active dungeon level', () => {
    const repo = repository([table('dungeon', { monsters: [monster('l1', { level: 1 }), monster('l2', { level: 2 })] })]);
    const decks = buildDecks(repo, settings({ dungeonActive: true, activeDungeonLevel: 2 }));
    expect(new Set(ids(drawMany(decks.dungeon, 20)))).toEqual(new Set(['l2']));
  });

  it('filters by ambience and falls back to everything when nothing matches', () => {
    const repo = repository([
      table('dungeon', { monsters: [monster('cave', { ambiences: ['Caves'] }), monster('crypt', { ambiences: ['Crypt'] })] })
    ]);
    const caves = buildDecks(repo, settings({ adventureAmbience: 'caves' }));
    expect(new Set(ids(drawMany(caves.dungeon, 20)))).toEqual(new Set(['cave']));
    const swamp = buildDecks(repo, settings({ adventureAmbience: 'swamp' }));
    expect(new Set(ids(drawMany(swamp.dungeon, 50)))).toEqual(new Set(['cave', 'crypt']));
  });

  it('uses the event probability to choose between events and monsters', () => {
    const repo = repository([table('dungeon', { monsters: [monster('orc')], events: [event('ev')] })]);
    expect(ids(drawMany(buildDecks(repo, settings({ eventProbability: 0 })).dungeon, 10))).toEqual(Array(10).fill('orc'));
    expect(ids(drawMany(buildDecks(repo, settings({ eventProbability: 100 })).dungeon, 10))).toEqual(Array(10).fill('ev'));
  });

  it('resolves table references at their target level, combining repeated draws into a group', () => {
    const repo = repository([
      table('dungeon', { monsters: [tableRef('minions', { level: 3, targetLevel: 1, times: 2 })] }),
      table('minions', { active: false, monsters: [monster('rat', { level: 1 }), monster('ogre', { level: 5 })] })
    ]);
    const drawn = buildDecks(repo, settings({ dungeonActive: true, activeDungeonLevel: 3 })).dungeon.draw();
    expect(drawn).toEqual(group(3, [monster('rat', { level: 1 }), monster('rat', { level: 1 })]));
  });

  it('respects the target level of a table reference even without an active dungeon', () => {
    // El fallo: sin mazmorra activa se ignoraba targetLevel y salia cualquier nivel (Java siempre lo aplica).
    const repo = repository([
      table('dungeon', { monsters: [tableRef('minions', { level: 3, targetLevel: 1 })] }),
      table('minions', { active: false, monsters: [monster('rat', { level: 1 }), monster('ogre', { level: 5 })] })
    ]);
    const decks = buildDecks(repo, settings({ dungeonActive: false }));
    expect(new Set(ids(drawMany(decks.dungeon, 40)))).toEqual(new Set(['rat']));
  });

  it('resolves a table reference by stable id, so renaming the referenced table keeps it working', () => {
    // El fallo: se buscaba solo por nombre visible y, al renombrar la tabla, la referencia no la encontraba.
    const repo = repository([
      table('dungeon', { monsters: [tableRef('minions', { targetLevel: 1 })] }),
      table('Minions (renamed)', { id: 'minions', active: false, monsters: [monster('rat', { level: 1 })] })
    ]);
    expect(buildDecks(repo, settings()).dungeon.draw()).toEqual(monster('rat', { level: 1 }));
  });

  it('gives up on a table reference cycle instead of recursing forever', () => {
    const repo = repository([
      table('a', { monsters: [tableRef('b')] }),
      table('b', { active: false, monsters: [tableRef('a')] })
    ]);
    expect(buildDecks(repo, settings()).dungeon.draw()).toBeNull();
  });

  it('draws dungeon gold according to its probability when the treasure table also has other entries', () => {
    const repo = repository([table('treasure', { kind: 'treasure', events: [event('rpb-treasure-dungeon-gold'), event('sword')] })]);
    expect(ids(drawMany(buildDecks(repo, settings({ treasureGoldProbability: 100 })).treasure, 5))).toEqual(
      Array(5).fill('rpb-treasure-dungeon-gold')
    );
    expect(ids(drawMany(buildDecks(repo, settings({ treasureGoldProbability: 0 })).treasure, 5))).toEqual(Array(5).fill('sword'));
  });
});
