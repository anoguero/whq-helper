import { t } from '../i18n';
import { renderEventCard } from '../render';
import { appState } from '../state';
import type { EventModel } from '../types';
import { fitTreasureHeaderText } from './cardWindows';
import { escapeHtml } from './formatting';

export function getActiveTreasureEvents(): EventModel[] {
  const treasures = new Map<string, EventModel>();
  for (const table of appState.repository.tables.values()) {
    if (!table.active || table.kind !== 'treasure') {
      continue;
    }
    for (const entry of table.events) {
      const event = appState.repository.events.get(entry.id);
      if (event?.treasure) {
        treasures.set(event.id, event);
      }
    }
  }
  return Array.from(treasures.values()).sort((left, right) => left.name.localeCompare(right.name, undefined, { sensitivity: 'base' }));
}

export function openTreasureSearchDialog(): void {
  const dialog = document.querySelector<HTMLDialogElement>('#treasureSearchDialog');
  if (!dialog) {
    return;
  }

  const treasures = getActiveTreasureEvents();
  dialog.innerHTML = `
    <form method="dialog" class="treasure-search-dialog">
      <h2>${t(appState.settings.language, 'treasureSearch.title')}</h2>
      <label>${t(appState.settings.language, 'treasureSearch.filter')}
        <input id="treasureSearchInput" type="text" autocomplete="off" placeholder="${t(appState.settings.language, 'treasureSearch.placeholder')}">
      </label>
      <div class="treasure-search-layout">
        <section class="treasure-search-results">
          <h3>${t(appState.settings.language, 'treasureSearch.results')}</h3>
          <div id="treasureSearchList" class="table-list"></div>
        </section>
        <section class="treasure-search-preview">
          <h3>${t(appState.settings.language, 'contentDashboard.cardPreview')}</h3>
          <div id="treasureSearchPreview" class="treasure-card-preview"></div>
        </section>
      </div>
      <menu>
        <button value="cancel">${t(appState.settings.language, 'dialog.button.close')}</button>
      </menu>
    </form>
  `;

  const input = dialog.querySelector<HTMLInputElement>('#treasureSearchInput')!;
  const list = dialog.querySelector<HTMLElement>('#treasureSearchList')!;
  const preview = dialog.querySelector<HTMLElement>('#treasureSearchPreview')!;
  let selectedId = treasures[0]?.id ?? '';

  const renderPreview = (): void => {
    const selected = treasures.find((event) => event.id === selectedId) ?? null;
    preview.innerHTML = selected ? renderEventCard(selected, appState.settings.language) : `<p>${t(appState.settings.language, 'treasureSearch.empty')}</p>`;
    fitTreasureHeaderText(preview);
  };

  const refreshList = (): void => {
    const filter = input.value.trim().toLowerCase();
    const filtered = treasures.filter((event) => event.name.toLowerCase().includes(filter));
    if (!filtered.some((event) => event.id === selectedId)) {
      selectedId = filtered[0]?.id ?? '';
    }

    list.innerHTML = filtered.length
      ? filtered
          .map(
            (event) => `
              <button type="button" class="treasure-search-item ${event.id === selectedId ? 'selected' : ''}" data-event-id="${escapeHtml(event.id)}">
                <strong>${escapeHtml(event.name)}</strong>
                <span>${escapeHtml(event.id)}</span>
              </button>
            `
          )
          .join('')
      : `<p>${t(appState.settings.language, 'treasureSearch.noResults')}</p>`;

    list.querySelectorAll<HTMLButtonElement>('[data-event-id]').forEach((button) => {
      button.addEventListener('click', () => {
        selectedId = button.dataset.eventId ?? '';
        refreshList();
        renderPreview();
      });
    });

    renderPreview();
  };

  input.addEventListener('input', refreshList);
  refreshList();
  dialog.showModal();
}
