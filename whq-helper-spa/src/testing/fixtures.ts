// Constructores de datos para los tests (*.test.ts). No los importa el código de la aplicación.
import type {
  AppSettings,
  ContentRepository,
  DungeonCard,
  EventEntry,
  EventModel,
  GroupEntry,
  MonsterEntry,
  TableModel,
  TableRefEntry
} from '../types';
import { tableIdFromName } from '../tableIds';

export function settings(overrides: Partial<AppSettings> = {}): AppSettings {
  return {
    simulateDeck: false,
    showEventDeck: true,
    showSettlementDeck: true,
    showTravelDeck: true,
    showTreasureDeck: true,
    showObjectiveTreasureDeck: true,
    dungeonActive: false,
    activeDungeonLevel: 1,
    partySize: 4,
    partyWarriors: [],
    eventProbability: 0,
    treasureGoldProbability: 0,
    language: 'EN',
    adventureAmbience: 'generic',
    objectiveMonsterEasyWeight: 0,
    objectiveMonsterNormalWeight: 0,
    objectiveMonsterHardWeight: 0,
    objectiveMonsterVeryHardWeight: 0,
    objectiveMonsterExtremeWeight: 0,
    tableActiveById: {},
    legacyTableActive: {},
    ...overrides
  };
}

export function repository(tables: TableModel[] = [], events: string[] = []): ContentRepository {
  return {
    monsters: new Map(),
    events: new Map(events.map((id) => [id, eventModel(id)])),
    travelEvents: new Map(),
    settlementEvents: new Map(),
    rules: new Map(),
    tables: new Map(tables.map((table) => [table.name, table])),
    warriors: new Map(),
    locations: new Map()
  };
}

export function eventModel(id: string): EventModel {
  return {
    id,
    name: id,
    category: 'dungeon',
    flavor: '',
    rules: '',
    special: '',
    goldValue: '',
    users: '',
    treasure: true
  };
}

export function table(name: string, overrides: Partial<TableModel> = {}): TableModel {
  return {
    id: tableIdFromName(name),
    name,
    kindRaw: overrides.kind ?? 'dungeon',
    kind: 'dungeon',
    active: true,
    monsters: [],
    events: [],
    ...overrides
  };
}

export function monster(id: string, overrides: Partial<MonsterEntry> = {}): MonsterEntry {
  return {
    kind: 'monster',
    id,
    level: 1,
    min: 1,
    max: 1,
    ambiences: [],
    special: '',
    specialLinks: {},
    magicType: '',
    magicLevel: 0,
    appendSpecials: true,
    ...overrides
  };
}

export function group(level: number, entries: MonsterEntry[]): GroupEntry {
  return { kind: 'group', level, entries };
}

export function tableRef(tableName: string, overrides: Partial<TableRefEntry> = {}): TableRefEntry {
  return {
    kind: 'tableRef',
    tableName,
    level: 1,
    targetLevel: 1,
    times: 1,
    ambiences: [],
    ...overrides
  };
}

export function event(id: string): EventEntry {
  return { kind: 'event', id, ambiences: [] };
}

export function card(id: number, overrides: Partial<DungeonCard> = {}): DungeonCard {
  return {
    id,
    name: `Card ${id}`,
    type: 'DUNGEON_ROOM',
    environment: 'Dungeon',
    copyCount: 1,
    enabled: true,
    descriptionText: '',
    rulesText: '',
    tileImagePath: '',
    ...overrides
  };
}
