import { loadContentTranslations, translateContent } from './contentTranslations';
import type { DungeonCard, LanguageCode } from './types';

export interface WhiteDwarfReference {
  source: string;
  title: Record<LanguageCode, string>;
  text: Record<LanguageCode, string>;
}

// Referencias de reglas de sala de shared/data/xml/dungeon/room-references.xml, indexadas por id de carta.
// El XML lleva el texto base en ingles; los textos por idioma salen de content-*.xml
// (roomReference.<id>.title|text). Se cargan una vez al arrancar para que la consulta sea sincrona.
const ROOM_REFERENCES_PATH = '/data/xml/dungeon/room-references.xml';

let references = new Map<number, WhiteDwarfReference>();
let loading: Promise<void> | null = null;

interface RawReference {
  cardId: number;
  source: string;
  title: string;
  text: string;
}

// trim() solo recorta los extremos: la sangria de las lineas interiores forma parte del texto.
function childText(node: Element, tagName: string): string {
  return Array.from(node.children).find((child) => child.tagName === tagName)?.textContent?.trim() ?? '';
}

function parseRoomReferences(xml: string): RawReference[] {
  const doc = new DOMParser().parseFromString(xml, 'text/xml');
  const root = doc.documentElement;
  if (!root || root.tagName !== 'roomReferences') {
    return [];
  }

  const result: RawReference[] = [];
  for (const node of Array.from(root.children)) {
    if (node.tagName !== 'reference') {
      continue;
    }
    const cardId = Number.parseInt(node.getAttribute('cardId') ?? '', 10);
    if (!Number.isFinite(cardId)) {
      continue;
    }
    result.push({
      cardId,
      source: (node.getAttribute('source') ?? '').trim(),
      title: childText(node, 'title'),
      text: childText(node, 'text')
    });
  }
  return result;
}

export function loadRoomReferences(): Promise<void> {
  if (!loading) {
    loading = (async () => {
      try {
        const [xml, es, en] = await Promise.all([
          fetch(ROOM_REFERENCES_PATH).then((response) => (response.ok ? response.text() : '')),
          loadContentTranslations('ES'),
          loadContentTranslations('EN')
        ]);
        const loaded = new Map<number, WhiteDwarfReference>();
        for (const raw of parseRoomReferences(xml)) {
          const baseKey = `roomReference.${raw.cardId}`;
          loaded.set(raw.cardId, {
            source: raw.source,
            title: {
              EN: translateContent(en, `${baseKey}.title`, raw.title),
              ES: translateContent(es, `${baseKey}.title`, raw.title)
            },
            text: {
              EN: translateContent(en, `${baseKey}.text`, raw.text),
              ES: translateContent(es, `${baseKey}.text`, raw.text)
            }
          });
        }
        references = loaded;
      } catch {
        // Las referencias son informacion de apoyo: si no cargan, simplemente no hay boton de referencia.
        references = new Map();
      }
    })();
  }
  return loading;
}

export function getWhiteDwarfReference(card: DungeonCard | null | undefined): WhiteDwarfReference | null {
  if (!card) {
    return null;
  }
  return references.get(card.id) ?? null;
}
