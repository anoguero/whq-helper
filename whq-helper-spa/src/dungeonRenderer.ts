import { t } from './i18n';
import type { DungeonCard, LanguageCode } from './types';

const CARD_TYPE_ACCENT: Record<DungeonCard['type'], string> = {
  DUNGEON_ROOM: 'rgb(30,102,182)',
  OBJECTIVE_ROOM: 'rgb(239,68,30)',
  CORRIDOR: 'rgb(71,190,122)',
  SPECIAL: 'rgb(187,127,255)'
};

interface Assets {
  template: HTMLImageElement;
  tileCache: Map<string, HTMLImageElement>;
}

const assets: Assets = {
  template: new Image(),
  tileCache: new Map()
};
assets.template.src = '/resources/dungeon-card-template.png';

function loadImage(path: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error(`Cannot load image ${path}`));
    img.src = path;
  });
}

async function getTile(path: string): Promise<HTMLImageElement | null> {
  if (!path.trim()) {
    return null;
  }

  const normalized = path.startsWith('/') ? path : `/${path}`;
  const cached = assets.tileCache.get(normalized);
  if (cached) {
    return cached;
  }

  try {
    const loaded = await loadImage(normalized);
    assets.tileCache.set(normalized, loaded);
    return loaded;
  } catch {
    return null;
  }
}

function wrapText(ctx: CanvasRenderingContext2D, text: string, maxWidth: number): string[] {
  const words = text.split(/\s+/).filter(Boolean);
  if (words.length === 0) {
    return [''];
  }

  const lines: string[] = [];
  let current = words[0] as string;

  for (let i = 1; i < words.length; i += 1) {
    const word = words[i] as string;
    const test = `${current} ${word}`;
    if (ctx.measureText(test).width <= maxWidth) {
      current = test;
    } else {
      lines.push(current);
      current = word;
    }
  }
  lines.push(current);
  return lines;
}

function drawParagraph(
  ctx: CanvasRenderingContext2D,
  text: string,
  x: number,
  y: number,
  width: number,
  lineHeight: number
): number {
  const paragraphs = text.split('\n');
  let currentY = y;
  for (const paragraph of paragraphs) {
    const lines = wrapText(ctx, paragraph, width);
    for (const line of lines) {
      ctx.fillText(line, x, currentY);
      currentY += lineHeight;
    }
    currentY += Math.round(lineHeight * 0.4);
  }
  return currentY;
}

function drawCardTypeFooter(
  ctx: CanvasRenderingContext2D,
  width: number,
  height: number,
  type: DungeonCard['type'],
  language: LanguageCode
): void {
  const bandTop = Math.round(height * 0.90);
  const bandBottom = Math.round(height * 0.94);
  const bandLeft = Math.round(width * 0.14);
  const bandRight = Math.round(width * 0.86);

  ctx.strokeStyle = CARD_TYPE_ACCENT[type];
  ctx.lineWidth = 8;
  ctx.beginPath();
  ctx.moveTo(bandLeft, bandTop);
  ctx.lineTo(bandRight, bandTop);
  ctx.moveTo(bandLeft, bandBottom);
  ctx.lineTo(bandRight, bandBottom);
  ctx.stroke();

  ctx.fillStyle = '#000';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.font = `bold ${Math.round(width * 0.042)}px Copperplate WHQ, Verdana, serif`;
  ctx.fillText(getCardTypeLabel(type, language), (bandLeft + bandRight) / 2, ((bandTop + bandBottom) / 2) - 5);
}

export async function renderDungeonCardToCanvas(
  canvas: HTMLCanvasElement,
  card: DungeonCard,
  language: LanguageCode = 'EN'
): Promise<void> {
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    return;
  }

  const width = canvas.width;
  const height = canvas.height;

  ctx.clearRect(0, 0, width, height);

  if (assets.template.complete && assets.template.naturalWidth > 0) {
    ctx.drawImage(assets.template, 0, 0, width, height);
  } else {
    ctx.fillStyle = '#f4efe0';
    ctx.fillRect(0, 0, width, height);
  }

  const titleBox = {
    x: Math.round(width * 0.09),
    y: Math.round(height * 0.05),
    w: Math.round(width * 0.82),
    h: Math.round(height * 0.085)
  };

  ctx.fillStyle = '#000';
  const radius = Math.max(8, Math.round(width * 0.02));
  if (typeof ctx.roundRect === 'function') {
    ctx.beginPath();
    ctx.roundRect(titleBox.x, titleBox.y, titleBox.w, titleBox.h, radius);
    ctx.fill();
  } else {
    ctx.fillRect(titleBox.x, titleBox.y, titleBox.w, titleBox.h);
  }

  ctx.fillStyle = CARD_TYPE_ACCENT[card.type];
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.font = `bold ${Math.round(width * 0.0705)}px "Casablanca Antique", "Cinzel", "Times New Roman", serif`;
  const titleLines = wrapText(ctx, card.name, titleBox.w - 20);
  const fontSize = titleLines.length == 1 ? Math.round(width * 0.0705) : Math.round(width * 0.0505);
  const titleLineHeight = Math.round(width * 0.0682);
  const titleStartY = titleBox.y + titleBox.h / 2 - ((titleLines.length - 1) * titleLineHeight) / 2;
  ctx.font = `bold ${fontSize}px "Casablanca Antique", "Cinzel", "Times New Roman", serif`;
  titleLines.forEach((line, idx) => {
    ctx.fillText(line, titleBox.x + titleBox.w / 2, titleStartY + idx * titleLineHeight);
  });

  const bodyX = Math.round(width * 0.10);
  const bodyY = Math.round(height * 0.15);
  const bodyW = Math.round(width * 0.80);
  const bodyH = Math.round(height * 0.50);

  ctx.fillStyle = '#000';
  ctx.textAlign = 'left';
  ctx.textBaseline = 'top';

  ctx.font = `italic bold ${Math.round(width * 0.042)}px "Newtext Bk BT", Georgia, serif`;
  let nextY = drawParagraph(ctx, card.descriptionText, bodyX, bodyY, bodyW, Math.round(width * 0.039));

  const maxBodyY = bodyY + bodyH;
  if (nextY < maxBodyY - 8) {
    ctx.font = `${Math.round(width * 0.041)}px "Newtext Bk BT", "Trebuchet MS", serif`;
    drawParagraph(ctx, card.rulesText, bodyX, nextY, bodyW, Math.round(width * 0.038));
  }

  const tileX = Math.round(width * 0.14);
  const tileY = Math.round(height * 0.58);
  const tileW = Math.round(width * 0.72);
  const tileH = Math.round(height * 0.25);

  const tile = await getTile(card.tileImagePath);
  if (tile) {
    const scale = Math.min(tileW / tile.width, tileH / tile.height);
    const drawW = Math.max(1, Math.round(tile.width * scale));
    const drawH = Math.max(1, Math.round(tile.height * scale));
    const drawX = tileX + Math.round((tileW - drawW) / 2);
    const drawY = tileY + Math.round((tileH - drawH) / 2);
    ctx.drawImage(tile, drawX, drawY, drawW, drawH);
  } else {
    // Keep the area transparent when no tile exists.
  }

  drawCardTypeFooter(ctx, width, height, card.type, language);
}

function getCardTypeLabel(type: DungeonCard['type'], language: LanguageCode): string {
  return t(language, `dungeon.cardType.${type}`);
}

export async function renderDungeonCardToCanvasLocalized(
  canvas: HTMLCanvasElement,
  card: DungeonCard,
  language: LanguageCode
): Promise<void> {
  await renderDungeonCardToCanvas(canvas, card, language);
}
