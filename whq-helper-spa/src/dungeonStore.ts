import { loadContentTranslations, translateContent } from './contentTranslations';
import type { CardType, DungeonCard, LanguageCode, ObjectiveRoomAdventure } from './types';
import { buildUserContentXmlDocuments, loadUserDungeonCards } from './userContent';

const DUNGEON_CARDS_STORAGE_KEY = 'whq_helper_spa_dungeon_cards_v1';
const DEFAULT_ENVIRONMENT = 'The Old World';

function normalizeEnvironment(value: string): string {
  return value.trim() || DEFAULT_ENVIRONMENT;
}

function normalizeKey(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replaceAll('&', 'and')
    .replaceAll(/[^a-z0-9]+/g, '-')
    .replaceAll(/^-+|-+$/g, '');
}

function normalizeCard(card: DungeonCard): DungeonCard {
  return {
    ...card,
    name: card.name.trim(),
    environment: normalizeEnvironment(card.environment),
    copyCount: Math.max(0, card.copyCount),
    descriptionText: card.descriptionText.trim(),
    rulesText: card.rulesText.trim(),
    tileImagePath: card.tileImagePath.trim(),
    enabled: !!card.enabled
  };
}

function sortCards(cards: DungeonCard[]): DungeonCard[] {
  return [...cards].sort((a, b) => {
    const env = a.environment.localeCompare(b.environment, undefined, { sensitivity: 'base' });
    if (env !== 0) {
      return env;
    }
    const name = a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });
    if (name !== 0) {
      return name;
    }
    return a.id - b.id;
  });
}

function cardTypeFromRaw(raw: string): CardType {
  const normalized = raw.trim().toUpperCase();
  if (
    normalized === 'DUNGEON_ROOM' ||
    normalized === 'OBJECTIVE_ROOM' ||
    normalized === 'CORRIDOR' ||
    normalized === 'SPECIAL'
  ) {
    return normalized;
  }
  return 'DUNGEON_ROOM';
}

function parseDungeonCardsXml(xmlText: string): DungeonCard[] {
  const parser = new DOMParser();
  const doc = parser.parseFromString(xmlText, 'text/xml');
  const cards: DungeonCard[] = [];

  for (const node of Array.from(doc.documentElement.children)) {
    if (node.tagName !== 'card') {
      continue;
    }

    const id = Number.parseInt(node.getAttribute('id') ?? '', 10);
    const copyCount = Number.parseInt(node.getAttribute('copyCount') ?? '0', 10);

    const findChildText = (tag: string): string => {
      const found = Array.from(node.children).find((child) => child.tagName === tag);
      return found?.textContent?.trim() ?? '';
    };

    cards.push(
      normalizeCard({
        id: Number.isFinite(id) && id > 0 ? id : cards.length + 1,
        name: node.getAttribute('name')?.trim() ?? '',
        type: cardTypeFromRaw(node.getAttribute('type') ?? ''),
        environment: node.getAttribute('environment')?.trim() ?? DEFAULT_ENVIRONMENT,
        copyCount: Number.isFinite(copyCount) ? copyCount : 0,
        enabled: (node.getAttribute('enabled') ?? 'false').toLowerCase() === 'true',
        descriptionText: findChildText('description'),
        rulesText: findChildText('rules'),
        tileImagePath: findChildText('tileImagePath')
      })
    );
  }

  return sortCards(cards);
}

function parseAdventuresXml(xmlText: string): ObjectiveRoomAdventure[] {
  const parser = new DOMParser();
  const doc = parser.parseFromString(xmlText, 'text/xml');
  const result: ObjectiveRoomAdventure[] = [];

  for (const roomNode of Array.from(doc.documentElement.children)) {
    if (roomNode.tagName !== 'objectiveRoom') {
      continue;
    }

    const objectiveRoomName = (roomNode.getAttribute('name') ?? '').trim();

    for (const adventureNode of Array.from(roomNode.children)) {
      if (adventureNode.tagName !== 'adventure') {
        continue;
      }

      const childText = (tag: string): string => {
        const found = Array.from(adventureNode.children).find((child) => child.tagName === tag);
        return found?.textContent?.trim() ?? '';
      };

      result.push({
        objectiveRoomName,
        id: (adventureNode.getAttribute('id') ?? '').trim(),
        name: (adventureNode.getAttribute('name') ?? '').trim(),
        flavorText: childText('flavor'),
        rulesText: childText('rules'),
        generic: (adventureNode.getAttribute('generic') ?? 'false').toLowerCase() === 'true'
      });
    }
  }

  return result;
}

function mergeAdventuresWithBaseline(
  baselineAdventures: ObjectiveRoomAdventure[],
  userAdventures: ObjectiveRoomAdventure[]
): ObjectiveRoomAdventure[] {
  const merged = new Map<string, ObjectiveRoomAdventure>();

  for (const adventure of baselineAdventures) {
    merged.set(`${adventure.objectiveRoomName.trim().toUpperCase()}::${adventure.id.trim().toUpperCase()}`, adventure);
  }
  for (const adventure of userAdventures) {
    merged.set(`${adventure.objectiveRoomName.trim().toUpperCase()}::${adventure.id.trim().toUpperCase()}`, adventure);
  }

  return Array.from(merged.values());
}

function cardsFromStorage(): DungeonCard[] | null {
  try {
    const raw = localStorage.getItem(DUNGEON_CARDS_STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as DungeonCard[];
    if (!Array.isArray(parsed) || parsed.length === 0) {
      return null;
    }
    return sortCards(parsed.map(normalizeCard));
  } catch {
    return null;
  }
}

function saveCardsToStorage(cards: DungeonCard[]): void {
  localStorage.setItem(DUNGEON_CARDS_STORAGE_KEY, JSON.stringify(sortCards(cards.map(normalizeCard))));
}

function mergeCardsWithBaseline(baselineCards: DungeonCard[], persistedCards: DungeonCard[]): DungeonCard[] {
  const persistedById = new Map(persistedCards.map((card) => [card.id, normalizeCard(card)]));
  const merged = baselineCards.map((baselineCard) => {
    const persistedCard = persistedById.get(baselineCard.id);
    if (!persistedCard) {
      return baselineCard;
    }

    return normalizeCard({
      ...baselineCard,
      enabled: persistedCard.enabled,
      copyCount: persistedCard.copyCount
    });
  });

  const baselineIds = new Set(baselineCards.map((card) => card.id));
  for (const persistedCard of persistedCards) {
    if (!baselineIds.has(persistedCard.id)) {
      merged.push(normalizeCard(persistedCard));
    }
  }

  return sortCards(merged);
}

export class DungeonCardStore {
  private cards: DungeonCard[] = [];
  private adventures: ObjectiveRoomAdventure[] = [];
  private language: LanguageCode = 'ES';
  private translations = new Map<string, string>();

  async init(language: LanguageCode): Promise<void> {
    this.language = language;
    this.translations = await loadContentTranslations(language);
    const [dungeonXml, adventuresXml] = await Promise.all([
      fetch('/data/xml/dungeon/dungeon-cards.xml').then((response) => response.text()),
      fetch('/data/xml/adventures/original-objective-room-adventures.xml').then((response) => response.text())
    ]);

    const baselineCards = parseDungeonCardsXml(dungeonXml);
    const userCards = loadUserDungeonCards().map((card) => normalizeCard(card));
    const contentCards = mergeCardsWithBaseline(baselineCards, userCards);
    const userAdventureXml =
      buildUserContentXmlDocuments().find((entry) => entry.path === '/userdefined/adventures/userdefined-objective-room-adventures.xml')
        ?.xml ?? '';
    const userAdventures = userAdventureXml ? parseAdventuresXml(userAdventureXml) : [];
    const persisted = cardsFromStorage();
    this.cards = persisted ? mergeCardsWithBaseline(contentCards, persisted) : contentCards;
    this.adventures = mergeAdventuresWithBaseline(parseAdventuresXml(adventuresXml), userAdventures);
    saveCardsToStorage(this.cards);
  }

  async setLanguage(language: LanguageCode): Promise<void> {
    this.language = language;
    this.translations = await loadContentTranslations(language);
  }

  private translateCard(card: DungeonCard): DungeonCard {
    const keyBase = `dungeonCard.${card.id}`;
    return {
      ...card,
      name: translateContent(this.translations, `${keyBase}.name`, card.name),
      descriptionText: translateContent(this.translations, `${keyBase}.description`, card.descriptionText),
      rulesText: translateContent(this.translations, `${keyBase}.rules`, card.rulesText)
    };
  }

  private translateAdventure(adventure: ObjectiveRoomAdventure): ObjectiveRoomAdventure {
    const roomKey = normalizeKey(adventure.objectiveRoomName);
    const specificKeyBase = `adventure.${roomKey}.${adventure.id}`;
    const fallbackKeyBase = `adventure.${adventure.id}`;
    const name = translateContent(
      this.translations,
      `${specificKeyBase}.name`,
      translateContent(this.translations, `${fallbackKeyBase}.name`, adventure.name)
    );
    const flavorText = translateContent(
      this.translations,
      `${specificKeyBase}.flavor`,
      translateContent(this.translations, `${fallbackKeyBase}.flavor`, adventure.flavorText)
    );
    const rulesText = translateContent(
      this.translations,
      `${specificKeyBase}.rules`,
      translateContent(this.translations, `${fallbackKeyBase}.rules`, adventure.rulesText)
    );

    return {
      ...adventure,
      name,
      flavorText,
      rulesText
    };
  }

  loadCards(): DungeonCard[] {
    return sortCards(this.cards.map((card) => this.translateCard(card)));
  }

  loadEnvironments(): string[] {
    return [
      ...new Set(
        this.cards
          .filter((card) => card.type === 'OBJECTIVE_ROOM')
          .filter((card) => card.enabled && card.copyCount > 0)
          .map((card) => normalizeEnvironment(card.environment))
      )
    ].sort((a, b) =>
      a.localeCompare(b, undefined, { sensitivity: 'base' })
    );
  }

  loadObjectiveRoomsByEnvironment(environment: string): DungeonCard[] {
    const normalized = normalizeEnvironment(environment);
    return this.cards
      .filter((card) => card.environment.localeCompare(normalized, undefined, { sensitivity: 'base' }) === 0)
      .filter((card) => card.type === 'OBJECTIVE_ROOM')
      .filter((card) => card.enabled && card.copyCount > 0)
      .map((card) => this.translateCard(card))
      .sort((a, b) => a.name.localeCompare(b.name, undefined, { sensitivity: 'base' }));
  }

  updateCard(updatedCard: DungeonCard): void {
    const index = this.cards.findIndex((card) => card.id === updatedCard.id);
    if (index < 0) {
      throw new Error(
        this.language === 'EN'
          ? `Card with id ${updatedCard.id} was not found`
          : `No se ha encontrado la carta con id ${updatedCard.id}`
      );
    }

    this.cards[index] = normalizeCard(updatedCard);
    saveCardsToStorage(this.cards);
  }

  deleteCard(cardId: number): void {
    this.cards = this.cards.filter((card) => card.id !== cardId);
    saveCardsToStorage(this.cards);
  }

  insertCards(cards: DungeonCard[]): void {
    if (cards.length === 0) {
      return;
    }

    let nextId = this.cards.reduce((max, card) => Math.max(max, card.id), 0) + 1;
    for (const card of cards) {
      this.cards.push(
        normalizeCard({
          ...card,
          id: nextId++
        })
      );
    }

    saveCardsToStorage(this.cards);
  }

  loadAdventuresForObjectiveRoom(objectiveRoomName: string): ObjectiveRoomAdventure[] {
    const normalized = objectiveRoomName.trim().toUpperCase();
    const filtered = this.adventures
      .filter((adventure) => adventure.objectiveRoomName.trim().toUpperCase() === normalized)
      .map((adventure) => this.translateAdventure(adventure));

    const generic: ObjectiveRoomAdventure = {
      objectiveRoomName: objectiveRoomName.trim() || 'OBJECTIVE ROOM',
      id: 'generic',
      name: translateContent(this.translations, 'adventure.generic.name', this.language === 'EN' ? 'Generic' : 'Generica'),
      flavorText: translateContent(
        this.translations,
        'adventure.generic.flavor',
        this.language === 'EN'
          ? `Use the default ambience for ${objectiveRoomName || 'the objective room'} without a specific mission.`
          : `Usa la ambientacion normal de ${objectiveRoomName || 'la sala objetivo'} sin una aventura concreta.`
      ),
      rulesText: translateContent(
        this.translations,
        'adventure.generic.rules',
        this.language === 'EN'
          ? 'Resolve the objective room with the default app behaviour. There is no additional special objective.'
          : 'Resuelve la sala objetivo con el comportamiento habitual de la aplicacion. No hay objetivo especial adicional.'
      ),
      generic: true
    };

    if (filtered.length === 0) {
      return [generic];
    }

    if (!filtered.some((adventure) => adventure.generic)) {
      filtered.push(generic);
    }

    return filtered.sort((a, b) => {
      if (a.generic !== b.generic) {
        return a.generic ? -1 : 1;
      }
      return a.id.localeCompare(b.id, undefined, { sensitivity: 'base' });
    });
  }

  loadAllAdventures(): ObjectiveRoomAdventure[] {
    return this.adventures.map((adventure) => this.translateAdventure(adventure));
  }
}

const CSV_HEADER = [
  'name',
  'type',
  'environment',
  'copy_count',
  'enabled',
  'description_text',
  'rules_text',
  'tile_image_path'
];

function parseCsvRows(content: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = '';
  let inQuotes = false;

  for (let i = 0; i < content.length; i += 1) {
    const char = content[i] as string;

    if (inQuotes) {
      if (char === '"') {
        if (content[i + 1] === '"') {
          cell += '"';
          i += 1;
        } else {
          inQuotes = false;
        }
      } else {
        cell += char;
      }
      continue;
    }

    if (char === '"') {
      inQuotes = true;
    } else if (char === ',') {
      row.push(cell);
      cell = '';
    } else if (char === '\n') {
      row.push(cell);
      rows.push(row);
      row = [];
      cell = '';
    } else if (char !== '\r') {
      cell += char;
    }
  }

  if (inQuotes) {
    throw new Error('CSV invalido: comillas sin cerrar');
  }

  if (cell || row.length > 0) {
    row.push(cell);
    rows.push(row);
  }

  return rows;
}

export async function importCardsFromCsvFile(file: File): Promise<DungeonCard[]> {
  const content = await file.text();
  const rows = parseCsvRows(content);
  if (rows.length === 0) {
    return [];
  }

  let start = 0;
  const first = rows[0].map((cell) => cell.trim().toLowerCase());
  if (CSV_HEADER.every((header, idx) => first[idx] === header)) {
    start = 1;
  }

  const cards: DungeonCard[] = [];
  for (let i = start; i < rows.length; i += 1) {
    const row = rows[i] ?? [];
    if (row.length === 0 || row.every((value) => !value.trim())) {
      continue;
    }

    const name = (row[0] ?? '').trim();
    const type = cardTypeFromRaw(row[1] ?? '');
    const environment = normalizeEnvironment((row[2] ?? '').trim());

    const hasExtended = row.length >= 8;
    const copyCount = hasExtended ? Math.max(0, Number.parseInt(row[3] ?? '1', 10) || 0) : 1;

    let enabled = true;
    if (hasExtended) {
      const enabledRaw = (row[4] ?? '').trim().toLowerCase();
      enabled = ['1', 'true', 'yes', 'si', 'sí'].includes(enabledRaw);
    }

    const textOffset = hasExtended ? 5 : 3;

    cards.push({
      id: 0,
      name,
      type,
      environment,
      copyCount,
      enabled,
      descriptionText: (row[textOffset] ?? '').trim(),
      rulesText: (row[textOffset + 1] ?? '').trim(),
      tileImagePath: (row[textOffset + 2] ?? '').trim()
    });
  }

  return cards;
}

function csvCell(value: string): string {
  return `"${value.replaceAll('"', '""')}"`;
}

export function exportCardsToCsv(cards: DungeonCard[]): string {
  const lines: string[] = [CSV_HEADER.join(',')];

  for (const card of cards) {
    lines.push(
      [
        csvCell(card.name),
        csvCell(card.type),
        csvCell(normalizeEnvironment(card.environment)),
        csvCell(String(card.copyCount)),
        csvCell(card.enabled ? '1' : '0'),
        csvCell(card.descriptionText),
        csvCell(card.rulesText),
        csvCell(card.tileImagePath)
      ].join(',')
    );
  }

  return `${lines.join('\n')}\n`;
}

export function downloadCsv(filename: string, csvContent: string): void {
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
