import type {
  DungeonCard,
  EventModel,
  Monster,
  ObjectiveRoomAdventure,
  Rule,
  SettlementLocation,
  TableModel,
  WarriorDefinition
} from './types';
import {
  normalizeDungeonCardData,
  normalizeUserContentItem,
  nowIso,
  slugify
} from './userContent/normalize';
import type {
  UserContentItem,
  UserContentKind,
  UserDungeonCardData,
  UserDungeonCardItem,
  UserDungeonEventItem,
  UserEventData,
  UserLocationData,
  UserLocationItem,
  UserMonsterData,
  UserMonsterItem,
  UserObjectiveRoomAdventureData,
  UserObjectiveRoomAdventureItem,
  UserObjectiveTreasureItem,
  UserRuleData,
  UserRuleItem,
  UserSettlementEventItem,
  UserTableData,
  UserTableItem,
  UserTravelEventItem,
  UserTreasureItem,
  UserWarriorData,
  UserWarriorItem,
  UserXmlDocument
} from './userContent/types';
import {
  parseEventIdsFromTableXml,
  serializeDungeonCard,
  serializeEvent,
  serializeEventTableXml,
  serializeLocation,
  serializeMonster,
  serializeObjectiveRoomAdventure,
  serializeRule,
  serializeTableFromModel,
  serializeWarrior
} from './userContent/xml';

const STORAGE_KEY = 'whq_helper_spa_user_content_v1';

function ensureManagedTreasureTable(
  items: UserContentItem[],
  name: string,
  eventId: string,
  updatedAt: string
): void {
  const tableIndex = items.findIndex((item) => item.kind === 'table' && item.data.name.trim() === name);
  if (tableIndex >= 0) {
    const table = normalizeUserContentItem(items[tableIndex] as UserTableItem) as UserTableItem;
    const ids = parseEventIdsFromTableXml(table.data.xml);
    if (!ids.includes(eventId)) {
      ids.push(eventId);
      table.data = {
        ...table.data,
        name,
        kind: 'treasure',
        xml: serializeEventTableXml(name, 'treasure', ids)
      };
      table.title = name;
      table.updatedAt = updatedAt;
      items[tableIndex] = table;
    }
    return;
  }

  items.push({
    uid: createUserContentUid('table'),
    kind: 'table',
    mode: 'new',
    title: name,
    updatedAt,
    data: {
      name,
      kind: 'treasure',
      xml: serializeEventTableXml(name, 'treasure', [eventId])
    }
  });
}

function removeEventFromManagedTreasureTable(items: UserContentItem[], name: string, eventId: string): void {
  const tableIndex = items.findIndex((item) => item.kind === 'table' && item.data.name.trim() === name);
  if (tableIndex < 0) {
    return;
  }
  const table = normalizeUserContentItem(items[tableIndex] as UserTableItem) as UserTableItem;
  const ids = parseEventIdsFromTableXml(table.data.xml).filter((id) => id !== eventId);
  if (ids.length === 0) {
    items.splice(tableIndex, 1);
    return;
  }
  table.data = {
    ...table.data,
    name,
    kind: 'treasure',
    xml: serializeEventTableXml(name, 'treasure', ids)
  };
  table.title = name;
  table.updatedAt = nowIso();
  items[tableIndex] = table;
}

export function loadUserContentItems(): UserContentItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw) as UserContentItem[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function saveUserContentItems(items: UserContentItem[]): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
}

export function upsertUserContentItem(item: UserContentItem): UserContentItem[] {
  const items = loadUserContentItems();
  const normalized = normalizeUserContentItem(item);
  const index = items.findIndex((entry) => entry.uid === normalized.uid);
  if (index >= 0) {
    items[index] = normalized;
  } else {
    items.push(normalized);
  }

  if (normalized.mode === 'new' && (normalized.kind === 'treasure' || normalized.kind === 'objectiveTreasure')) {
    ensureManagedTreasureTable(
      items,
      normalized.kind === 'objectiveTreasure' ? 'userdefined-objective-treasure' : 'userdefined-treasure',
      normalized.data.id,
      normalized.updatedAt
    );
  }

  saveUserContentItems(items);
  return items;
}

export function deleteUserContentItem(uid: string): UserContentItem[] {
  const allItems = loadUserContentItems();
  const removed = allItems.find((item) => item.uid === uid) ?? null;
  const items = allItems.filter((item) => item.uid !== uid);
  if (removed?.mode === 'new' && (removed.kind === 'treasure' || removed.kind === 'objectiveTreasure')) {
    const normalized = normalizeUserContentItem(removed) as UserTreasureItem | UserObjectiveTreasureItem;
    removeEventFromManagedTreasureTable(
      items,
      normalized.kind === 'objectiveTreasure' ? 'userdefined-objective-treasure' : 'userdefined-treasure',
      normalized.data.id
    );
  }
  saveUserContentItems(items);
  return items;
}

export function createUserContentUid(kind: UserContentKind): string {
  return `${kind}-${crypto.randomUUID()}`;
}

export function userContentItemXml(item: UserContentItem): string {
  const normalized = normalizeUserContentItem(item);
  if (normalized.kind === 'dungeonCard') {
    return ['<?xml version="1.0"?>', '<dungeonCards>', serializeDungeonCard(normalized.data), '</dungeonCards>'].join('\n');
  }
  if (
    normalized.kind === 'treasure' ||
    normalized.kind === 'dungeonEvent' ||
    normalized.kind === 'objectiveTreasure' ||
    normalized.kind === 'travelEvent' ||
    normalized.kind === 'settlementEvent'
  ) {
    return ['<?xml version="1.0"?>', '<events>', serializeEvent(normalized.data), '</events>'].join('\n');
  }
  if (normalized.kind === 'rule') {
    return ['<?xml version="1.0"?>', '<rules>', serializeRule(normalized.data), '</rules>'].join('\n');
  }
  if (normalized.kind === 'monster') {
    return ['<?xml version="1.0"?>', '<monsters>', serializeMonster(normalized.data), '</monsters>'].join('\n');
  }
  if (normalized.kind === 'objectiveRoomAdventure') {
    return [
      '<?xml version="1.0"?>',
      '<objectiveRoomAdventures>',
      serializeObjectiveRoomAdventure(normalized.data),
      '</objectiveRoomAdventures>'
    ].join('\n');
  }
  if (normalized.kind === 'warrior') {
    return ['<?xml version="1.0"?>', '<warriors>', serializeWarrior(normalized.data), '</warriors>'].join('\n');
  }
  if (normalized.kind === 'location') {
    return ['<?xml version="1.0"?>', '<locations>', serializeLocation(normalized.data), '</locations>'].join('\n');
  }
  return normalized.data.xml;
}

export function buildUserContentXmlDocuments(): UserXmlDocument[] {
  const items = loadUserContentItems().map(normalizeUserContentItem);
  const dungeonCards = items.filter((item): item is UserDungeonCardItem => item.kind === 'dungeonCard');
  const dungeonEvents = items.filter((item): item is UserDungeonEventItem => item.kind === 'dungeonEvent');
  const treasures = items.filter((item): item is UserTreasureItem => item.kind === 'treasure');
  const objectiveTreasures = items.filter((item): item is UserObjectiveTreasureItem => item.kind === 'objectiveTreasure');
  const travelEvents = items.filter((item): item is UserTravelEventItem => item.kind === 'travelEvent');
  const settlementEvents = items.filter((item): item is UserSettlementEventItem => item.kind === 'settlementEvent');
  const rules = items.filter((item): item is UserRuleItem => item.kind === 'rule');
  const monsters = items.filter((item): item is UserMonsterItem => item.kind === 'monster');
  const tables = items.filter((item): item is UserTableItem => item.kind === 'table');
  const objectiveRoomAdventures = items.filter(
    (item): item is UserObjectiveRoomAdventureItem => item.kind === 'objectiveRoomAdventure'
  );
  const warriors = items.filter((item): item is UserWarriorItem => item.kind === 'warrior');
  const locations = items.filter((item): item is UserLocationItem => item.kind === 'location');

  const documents: UserXmlDocument[] = [];

  if (dungeonCards.length > 0) {
    documents.push({
      path: '/userdefined/dungeon/dungeon-cards.xml',
      xml: ['<?xml version="1.0"?>', '<dungeonCards>', ...dungeonCards.map((item) => serializeDungeonCard(item.data)), '</dungeonCards>'].join('\n')
    });
  }

  const eventGroups: Array<{
    path: string;
    items: Array<
      UserDungeonEventItem | UserTreasureItem | UserObjectiveTreasureItem | UserTravelEventItem | UserSettlementEventItem
    >;
  }> = [
    {
      path: '/userdefined/events/userdefined-dungeon-events.xml',
      items: dungeonEvents
    },
    {
      path: '/userdefined/events/userdefined-treasure-events.xml',
      items: [...treasures, ...objectiveTreasures]
    },
    {
      path: '/userdefined/travel/userdefined-travel-events.xml',
      items: travelEvents
    },
    {
      path: '/userdefined/settlement/userdefined-settlement-events.xml',
      items: settlementEvents
    }
  ];

  for (const group of eventGroups) {
    if (group.items.length === 0) {
      continue;
    }
    documents.push({
      path: group.path,
      xml: ['<?xml version="1.0"?>', '<events>', ...group.items.map((item) => serializeEvent(item.data)), '</events>'].join('\n')
    });
  }

  if (rules.length > 0) {
    documents.push({
      path: '/userdefined/rules/userdefined-rules.xml',
      xml: ['<?xml version="1.0"?>', '<rules>', ...rules.map((item) => serializeRule(item.data)), '</rules>'].join('\n')
    });
  }

  if (monsters.length > 0) {
    documents.push({
      path: '/userdefined/monsters/userdefined-monsters.xml',
      xml: ['<?xml version="1.0"?>', '<monsters>', ...monsters.map((item) => serializeMonster(item.data)), '</monsters>'].join('\n')
    });
  }

  if (objectiveRoomAdventures.length > 0) {
    documents.push({
      path: '/userdefined/adventures/userdefined-objective-room-adventures.xml',
      xml: [
        '<?xml version="1.0"?>',
        '<objectiveRoomAdventures>',
        ...objectiveRoomAdventures.map((item) => serializeObjectiveRoomAdventure(item.data)),
        '</objectiveRoomAdventures>'
      ].join('\n')
    });
  }

  if (warriors.length > 0) {
    documents.push({
      path: '/userdefined/warriors/userdefined-warriors.xml',
      xml: ['<?xml version="1.0"?>', '<warriors>', ...warriors.map((item) => serializeWarrior(item.data)), '</warriors>'].join('\n')
    });
  }

  if (locations.length > 0) {
    documents.push({
      path: '/userdefined/locations/userdefined-locations.xml',
      xml: ['<?xml version="1.0"?>', '<locations>', ...locations.map((item) => serializeLocation(item.data)), '</locations>'].join('\n')
    });
  }

  for (const table of tables) {
    documents.push({
      path: `/userdefined/tables/${slugify(table.data.name) || table.uid}.xml`,
      xml: table.data.xml
    });
  }

  return documents;
}

export function loadUserDungeonCards(): UserDungeonCardData[] {
  return loadUserContentItems()
    .filter((item): item is UserDungeonCardItem => item.kind === 'dungeonCard')
    .map((item) => normalizeDungeonCardData(item.data));
}

export function createDefaultDungeonCard(nextId: number): UserDungeonCardData {
  return {
    id: nextId,
    name: '',
    type: 'DUNGEON_ROOM',
    environment: 'The Old World',
    copyCount: 1,
    enabled: true,
    descriptionText: '',
    rulesText: '',
    tileImagePath: ''
  };
}

export function createDefaultEvent(
  kind: Exclude<UserContentKind, 'dungeonCard' | 'rule' | 'monster' | 'table' | 'warrior' | 'location'>
): UserEventData {
  return {
    id: '',
    name: '',
    rules: '',
    special: '',
    flavor: '',
    goldValue: '',
    users: '',
    treasure: kind === 'treasure' || kind === 'objectiveTreasure'
  };
}

export function createDefaultRule(): UserRuleData {
  return {
    id: '',
    type: 'rule',
    name: '',
    text: '',
    parameterName: '',
    parameterNames: [],
    parameterFormat: ''
  };
}

export function createDefaultMonster(): UserMonsterData {
  return {
    id: '',
    name: '',
    plural: '',
    factions: [],
    move: '4',
    weaponskill: '3',
    ballisticskill: '4+',
    strength: '3',
    toughness: '3',
    wounds: '1',
    initiative: '3',
    attacks: '1',
    gold: '50',
    armor: '-',
    damage: '1D6',
    special: '',
    specialLinks: {},
    magicType: '',
    magicLevel: 0
  };
}

export function createDefaultTable(): UserTableData {
  const xml = [
    '<?xml version="1.0"?>',
    '<tables>',
    '  <table name="userdefined-new-table">',
    '    <monster id="userdefined-monster-example" number="1-3" level="1" ambiences="generic" />',
    '  </table>',
    '</tables>'
  ].join('\n');
  return {
    name: 'userdefined-new-table',
    kind: 'dungeon',
    xml
  };
}

export function createDefaultObjectiveRoomAdventure(): UserObjectiveRoomAdventureData {
  return {
    objectiveRoomName: '',
    id: '',
    name: '',
    flavorText: '',
    rulesText: '',
    generic: false
  };
}

export function createDefaultWarrior(): UserWarriorData {
  return {
    id: '',
    name: '',
    race: '',
    counterPath: '',
    rulesPath: ''
  };
}

export function createDefaultLocation(): UserLocationData {
  return {
    id: '',
    name: '',
    availableTypes: ['city'],
    description: '',
    visitors: ['all'],
    rules: ''
  };
}

export function mapDungeonCardToUserData(card: DungeonCard): UserDungeonCardData {
  return {
    id: card.id,
    name: card.name,
    type: card.type,
    environment: card.environment,
    copyCount: card.copyCount,
    enabled: card.enabled,
    descriptionText: card.descriptionText,
    rulesText: card.rulesText,
    tileImagePath: card.tileImagePath
  };
}

export function mapEventToUserData(event: EventModel): UserEventData {
  return {
    id: event.id,
    name: event.name,
    rules: event.rules,
    special: event.special,
    flavor: event.flavor,
    goldValue: event.goldValue,
    users: event.users,
    treasure: event.treasure
  };
}

export function mapRuleToUserData(rule: Rule): UserRuleData {
  return {
    id: rule.id,
    type: rule.type === 'magic' ? 'magic' : 'rule',
    name: rule.name,
    text: rule.text,
    parameterName: rule.parameterName,
    parameterNames: rule.parameterNames,
    parameterFormat: rule.parameterFormat
  };
}

export function mapMonsterToUserData(monster: Monster): UserMonsterData {
  return {
    id: monster.id,
    name: monster.name,
    plural: monster.plural,
    factions: monster.factions,
    move: monster.move,
    weaponskill: monster.weaponskill,
    ballisticskill: monster.ballisticskill,
    strength: monster.strength,
    toughness: monster.toughness,
    wounds: monster.wounds,
    initiative: monster.initiative,
    attacks: monster.attacks,
    gold: monster.gold,
    armor: monster.armor,
    damage: monster.damage,
    special: monster.special,
    specialLinks: monster.specialLinks,
    magicType: monster.magicType,
    magicLevel: monster.magicLevel
  };
}

export function mapTableToUserData(table: TableModel): UserTableData {
  return {
    name: table.name,
    kind: table.kind,
    xml: serializeTableFromModel(table)
  };
}

export function mapObjectiveRoomAdventureToUserData(adventure: ObjectiveRoomAdventure): UserObjectiveRoomAdventureData {
  return {
    objectiveRoomName: adventure.objectiveRoomName,
    id: adventure.id,
    name: adventure.name,
    flavorText: adventure.flavorText,
    rulesText: adventure.rulesText,
    generic: adventure.generic
  };
}

export function mapWarriorToUserData(warrior: WarriorDefinition): UserWarriorData {
  return {
    id: warrior.id,
    name: warrior.name,
    race: warrior.race,
    counterPath: warrior.counterPath,
    rulesPath: warrior.rulesPath
  };
}

export function mapLocationToUserData(location: SettlementLocation): UserLocationData {
  return {
    id: location.id,
    name: location.name,
    availableTypes: [...location.availableTypes],
    description: location.description,
    visitors: [...location.visitors],
    rules: location.rules
  };
}
