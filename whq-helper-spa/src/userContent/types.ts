import type { DungeonCard, SettlementType, SpecialRuleLink, TableKind } from '../types';

export type UserContentKind =
  | 'dungeonCard'
  | 'dungeonEvent'
  | 'treasure'
  | 'objectiveTreasure'
  | 'travelEvent'
  | 'settlementEvent'
  | 'rule'
  | 'monster'
  | 'table'
  | 'objectiveRoomAdventure'
  | 'warrior'
  | 'location';

export type UserContentMode = 'new' | 'modified';

export interface UserDungeonCardData {
  id: number;
  name: string;
  type: DungeonCard['type'];
  environment: string;
  copyCount: number;
  enabled: boolean;
  descriptionText: string;
  rulesText: string;
  tileImagePath: string;
}

export interface UserEventData {
  id: string;
  name: string;
  rules: string;
  special: string;
  flavor: string;
  goldValue: string;
  users: string;
  treasure: boolean;
}

export interface UserRuleData {
  id: string;
  type: 'rule' | 'magic';
  name: string;
  text: string;
  parameterName: string;
  parameterNames?: string[];
  parameterFormat: string;
}

export interface UserMonsterData {
  id: string;
  name: string;
  plural: string;
  factions: string[];
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
  special: string;
  specialLinks: Record<string, SpecialRuleLink>;
  magicType: string;
  magicLevel: number;
}

export interface UserTableData {
  name: string;
  kind: TableKind;
  xml: string;
}

export interface UserObjectiveRoomAdventureData {
  objectiveRoomName: string;
  id: string;
  name: string;
  flavorText: string;
  rulesText: string;
  generic: boolean;
}

export interface UserWarriorData {
  id: string;
  name: string;
  race: string;
  counterPath: string;
  rulesPath: string;
}

export interface UserLocationData {
  id: string;
  name: string;
  availableTypes: SettlementType[];
  description: string;
  visitors: string[];
  rules: string;
}

interface UserContentBase<TKind extends UserContentKind, TData> {
  uid: string;
  kind: TKind;
  mode: UserContentMode;
  sourceId?: string;
  title: string;
  updatedAt: string;
  data: TData;
}

export type UserDungeonCardItem = UserContentBase<'dungeonCard', UserDungeonCardData>;

export type UserDungeonEventItem = UserContentBase<'dungeonEvent', UserEventData>;

export type UserTreasureItem = UserContentBase<'treasure', UserEventData>;

export type UserObjectiveTreasureItem = UserContentBase<'objectiveTreasure', UserEventData>;

export type UserTravelEventItem = UserContentBase<'travelEvent', UserEventData>;

export type UserSettlementEventItem = UserContentBase<'settlementEvent', UserEventData>;

export type UserRuleItem = UserContentBase<'rule', UserRuleData>;

export type UserMonsterItem = UserContentBase<'monster', UserMonsterData>;

export type UserTableItem = UserContentBase<'table', UserTableData>;

export type UserObjectiveRoomAdventureItem = UserContentBase<'objectiveRoomAdventure', UserObjectiveRoomAdventureData>;

export type UserWarriorItem = UserContentBase<'warrior', UserWarriorData>;

export type UserLocationItem = UserContentBase<'location', UserLocationData>;

export type UserContentItem =
  | UserDungeonCardItem
  | UserDungeonEventItem
  | UserTreasureItem
  | UserObjectiveTreasureItem
  | UserTravelEventItem
  | UserSettlementEventItem
  | UserRuleItem
  | UserMonsterItem
  | UserTableItem
  | UserObjectiveRoomAdventureItem
  | UserWarriorItem
  | UserLocationItem;

export interface UserXmlDocument {
  path: string;
  xml: string;
}
