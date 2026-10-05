import { resolveCounterAsset } from '../counterAssets';
import { t, tf } from '../i18n';
import { saveSettings } from '../settings';
import { appState } from '../state';
import type { WarriorDefinition } from '../types';
import { makeCardWindowDraggable, nextZIndex } from './cardWindows';
import { escapeHtml } from './formatting';

const WARRIOR_COUNTER_CASCADE = 28;
let warriorCounterAvailableIds = new Set<string>();
let warriorCounterOpenIds = new Set<string>();
let warriorCounterCascadeIndex = 0;

export function syncPartySize(): void {
  appState.settings.partyWarriors = appState.settings.partyWarriors.filter((id, index, array) => !!appState.repository.warriors.get(id) && array.indexOf(id) === index);
  if (appState.settings.partyWarriors.length === 0) {
    appState.settings.partyWarriors = ['warrior-barbarian', 'warrior-dwarf', 'warrior-elf', 'warrior-wizard'].filter((id) =>
      appState.repository.warriors.has(id)
    );
  }
  if (appState.settings.partyWarriors.length === 0) {
    appState.settings.partyWarriors = Array.from(appState.repository.warriors.keys()).slice(0, 4);
  }
  appState.settings.partySize = Math.max(1, appState.settings.partyWarriors.length);
}

export function resetWarriorCounterPool(): void {
  warriorCounterAvailableIds = new Set(appState.settings.partyWarriors);
  warriorCounterOpenIds = new Set<string>();
  warriorCounterCascadeIndex = 0;
}

export function warriorDisplayLabel(warrior: WarriorDefinition): string {
  return `${warrior.name} (${warrior.race})`;
}

export function closeAllWarriorCounters(): void {
  document.querySelectorAll<HTMLElement>('#windows .warrior-counter-window').forEach((windowEl) => {
    windowEl.remove();
  });
}

export function openPartyDialog(): void {
  const dialog = document.querySelector<HTMLDialogElement>('#partyDialog');
  if (!dialog) {
    return;
  }
  const available = Array.from(appState.repository.warriors.values()).sort((left, right) =>
    left.name.localeCompare(right.name, undefined, { sensitivity: 'base' })
  );
  const selected = [...appState.settings.partyWarriors];

  const renderPartyDialog = (): void => {
    dialog.innerHTML = `
      <form method="dialog" class="party-dialog">
        <h2>${t(appState.settings.language, 'party.title')}</h2>
        <div class="party-dialog-layout">
          <section>
            <label>${t(appState.settings.language, 'party.available')}
              <select id="partyAvailableSelect">
                ${available
                  .map((warrior) => `<option value="${escapeHtml(warrior.id)}">${escapeHtml(warriorDisplayLabel(warrior))}</option>`)
                  .join('')}
              </select>
            </label>
            <div class="dashboard-inline-actions">
              <button type="button" id="partyAddBtn">+</button>
              <button type="button" id="partyRemoveBtn">-</button>
            </div>
          </section>
          <section>
            <label>${t(appState.settings.language, 'party.selected')}
              <select id="partySelectedList" size="10">
                ${selected
                  .map((warriorId) => {
                    const warrior = appState.repository.warriors.get(warriorId);
                    const label = warrior ? warriorDisplayLabel(warrior) : warriorId;
                    return `<option value="${escapeHtml(warriorId)}">${escapeHtml(label)}</option>`;
                  })
                  .join('')}
              </select>
            </label>
            <p class="party-dialog-size">${tf(appState.settings.language, 'party.size', { count: selected.length })}</p>
          </section>
        </div>
        <menu>
          <button value="cancel">${t(appState.settings.language, 'dialog.button.cancel')}</button>
          <button type="button" id="partySaveBtn">${t(appState.settings.language, 'dialog.button.save')}</button>
        </menu>
      </form>
    `;

    dialog.querySelector<HTMLButtonElement>('#partyAddBtn')?.addEventListener('click', () => {
      const warriorId = dialog.querySelector<HTMLSelectElement>('#partyAvailableSelect')?.value ?? '';
      if (warriorId && !selected.includes(warriorId)) {
        selected.push(warriorId);
        renderPartyDialog();
      }
    });

    dialog.querySelector<HTMLButtonElement>('#partyRemoveBtn')?.addEventListener('click', () => {
      const warriorId = dialog.querySelector<HTMLSelectElement>('#partySelectedList')?.value ?? '';
      const index = selected.indexOf(warriorId);
      if (index >= 0) {
        selected.splice(index, 1);
        renderPartyDialog();
      }
    });

    dialog.querySelector<HTMLButtonElement>('#partySaveBtn')?.addEventListener('click', () => {
      if (selected.length === 0) {
        window.alert(t(appState.settings.language, 'party.selectAtLeastOne'));
        return;
      }
      appState.settings.partyWarriors = [...selected];
      syncPartySize();
      resetWarriorCounterPool();
      saveSettings(appState.settings);
      appState.hooks.buildControls();
      dialog.close();
    });
  };

  renderPartyDialog();
  dialog.showModal();
}

export function openWarriorCounterWindow(warrior: WarriorDefinition): void {
  const container = document.querySelector<HTMLElement>('#windows');
  if (!container) {
    return;
  }
  const src = resolveCounterAsset(warrior.counterPath);
  const windowEl = document.createElement('article');
  windowEl.className = 'card-window warrior-counter-window';
  const width = 200;
  const height = 240;
  const maxCols = Math.max(1, Math.floor((window.innerWidth - 48) / (width + WARRIOR_COUNTER_CASCADE)));
  const col = warriorCounterCascadeIndex % maxCols;
  const row = Math.floor(warriorCounterCascadeIndex / maxCols);
  warriorCounterCascadeIndex += 1;
  windowEl.style.left = `${32 + col * WARRIOR_COUNTER_CASCADE}px`;
  windowEl.style.top = `${32 + row * WARRIOR_COUNTER_CASCADE}px`;
  windowEl.style.width = `${width}px`;
  windowEl.style.height = `${height}px`;
  windowEl.style.zIndex = `${nextZIndex()}`;
  windowEl.innerHTML = `
    <button class="close-window" type="button">x</button>
    <div class="warrior-counter-card">
      <img src="${escapeHtml(src)}" alt="${escapeHtml(warrior.name)}" />
      <div class="warrior-counter-name"><span>${escapeHtml(warrior.name)}</span></div>
    </div>
  `;
  const closeButton = windowEl.querySelector<HTMLButtonElement>('.close-window');
  closeButton?.addEventListener('click', () => windowEl.remove());
  windowEl.addEventListener('mousedown', () => {
    windowEl.style.zIndex = `${nextZIndex()}`;
  });
  const observer = new MutationObserver(() => {
    if (!document.body.contains(windowEl)) {
      warriorCounterAvailableIds.add(warrior.id);
      warriorCounterOpenIds.delete(warrior.id);
      observer.disconnect();
    }
  });
  observer.observe(container, { childList: true });
  makeCardWindowDraggable(windowEl);
  container.appendChild(windowEl);
}

export function drawWarriorCounter(): void {
  const availableIds = [...warriorCounterAvailableIds].filter((id) => appState.settings.partyWarriors.includes(id) && !warriorCounterOpenIds.has(id));
  if (availableIds.length === 0) {
    window.alert(t(appState.settings.language, 'warriorCounter.noneLeft'));
    return;
  }
  const selectedId = availableIds[Math.floor(Math.random() * availableIds.length)] ?? '';
  const warrior = appState.repository.warriors.get(selectedId);
  if (!warrior) {
    return;
  }
  warriorCounterAvailableIds.delete(selectedId);
  warriorCounterOpenIds.add(selectedId);
  openWarriorCounterWindow(warrior);
}
