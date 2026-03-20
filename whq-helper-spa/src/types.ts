export type LanguageCode = 'ES' | 'EN';

export interface Rule {
  id: string;
  name: string;
  text: string;
  type: string;
}

export interface SpecialData {
  special: string;
  specialLinks: Record<string, string>;
  magicType: string;
  magicLevel: number;
}

export interface EventModel {
  id: string;
  name: string;
  category: 'dungeon' | 'travel' | 'settlement';
  flavor: string;
  rules: string;
  special: string;
  goldValue: string;
  users: string;
  treasure: boolean;
}

export interface Monster extends SpecialData {
  id: string;
  name: string;
  plural: string;
  move: string;
  weaponskill: string;
  ballisticskill: string;
  strength: string;
  toughness: string;
  wounds: string;
  initiative: string;
  attacks: string;
  gold: string;
  armor: string;
  damage: string;
  factions: string[];
}

export interface EventEntry {
  kind: 'event';
  id: string;
  ambiences: string[];
}

export interface MonsterEntry extends SpecialData {
  kind: 'monster';
  id: string;
  level: number;
  min: number;
  max: number;
  ambiences: string[];
  appendSpecials: boolean;
}

export interface GroupEntry {
  kind: 'group';
  level: number;
  entries: MonsterEntry[];
}

export interface TableRefEntry {
  kind: 'tableRef';
  tableName: string;
  level: number;
  targetLevel: number;
  times: number;
  ambiences: string[];
}

export type DrawEntry = EventEntry | MonsterEntry | GroupEntry | TableRefEntry;

export type TableKind = 'dungeon' | 'travel' | 'settlement' | 'treasure';

export interface TableModel {
  name: string;
  kindRaw: string;
  kind: TableKind;
  active: boolean;
  monsters: Array<MonsterEntry | GroupEntry | TableRefEntry>;
  events: EventEntry[];
}

export interface ContentRepository {
  monsters: Map<string, Monster>;
  events: Map<string, EventModel>;
  travelEvents: Map<string, EventModel>;
  settlementEvents: Map<string, EventModel>;
  rules: Map<string, Rule>;
  tables: Map<string, TableModel>;
}

export interface AppSettings {
  simulateDeck: boolean;
  showEventDeck: boolean;
  showSettlementDeck: boolean;
  showTravelDeck: boolean;
  showTreasureDeck: boolean;
  showObjectiveTreasureDeck: boolean;
  dungeonActive: boolean;
  activeDungeonLevel: number;
  partySize: number;
  eventProbability: number;
  treasureGoldProbability: number;
  language: LanguageCode;
  adventureAmbience: string;
  objectiveMonsterEasyWeight: number;
  objectiveMonsterNormalWeight: number;
  objectiveMonsterHardWeight: number;
  objectiveMonsterVeryHardWeight: number;
  objectiveMonsterExtremeWeight: number;
  tableActive: Record<string, boolean>;
}

export interface DeckBundle {
  dungeon: EventList;
  travel: EventList;
  settlement: EventList;
  treasure: EventList;
  objectiveTreasure: EventList;
}

export interface EventList {
  draw(): DrawEntry | null;
  addEntry(entry: DrawEntry): void;
  addEntries(entries: DrawEntry[]): void;
  size(): number;
}

export type CardType = 'DUNGEON_ROOM' | 'OBJECTIVE_ROOM' | 'CORRIDOR' | 'SPECIAL';

export interface DungeonCard {
  id: number;
  name: string;
  type: CardType;
  environment: string;
  copyCount: number;
  enabled: boolean;
  descriptionText: string;
  rulesText: string;
  tileImagePath: string;
}

export interface ObjectiveRoomAdventure {
  objectiveRoomName: string;
  id: string;
  name: string;
  flavorText: string;
  rulesText: string;
  generic: boolean;
}
