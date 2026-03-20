const STORAGE_KEY = 'whq_helper_spa_tile_assets_v1';

function loadTileAssets(): Record<string, string> {
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

function saveTileAssets(map: Record<string, string>): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(map));
}

export function getTileAsset(name: string): string | null {
  const normalized = name.trim();
  if (!normalized) {
    return null;
  }
  return loadTileAssets()[normalized] ?? null;
}

export function saveTileAsset(name: string, dataUrl: string): void {
  const normalized = name.trim();
  if (!normalized || !dataUrl.trim()) {
    return;
  }
  const map = loadTileAssets();
  map[normalized] = dataUrl;
  saveTileAssets(map);
}

export function getTileAssetDisplayName(path: string): string {
  const trimmed = path.trim();
  if (!trimmed) {
    return '';
  }
  const slashIndex = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
  return slashIndex >= 0 ? trimmed.slice(slashIndex + 1) : trimmed;
}
