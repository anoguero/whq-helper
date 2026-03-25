import type {
  ContentRepository,
  EventEntry,
  EventModel,
  GroupEntry,
  LanguageCode,
  Monster,
  MonsterEntry,
  Rule,
  SpecialRuleLink,
  TableRefEntry,
  TableKind,
  TableModel
} from './types';
import { getXmlOverride } from './contentOverrides';
import { loadContentTranslations, translateContent } from './contentTranslations';
import { buildUserContentXmlDocuments } from './userContent';

interface ContentManifest {
  xmlFiles: string[];
}

export async function loadContentManifest(): Promise<ContentManifest> {
  const manifestResponse = await fetch('/content-manifest.json');
  return (await manifestResponse.json()) as ContentManifest;
}

function getAttribute(node: Element, name: string): string {
  return node.getAttribute(name) ?? '';
}

function getNamedChild(node: Element, name: string): Element | null {
  return Array.from(node.children).find((child) => child.tagName === name) ?? null;
}

function getChildValue(node: Element, childName: string): string {
  const child = getNamedChild(node, childName);
  return child ? child.textContent?.trim() ?? '' : '';
}

function parseSpecial(node: Element): {
  special: string;
  specialLinks: Record<string, SpecialRuleLink>;
  magicType: string;
  magicLevel: number;
} {
  const result = {
    special: '',
    specialLinks: {} as Record<string, SpecialRuleLink>,
    magicType: '',
    magicLevel: 0
  };

  const specialNode = getNamedChild(node, 'special');
  if (!specialNode) {
    return result;
  }

  for (const child of Array.from(specialNode.children)) {
    if (child.tagName === 'rule') {
      const id = getAttribute(child, 'id').trim();
      const text = child.textContent?.trim() ?? '';
      const parameter = getAttribute(child, 'param').trim();
      const parameters = parameter ? [parameter] : [];
      if (id && text) {
        result.specialLinks[id] = { text, parameter, parameters };
      }
    } else if (child.tagName === 'magic') {
      result.magicType = getAttribute(child, 'id').trim();
      result.magicLevel = Number.parseInt(getAttribute(child, 'level'), 10) || 0;
    } else if (child.tagName === 'text') {
      result.special = child.textContent?.trim() ?? '';
    }
  }

  return result;
}

function toTableKind(raw: string): TableKind {
  const normalized = raw.trim().toLowerCase();
  if (normalized === 'travel') {
    return 'travel';
  }
  if (normalized === 'settlement') {
    return 'settlement';
  }
  if (normalized === 'treasure') {
    return 'treasure';
  }
  return 'dungeon';
}

function parseAmbiences(attr: string): string[] {
  return attr
    .trim()
    .split(/\s+/)
    .filter(Boolean);
}

function parseLevel(raw: string): number {
  const parsed = Number.parseInt(raw, 10);
  if (!Number.isFinite(parsed)) {
    return 1;
  }
  return Math.max(1, Math.min(10, parsed));
}

function parseMonsterEntry(node: Element): MonsterEntry {
  const numberRaw = getAttribute(node, 'number');
  const [min, max] = numberRaw.includes('-')
    ? numberRaw.split('-').map((part) => Number.parseInt(part, 10) || 0)
    : [Number.parseInt(numberRaw, 10) || 0, Number.parseInt(numberRaw, 10) || 0];

  const special = parseSpecial(node);
  const specialNode = getNamedChild(node, 'special');
  const appendSpecials = specialNode ? getAttribute(specialNode, 'append') !== 'false' : true;

  return {
    kind: 'monster',
    id: getAttribute(node, 'id'),
    level: parseLevel(getAttribute(node, 'level')),
    min,
    max,
    ambiences: parseAmbiences(getAttribute(node, 'ambiences')),
    appendSpecials,
    ...special
  };
}

function parseEventEntry(node: Element): EventEntry {
  return {
    kind: 'event',
    id: getAttribute(node, 'id'),
    ambiences: parseAmbiences(getAttribute(node, 'ambiences'))
  };
}

function parseTableRefEntry(node: Element): TableRefEntry {
  const level = parseLevel(getAttribute(node, 'level'));
  const targetLevelRaw = getAttribute(node, 'targetLevel');
  return {
    kind: 'tableRef',
    tableName: getAttribute(node, 'name'),
    level,
    targetLevel: targetLevelRaw ? parseLevel(targetLevelRaw) : level,
    times: Math.max(1, Number.parseInt(getAttribute(node, 'times'), 10) || 1),
    ambiences: parseAmbiences(getAttribute(node, 'ambiences'))
  };
}

function parseTable(node: Element): TableModel {
  const monsters: Array<MonsterEntry | GroupEntry | TableRefEntry> = [];
  const events: EventEntry[] = [];

  for (const child of Array.from(node.children)) {
    if (child.tagName === 'monster') {
      monsters.push(parseMonsterEntry(child));
    } else if (child.tagName === 'event') {
      events.push(parseEventEntry(child));
    } else if (child.tagName === 'tableRef') {
      monsters.push(parseTableRefEntry(child));
    } else if (child.tagName === 'group') {
      const groupEntries = Array.from(child.children)
        .filter((member) => member.tagName === 'monster')
        .map((member) => parseMonsterEntry(member));
      if (groupEntries.length > 0) {
        monsters.push({ kind: 'group', level: parseLevel(getAttribute(child, 'level')), entries: groupEntries });
      }
    }
  }

  const kindRaw = getAttribute(node, 'kind');
  return {
    name: getAttribute(node, 'name'),
    kindRaw,
    kind: toTableKind(kindRaw),
    active: false,
    monsters,
    events
  };
}

function parseEvent(node: Element, treasureDefault: boolean, translations: Map<string, string>): EventModel {
  const id = getAttribute(node, 'id');
  const treasureRaw = getChildValue(node, 'treasure');
  return {
    id,
    name: translateContent(translations, `event.${id}.name`, getAttribute(node, 'name')),
    category: 'dungeon',
    flavor: translateContent(translations, `event.${id}.flavor`, getChildValue(node, 'flavor')),
    rules: translateContent(translations, `event.${id}.rules`, getChildValue(node, 'rules')),
    special: translateContent(translations, `event.${id}.special`, getChildValue(node, 'special')),
    goldValue: translateContent(translations, `event.${id}.goldValue`, getChildValue(node, 'goldValue')),
    users: translateContent(translations, `event.${id}.users`, getChildValue(node, 'users')),
    treasure: treasureRaw ? treasureRaw.toLowerCase() === 'true' : treasureDefault
  };
}

function parseTravelOrSettlementEvent(
  node: Element,
  category: 'travel' | 'settlement',
  translations: Map<string, string>
): EventModel {
  const id = getAttribute(node, 'id');
  return {
    id,
    name: translateContent(translations, `event.${id}.name`, getAttribute(node, 'name')),
    category,
    flavor: '',
    rules: translateContent(translations, `event.${id}.rules`, getChildValue(node, 'rules')),
    special: '',
    goldValue: '',
    users: '',
    treasure: false
  };
}

function parseMonster(node: Element, translations: Map<string, string>): Monster {
  const id = getAttribute(node, 'id');
  const special = parseSpecial(node);
  return {
    id,
    name: translateContent(translations, `monster.${id}.name`, getAttribute(node, 'name')),
    plural: translateContent(translations, `monster.${id}.plural`, getAttribute(node, 'plural')),
    factions: parseAmbiences(getAttribute(node, 'factions')),
    move: getChildValue(node, 'move'),
    weaponskill: getChildValue(node, 'weaponskill'),
    ballisticskill: getChildValue(node, 'ballisticskill'),
    strength: getChildValue(node, 'strength'),
    toughness: getChildValue(node, 'toughness'),
    wounds: getChildValue(node, 'wounds'),
    initiative: getChildValue(node, 'initiative'),
    attacks: getChildValue(node, 'attacks'),
    gold: getChildValue(node, 'gold'),
    armor: getChildValue(node, 'armor'),
    damage: getChildValue(node, 'damage'),
    ...special,
    special: translateContent(translations, `monster.${id}.special`, special.special)
  };
}

function parseRule(node: Element, translations: Map<string, string>): Rule {
  const id = getAttribute(node, 'id');
  return {
    type: node.tagName,
    id,
    name: translateContent(translations, `rule.${id}.name`, getAttribute(node, 'name')),
    text: translateContent(translations, `rule.${id}.text`, node.textContent?.trim() ?? ''),
    parameterName: getAttribute(node, 'parameterName').trim(),
    parameterNames: getAttribute(node, 'parameterNames')
      .split(',')
      .map((value) => value.trim())
      .filter(Boolean),
    parameterFormat: getAttribute(node, 'parameterFormat').trim()
  };
}

export async function loadContent(language: LanguageCode): Promise<ContentRepository> {
  const manifest = await loadContentManifest();
  const translations = await loadContentTranslations(language);

  const repository: ContentRepository = {
    monsters: new Map<string, Monster>(),
    events: new Map<string, EventModel>(),
    travelEvents: new Map<string, EventModel>(),
    settlementEvents: new Map<string, EventModel>(),
    rules: new Map<string, Rule>(),
    tables: new Map<string, TableModel>()
  };

  const parser = new DOMParser();
  const sources = [
    ...manifest.xmlFiles.map((path) => ({ path, override: getXmlOverride(path) })),
    ...buildUserContentXmlDocuments().map((entry) => ({ path: entry.path, override: entry.xml }))
  ];

  for (const { path, override } of sources) {
    let xml = override ?? '';
    if (!override) {
      const response = await fetch(path);
      if (!response.ok) {
        continue;
      }
      xml = await response.text();
    }
    const doc = parser.parseFromString(xml, 'text/xml');
    const root = doc.documentElement;

    if (!root) {
      continue;
    }

    if (root.tagName === 'rules') {
      for (const node of Array.from(root.children)) {
        if (node.tagName === 'rule' || node.tagName === 'magic') {
          const parsed = parseRule(node, translations);
          const existing = repository.rules.get(parsed.id);
          repository.rules.set(parsed.id, {
            ...parsed,
            parameterName: parsed.parameterName || existing?.parameterName || '',
            parameterNames: parsed.parameterNames.length > 0 ? parsed.parameterNames : existing?.parameterNames ?? [],
            parameterFormat: parsed.parameterFormat || existing?.parameterFormat || ''
          });
        }
      }
      continue;
    }

    if (root.tagName === 'monsters') {
      for (const node of Array.from(root.children)) {
        if (node.tagName === 'monster') {
          const parsed = parseMonster(node, translations);
          repository.monsters.set(parsed.id, parsed);
        }
      }
      continue;
    }

    if (root.tagName === 'tables') {
      for (const node of Array.from(root.children)) {
        if (node.tagName === 'table') {
          const parsed = parseTable(node);
          repository.tables.set(parsed.name, parsed);
        }
      }
      continue;
    }

    if (root.tagName === 'events') {
      if (path.includes('/travel/')) {
        for (const node of Array.from(root.children)) {
          if (node.tagName === 'event') {
            const parsed = parseTravelOrSettlementEvent(node, 'travel', translations);
            repository.travelEvents.set(parsed.id, parsed);
          }
        }
      } else if (path.includes('/settlement/')) {
        for (const node of Array.from(root.children)) {
          if (node.tagName === 'event') {
            const parsed = parseTravelOrSettlementEvent(node, 'settlement', translations);
            repository.settlementEvents.set(parsed.id, parsed);
          }
        }
      } else {
        for (const node of Array.from(root.children)) {
          if (node.tagName === 'event') {
            const parsed = parseEvent(node, true, translations);
            repository.events.set(parsed.id, parsed);
          }
        }
      }
    }
  }

  return repository;
}

export function findAnyEvent(repository: ContentRepository, id: string): EventModel | undefined {
  return repository.events.get(id) ?? repository.travelEvents.get(id) ?? repository.settlementEvents.get(id);
}
