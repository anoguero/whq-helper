import type {
  AppSettings,
  ContentRepository,
  DeckBundle,
  DrawEntry,
  EventEntry,
  EventList,
  GroupEntry,
  MonsterEntry,
  TableKind,
  TableModel
} from './types';

const DUNGEON_GOLD_TREASURE_ID = 'rpb-treasure-dungeon-gold';

function randomIndex(length: number): number {
  return Math.floor(Math.random() * length);
}

function randomEntry<T>(source: T[]): T | null {
  if (source.length === 0) {
    return null;
  }
  return source[randomIndex(source.length)] ?? null;
}

function shuffle<T>(items: T[]): void {
  for (let i = items.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [items[i], items[j]] = [items[j] as T, items[i] as T];
  }
}

function matchesAmbience(ambiences: string[], selected: string): boolean {
  if (selected === 'generic') {
    return true;
  }
  if (ambiences.length === 0) {
    return true;
  }
  return ambiences.some((ambience) => ambience.toLowerCase() === selected.toLowerCase());
}

function clampProbability(value: number): number {
  return Math.max(0, Math.min(100, value));
}

class DeckList implements EventList {
  private drawPile: DrawEntry[] = [];
  private discard: DrawEntry[] = [];

  addEntry(entry: DrawEntry): void {
    this.drawPile.push(entry);
  }

  addEntries(entries: DrawEntry[]): void {
    this.drawPile.push(...entries);
  }

  draw(): DrawEntry | null {
    if (this.drawPile.length === 0) {
      this.shuffle();
    }
    const item = this.drawPile.shift() ?? null;
    if (item) {
      this.discard.push(item);
    }
    return item;
  }

  size(): number {
    return this.drawPile.length + this.discard.length;
  }

  shuffle(): void {
    this.drawPile.push(...this.discard);
    this.discard.length = 0;
    shuffle(this.drawPile);
  }
}

class TableList implements EventList {
  private readonly kind: TableKind;
  private readonly settings: AppSettings;
  private monsters: Array<MonsterEntry | GroupEntry> = [];
  private events: EventEntry[] = [];

  constructor(kind: TableKind, settings: AppSettings) {
    this.kind = kind;
    this.settings = settings;
  }

  addEntry(entry: DrawEntry): void {
    if (entry.kind === 'event') {
      this.events.push(entry);
    } else {
      this.monsters.push(entry);
    }
  }

  addEntries(entries: DrawEntry[]): void {
    for (const entry of entries) {
      this.addEntry(entry);
    }
  }

  draw(): DrawEntry | null {
    const filteredMonsters = this.filterMonsters(this.monsters, this.settings.adventureAmbience);
    const hasMonsters = filteredMonsters.length > 0;
    const hasEvents = this.events.length > 0;

    if (!hasMonsters && !hasEvents) {
      return null;
    }

    if (this.kind === 'treasure' && hasEvents && !hasMonsters) {
      return this.drawTreasureEntry(this.events);
    }

    if (hasMonsters && hasEvents) {
      const drawEvent = Math.random() * 100 < clampProbability(this.settings.eventProbability);
      return drawEvent ? randomEntry(this.events) : randomEntry(filteredMonsters);
    }

    return hasMonsters ? randomEntry(filteredMonsters) : randomEntry(this.events);
  }

  size(): number {
    return this.monsters.length + this.events.length;
  }

  private drawTreasureEntry(entries: EventEntry[]): EventEntry | null {
    const goldEntries = entries.filter((entry) => entry.id.trim().toLowerCase() === DUNGEON_GOLD_TREASURE_ID);
    const regularEntries = entries.filter((entry) => entry.id.trim().toLowerCase() !== DUNGEON_GOLD_TREASURE_ID);

    if (goldEntries.length === 0 || regularEntries.length === 0) {
      return randomEntry(entries);
    }

    const drawGold = Math.random() * 100 < clampProbability(this.settings.treasureGoldProbability);
    return drawGold ? randomEntry(goldEntries) : randomEntry(regularEntries);
  }

  private filterMonsters(
    monsters: Array<MonsterEntry | GroupEntry>,
    selectedAmbience: string
  ): Array<MonsterEntry | GroupEntry> {
    const ambienceFiltered =
      selectedAmbience === 'generic'
        ? monsters
        : monsters.filter((entry) => {
            if (entry.kind === 'group') {
              return entry.entries.every((groupMember) => matchesAmbience(groupMember.ambiences, selectedAmbience));
            }
            return matchesAmbience(entry.ambiences, selectedAmbience);
          });

    const fallbackAmbience = ambienceFiltered.length > 0 ? ambienceFiltered : monsters;

    if (!this.settings.dungeonActive || this.kind !== 'dungeon') {
      return fallbackAmbience;
    }

    const level = Math.max(1, Math.min(10, this.settings.activeDungeonLevel));
    return fallbackAmbience.filter((entry) => {
      if (entry.kind === 'group') {
        return entry.level === level;
      }
      return entry.level === level;
    });
  }
}

function toDrawEntries(table: TableModel): DrawEntry[] {
  return [...table.monsters, ...table.events];
}

function filterTreasureEntries(entries: DrawEntry[], dungeonTreasure: boolean): EventEntry[] {
  return entries
    .filter((entry): entry is EventEntry => entry.kind === 'event')
    .filter((entry) => {
      const isObjective = entry.id.trim().toLowerCase().includes('-objective-');
      return dungeonTreasure ? !isObjective : isObjective;
    });
}

function hasActiveDungeonGoldTreasureEntry(repository: ContentRepository): boolean {
  for (const table of repository.tables.values()) {
    if (!table.active || table.kind !== 'treasure') {
      continue;
    }
    if (table.events.some((entry) => entry.id.trim().toLowerCase() === DUNGEON_GOLD_TREASURE_ID)) {
      return true;
    }
  }
  return false;
}

export function applyTableActiveState(repository: ContentRepository, settings: AppSettings): void {
  for (const table of repository.tables.values()) {
    const configured = settings.tableActive[table.name];
    table.active = configured !== undefined ? configured : true;
  }
}

export function buildDecks(repository: ContentRepository, settings: AppSettings): DeckBundle {
  const dungeon = settings.simulateDeck ? new DeckList() : new TableList('dungeon', settings);
  const travel = settings.simulateDeck ? new DeckList() : new TableList('travel', settings);
  const settlement = settings.simulateDeck ? new DeckList() : new TableList('settlement', settings);
  const treasure = settings.simulateDeck ? new DeckList() : new TableList('treasure', settings);
  const objectiveTreasure = settings.simulateDeck ? new DeckList() : new TableList('treasure', settings);

  for (const table of repository.tables.values()) {
    if (!table.active) {
      continue;
    }

    const entries = toDrawEntries(table);

    if (table.kind === 'travel') {
      travel.addEntries(entries);
    } else if (table.kind === 'settlement') {
      settlement.addEntries(entries);
    } else if (table.kind === 'treasure') {
      treasure.addEntries(filterTreasureEntries(entries, true));
      objectiveTreasure.addEntries(filterTreasureEntries(entries, false));
    } else {
      dungeon.addEntries(entries);
    }
  }

  if (repository.events.has(DUNGEON_GOLD_TREASURE_ID) && !hasActiveDungeonGoldTreasureEntry(repository)) {
    treasure.addEntry({ kind: 'event', id: DUNGEON_GOLD_TREASURE_ID, ambiences: [] });
  }

  if (settings.simulateDeck) {
    (dungeon as DeckList).shuffle();
    (travel as DeckList).shuffle();
    (settlement as DeckList).shuffle();
    (treasure as DeckList).shuffle();
    (objectiveTreasure as DeckList).shuffle();
  }

  return {
    dungeon,
    travel,
    settlement,
    treasure,
    objectiveTreasure
  };
}

export function getMonsterNumber(entry: MonsterEntry, partySize: number): number {
  const base = entry.max <= entry.min ? entry.min : entry.min + Math.floor(Math.random() * (entry.max - entry.min + 1));
  if (base === 0) {
    return 0;
  }
  return Math.max(1, Math.floor((base * Math.max(1, partySize)) / 4));
}

export function toHitRollNeeded(monsterWs: number, targetWs: number): number {
  if (targetWs < monsterWs - 5 || (monsterWs > 2 && targetWs === 1)) {
    return 2;
  }
  if (targetWs < monsterWs) {
    return 3;
  }
  if (targetWs <= monsterWs * 2) {
    return 4;
  }
  if (targetWs <= monsterWs * 3) {
    return 5;
  }
  return 6;
}
