const STORAGE_KEY = 'whq_helper_spa_xml_overrides_v1';

export function loadXmlOverrides(): Record<string, string> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return {};
    }
    const parsed = JSON.parse(raw) as Record<string, string>;
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

export function getXmlOverride(path: string): string | null {
  const map = loadXmlOverrides();
  return map[path] ?? null;
}
