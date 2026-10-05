import { parseTranslations } from './contentTranslations';
import type { LanguageCode } from './types';

// Textos de interfaz en shared/data/i18n/ui-{es,en}.xml (mismo formato que content-*.xml).
// loadUiTranslations() se espera antes del primer render, asi t() sigue siendo sincrono.
const UI_LANGUAGES: LanguageCode[] = ['ES', 'EN'];
const dictionaries = new Map<LanguageCode, Map<string, string>>();
let loading: Promise<void> | null = null;

async function loadUiDictionary(language: LanguageCode): Promise<Map<string, string>> {
  try {
    const response = await fetch(`/data/i18n/ui-${language.toLowerCase()}.xml`);
    return response.ok ? parseTranslations(await response.text()) : new Map();
  } catch {
    return new Map();
  }
}

export function loadUiTranslations(): Promise<void> {
  if (!loading) {
    loading = Promise.all(UI_LANGUAGES.map(async (language) => {
      dictionaries.set(language, await loadUiDictionary(language));
    })).then(() => undefined);
  }
  return loading;
}

export function t(language: LanguageCode, key: string): string {
  return dictionaries.get(language)?.get(key) ?? key;
}

export function tf(language: LanguageCode, key: string, values: Record<string, string | number>): string {
  let text = t(language, key);
  for (const [name, value] of Object.entries(values)) {
    text = text.replaceAll(`{${name}}`, String(value));
  }
  return text;
}

export function formatTreasureUsers(language: LanguageCode, users: string): string {
  const map: Record<string, string> = {
    B: t(language, 'card.treasure.user.barbarian'),
    D: t(language, 'card.treasure.user.dwarf'),
    E: t(language, 'card.treasure.user.elf'),
    W: t(language, 'card.treasure.user.wizard')
  };

  return users
    .trim()
    .toUpperCase()
    .split('')
    .map((code) => map[code] ?? code)
    .join(', ');
}

export function getAdventureAmbiences(language: LanguageCode): Array<{ value: string; label: string }> {
  return [
    { value: 'generic', label: language === 'EN' ? 'Generic' : 'Generica' },
    { value: 'chaos', label: 'Chaos' },
    { value: 'undead', label: 'Undead' },
    { value: 'skaven', label: 'Skaven' },
    { value: 'orcs', label: 'Orcs' },
    { value: 'chaos-dwarves', label: 'Chaos Dwarves' },
    { value: 'dark-elves', label: 'Dark Elves' }
  ];
}

export function getSettlementTypes(language: LanguageCode): Array<{ value: string; label: string }> {
  return [
    { value: 'any', label: t(language, 'settlement.type.any') },
    { value: 'city', label: t(language, 'settlement.type.city') },
    { value: 'town', label: t(language, 'settlement.type.town') },
    { value: 'village', label: t(language, 'settlement.type.village') },
    { value: 'outskirts', label: t(language, 'settlement.type.outskirts') },
    { value: 'special', label: t(language, 'settlement.type.special') }
  ];
}
