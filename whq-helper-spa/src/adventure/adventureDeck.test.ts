import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  OBJECTIVE_DIFFICULTIES,
  buildAdventureDeck,
  collectAdventureCardIds,
  generateObjectiveRoomMonsterEntries,
  matchesMonsterAmbience,
  pickAdditionalAdventureCards,
  pickCardsByCopies,
  resolveObjectiveEncounterLevels,
  rollObjectiveDifficulty,
  splitSelectedPile,
  splitSelectedPileHistories
} from './adventureDeck';
import { card, group, monster, repository, settings, table, tableRef } from '../testing/fixtures';

afterEach(() => {
  vi.restoreAllMocks();
});

describe('pickCardsByCopies', () => {
  it('expands copies before picking', () => {
    const picked = pickCardsByCopies([card(1, { copyCount: 3 })], 3);
    expect(picked.map((c) => c.id)).toEqual([1, 1, 1]);
    expect(pickCardsByCopies([card(1)], 0)).toEqual([]);
  });
});

describe('buildAdventureDeck', () => {
  const objective = card(99, { type: 'OBJECTIVE_ROOM' });
  const pool = [
    ...[1, 2, 3, 4].map((id) => card(id, { type: 'DUNGEON_ROOM' })),
    ...[10, 11, 12, 13, 14, 15].map((id) => card(id, { type: 'CORRIDOR' })),
    card(20, { type: 'DUNGEON_ROOM', environment: 'Caves' }),
    card(21, { type: 'DUNGEON_ROOM', enabled: false })
  ];

  it('builds a deck of the requested size with the objective room among the last five cards', () => {
    for (let run = 0; run < 20; run += 1) {
      const deck = buildAdventureDeck('dungeon', objective, 10, 3, pool);
      expect(deck).toHaveLength(10);
      expect(deck.filter((c) => c.id === 99)).toHaveLength(1);
      expect(deck.findIndex((c) => c.id === 99)).toBeGreaterThanOrEqual(5);
      expect(deck.filter((c) => c.type === 'DUNGEON_ROOM')).toHaveLength(3);
      expect(deck.some((c) => c.id === 20 || c.id === 21)).toBe(false);
    }
  });

  it('rejects impossible sizes', () => {
    expect(() => buildAdventureDeck('Dungeon', objective, 1, 1, pool)).toThrow();
    expect(() => buildAdventureDeck('Dungeon', objective, 5, 5, pool)).toThrow();
    expect(() => buildAdventureDeck('Dungeon', objective, 10, 5, pool)).toThrow(/DUNGEON ROOM/);
    expect(() => buildAdventureDeck('Dungeon', objective, 12, 4, pool)).toThrow(/pasillo/);
  });
});

describe('collectAdventureCardIds and pickAdditionalAdventureCards', () => {
  it('collects ids from piles, histories and the selected card', () => {
    const ids = collectAdventureCardIds({
      piles: [[card(1)], [card(2)]],
      histories: [[card(3)]],
      selectedCard: card(4),
      selectedPile: 0
    });
    expect([...ids].sort()).toEqual([1, 2, 3, 4]);
  });

  it('only offers enabled, non-objective cards of the environment that are not in play', () => {
    const cards = [
      card(1),
      card(2),
      card(3, { enabled: false }),
      card(4, { copyCount: 0 }),
      card(5, { type: 'OBJECTIVE_ROOM' }),
      card(6, { environment: 'Caves' })
    ];
    const picked = pickAdditionalAdventureCards('DUNGEON', cards, new Set([1]));
    expect(picked.map((c) => c.id)).toEqual([2]);
  });
});

describe('pile splitting', () => {
  it('deals the selected pile round-robin and copies the others', () => {
    const piles = [[card(1)], [card(2), card(3), card(4), card(5), card(6)]];
    const result = splitSelectedPile(piles, 1, 2);
    expect(result.map((pile) => pile.map((c) => c.id))).toEqual([[1], [2, 4, 6], [3, 5]]);
    expect(result[0]).not.toBe(piles[0]);
  });

  it('keeps the selected history on the first of the new piles', () => {
    const result = splitSelectedPileHistories([[card(1)], [card(2)]], 1, 3);
    expect(result.map((pile) => pile.map((c) => c.id))).toEqual([[1], [2], [], []]);
  });
});

describe('matchesMonsterAmbience', () => {
  it('matches case-insensitively, and entries without ambience match everything', () => {
    expect(matchesMonsterAmbience(monster('a', { ambiences: ['Caves'] }), 'caves')).toBe(true);
    expect(matchesMonsterAmbience(monster('a', { ambiences: ['Caves'] }), 'crypt')).toBe(false);
    expect(matchesMonsterAmbience(monster('a'), 'crypt')).toBe(true);
    expect(matchesMonsterAmbience(monster('a', { ambiences: ['Caves'] }), 'generic')).toBe(true);
  });

  it('requires every group member to match', () => {
    const mixed = group(1, [monster('a', { ambiences: ['Caves'] }), monster('b', { ambiences: ['Crypt'] })]);
    expect(matchesMonsterAmbience(mixed, 'caves')).toBe(false);
  });
});

describe('objective room monsters', () => {
  const repo = repository([
    table('dungeon', { monsters: [monster('l2', { level: 2 }), monster('l4', { level: 4 }), tableRef('minions', { level: 6, targetLevel: 1 })] }),
    table('minions', { active: false, monsters: [monster('rat', { level: 1 })] }),
    table('travel', { kind: 'travel', monsters: [monster('bandit', { level: 3 })] })
  ]);

  it('uses the requested level when it exists among active dungeon tables', () => {
    expect(resolveObjectiveEncounterLevels(repo, 'generic', 4)).toEqual([4]);
  });

  it('doubles the closest lower level when the requested one is missing', () => {
    expect(resolveObjectiveEncounterLevels(repo, 'generic', 3)).toEqual([2, 2]);
  });

  it('doubles the lowest level when nothing is at or below the requested one', () => {
    expect(resolveObjectiveEncounterLevels(repo, 'generic', 1)).toEqual([2, 2]);
    expect(resolveObjectiveEncounterLevels(repository([]), 'generic', 1)).toEqual([]);
  });

  it('rolls no difficulty when every weight is zero', () => {
    expect(rollObjectiveDifficulty(settings())).toBeNull();
  });

  it('rolls the only difficulty with weight', () => {
    expect(rollObjectiveDifficulty(settings({ objectiveMonsterHardWeight: 5 }))?.id).toBe('hard');
  });

  it('generates one encounter per difficulty offset, resolving table references', () => {
    const result = generateObjectiveRoomMonsterEntries(repo, settings({ objectiveMonsterEasyWeight: 1 }), 6);
    expect(result?.difficulty).toBe(OBJECTIVE_DIFFICULTIES[0]);
    expect(result?.entries).toEqual([monster('rat', { level: 1 }), monster('rat', { level: 1 })]);
  });
  it('resolves a table reference by stable id after the referenced table is renamed', () => {
    const renamed = repository([
      table('dungeon', { monsters: [tableRef('minions', { level: 6, targetLevel: 1 })] }),
      table('Minions (renamed)', { id: 'minions', active: false, monsters: [monster('rat', { level: 1 })] })
    ]);
    const result = generateObjectiveRoomMonsterEntries(renamed, settings({ objectiveMonsterEasyWeight: 1 }), 6);
    expect(result?.entries).toEqual([monster('rat', { level: 1 }), monster('rat', { level: 1 })]);
  });
});
