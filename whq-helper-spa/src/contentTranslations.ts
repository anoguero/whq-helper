import type { LanguageCode } from './types';

const cache = new Map<LanguageCode, Map<string, string>>();

function normalizeLanguage(language: LanguageCode): string {
  return language.toLowerCase();
}

function parseTranslations(xml: string): Map<string, string> {
  const map = new Map<string, string>();
  const doc = new DOMParser().parseFromString(xml, 'text/xml');
  const root = doc.documentElement;
  if (!root || root.tagName !== 'translations') {
    return map;
  }

  for (const node of Array.from(root.children)) {
    if (node.tagName !== 'entry') {
      continue;
    }
    const key = (node.getAttribute('key') ?? '').trim();
    const value = node.textContent?.trim() ?? '';
    if (!key || !value) {
      continue;
    }
    map.set(key, value);
  }
  return map;
}

export async function loadContentTranslations(language: LanguageCode): Promise<Map<string, string>> {
  const cached = cache.get(language);
  if (cached) {
    return cached;
  }

  const path = `/data/i18n/content-${normalizeLanguage(language)}.xml`;
  try {
    const response = await fetch(path);
    if (!response.ok) {
      cache.set(language, new Map());
      return cache.get(language)!;
    }
    const map = parseTranslations(await response.text());
    cache.set(language, map);
    return map;
  } catch {
    cache.set(language, new Map());
    return cache.get(language)!;
  }
}

export function translateContent(translations: Map<string, string>, key: string, fallback: string): string {
  return translations.get(key) ?? fallback;
}
