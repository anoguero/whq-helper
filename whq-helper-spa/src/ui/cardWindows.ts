import { findAnyEvent } from '../content';
import { getMonsterNumber } from '../deck';
import { t } from '../i18n';
import { renderEventCard, renderMonsterCard } from '../render';
import { appState } from '../state';
import type { DrawEntry } from '../types';

let zIndexCounter = 20;

export function nextZIndex(): number {
  return zIndexCounter++;
}

export const CARD_WINDOW_WIDTH = 320;
export const CARD_WINDOW_HEIGHT = 500;
export const CARD_WINDOW_GAP = 14;

export function closeAllOpenCards(): void {
  document.querySelectorAll<HTMLElement>('#windows .card-window:not(.warrior-counter-window)').forEach((windowEl) => {
    windowEl.remove();
  });
}

export function showEntry(entry: DrawEntry): void {
  if (entry.kind === 'group') {
    entry.entries.forEach((nested) => showEntry(nested));
    return;
  }

  const container = document.querySelector<HTMLElement>('#windows');
  if (!container) {
    return;
  }

  const windowEl = document.createElement('article');
  windowEl.className = 'card-window';
  const openWindows = container.querySelectorAll<HTMLElement>('.card-window').length;
  const cols = Math.max(1, Math.floor((window.innerWidth - 40) / (CARD_WINDOW_WIDTH + CARD_WINDOW_GAP)));
  const col = openWindows % cols;
  const row = Math.floor(openWindows / cols);
  const startX = 24;
  const startY = 24;
  windowEl.style.left = `${startX + col * (CARD_WINDOW_WIDTH + CARD_WINDOW_GAP)}px`;
  windowEl.style.top = `${startY + row * (CARD_WINDOW_HEIGHT + CARD_WINDOW_GAP)}px`;
  windowEl.style.width = `${CARD_WINDOW_WIDTH}px`;
  windowEl.style.height = `${CARD_WINDOW_HEIGHT}px`;
  windowEl.style.zIndex = `${nextZIndex()}`;

  const closeButton = document.createElement('button');
  closeButton.className = 'close-window';
  closeButton.type = 'button';
  closeButton.textContent = 'x';
  closeButton.addEventListener('click', () => windowEl.remove());
  windowEl.appendChild(closeButton);

  if (entry.kind === 'event') {
    const event = findAnyEvent(appState.repository, entry.id);
    if (!event) {
      window.alert(`${t(appState.settings.language, 'dialog.card.notFound.event')}: ${entry.id}`);
      return;
    }
    windowEl.insertAdjacentHTML('beforeend', renderEventCard(event, appState.settings.language));
  } else if (entry.kind === 'tableRef') {
    window.alert(`Unresolved table reference: ${entry.tableName}`);
    return;
  } else {
    const monster = appState.repository.monsters.get(entry.id);
    if (!monster) {
      window.alert(`${t(appState.settings.language, 'dialog.card.notFound.monster')}: ${entry.id}`);
      return;
    }

    const number = getMonsterNumber(entry, appState.settings.partySize);
    const title = number > 0 ? `${number} ${number > 1 ? monster.plural : monster.name}` : `* ${monster.name}`;
    windowEl.insertAdjacentHTML(
      'beforeend',
      renderMonsterCard(monster, title, entry, entry.appendSpecials, appState.repository.rules, appState.settings.language)
    );
  }

  windowEl.addEventListener('mousedown', () => {
    windowEl.style.zIndex = `${nextZIndex()}`;
  });

  makeCardWindowDraggable(windowEl);

  windowEl.addEventListener('click', (event) => {
    const target = event.target as HTMLElement;
    if (target.matches('.rule-link')) {
      const id = target.getAttribute('data-rule-id') ?? '';
      const rule = appState.repository.rules.get(id);
      if (rule?.text) {
        window.alert(`${rule.name || id}\n\n${rule.text}`);
      }
    }
  });

  container.appendChild(windowEl);
  requestAnimationFrame(() => fitTreasureHeaderText(windowEl));
}

export function showEntries(entries: DrawEntry[]): void {
  entries.forEach((entry) => showEntry(entry));
}

export function fitTreasureHeaderText(scope: ParentNode): void {
  scope.querySelectorAll<HTMLElement>('.card.treasure .treasure-title').forEach((title) => {
    const minPx = 20;
    let currentPx = Number.parseFloat(getComputedStyle(title).fontSize);

    while ((title.scrollHeight > title.clientHeight || title.scrollWidth > title.clientWidth) && currentPx > minPx) {
      currentPx -= 1;
      title.style.fontSize = `${currentPx}px`;
    }
  });
}

export function makeCardWindowDraggable(windowEl: HTMLElement): void {
  let offsetX = 0;
  let offsetY = 0;

  // Los listeners de movimiento/soltar se registran en window SOLO mientras dura el arrastre y se
  // eliminan al soltar. Antes se anhadian de forma permanente por cada ventana, acumulandose y
  // reteniendo las ventanas cerradas en memoria.
  const onMouseMove = (event: MouseEvent): void => {
    const nextX = Math.max(0, Math.min(window.innerWidth - windowEl.offsetWidth, event.clientX - offsetX));
    const nextY = Math.max(0, Math.min(window.innerHeight - windowEl.offsetHeight, event.clientY - offsetY));
    windowEl.style.left = `${nextX}px`;
    windowEl.style.top = `${nextY}px`;
  };

  const onMouseUp = (): void => {
    window.removeEventListener('mousemove', onMouseMove);
    window.removeEventListener('mouseup', onMouseUp);
  };

  windowEl.addEventListener('mousedown', (event) => {
    const target = event.target as HTMLElement;
    if (target.closest('button, input, select, textarea, a, .rule-link')) {
      return;
    }
    const rect = windowEl.getBoundingClientRect();
    offsetX = event.clientX - rect.left;
    offsetY = event.clientY - rect.top;
    windowEl.style.zIndex = `${nextZIndex()}`;
    window.addEventListener('mousemove', onMouseMove);
    window.addEventListener('mouseup', onMouseUp);
    event.preventDefault();
  });
}
