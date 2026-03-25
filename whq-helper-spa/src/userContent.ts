import type {
  DungeonCard,
  EventEntry,
  EventModel,
  GroupEntry,
  Monster,
  MonsterEntry,
  ObjectiveRoomAdventure,
  Rule,
  SpecialRuleLink,
  TableKind,
  TableModel,
  TableRefEntry
} from './types';

const STORAGE_KEY = 'whq_helper_spa_user_content_v1';

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
  | 'objectiveRoomAdventure';

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
  | UserObjectiveRoomAdventureItem;

export interface UserXmlDocument {
  path: string;
  xml: string;
}

function escapeXml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&apos;');
}

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

function joinAmbiences(values: string[]): string {
  return values.map((value) => value.trim()).filter(Boolean).join(' ');
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

function normalizeSpecialRuleLinks(value: Record<string, string | SpecialRuleLink>): Record<string, SpecialRuleLink> {
  const result: Record<string, SpecialRuleLink> = {};
  for (const [key, rawValue] of Object.entries(value ?? {})) {
    const normalizedKey = key.trim();
    const normalizedText = (typeof rawValue === 'string' ? rawValue : rawValue?.text ?? '').trim();
    const normalizedParameter = (typeof rawValue === 'string' ? '' : rawValue?.parameter ?? '').trim();
    const normalizedParameters =
      typeof rawValue === 'string'
        ? normalizedParameter
          ? [normalizedParameter]
          : []
        : (rawValue?.parameters ?? []).map((value) => value.trim()).filter(Boolean);
    if (normalizedKey && normalizedText) {
      result[normalizedKey] = {
        text: normalizedText,
        parameter: normalizedParameter || normalizedParameters[0] || '',
        parameters: normalizedParameters.length > 0 ? normalizedParameters : normalizedParameter ? [normalizedParameter] : []
      };
    }
  }
  return result;
}

function parseTableName(xml: string): { name: string; kind: TableKind } | null {
  const doc = new DOMParser().parseFromString(xml, 'text/xml');
  if (doc.querySelector('parsererror')) {
    return null;
  }
  const table = Array.from(doc.documentElement.children).find((node) => node.tagName === 'table');
  if (!table) {
    return null;
  }
  const kindRaw = (table.getAttribute('kind') ?? '').trim().toLowerCase();
  const kind: TableKind =
    kindRaw === 'travel' ? 'travel' : kindRaw === 'settlement' ? 'settlement' : kindRaw === 'treasure' ? 'treasure' : 'dungeon';
  return {
    name: (table.getAttribute('name') ?? '').trim(),
    kind
  };
}

function parseEventIdsFromTableXml(xml: string): string[] {
  const doc = new DOMParser().parseFromString(xml, 'text/xml');
  if (doc.querySelector('parsererror')) {
    return [];
  }
  const table = Array.from(doc.documentElement.children).find((node) => node.tagName === 'table');
  if (!table) {
    return [];
  }
  return Array.from(table.children)
    .filter((node) => node.tagName === 'event')
    .map((node) => (node.getAttribute('id') ?? '').trim())
    .filter(Boolean);
}

function serializeEventTableXml(name: string, kind: TableKind, eventIds: string[]): string {
  const attrs = [`name="${escapeXml(name)}"`];
  if (kind !== 'dungeon') {
    attrs.push(`kind="${escapeXml(kind)}"`);
  }
  return [
    '<?xml version="1.0"?>',
    '<tables>',
    `  <table ${attrs.join(' ')}>`,
    ...eventIds.map((eventId) => `    <event id="${escapeXml(eventId)}" />`),
    '  </table>',
    '</tables>'
  ].join('\n');
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

function serializeSpecial(
  special: string,
  specialLinks: Record<string, SpecialRuleLink>,
  magicType: string,
  magicLevel: number,
  indent: string
): string {
  const links = Object.entries(normalizeSpecialRuleLinks(specialLinks));
  const safeSpecial = special.trim();
  const safeMagicType = magicType.trim();
  const hasContent = safeSpecial || links.length > 0 || safeMagicType;
  if (!hasContent) {
    return '';
  }

  const lines = [`${indent}<special>`];
  if (safeSpecial) {
    lines.push(`${indent}  <text>${escapeXml(safeSpecial)}</text>`);
  }
  for (const [id, link] of links) {
    const attrs = [`id="${escapeXml(id)}"`];
    const parameters = link.parameters?.map((value) => value.trim()).filter(Boolean) ?? [];
    if (parameters.length === 1) {
      attrs.push(`param="${escapeXml(parameters[0])}"`);
    } else if (!parameters.length && link.parameter.trim()) {
      attrs.push(`param="${escapeXml(link.parameter.trim())}"`);
    }
    lines.push(`${indent}  <rule ${attrs.join(' ')}>${escapeXml(link.text)}</rule>`);
  }
  if (safeMagicType) {
    lines.push(`${indent}  <magic id="${escapeXml(safeMagicType)}" level="${Math.max(0, magicLevel)}" />`);
  }
  lines.push(`${indent}</special>`);
  return lines.join('\n');
}

function serializeDungeonCard(card: UserDungeonCardData): string {
  return [
    `  <card id="${card.id}" name="${escapeXml(card.name.trim())}" type="${escapeXml(card.type)}" environment="${escapeXml(card.environment.trim())}" copyCount="${Math.max(0, card.copyCount)}" enabled="${card.enabled ? 'true' : 'false'}">`,
    `    <description>${escapeXml(card.descriptionText.trim())}</description>`,
    `    <rules>${escapeXml(card.rulesText.trim())}</rules>`,
    `    <tileImagePath>${escapeXml(card.tileImagePath.trim())}</tileImagePath>`,
    '  </card>'
  ].join('\n');
}

function serializeEvent(item: UserEventData): string {
  const lines = [`  <event id="${escapeXml(item.id.trim())}" name="${escapeXml(item.name.trim())}">`];
  if (item.flavor.trim()) {
    lines.push(`    <flavor>${escapeXml(item.flavor.trim())}</flavor>`);
  }
  lines.push(`    <rules>${escapeXml(item.rules.trim())}</rules>`);
  if (item.special.trim()) {
    lines.push(`    <special>${escapeXml(item.special.trim())}</special>`);
  }
  if (item.goldValue.trim()) {
    lines.push(`    <goldValue>${escapeXml(item.goldValue.trim())}</goldValue>`);
  }
  if (item.users.trim()) {
    lines.push(`    <users>${escapeXml(item.users.trim())}</users>`);
  }
  lines.push(`    <treasure>${item.treasure ? 'true' : 'false'}</treasure>`);
  lines.push('  </event>');
  return lines.join('\n');
}

function serializeRule(item: UserRuleData): string {
  const attrs = [`id="${escapeXml(item.id.trim())}"`, `name="${escapeXml(item.name.trim())}"`];
  if (item.parameterName.trim()) {
    attrs.push(`parameterName="${escapeXml(item.parameterName.trim())}"`);
  }
  if ((item.parameterNames ?? []).length > 0) {
    attrs.push(`parameterNames="${escapeXml(item.parameterNames!.join(', '))}"`);
  }
  if ((item.parameterFormat ?? '').trim()) {
    attrs.push(`parameterFormat="${escapeXml(item.parameterFormat.trim())}"`);
  }
  return `  <${item.type} ${attrs.join(' ')}>${escapeXml(item.text.trim())}</${item.type}>`;
}

function serializeMonster(item: UserMonsterData): string {
  const lines = [
    `  <monster id="${escapeXml(item.id.trim())}" name="${escapeXml(item.name.trim())}" plural="${escapeXml(item.plural.trim())}"${item.factions.length > 0 ? ` factions="${escapeXml(joinAmbiences(item.factions))}"` : ''}>`,
    `    <move>${escapeXml(item.move.trim())}</move>`,
    `    <weaponskill>${escapeXml(item.weaponskill.trim())}</weaponskill>`,
    `    <ballisticskill>${escapeXml(item.ballisticskill.trim())}</ballisticskill>`,
    `    <strength>${escapeXml(item.strength.trim())}</strength>`,
    `    <toughness>${escapeXml(item.toughness.trim())}</toughness>`,
    `    <wounds>${escapeXml(item.wounds.trim())}</wounds>`,
    `    <initiative>${escapeXml(item.initiative.trim())}</initiative>`,
    `    <attacks>${escapeXml(item.attacks.trim())}</attacks>`,
    `    <gold>${escapeXml(item.gold.trim())}</gold>`,
    `    <armor>${escapeXml(item.armor.trim())}</armor>`,
    `    <damage>${escapeXml(item.damage.trim())}</damage>`
  ];
  const special = serializeSpecial(item.special, item.specialLinks, item.magicType, item.magicLevel, '    ');
  if (special) {
    lines.push(special);
  }
  lines.push('  </monster>');
  return lines.join('\n');
}

function serializeObjectiveRoomAdventure(item: UserObjectiveRoomAdventureData): string {
  return [
    `  <objectiveRoom name="${escapeXml(item.objectiveRoomName.trim())}">`,
    `    <adventure id="${escapeXml(item.id.trim())}" name="${escapeXml(item.name.trim())}" generic="${item.generic ? 'true' : 'false'}">`,
    `      <flavor>${escapeXml(item.flavorText.trim())}</flavor>`,
    `      <rules>${escapeXml(item.rulesText.trim())}</rules>`,
    '    </adventure>',
    '  </objectiveRoom>'
  ].join('\n');
}

function serializeTableMonsterEntry(entry: MonsterEntry, indent: string): string {
  const number = entry.min === entry.max ? String(entry.min) : `${entry.min}-${entry.max}`;
  const attrs = [
    `id="${escapeXml(entry.id.trim())}"`,
    `number="${escapeXml(number)}"`,
    `level="${Math.max(1, entry.level)}"`
  ];
  if (entry.ambiences.length > 0) {
    attrs.push(`ambiences="${escapeXml(joinAmbiences(entry.ambiences))}"`);
  }
  const lines = [`${indent}<monster ${attrs.join(' ')}>`];
  const special = serializeSpecial(entry.special, entry.specialLinks, entry.magicType, entry.magicLevel, `${indent}  `);
  if (special) {
    lines.push(special);
  }
  lines.push(`${indent}</monster>`);
  return lines.join('\n');
}

function serializeTableRefEntry(entry: TableRefEntry, indent: string): string {
  const attrs = [
    `name="${escapeXml(entry.tableName.trim())}"`,
    `level="${Math.max(1, entry.level)}"`,
    `targetLevel="${Math.max(1, entry.targetLevel)}"`,
    `times="${Math.max(1, entry.times)}"`
  ];
  if (entry.ambiences.length > 0) {
    attrs.push(`ambiences="${escapeXml(joinAmbiences(entry.ambiences))}"`);
  }
  return `${indent}<tableRef ${attrs.join(' ')} />`;
}

function serializeEventEntry(entry: EventEntry, indent: string): string {
  const attrs = [`id="${escapeXml(entry.id.trim())}"`];
  if (entry.ambiences.length > 0) {
    attrs.push(`ambiences="${escapeXml(joinAmbiences(entry.ambiences))}"`);
  }
  return `${indent}<event ${attrs.join(' ')} />`;
}

function serializeGroupEntry(entry: GroupEntry, indent: string): string {
  const lines = [`${indent}<group level="${Math.max(1, entry.level)}">`];
  for (const member of entry.entries) {
    lines.push(serializeTableMonsterEntry(member, `${indent}  `));
  }
  lines.push(`${indent}</group>`);
  return lines.join('\n');
}

function serializeTableFromModel(table: TableModel): string {
  const tableAttrs = [`name="${escapeXml(table.name)}"`];
  if (table.kind !== 'dungeon') {
    tableAttrs.push(`kind="${escapeXml(table.kindRaw || table.kind)}"`);
  }

  const lines = [
    '<?xml version="1.0"?>',
    '<tables>',
    `  <table ${tableAttrs.join(' ')}>`
  ];
  for (const monster of table.monsters) {
    if (monster.kind === 'monster') {
      lines.push(serializeTableMonsterEntry(monster, '    '));
    } else if (monster.kind === 'tableRef') {
      lines.push(serializeTableRefEntry(monster, '    '));
    } else {
      lines.push(serializeGroupEntry(monster, '    '));
    }
  }
  for (const event of table.events) {
    lines.push(serializeEventEntry(event, '    '));
  }
  lines.push('  </table>');
  lines.push('</tables>');
  return lines.join('\n');
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
  kind: Exclude<UserContentKind, 'dungeonCard' | 'rule' | 'monster' | 'table'>
): UserEventData {
  return {
    id: '',
    name: '',
    rules: '',
    special: '',
    flavor: kind === 'travelEvent' || kind === 'settlementEvent' ? '' : '',
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

export function parseTableMetadata(xml: string): { name: string; kind: TableKind } | null {
  return parseTableName(xml);
}
