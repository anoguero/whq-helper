import type {
  DungeonCard,
  EventModel,
  Monster,
  ObjectiveRoomAdventure,
  Rule,
  SettlementLocation,
  SettlementType,
  TableModel,
  WarriorDefinition
} from './types';
import type {
  UserContentItem,
  UserContentKind,
  UserContentMode,
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
  normalizeSpecialRuleLinks,
  parseEventIdsFromTableXml,
  parseTableName,
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

function slugify(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replaceAll('&', 'and')
    .replaceAll(/[^a-z0-9]+/g, '-')
    .replaceAll(/^-+|-+$/g, '');
}

function nowIso(): string {
  return new Date().toISOString();
}

function normalizeId(value: string, fallback: string): string {
  return slugify(value) || fallback;
}

function ensurePrefixedId(value: string, prefix: string, fallback: string): string {
  const normalized = normalizeId(value, fallback);
  if (normalized.startsWith(prefix)) {
    return normalized;
  }
  return `${prefix}${normalized.replace(/^userdefined-/, '')}`;
}

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

function normalizeDungeonCardData(data: UserDungeonCardData): UserDungeonCardData {
  return {
    ...data,
    id: Math.max(1, Math.trunc(data.id)),
    name: data.name.trim(),
    environment: data.environment.trim(),
    copyCount: Math.max(0, Math.trunc(data.copyCount)),
    enabled: !!data.enabled,
    descriptionText: data.descriptionText.trim(),
    rulesText: data.rulesText.trim(),
    tileImagePath: data.tileImagePath.trim()
  };
}

function normalizeEventData(kind: UserContentKind, data: UserEventData, mode: UserContentMode): UserEventData {
  const generatedId =
    kind === 'dungeonEvent'
      ? ensurePrefixedId(data.id || data.name, 'userdefined-event-', 'item')
      : kind === 'objectiveTreasure'
      ? ensurePrefixedId(data.id || data.name, 'userdefined-objective-', 'item')
      : kind === 'treasure'
      ? ensurePrefixedId(data.id || data.name, 'userdefined-treasure-', 'item')
      : kind === 'travelEvent'
      ? ensurePrefixedId(data.id || data.name, 'userdefined-travel-', 'item')
      : kind === 'settlementEvent'
      ? ensurePrefixedId(data.id || data.name, 'userdefined-settlement-', 'item')
      : ensurePrefixedId(data.id || data.name, 'userdefined-', 'item');

  return {
    ...data,
    id: mode === 'modified' ? data.id.trim() : generatedId,
    name: data.name.trim(),
    rules: data.rules.trim(),
    special: data.special.trim(),
    flavor: data.flavor.trim(),
    goldValue: data.goldValue.trim(),
    users: data.users.trim(),
    treasure: kind === 'treasure' || kind === 'objectiveTreasure' ? true : !!data.treasure
  };
}

function normalizeRuleData(data: UserRuleData, mode: UserContentMode): UserRuleData {
  return {
    ...data,
    id: mode === 'modified' ? (data.id ?? '').trim() : ensurePrefixedId(data.id || data.name || '', 'userdefined-rule-', 'rule'),
    type: data.type === 'magic' ? 'magic' : 'rule',
    name: (data.name ?? '').trim(),
    text: (data.text ?? '').trim(),
    parameterName: (data.parameterName ?? '').trim(),
    parameterNames: (data.parameterNames ?? []).map((value) => value.trim()).filter(Boolean),
    parameterFormat: (data.parameterFormat ?? '').trim()
  };
}

function normalizeMonsterData(data: UserMonsterData, mode: UserContentMode): UserMonsterData {
  return {
    ...data,
    id:
      mode === 'modified'
        ? (data.id ?? '').trim()
        : ensurePrefixedId(data.id || data.name || '', 'userdefined-monster-', 'monster'),
    name: (data.name ?? '').trim(),
    plural: (data.plural ?? '').trim(),
    factions: (data.factions ?? []).map((value) => value.trim()).filter(Boolean),
    move: (data.move ?? '').trim(),
    weaponskill: (data.weaponskill ?? '').trim(),
    ballisticskill: (data.ballisticskill ?? '').trim(),
    strength: (data.strength ?? '').trim(),
    toughness: (data.toughness ?? '').trim(),
    wounds: (data.wounds ?? '').trim(),
    initiative: (data.initiative ?? '').trim(),
    attacks: (data.attacks ?? '').trim(),
    gold: (data.gold ?? '').trim(),
    armor: (data.armor ?? '').trim(),
    damage: (data.damage ?? '').trim(),
    special: (data.special ?? '').trim(),
    specialLinks: normalizeSpecialRuleLinks(data.specialLinks ?? {}),
    magicType: (data.magicType ?? '').trim(),
    magicLevel: Math.max(0, Math.trunc(data.magicLevel ?? 0))
  };
}

function normalizeTableData(data: UserTableData, mode: UserContentMode): UserTableData {
  const parsed = parseTableName(data.xml);
  const fallbackName = mode === 'modified' ? data.name.trim() : `userdefined-${normalizeId(data.name, 'table')}`;
  return {
    name: parsed?.name || fallbackName,
    kind: parsed?.kind || data.kind,
    xml: data.xml.trim()
  };
}

function normalizeObjectiveRoomAdventureData(
  data: UserObjectiveRoomAdventureData,
  mode: UserContentMode
): UserObjectiveRoomAdventureData {
  return {
    ...data,
    objectiveRoomName: data.objectiveRoomName.trim(),
    id:
      mode === 'modified'
        ? data.id.trim()
        : ensurePrefixedId(data.id || data.name, 'userdefined-adventure-', 'adventure'),
    name: data.name.trim(),
    flavorText: data.flavorText.trim(),
    rulesText: data.rulesText.trim(),
    generic: !!data.generic
  };
}

function normalizeWarriorData(data: UserWarriorData, mode: UserContentMode): UserWarriorData {
  return {
    ...data,
    id:
      mode === 'modified'
        ? (data.id ?? '').trim()
        : ensurePrefixedId(data.id || data.name || '', 'warrior-', 'warrior'),
    name: (data.name ?? '').trim(),
    race: (data.race ?? '').trim(),
    counterPath: (data.counterPath ?? '').trim(),
    rulesPath: (data.rulesPath ?? '').trim()
  };
}

function normalizeLocationData(data: UserLocationData, mode: UserContentMode): UserLocationData {
  return {
    ...data,
    id:
      mode === 'modified'
        ? (data.id ?? '').trim()
        : ensurePrefixedId(data.id || data.name || '', 'location-', 'location'),
    name: (data.name ?? '').trim(),
    availableTypes: (data.availableTypes ?? []).map((value) => value.trim() as SettlementType).filter(Boolean),
    description: (data.description ?? '').trim(),
    visitors: (data.visitors ?? []).map((value) => value.trim()).filter(Boolean),
    rules: (data.rules ?? '').trim()
  };
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

export function normalizeUserContentItem(item: UserContentItem): UserContentItem {
  const updatedAt = item.updatedAt || nowIso();

  if (item.kind === 'dungeonCard') {
    return {
      ...item,
      title: normalizeDungeonCardData(item.data).name || item.title.trim(),
      updatedAt,
      data: normalizeDungeonCardData(item.data)
    };
  }
  if (
    item.kind === 'treasure' ||
    item.kind === 'dungeonEvent' ||
    item.kind === 'objectiveTreasure' ||
    item.kind === 'travelEvent' ||
    item.kind === 'settlementEvent'
  ) {
    const data = normalizeEventData(item.kind, item.data, item.mode);
    return {
      ...item,
      title: data.name || data.id,
      updatedAt,
      data
    };
  }
  if (item.kind === 'rule') {
    const data = normalizeRuleData(item.data, item.mode);
    return {
      ...item,
      title: data.name || data.id,
      updatedAt,
      data
    };
  }
  if (item.kind === 'monster') {
    const data = normalizeMonsterData(item.data, item.mode);
    return {
      ...item,
      title: data.name || data.id,
      updatedAt,
      data
    };
  }
  if (item.kind === 'objectiveRoomAdventure') {
    const data = normalizeObjectiveRoomAdventureData(item.data, item.mode);
    return {
      ...item,
      title: `${data.objectiveRoomName} - ${data.name || data.id}`,
      updatedAt,
      data
    };
  }
  if (item.kind === 'warrior') {
    const data = normalizeWarriorData(item.data, item.mode);
    return {
      ...item,
      title: data.name || data.id,
      updatedAt,
      data
    };
  }
  if (item.kind === 'location') {
    const data = normalizeLocationData(item.data, item.mode);
    return {
      ...item,
      title: data.name || data.id,
      updatedAt,
      data
    };
  }

  const data = normalizeTableData(item.data, item.mode);
  return {
    ...item,
    title: data.name || item.title.trim(),
    updatedAt,
    data
  };
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
