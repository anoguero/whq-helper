const STORAGE_KEY = 'whq_helper_spa_counter_assets_v1';

function loadCounterAssets(): Record<string, string> {
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

function saveCounterAssets(map: Record<string, string>): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(map));
}

export function getCounterAsset(name: string): string | null {
  const normalized = name.trim();
  if (!normalized) {
    return null;
  }
  return loadCounterAssets()[normalized] ?? null;
}

export function saveCounterAsset(name: string, dataUrl: string): void {
  const normalized = name.trim();
  if (!normalized || !dataUrl.trim()) {
    return;
  }
  const map = loadCounterAssets();
  map[normalized] = dataUrl;
  saveCounterAssets(map);
}

export function getCounterAssetDisplayName(path: string): string {
  const trimmed = path.trim();
  if (!trimmed) {
    return '';
  }
  const slashIndex = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
  return slashIndex >= 0 ? trimmed.slice(slashIndex + 1) : trimmed;
}

export function resolveCounterAsset(path: string): string {
  const trimmed = path.trim();
  if (!trimmed) {
    return '';
  }
  const displayName = getCounterAssetDisplayName(trimmed);
  return getCounterAsset(displayName) ?? (trimmed.startsWith('/') ? trimmed : `/${trimmed}`);
}
