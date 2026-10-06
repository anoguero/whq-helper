import { tableIdFromName } from '../tableIds';
import type {
  EventEntry,
  GroupEntry,
  MonsterEntry,
  SpecialRuleLink,
  TableKind,
  TableModel,
  TableRefEntry
} from '../types';
import type {
  UserDungeonCardData,
  UserEventData,
  UserLocationData,
  UserMonsterData,
  UserObjectiveRoomAdventureData,
  UserRuleData,
  UserWarriorData
} from './types';

function escapeXml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&apos;');
}

function joinAmbiences(values: string[]): string {
  return values.map((value) => value.trim()).filter(Boolean).join(' ');
}

export function normalizeSpecialRuleLinks(value: Record<string, string | SpecialRuleLink>): Record<string, SpecialRuleLink> {
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

export function parseTableName(xml: string): { name: string; kind: TableKind } | null {
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

/**
 * Id con el que se cargara la tabla del XML: su atributo id o, si no lo trae, el slug de su
 * nombre (como parseTable en content.ts). Null si el XML no es valido o no tiene tabla.
 */
export function parseTableId(xml: string): string | null {
  const doc = new DOMParser().parseFromString(xml, 'text/xml');
  if (doc.querySelector('parsererror')) {
    return null;
  }
  const table = Array.from(doc.documentElement.children).find((node) => node.tagName === 'table');
  if (!table) {
    return null;
  }
  return (table.getAttribute('id') ?? '').trim() || tableIdFromName((table.getAttribute('name') ?? '').trim());
}

export function parseEventIdsFromTableXml(xml: string): string[] {
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

export function serializeEventTableXml(name: string, kind: TableKind, eventIds: string[]): string {
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

export function serializeSpecial(
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

export function serializeDungeonCard(card: UserDungeonCardData): string {
  return [
    `  <card id="${card.id}" name="${escapeXml(card.name.trim())}" type="${escapeXml(card.type)}" environment="${escapeXml(card.environment.trim())}" copyCount="${Math.max(0, card.copyCount)}" enabled="${card.enabled ? 'true' : 'false'}">`,
    `    <description>${escapeXml(card.descriptionText.trim())}</description>`,
    `    <rules>${escapeXml(card.rulesText.trim())}</rules>`,
    `    <tileImagePath>${escapeXml(card.tileImagePath.trim())}</tileImagePath>`,
    '  </card>'
  ].join('\n');
}

export function serializeEvent(item: UserEventData): string {
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

export function serializeRule(item: UserRuleData): string {
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

export function serializeMonster(item: UserMonsterData): string {
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

export function serializeObjectiveRoomAdventure(item: UserObjectiveRoomAdventureData): string {
  return [
    `  <objectiveRoom name="${escapeXml(item.objectiveRoomName.trim())}">`,
    `    <adventure id="${escapeXml(item.id.trim())}" name="${escapeXml(item.name.trim())}" generic="${item.generic ? 'true' : 'false'}">`,
    `      <flavor>${escapeXml(item.flavorText.trim())}</flavor>`,
    `      <rules>${escapeXml(item.rulesText.trim())}</rules>`,
    '    </adventure>',
    '  </objectiveRoom>'
  ].join('\n');
}

export function serializeWarrior(item: UserWarriorData): string {
  const lines = [`  <warrior id="${escapeXml(item.id.trim())}">`];
  lines.push(`    <name>${escapeXml(item.name.trim())}</name>`);
  lines.push(`    <race>${escapeXml(item.race.trim())}</race>`);
  lines.push(`    <counter>${escapeXml(item.counterPath.trim())}</counter>`);
  if (item.rulesPath.trim()) {
    lines.push(`    <rules>${escapeXml(item.rulesPath.trim())}</rules>`);
  }
  lines.push('  </warrior>');
  return lines.join('\n');
}

export function serializeLocation(item: UserLocationData): string {
  return [
    `  <location id="${escapeXml(item.id.trim())}">`,
    `    <name>${escapeXml(item.name.trim())}</name>`,
    '    <available>',
    ...item.availableTypes.map((type) => `      <type>${escapeXml(type)}</type>`),
    '    </available>',
    `    <description>${escapeXml(item.description.trim())}</description>`,
    '    <visitors>',
    ...item.visitors.map((visitor) => `      <visitor>${escapeXml(visitor.trim())}</visitor>`),
    '    </visitors>',
    `    <rules>${escapeXml(item.rules.trim())}</rules>`,
    '  </location>'
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

export function serializeTableFromModel(table: TableModel): string {
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

export function parseTableMetadata(xml: string): { name: string; kind: TableKind } | null {
  return parseTableName(xml);
}
