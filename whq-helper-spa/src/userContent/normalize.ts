import type { SettlementType } from '../types';
import type {
  UserContentItem,
  UserContentKind,
  UserContentMode,
  UserDungeonCardData,
  UserEventData,
  UserLocationData,
  UserMonsterData,
  UserObjectiveRoomAdventureData,
  UserRuleData,
  UserTableData,
  UserWarriorData
} from './types';
import { normalizeSpecialRuleLinks, parseTableName } from './xml';

export function slugify(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replaceAll('&', 'and')
    .replaceAll(/[^a-z0-9]+/g, '-')
    .replaceAll(/^-+|-+$/g, '');
}

export function nowIso(): string {
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

export function normalizeDungeonCardData(data: UserDungeonCardData): UserDungeonCardData {
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
