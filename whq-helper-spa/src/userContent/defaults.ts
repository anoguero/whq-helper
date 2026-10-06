import type {
  DungeonCard,
  EventModel,
  Monster,
  ObjectiveRoomAdventure,
  Rule,
  SettlementLocation,
  TableModel,
  WarriorDefinition
} from '../types';
import type {
  UserContentKind,
  UserDungeonCardData,
  UserEventData,
  UserLocationData,
  UserMonsterData,
  UserObjectiveRoomAdventureData,
  UserRuleData,
  UserTableData,
  UserWarriorData
} from './types';
import { serializeTableFromModel } from './xml';

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
