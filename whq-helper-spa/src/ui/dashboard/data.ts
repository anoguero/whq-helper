import { appState } from '../../state';
import type { GroupEntry, MonsterEntry, Rule } from '../../types';
import { createUserContentUid, loadUserContentItems } from '../../userContent';
import {
  createDefaultDungeonCard,
  createDefaultEvent,
  createDefaultLocation,
  createDefaultMonster,
  createDefaultObjectiveRoomAdventure,
  createDefaultRule,
  createDefaultTable,
  createDefaultWarrior,
  mapDungeonCardToUserData,
  mapEventToUserData,
  mapLocationToUserData,
  mapMonsterToUserData,
  mapObjectiveRoomAdventureToUserData,
  mapRuleToUserData,
  mapTableToUserData,
  mapWarriorToUserData
} from '../../userContent/defaults';
import type { UserContentItem, UserContentKind } from '../../userContent/types';
import { serializeMonster, serializeSpecial } from '../../userContent/xml';
import { escapeHtml } from '../formatting';
import { DASHBOARD_CREATE_PREFIX, dashboardState } from './state';

export type EventTableKind = 'dungeon' | 'travel' | 'settlement';

export function nextUserDungeonCardId(): number {
  const fromCards = appState.dungeonCards.map((card) => card.id);
  const fromUserItems = loadUserContentItems()
    .filter((item): item is Extract<UserContentItem, { kind: 'dungeonCard' }> => item.kind === 'dungeonCard')
    .map((item) => item.data.id);
  return Math.max(0, ...fromCards, ...fromUserItems) + 1;
}

export function isHiddenManagedTreasureTable(item: UserContentItem): boolean {
  return (
    item.kind === 'table' &&
    (item.data.name.trim() === 'userdefined-treasure' || item.data.name.trim() === 'userdefined-objective-treasure')
  );
}

export function dashboardItemsByKind(kind: UserContentKind): UserContentItem[] {
  return loadUserContentItems()
    .filter((item) => item.kind === kind)
    .filter((item) => !isHiddenManagedTreasureTable(item))
    .sort((left, right) => left.title.localeCompare(right.title, undefined, { sensitivity: 'base' }));
}

export function isDashboardDraftSelected(): boolean {
  return dashboardState.activeDashboardItemUid === 'draft' && dashboardState.dashboardDraftItem !== null;
}

export function currentDashboardItem(): UserContentItem | null {
  if (isDashboardDraftSelected()) {
    return dashboardState.dashboardDraftItem;
  }
  if (!dashboardState.activeDashboardItemUid || dashboardState.activeDashboardItemUid.startsWith(DASHBOARD_CREATE_PREFIX)) {
    return null;
  }
  return loadUserContentItems().find((item) => item.uid === dashboardState.activeDashboardItemUid) ?? null;
}

export function availableObjectiveRoomNames(): string[] {
  return [...new Set(appState.dungeonStore.loadCards().filter((card) => card.type === 'OBJECTIVE_ROOM').map((card) => card.name))]
    .sort((left, right) => left.localeCompare(right, undefined, { sensitivity: 'base' }));
}

export function availableMonsterFactions(): string[] {
  return [...new Set(Array.from(appState.repository.monsters.values()).flatMap((monster) => monster.factions).filter(Boolean))].sort((left, right) =>
    left.localeCompare(right, undefined, { sensitivity: 'base' })
  );
}

export function availableMonsterRules(): Rule[] {
  return Array.from(appState.repository.rules.values())
    .filter((rule) => rule.type !== 'magic')
    .sort((left, right) => left.name.localeCompare(right.name, undefined, { sensitivity: 'base' }));
}

export function availableMagicRules(): Rule[] {
  return Array.from(appState.repository.rules.values())
    .filter((rule) => rule.type === 'magic')
    .sort((left, right) => left.name.localeCompare(right.name, undefined, { sensitivity: 'base' }));
}

export function eventTableUserContentKind(kind: EventTableKind): Extract<UserContentKind, 'dungeonEvent' | 'travelEvent' | 'settlementEvent'> {
  return kind === 'travel' ? 'travelEvent' : kind === 'settlement' ? 'settlementEvent' : 'dungeonEvent';
}

export function parseEventOnlyTable(xml: string): { name: string; kind: EventTableKind; eventIds: string[] } | null {
  const doc = new DOMParser().parseFromString(xml, 'text/xml');
  if (doc.querySelector('parsererror')) {
    return null;
  }
  const table = Array.from(doc.documentElement.children).find((node) => node.tagName === 'table');
  if (!table) {
    return null;
  }
  const kindRaw = (table.getAttribute('kind') ?? '').trim().toLowerCase();
  const kind: EventTableKind =
    kindRaw === 'travel' ? 'travel' : kindRaw === 'settlement' ? 'settlement' : kindRaw === '' ? 'dungeon' : kindRaw === 'dungeon' ? 'dungeon' : 'dungeon';

  const eventIds: string[] = [];
  for (const child of Array.from(table.children)) {
    if (child.tagName !== 'event') {
      return null;
    }
    const eventId = (child.getAttribute('id') ?? '').trim();
    if (eventId) {
      eventIds.push(eventId);
    }
  }

  return {
    name: (table.getAttribute('name') ?? '').trim(),
    kind,
    eventIds
  };
}

export function serializeEventOnlyTable(name: string, kind: EventTableKind, eventIds: string[], id?: string): string {
  const tableAttrs = [...(id ? [`id="${escapeHtml(id)}"`] : []), `name="${escapeHtml(name)}"`];
  if (kind !== 'dungeon') {
    tableAttrs.push(`kind="${kind}"`);
  }
  return [
    '<?xml version="1.0"?>',
    '<tables>',
    `  <table ${tableAttrs.join(' ')}>`,
    ...eventIds.map((eventId) => `    <event id="${escapeHtml(eventId)}" />`),
    '  </table>',
    '</tables>'
  ].join('\n');
}

export function parseMonsterTableSpecial(node: Element): Pick<MonsterEntry, 'special' | 'specialLinks' | 'magicType' | 'magicLevel' | 'appendSpecials'> {
  const result: Pick<MonsterEntry, 'special' | 'specialLinks' | 'magicType' | 'magicLevel' | 'appendSpecials'> = {
    special: '',
    specialLinks: {},
    magicType: '',
    magicLevel: 0,
    appendSpecials: true
  };
  const specialNode = Array.from(node.children).find((child) => child.tagName === 'special');
  if (!specialNode) {
    return result;
  }
  result.appendSpecials = specialNode.getAttribute('append') !== 'false';
  for (const child of Array.from(specialNode.children)) {
    if (child.tagName === 'rule') {
      const id = (child.getAttribute('id') ?? '').trim();
      const text = child.textContent?.trim() ?? '';
      const parameter = (child.getAttribute('param') ?? '').trim();
      const parameters = parameter ? [parameter] : [];
      if (id && text) {
        result.specialLinks[id] = { text, parameter, parameters };
      }
    } else if (child.tagName === 'magic') {
      result.magicType = (child.getAttribute('id') ?? '').trim();
      result.magicLevel = Number.parseInt(child.getAttribute('level') ?? '0', 10) || 0;
    } else if (child.tagName === 'text') {
      result.special = child.textContent?.trim() ?? '';
    }
  }
  return result;
}

export function parseMonsterTableEntry(node: Element, defaultLevel: number): MonsterEntry {
  const numberRaw = (node.getAttribute('number') ?? '1').trim();
  const [min, max] = numberRaw.includes('-')
    ? numberRaw.split('-').map((part) => Number.parseInt(part, 10) || 0)
    : [Number.parseInt(numberRaw, 10) || 0, Number.parseInt(numberRaw, 10) || 0];
  const level = Math.max(1, Math.min(10, Number.parseInt(node.getAttribute('level') ?? String(defaultLevel), 10) || defaultLevel));
  const ambiences = (node.getAttribute('ambiences') ?? '')
    .trim()
    .split(/\s+/)
    .filter(Boolean);
  return {
    kind: 'monster',
    id: (node.getAttribute('id') ?? '').trim(),
    level,
    min,
    max,
    ambiences,
    ...parseMonsterTableSpecial(node)
  };
}

export function parseMonsterOnlyTable(xml: string): { name: string; entries: Array<MonsterEntry | GroupEntry> } | null {
  const doc = new DOMParser().parseFromString(xml, 'text/xml');
  if (doc.querySelector('parsererror')) {
    return null;
  }
  const table = Array.from(doc.documentElement.children).find((node) => node.tagName === 'table');
  if (!table) {
    return null;
  }
  const entries: Array<MonsterEntry | GroupEntry> = [];
  for (const child of Array.from(table.children)) {
    if (child.tagName === 'monster') {
      entries.push(parseMonsterTableEntry(child, 1));
      continue;
    }
    if (child.tagName === 'group') {
      const level = Math.max(1, Math.min(10, Number.parseInt(child.getAttribute('level') ?? '1', 10) || 1));
      const monsters = Array.from(child.children)
        .filter((member) => member.tagName === 'monster')
        .map((member) => parseMonsterTableEntry(member, level));
      if (monsters.length === 0 || monsters.length !== child.children.length) {
        return null;
      }
      entries.push({ kind: 'group', level, entries: monsters });
      continue;
    }
    return null;
  }
  return {
    name: (table.getAttribute('name') ?? '').trim(),
    entries
  };
}

export function serializeMonsterOnlyTable(name: string, entries: Array<MonsterEntry | GroupEntry>, id?: string): string {
  const serializeSpecial = (entry: MonsterEntry, indent: string): string[] => {
    const lines: string[] = [];
    if (!entry.special.trim() && Object.keys(entry.specialLinks).length === 0 && !entry.magicType.trim()) {
      return lines;
    }
    const attrs = entry.appendSpecials ? '' : ' append="false"';
    lines.push(`${indent}<special${attrs}>`);
    if (entry.special.trim()) {
      lines.push(`${indent}  <text>${escapeHtml(entry.special.trim())}</text>`);
    }
    for (const [id, link] of Object.entries(entry.specialLinks)) {
      const attrs = [`id="${escapeHtml(id)}"`];
      if (link.parameter.trim()) {
        attrs.push(`param="${escapeHtml(link.parameter.trim())}"`);
      }
      lines.push(`${indent}  <rule ${attrs.join(' ')}>${escapeHtml(link.text)}</rule>`);
    }
    if (entry.magicType.trim()) {
      lines.push(`${indent}  <magic id="${escapeHtml(entry.magicType.trim())}" level="${Math.max(0, entry.magicLevel)}" />`);
    }
    lines.push(`${indent}</special>`);
    return lines;
  };

  const serializeMonster = (entry: MonsterEntry, indent: string): string[] => {
    const number = entry.min === entry.max ? String(entry.min) : `${entry.min}-${entry.max}`;
    const attrs = [
      `id="${escapeHtml(entry.id)}"`,
      `number="${escapeHtml(number)}"`,
      `level="${Math.max(1, entry.level)}"`
    ];
    if (entry.ambiences.length > 0) {
      attrs.push(`ambiences="${escapeHtml(entry.ambiences.join(' '))}"`);
    }
    const lines = [`${indent}<monster ${attrs.join(' ')}>`];
    lines.push(...serializeSpecial(entry, `${indent}  `));
    lines.push(`${indent}</monster>`);
    return lines;
  };

  const idAttr = id ? `id="${escapeHtml(id)}" ` : '';
  const lines = ['<?xml version="1.0"?>', '<tables>', `  <table ${idAttr}name="${escapeHtml(name)}">`];
  for (const entry of entries) {
    if (entry.kind === 'monster') {
      lines.push(...serializeMonster(entry, '    '));
      continue;
    }
    lines.push(`    <group level="${Math.max(1, entry.level)}">`);
    for (const member of entry.entries) {
      lines.push(...serializeMonster(member, '      '));
    }
    lines.push('    </group>');
  }
  lines.push('  </table>', '</tables>');
  return lines.join('\n');
}

export function summarizeMonsterCount(min: number, max: number): string {
  return min === max ? String(min) : `${min}-${max}`;
}

export function monsterEntryLabel(entry: MonsterEntry): string {
  const monster = appState.repository.monsters.get(entry.id);
  const name = monster?.name ?? entry.id;
  return `${name} (${entry.id}) x ${summarizeMonsterCount(entry.min, entry.max)}`;
}

export function tableEncounterLabel(entry: MonsterEntry | GroupEntry, index: number): string {
  if (entry.kind === 'monster') {
    const ambiences = entry.ambiences.join(', ') || '-';
    return `${index + 1}. ${monsterEntryLabel(entry)} | L${entry.level} | ${ambiences}`;
  }
  const monsters = entry.entries.map((member) => monsterEntryLabel(member)).join(' + ');
  const ambiences = entry.entries[0]?.ambiences.join(', ') || '-';
  return `${index + 1}. ${monsters} | L${entry.level} | ${ambiences}`;
}

export function availableEventItemsForTable(kind: EventTableKind, currentItemUid: string, currentIds: string[]): Array<{ id: string; label: string }> {
  const usedIds = new Set<string>();

  for (const item of loadUserContentItems()) {
    if (item.kind !== 'table' || item.uid === currentItemUid) {
      continue;
    }
    const parsed = parseEventOnlyTable(item.data.xml);
    if (!parsed || parsed.kind !== kind) {
      continue;
    }
    for (const eventId of parsed.eventIds) {
      usedIds.add(eventId);
    }
  }

  const selectedIds = new Set(currentIds);
  const eventItems = dashboardItemsByKind(eventTableUserContentKind(kind)) as Array<
    Extract<UserContentItem, { kind: 'dungeonEvent' | 'travelEvent' | 'settlementEvent' }>
  >;
  return eventItems
    .map((entry) => ({ id: entry.data.id, label: `${entry.data.name} (${entry.data.id})` }))
    .filter((entry) => selectedIds.has(entry.id) || !usedIds.has(entry.id))
    .sort((left, right) => left.label.localeCompare(right.label, undefined, { sensitivity: 'base' }));
}

export function createBlankEventTableItem(kind: EventTableKind): Extract<UserContentItem, { kind: 'table' }> {
  const name =
    kind === 'travel'
      ? 'userdefined-travel-events-table'
      : kind === 'settlement'
      ? 'userdefined-settlement-events-table'
      : 'userdefined-dungeon-events-table';

  return {
    uid: createUserContentUid('table'),
    kind: 'table',
    mode: 'new',
    title: name,
    updatedAt: new Date().toISOString(),
    data: {
      name,
      kind,
      xml: serializeEventOnlyTable(name, kind, [])
    }
  };
}

export function dashboardSourceOptions(kind: UserContentKind): Array<{ id: string; label: string }> {
  if (kind === 'dungeonCard') {
    return appState.dungeonStore.loadCards().map((card) => ({ id: String(card.id), label: `${card.name} (#${card.id})` }));
  }
  if (kind === 'treasure') {
    return Array.from(appState.repository.events.values())
      .filter((event) => event.treasure && !event.id.toLowerCase().includes('-objective-'))
      .map((event) => ({ id: event.id, label: `${event.name} (${event.id})` }));
  }
  if (kind === 'dungeonEvent') {
    return Array.from(appState.repository.events.values())
      .filter((event) => !event.treasure)
      .map((event) => ({ id: event.id, label: `${event.name} (${event.id})` }));
  }
  if (kind === 'objectiveTreasure') {
    return Array.from(appState.repository.events.values())
      .filter((event) => event.treasure && event.id.toLowerCase().includes('-objective-'))
      .map((event) => ({ id: event.id, label: `${event.name} (${event.id})` }));
  }
  if (kind === 'travelEvent') {
    return Array.from(appState.repository.travelEvents.values()).map((event) => ({ id: event.id, label: `${event.name} (${event.id})` }));
  }
  if (kind === 'settlementEvent') {
    return Array.from(appState.repository.settlementEvents.values()).map((event) => ({ id: event.id, label: `${event.name} (${event.id})` }));
  }
  if (kind === 'rule') {
    return Array.from(appState.repository.rules.values()).map((rule) => ({ id: rule.id, label: `${rule.name} (${rule.id})` }));
  }
  if (kind === 'monster') {
    return Array.from(appState.repository.monsters.values()).map((monster) => ({ id: monster.id, label: `${monster.name} (${monster.id})` }));
  }
  if (kind === 'objectiveRoomAdventure') {
    return appState.dungeonStore
      .loadAllAdventures()
      .map((adventure) => ({
        id: `${adventure.objectiveRoomName}::${adventure.id}`,
        label: `${adventure.objectiveRoomName} - ${adventure.name} (${adventure.id})`
      }));
  }
  if (kind === 'warrior') {
    return Array.from(appState.repository.warriors.values()).map((warrior) => ({
      id: warrior.id,
      label: `${warrior.name} (${warrior.id})`
    }));
  }
  if (kind === 'location') {
    return Array.from(appState.repository.locations.values()).map((location) => ({
      id: location.id,
      label: `${location.name} (${location.id})`
    }));
  }
  return Array.from(appState.repository.tables.values()).map((table) => ({ id: table.name, label: table.name }));
}

export function createBlankDashboardItem(kind: UserContentKind): UserContentItem {
  const uid = createUserContentUid(kind);
  const updatedAt = new Date().toISOString();
  switch (kind) {
    case 'dungeonCard':
      return { uid, kind, mode: 'new', title: '', updatedAt, data: createDefaultDungeonCard(nextUserDungeonCardId()) };
    case 'treasure':
    case 'dungeonEvent':
    case 'objectiveTreasure':
    case 'travelEvent':
    case 'settlementEvent':
      return { uid, kind, mode: 'new', title: '', updatedAt, data: createDefaultEvent(kind) };
    case 'rule':
      return { uid, kind, mode: 'new', title: '', updatedAt, data: createDefaultRule() };
    case 'monster':
      return { uid, kind, mode: 'new', title: '', updatedAt, data: createDefaultMonster() };
    case 'table':
      return { uid, kind, mode: 'new', title: '', updatedAt, data: createDefaultTable() };
    case 'objectiveRoomAdventure':
      return { uid, kind, mode: 'new', title: '', updatedAt, data: createDefaultObjectiveRoomAdventure() };
    case 'warrior':
      return { uid, kind, mode: 'new', title: '', updatedAt, data: createDefaultWarrior() };
    case 'location':
      return { uid, kind, mode: 'new', title: '', updatedAt, data: createDefaultLocation() };
  }
}

export function createModifiedDashboardItem(kind: UserContentKind, sourceId: string): UserContentItem | null {
  const uid = createUserContentUid(kind);
  const updatedAt = new Date().toISOString();
  switch (kind) {
    case 'dungeonCard': {
      const source = appState.dungeonStore.loadCards().find((card) => String(card.id) === sourceId);
      return source ? { uid, kind, mode: 'modified', sourceId, title: source.name, updatedAt, data: mapDungeonCardToUserData(source) } : null;
    }
    case 'treasure':
    case 'dungeonEvent':
    case 'objectiveTreasure': {
      const source = Array.from(appState.repository.events.values()).find((event) => event.id === sourceId);
      return source ? { uid, kind, mode: 'modified', sourceId, title: source.name, updatedAt, data: mapEventToUserData(source) } : null;
    }
    case 'travelEvent': {
      const source = Array.from(appState.repository.travelEvents.values()).find((event) => event.id === sourceId);
      return source ? { uid, kind, mode: 'modified', sourceId, title: source.name, updatedAt, data: mapEventToUserData(source) } : null;
    }
    case 'settlementEvent': {
      const source = Array.from(appState.repository.settlementEvents.values()).find((event) => event.id === sourceId);
      return source ? { uid, kind, mode: 'modified', sourceId, title: source.name, updatedAt, data: mapEventToUserData(source) } : null;
    }
    case 'rule': {
      const source = appState.repository.rules.get(sourceId);
      return source ? { uid, kind, mode: 'modified', sourceId, title: source.name, updatedAt, data: mapRuleToUserData(source) } : null;
    }
    case 'monster': {
      const source = appState.repository.monsters.get(sourceId);
      return source ? { uid, kind, mode: 'modified', sourceId, title: source.name, updatedAt, data: mapMonsterToUserData(source) } : null;
    }
    case 'table': {
      const source = appState.repository.tables.get(sourceId);
      return source ? { uid, kind, mode: 'modified', sourceId, title: source.name, updatedAt, data: mapTableToUserData(source) } : null;
    }
    case 'objectiveRoomAdventure': {
      const [roomName, adventureId] = sourceId.split('::');
      const source = appState.dungeonStore
        .loadAllAdventures()
        .find((adventure) => adventure.objectiveRoomName === roomName && adventure.id === adventureId);
      return source
        ? {
            uid,
            kind,
            mode: 'modified',
            sourceId,
            title: `${source.objectiveRoomName} - ${source.name}`,
            updatedAt,
            data: mapObjectiveRoomAdventureToUserData(source)
          }
        : null;
    }
    case 'warrior': {
      const source = appState.repository.warriors.get(sourceId);
      return source ? { uid, kind, mode: 'modified', sourceId, title: source.name, updatedAt, data: mapWarriorToUserData(source) } : null;
    }
    case 'location': {
      const source = appState.repository.locations.get(sourceId);
      return source ? { uid, kind, mode: 'modified', sourceId, title: source.name, updatedAt, data: mapLocationToUserData(source) } : null;
    }
  }
}
