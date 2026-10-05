import { t } from '../i18n';
import type {
  AppSettings,
  ContentRepository,
  DrawEntry,
  DungeonCard,
  GroupEntry,
  LanguageCode,
  MonsterEntry,
  TableRefEntry
} from '../types';

export interface AdventureSimulatorState {
  piles: DungeonCard[][];
  histories: DungeonCard[][];
  selectedCard: DungeonCard | null;
  selectedPile: number;
}

export type ObjectiveDifficultyId = 'easy' | 'normal' | 'hard' | 'veryHard' | 'extreme';

export interface ObjectiveDifficulty {
  id: ObjectiveDifficultyId;
  offsets: number[];
}

export const OBJECTIVE_DIFFICULTIES: ObjectiveDifficulty[] = [
  { id: 'easy', offsets: [0, 0] },
  { id: 'normal', offsets: [0, 0, 0] },
  { id: 'hard', offsets: [1, 0, 0] },
  { id: 'veryHard', offsets: [1, 1, 0] },
  { id: 'extreme', offsets: [2, 1, 0] }
];

export function pickCardsByCopies(pool: DungeonCard[], count: number): DungeonCard[] {
  if (count <= 0) {
    return [];
  }

  const expanded: DungeonCard[] = [];
  for (const card of pool) {
    for (let i = 0; i < card.copyCount; i += 1) {
      expanded.push(card);
    }
  }

  for (let i = expanded.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [expanded[i], expanded[j]] = [expanded[j] as DungeonCard, expanded[i] as DungeonCard];
  }

  return expanded.slice(0, count);
}

export function shuffleCards(cards: DungeonCard[]): void {
  for (let i = cards.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [cards[i], cards[j]] = [cards[j] as DungeonCard, cards[i] as DungeonCard];
  }
}

export function collectAdventureCardIds(state: AdventureSimulatorState): Set<number> {
  const ids = new Set<number>();
  for (const pile of state.piles) {
    for (const card of pile) {
      ids.add(card.id);
    }
  }
  for (const history of state.histories) {
    for (const card of history) {
      ids.add(card.id);
    }
  }
  if (state.selectedCard) {
    ids.add(state.selectedCard.id);
  }
  return ids;
}

export function pickAdditionalAdventureCards(environment: string, cards: DungeonCard[], existingIds: Set<number>): DungeonCard[] {
  const eligible = cards
    .filter((card) => card.environment.localeCompare(environment, undefined, { sensitivity: 'base' }) === 0)
    .filter((card) => card.enabled && card.copyCount > 0)
    .filter((card) => card.type !== 'OBJECTIVE_ROOM')
    .filter((card) => !existingIds.has(card.id));

  shuffleCards(eligible);
  return eligible;
}

export function matchesMonsterAmbience(entry: MonsterEntry | GroupEntry | TableRefEntry, selectedAmbience: string): boolean {
  if (selectedAmbience === 'generic') {
    return true;
  }
  if (entry.kind === 'group') {
    return entry.entries.every((groupMember) => {
      if (groupMember.ambiences.length === 0) {
        return true;
      }
      return groupMember.ambiences.some(
        (ambience) => ambience.localeCompare(selectedAmbience, undefined, { sensitivity: 'base' }) === 0
      );
    });
  }
  if (entry.kind === 'tableRef') {
    if (entry.ambiences.length === 0) {
      return true;
    }
    return entry.ambiences.some(
      (ambience) => ambience.localeCompare(selectedAmbience, undefined, { sensitivity: 'base' }) === 0
    );
  }
  if (entry.ambiences.length === 0) {
    return true;
  }
  return entry.ambiences.some(
    (ambience) => ambience.localeCompare(selectedAmbience, undefined, { sensitivity: 'base' }) === 0
  );
}

export function activeDungeonMonsterEntries(
  repositoryToUse: ContentRepository,
  selectedAmbience: string
): Array<MonsterEntry | GroupEntry | TableRefEntry> {
  const activeEntries = Array.from(repositoryToUse.tables.values())
    .filter((table) => table.active && table.kind === 'dungeon')
    .flatMap((table) => table.monsters);

  if (selectedAmbience === 'generic') {
    return activeEntries;
  }

  const ambienceFiltered = activeEntries.filter((entry) => matchesMonsterAmbience(entry, selectedAmbience));
  return ambienceFiltered.length > 0 ? ambienceFiltered : activeEntries;
}

export function availableObjectiveMonsterLevels(repositoryToUse: ContentRepository, selectedAmbience: string): number[] {
  return [...new Set(activeDungeonMonsterEntries(repositoryToUse, selectedAmbience).map((entry) => entry.level))].sort(
    (a, b) => a - b
  );
}

export function pickRandomMonsterEncounter(
  repositoryToUse: ContentRepository,
  selectedAmbience: string,
  level: number
): DrawEntry | null {
  const entries = activeDungeonMonsterEntries(repositoryToUse, selectedAmbience).filter((entry) => entry.level === level);
  if (entries.length === 0) {
    return null;
  }
  const selected = entries[Math.floor(Math.random() * entries.length)] ?? null;
  if (!selected || selected.kind !== 'tableRef') {
    return selected;
  }
  return resolveObjectiveTableRef(repositoryToUse, selectedAmbience, selected, new Set<string>());
}

export function resolveObjectiveTableRef(
  repositoryToUse: ContentRepository,
  selectedAmbience: string,
  entry: TableRefEntry,
  visited: Set<string>
): DrawEntry | null {
  const referencedTable = repositoryToUse.tables.get(entry.tableName);
  if (!referencedTable || visited.has(entry.tableName)) {
    return null;
  }

  visited.add(entry.tableName);
  try {
    for (let attempt = 0; attempt < 32; attempt += 1) {
      const referencedEntries = referencedTable.monsters.filter((candidate) => {
        if (candidate.level !== entry.targetLevel) {
          return false;
        }
        return matchesMonsterAmbience(candidate, selectedAmbience);
      });
      if (referencedEntries.length === 0) {
        return null;
      }

      const drawn = referencedEntries[Math.floor(Math.random() * referencedEntries.length)] ?? null;
      if (!drawn) {
        continue;
      }
      if (drawn.kind !== 'tableRef') {
        return drawn;
      }

      const resolved = resolveObjectiveTableRef(repositoryToUse, selectedAmbience, drawn, visited);
      if (resolved) {
        return resolved;
      }
    }
    return null;
  } finally {
    visited.delete(entry.tableName);
  }
}

export function resolveObjectiveEncounterLevels(
  repositoryToUse: ContentRepository,
  selectedAmbience: string,
  requestedLevel: number
): number[] {
  const availableLevels = availableObjectiveMonsterLevels(repositoryToUse, selectedAmbience);
  if (availableLevels.length === 0) {
    return [];
  }
  if (availableLevels.includes(requestedLevel)) {
    return [requestedLevel];
  }

  const previousAvailable = [...availableLevels].reverse().find((level) => level <= requestedLevel);
  if (previousAvailable !== undefined) {
    return [previousAvailable, previousAvailable];
  }

  const fallbackLevel = availableLevels[0] as number;
  return [fallbackLevel, fallbackLevel];
}

export function objectiveDifficultyWeight(settingsToUse: AppSettings, id: ObjectiveDifficultyId): number {
  if (id === 'easy') {
    return settingsToUse.objectiveMonsterEasyWeight;
  }
  if (id === 'normal') {
    return settingsToUse.objectiveMonsterNormalWeight;
  }
  if (id === 'hard') {
    return settingsToUse.objectiveMonsterHardWeight;
  }
  if (id === 'veryHard') {
    return settingsToUse.objectiveMonsterVeryHardWeight;
  }
  return settingsToUse.objectiveMonsterExtremeWeight;
}

export function rollObjectiveDifficulty(settingsToUse: AppSettings): ObjectiveDifficulty | null {
  const totalWeight = OBJECTIVE_DIFFICULTIES.reduce(
    (sum, difficulty) => sum + Math.max(0, objectiveDifficultyWeight(settingsToUse, difficulty.id)),
    0
  );
  if (totalWeight <= 0) {
    return null;
  }

  let roll = Math.floor(Math.random() * totalWeight);
  for (const difficulty of OBJECTIVE_DIFFICULTIES) {
    roll -= Math.max(0, objectiveDifficultyWeight(settingsToUse, difficulty.id));
    if (roll < 0) {
      return difficulty;
    }
  }
  return OBJECTIVE_DIFFICULTIES[OBJECTIVE_DIFFICULTIES.length - 1] as ObjectiveDifficulty;
}

export function objectiveDifficultyLabel(language: LanguageCode, id: ObjectiveDifficultyId): string {
  if (id === 'easy') {
    return t(language, 'newDungeon.objectiveMonsterEasy');
  }
  if (id === 'normal') {
    return t(language, 'newDungeon.objectiveMonsterNormal');
  }
  if (id === 'hard') {
    return t(language, 'newDungeon.objectiveMonsterHard');
  }
  if (id === 'veryHard') {
    return t(language, 'newDungeon.objectiveMonsterVeryHard');
  }
  return t(language, 'newDungeon.objectiveMonsterExtreme');
}

export function generateObjectiveRoomMonsterEntries(
  repositoryToUse: ContentRepository,
  settingsToUse: AppSettings,
  dungeonLevel: number
): { difficulty: ObjectiveDifficulty; entries: DrawEntry[] } | null {
  const difficulty = rollObjectiveDifficulty(settingsToUse);
  if (!difficulty) {
    return null;
  }

  const entries: DrawEntry[] = [];
  for (const offset of difficulty.offsets) {
    const resolvedLevels = resolveObjectiveEncounterLevels(
      repositoryToUse,
      settingsToUse.adventureAmbience,
      dungeonLevel + offset
    );
    for (const resolvedLevel of resolvedLevels) {
      const entry = pickRandomMonsterEncounter(repositoryToUse, settingsToUse.adventureAmbience, resolvedLevel);
      if (entry) {
        entries.push(entry);
      }
    }
  }

  return { difficulty, entries };
}

export function buildAdventureDeck(
  environment: string,
  objectiveRoom: DungeonCard,
  deckSize: number,
  roomCount: number,
  cards: DungeonCard[]
): DungeonCard[] {
  if (deckSize < 2) {
    throw new Error('El mazo debe tener al menos 2 cartas.');
  }
  if (roomCount < 1 || roomCount >= deckSize) {
    throw new Error('El número de habitaciones debe ser menor que el tamaño del mazo.');
  }

  const environmentCards = cards
    .filter((card) => card.environment.toLowerCase() === environment.toLowerCase())
    .filter((card) => card.enabled && card.copyCount > 0);

  const dungeonPool = environmentCards.filter((card) => card.type === 'DUNGEON_ROOM');
  const fillerPool = environmentCards.filter((card) => card.type === 'CORRIDOR' || card.type === 'SPECIAL');

  const availableDungeon = dungeonPool.reduce((sum, card) => sum + card.copyCount, 0);
  if (roomCount > availableDungeon) {
    throw new Error('No hay suficientes habitaciones de tipo DUNGEON ROOM para ese tamaño.');
  }

  const fillerCount = deckSize - roomCount - 1;
  const availableFiller = fillerPool.reduce((sum, card) => sum + card.copyCount, 0);
  if (fillerCount > availableFiller) {
    throw new Error('No hay suficientes cartas de pasillo/especial para completar el mazo.');
  }

  const dungeonRooms = pickCardsByCopies(dungeonPool, roomCount);
  const fillers = pickCardsByCopies(fillerPool, fillerCount);
  const nonObjective = [...dungeonRooms, ...fillers];

  for (let i = nonObjective.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [nonObjective[i], nonObjective[j]] = [nonObjective[j] as DungeonCard, nonObjective[i] as DungeonCard];
  }

  const deck: Array<DungeonCard | null> = new Array(deckSize).fill(null);
  const minObjectiveIndex = Math.max(0, deckSize - 5);
  const objectiveIndex = minObjectiveIndex + Math.floor(Math.random() * (deckSize - minObjectiveIndex));
  deck[objectiveIndex] = objectiveRoom;

  let idx = 0;
  for (let i = 0; i < deck.length; i += 1) {
    if (!deck[i]) {
      deck[i] = nonObjective[idx] as DungeonCard;
      idx += 1;
    }
  }

  return deck as DungeonCard[];
}

export function splitSelectedPile(piles: DungeonCard[][], selectedIndex: number, pileCount: number): DungeonCard[][] {
  const result: DungeonCard[][] = [];
  for (let i = 0; i < piles.length; i += 1) {
    if (i !== selectedIndex) {
      result.push([...piles[i]!]);
      continue;
    }

    const source = piles[i] as DungeonCard[];
    const split = Array.from({ length: pileCount }, () => [] as DungeonCard[]);
    source.forEach((card, idx) => {
      split[idx % pileCount]!.push(card);
    });
    result.push(...split);
  }
  return result;
}

export function splitSelectedPileHistories(histories: DungeonCard[][], selectedIndex: number, pileCount: number): DungeonCard[][] {
  const result: DungeonCard[][] = [];
  for (let i = 0; i < histories.length; i += 1) {
    if (i !== selectedIndex) {
      result.push([...histories[i]!]);
      continue;
    }

    for (let j = 0; j < pileCount; j += 1) {
      result.push([]);
    }
    const source = histories[i] as DungeonCard[];
    if (source.length > 0) {
      result[result.length - pileCount] = [...source];
    }
  }
  return result;
}
