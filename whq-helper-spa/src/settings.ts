import type { AppSettings } from './types';

const STORAGE_KEY = 'whq_helper_spa_settings_v1';

const DEFAULT_SETTINGS: AppSettings = {
  simulateDeck: false,
  showEventDeck: true,
  showSettlementDeck: true,
  showTravelDeck: true,
  showTreasureDeck: true,
  showObjectiveTreasureDeck: true,
  dungeonActive: false,
  activeDungeonLevel: 1,
  partySize: 4,
  eventProbability: 37,
  treasureGoldProbability: 19,
  language: 'ES',
  adventureAmbience: 'generic',
  objectiveMonsterEasyWeight: 1,
  objectiveMonsterNormalWeight: 2,
  objectiveMonsterHardWeight: 1,
  objectiveMonsterVeryHardWeight: 1,
  objectiveMonsterExtremeWeight: 1,
  tableActive: {}
};

function parseBoolean(value: string | undefined, fallback: boolean): boolean {
  if (!value) {
    return fallback;
  }
  return value.trim().toLowerCase() === 'true';
}

function parseNumber(value: string | undefined, fallback: number): number {
  if (!value) {
    return fallback;
  }
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function decodeJavaPropertyKey(rawKey: string): string {
  return rawKey.replace(/\\ /g, ' ').replace(/\\:/g, ':').replace(/\\=/g, '=');
}

function parseSettingsCfg(content: string): Partial<AppSettings> {
  const props = new Map<string, string>();

  for (const lineRaw of content.split(/\r?\n/)) {
    const line = lineRaw.trim();
    if (!line || line.startsWith('#')) {
      continue;
    }
    const separatorIndex = line.indexOf('=');
    if (separatorIndex < 0) {
      continue;
    }
    const key = decodeJavaPropertyKey(line.slice(0, separatorIndex).trim());
    const value = line.slice(separatorIndex + 1).trim();
    props.set(key, value);
  }

  const tableActive: Record<string, boolean> = {};
  for (const [key, value] of props.entries()) {
    if (key.endsWith('.active')) {
      const tableName = key.slice(0, -'.active'.length);
      tableActive[tableName] = parseBoolean(value, false);
    }
  }

  const languageRaw = props.get('Language')?.trim().toUpperCase();
  const language = languageRaw === 'EN' ? 'EN' : 'ES';

  return {
    simulateDeck: parseBoolean(props.get('SimulateDeck'), DEFAULT_SETTINGS.simulateDeck),
    showEventDeck: parseBoolean(props.get('ShowEventDeck'), DEFAULT_SETTINGS.showEventDeck),
    showSettlementDeck: parseBoolean(props.get('ShowSettlementDeck'), DEFAULT_SETTINGS.showSettlementDeck),
    showTravelDeck: parseBoolean(props.get('ShowTravelDeck'), DEFAULT_SETTINGS.showTravelDeck),
    showTreasureDeck: parseBoolean(props.get('ShowTreasureDeck'), DEFAULT_SETTINGS.showTreasureDeck),
    showObjectiveTreasureDeck: parseBoolean(
      props.get('ShowObjectiveTreasureDeck'),
      DEFAULT_SETTINGS.showObjectiveTreasureDeck
    ),
    dungeonActive: parseBoolean(props.get('AdventureActive'), DEFAULT_SETTINGS.dungeonActive),
    activeDungeonLevel: parseNumber(props.get('AdventureLevel'), DEFAULT_SETTINGS.activeDungeonLevel),
    partySize: parseNumber(props.get('PartySize'), DEFAULT_SETTINGS.partySize),
    eventProbability: parseNumber(props.get('EventPropability'), DEFAULT_SETTINGS.eventProbability),
    treasureGoldProbability: parseNumber(
      props.get('TreasureGoldProbability'),
      DEFAULT_SETTINGS.treasureGoldProbability
    ),
    language,
    adventureAmbience: props.get('AdventureAmbience')?.trim() || DEFAULT_SETTINGS.adventureAmbience,
    objectiveMonsterEasyWeight: parseNumber(
      props.get('ObjectiveMonsterEasyWeight'),
      DEFAULT_SETTINGS.objectiveMonsterEasyWeight
    ),
    objectiveMonsterNormalWeight: parseNumber(
      props.get('ObjectiveMonsterNormalWeight'),
      DEFAULT_SETTINGS.objectiveMonsterNormalWeight
    ),
    objectiveMonsterHardWeight: parseNumber(
      props.get('ObjectiveMonsterHardWeight'),
      DEFAULT_SETTINGS.objectiveMonsterHardWeight
    ),
    objectiveMonsterVeryHardWeight: parseNumber(
      props.get('ObjectiveMonsterVeryHardWeight'),
      DEFAULT_SETTINGS.objectiveMonsterVeryHardWeight
    ),
    objectiveMonsterExtremeWeight: parseNumber(
      props.get('ObjectiveMonsterExtremeWeight'),
      DEFAULT_SETTINGS.objectiveMonsterExtremeWeight
    ),
    tableActive
  };
}

function clampProbability(value: number): number {
  return Math.max(0, Math.min(100, value));
}

export async function loadSettings(): Promise<AppSettings> {
  let fromCfg: Partial<AppSettings> = {};

  try {
    const response = await fetch('/settings.cfg');
    if (response.ok) {
      const raw = await response.text();
      fromCfg = parseSettingsCfg(raw);
    }
  } catch {
    // Ignore and keep defaults.
  }

  let fromStorage: Partial<AppSettings> = {};
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      fromStorage = JSON.parse(raw) as Partial<AppSettings>;
    }
  } catch {
    // Ignore and keep defaults.
  }

  const merged: AppSettings = {
    ...DEFAULT_SETTINGS,
    ...fromCfg,
    ...fromStorage,
    tableActive: {
      ...(fromCfg.tableActive ?? {}),
      ...(fromStorage.tableActive ?? {})
    }
  };

  merged.eventProbability = clampProbability(merged.eventProbability);
  merged.treasureGoldProbability = clampProbability(merged.treasureGoldProbability);
  merged.partySize = Math.max(1, merged.partySize);
  merged.activeDungeonLevel = Math.max(1, Math.min(10, merged.activeDungeonLevel));
  merged.objectiveMonsterEasyWeight = Math.max(0, merged.objectiveMonsterEasyWeight);
  merged.objectiveMonsterNormalWeight = Math.max(0, merged.objectiveMonsterNormalWeight);
  merged.objectiveMonsterHardWeight = Math.max(0, merged.objectiveMonsterHardWeight);
  merged.objectiveMonsterVeryHardWeight = Math.max(0, merged.objectiveMonsterVeryHardWeight);
  merged.objectiveMonsterExtremeWeight = Math.max(0, merged.objectiveMonsterExtremeWeight);

  return merged;
}

export function saveSettings(settings: AppSettings): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
}
