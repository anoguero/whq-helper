import './styles.css';

import { getAdventureAmbiences, t, tf } from './i18n';
import { findAnyEvent, loadContent, loadContentManifest } from './content';
import { applyTableActiveState, buildDecks, getMonsterNumber } from './deck';
import { loadSettings, saveSettings } from './settings';
import { renderEventCard, renderMonsterCard } from './render';
import { DungeonCardStore, downloadCsv, exportCardsToCsv, importCardsFromCsvFile } from './dungeonStore';
import { renderDungeonCardToCanvasLocalized } from './dungeonRenderer';
import { getXmlOverride, removeXmlOverride, saveXmlOverride } from './contentOverrides';
import type {
  AppSettings,
  ContentRepository,
  DeckBundle,
  DrawEntry,
  DungeonCard,
  GroupEntry,
  LanguageCode,
  MonsterEntry,
  ObjectiveRoomAdventure,
  TableModel
} from './types';

interface DeckMeta {
  key: keyof DeckBundle;
  titleKey: string;
  subtitleKey: string;
  image: string;
  toggleKey: keyof Pick<
    AppSettings,
    'showEventDeck' | 'showSettlementDeck' | 'showTravelDeck' | 'showTreasureDeck' | 'showObjectiveTreasureDeck'
  >;
}

interface AdventureSimulatorState {
  piles: DungeonCard[][];
  histories: DungeonCard[][];
  selectedCard: DungeonCard | null;
  selectedPile: number;
}

type ObjectiveDifficultyId = 'easy' | 'normal' | 'hard' | 'veryHard' | 'extreme';

interface ObjectiveDifficulty {
  id: ObjectiveDifficultyId;
  offsets: number[];
}

const OBJECTIVE_DIFFICULTIES: ObjectiveDifficulty[] = [
  { id: 'easy', offsets: [0, 0] },
  { id: 'normal', offsets: [0, 0, 0] },
  { id: 'hard', offsets: [1, 0, 0] },
  { id: 'veryHard', offsets: [1, 1, 0] },
  { id: 'extreme', offsets: [2, 1, 0] }
];

const DECKS: DeckMeta[] = [
  {
    key: 'dungeon',
    titleKey: 'deck.events.title',
    subtitleKey: 'deck.events.subtitle',
    image: '/data/graphics/eventback.png',
    toggleKey: 'showEventDeck'
  },
  {
    key: 'settlement',
    titleKey: 'deck.settlement.title',
    subtitleKey: 'deck.settlement.subtitle',
    image: '/data/graphics/settlement.png',
    toggleKey: 'showSettlementDeck'
  },
  {
    key: 'travel',
    titleKey: 'deck.travel.title',
    subtitleKey: 'deck.travel.subtitle',
    image: '/data/graphics/travel.png',
    toggleKey: 'showTravelDeck'
  },
  {
    key: 'treasure',
    titleKey: 'deck.treasure.title',
    subtitleKey: 'deck.treasure.subtitle',
    image: '/data/graphics/treasureback.png',
    toggleKey: 'showTreasureDeck'
  },
  {
    key: 'objectiveTreasure',
    titleKey: 'deck.objectiveTreasure.title',
    subtitleKey: 'deck.objectiveTreasure.subtitle',
    image: '/data/graphics/objective-treasureback.png',
    toggleKey: 'showObjectiveTreasureDeck'
  }
];

let repository: ContentRepository;
let settings: AppSettings;
let decks: DeckBundle;
const dungeonStore = new DungeonCardStore();

let dungeonCards: DungeonCard[] = [];
let selectedDungeonCard: DungeonCard | null = null;

let zIndexCounter = 20;
const CARD_WINDOW_WIDTH = 320;
const CARD_WINDOW_HEIGHT = 500;
const CARD_WINDOW_GAP = 14;

function clampProbability(value: number): number {
  return Math.max(0, Math.min(100, value));
}

async function applyLanguageChange(language: LanguageCode): Promise<void> {
  settings.language = language;
  await Promise.all([dungeonStore.setLanguage(language)]);
  repository = await loadContent(language);
  saveSettings(settings);
  render();
}

function cardTypeLabel(type: DungeonCard['type']): string {
  return type.replaceAll('_', ' ');
}

function createAppShell(language: LanguageCode): void {
  const app = document.querySelector<HTMLDivElement>('#app');
  if (!app) {
    throw new Error('Missing #app container');
  }

  app.innerHTML = `
    <div class="page">
      <header class="hero">
        <div class="hero-copy">
          <h1>Warhammer Quest - WHQ Helper</h1>
          <p>${t(language, 'deck.window.subtitle')}</p>
          <div class="hero-actions">
            <button type="button" id="newDungeonBtn">${t(language, 'deck.newDungeon')}</button>
            <button type="button" id="tileConfigBtn">${t(language, 'deck.configureTiles')}</button>
          </div>
        </div>
      </header>

      <section class="controls" id="controls"></section>
      <section class="deck-toggles" id="deckToggles"></section>
      <section class="decks" id="decks"></section>
      <section id="simulatorPanel" class="simulator-panel" hidden></section>
      <section class="windows" id="windows"></section>

      <dialog id="tableDialog" class="table-dialog">
        <form method="dialog">
          <h2>${t(language, 'menu.item.activateTables')}</h2>
          <div class="table-list" id="tableList"></div>
          <menu>
            <button value="cancel">${t(language, 'dialog.button.cancel')}</button>
            <button id="saveTables" value="default">${t(language, 'dialog.button.save')}</button>
          </menu>
        </form>
      </dialog>

      <dialog id="newDungeonDialog" class="table-dialog wide-dialog"></dialog>
      <dialog id="missionDialog" class="table-dialog"></dialog>
      <dialog id="maintenanceDialog" class="table-dialog wide-dialog"></dialog>
    </div>
  `;
}

function wireHeroActions(): void {
  document.querySelector<HTMLButtonElement>('#newDungeonBtn')?.addEventListener('click', () => {
    openNewDungeonDialog();
  });

  document.querySelector<HTMLButtonElement>('#tileConfigBtn')?.addEventListener('click', () => {
    openMaintenanceDialog();
  });
}

function buildControls(): void {
  const controls = document.querySelector<HTMLElement>('#controls');
  if (!controls) {
    return;
  }

  controls.innerHTML = `
    <label>
      ${t(settings.language, 'controls.language')}
      <select id="languageSelect">
        <option value="ES" ${settings.language === 'ES' ? 'selected' : ''}>Español</option>
        <option value="EN" ${settings.language === 'EN' ? 'selected' : ''}>English</option>
      </select>
    </label>

    <label>
      ${t(settings.language, 'controls.ambience')}
      <select id="ambienceSelect">
        ${getAdventureAmbiences(settings.language)
          .map(
          (ambience) =>
            `<option value="${ambience.value}" ${settings.adventureAmbience === ambience.value ? 'selected' : ''}>${ambience.label}</option>`
          )
          .join('')}
      </select>
    </label>

    <label>
      ${t(settings.language, 'controls.partySize')}
      <input id="partySizeInput" type="number" min="1" max="12" value="${settings.partySize}">
    </label>

    <label>
      ${t(settings.language, 'controls.eventProbability')}
      <input id="eventProbabilityInput" type="number" min="0" max="100" value="${settings.eventProbability}">
    </label>

    <label>
      ${t(settings.language, 'controls.goldProbability')}
      <input id="goldProbabilityInput" type="number" min="0" max="100" value="${settings.treasureGoldProbability}">
    </label>

    <fieldset class="mode-field">
      <label>
        <input type="radio" name="mode" value="table" ${settings.simulateDeck ? '' : 'checked'}>
        ${t(settings.language, 'menu.item.simulateTable')}
      </label>
      <label>
        <input type="radio" name="mode" value="deck" ${settings.simulateDeck ? 'checked' : ''}>
        ${t(settings.language, 'menu.item.simulateDeck')}
      </label>
    </fieldset>

    <div class="control-actions">
      <button type="button" id="activateTablesBtn">${t(settings.language, 'menu.item.activateTables')}</button>
      <button type="button" id="closeCardsBtn">${t(settings.language, 'menu.item.closeAllCards')}</button>
    </div>
  `;

  controls.querySelector<HTMLSelectElement>('#languageSelect')?.addEventListener('change', (event) => {
    const value = (event.target as HTMLSelectElement).value === 'EN' ? 'EN' : 'ES';
    applyLanguageChange(value).catch((error) => window.alert(String(error)));
  });

  controls.querySelector<HTMLSelectElement>('#ambienceSelect')?.addEventListener('change', (event) => {
    settings.adventureAmbience = (event.target as HTMLSelectElement).value;
    rebuildDecks();
  });

  controls.querySelector<HTMLInputElement>('#partySizeInput')?.addEventListener('change', (event) => {
    const value = Number.parseInt((event.target as HTMLInputElement).value, 10);
    settings.partySize = Math.max(1, Number.isFinite(value) ? value : settings.partySize);
    saveSettings(settings);
  });

  controls.querySelector<HTMLInputElement>('#eventProbabilityInput')?.addEventListener('change', (event) => {
    const value = Number.parseInt((event.target as HTMLInputElement).value, 10);
    settings.eventProbability = clampProbability(Number.isFinite(value) ? value : settings.eventProbability);
    rebuildDecks();
  });

  controls.querySelector<HTMLInputElement>('#goldProbabilityInput')?.addEventListener('change', (event) => {
    const value = Number.parseInt((event.target as HTMLInputElement).value, 10);
    settings.treasureGoldProbability = clampProbability(Number.isFinite(value) ? value : settings.treasureGoldProbability);
    rebuildDecks();
  });

  controls.querySelectorAll<HTMLInputElement>('input[name="mode"]').forEach((radio) => {
    radio.addEventListener('change', () => {
      settings.simulateDeck = radio.value === 'deck';
      rebuildDecks();
    });
  });

  controls.querySelector<HTMLButtonElement>('#closeCardsBtn')?.addEventListener('click', () => {
    document.querySelector<HTMLElement>('#windows')!.innerHTML = '';
  });

  controls.querySelector<HTMLButtonElement>('#activateTablesBtn')?.addEventListener('click', () => {
    openTableDialog();
  });
}

function buildDeckToggles(): void {
  const container = document.querySelector<HTMLElement>('#deckToggles');
  if (!container) {
    return;
  }

  container.innerHTML = DECKS.map((deck) => {
    const checked = settings[deck.toggleKey] ? 'checked' : '';
    const labelKey =
      deck.toggleKey === 'showEventDeck'
        ? 'toggle.showEventDeck'
        : deck.toggleKey === 'showSettlementDeck'
        ? 'toggle.showSettlementDeck'
        : deck.toggleKey === 'showTravelDeck'
        ? 'toggle.showTravelDeck'
        : deck.toggleKey === 'showTreasureDeck'
        ? 'toggle.showTreasureDeck'
        : 'toggle.showObjectiveTreasureDeck';

    return `
      <label>
        <input type="checkbox" data-toggle="${deck.toggleKey}" ${checked}>
        ${t(settings.language, labelKey)}
      </label>
    `;
  }).join('');

  container.querySelectorAll<HTMLInputElement>('input[type="checkbox"]').forEach((checkbox) => {
    checkbox.addEventListener('change', () => {
      const key = checkbox.dataset.toggle as DeckMeta['toggleKey'];
      settings[key] = checkbox.checked;
      saveSettings(settings);
      renderDecks();
    });
  });
}

function deckVisible(meta: DeckMeta): boolean {
  return settings[meta.toggleKey];
}

function renderDecks(): void {
  const section = document.querySelector<HTMLElement>('#decks');
  if (!section) {
    return;
  }

  section.innerHTML = DECKS.filter(deckVisible)
    .map((deck) => {
      const list = decks[deck.key];
      return `
        <article class="deck" data-deck="${deck.key}">
          <h3>${t(settings.language, deck.titleKey)}</h3>
          <p>${t(settings.language, deck.subtitleKey)}</p>
          <button class="deck-button" data-draw="${deck.key}">
            <img src="${deck.image}" alt="${t(settings.language, deck.titleKey)}" />
            <span>${t(settings.language, 'button.clickHere')}</span>
          </button>
          <small>${list.size()} ${t(settings.language, 'controls.entries')}</small>
        </article>
      `;
    })
    .join('');

  section.querySelectorAll<HTMLButtonElement>('[data-draw]').forEach((button) => {
    button.addEventListener('click', () => {
      const key = button.dataset.draw as keyof DeckBundle;
      drawFromDeck(key);
    });
  });
}

function drawFromDeck(deckKey: keyof DeckBundle): void {
  const list = decks[deckKey];
  if (list.size() < 1) {
    const wantsActivate = window.confirm(
      `${t(settings.language, 'dialog.deck.emptyTitle')}\n\n${t(settings.language, 'dialog.deck.emptyMessage')}`
    );
    if (wantsActivate) {
      openTableDialog();
    }
    return;
  }

  const entry = list.draw();
  if (!entry) {
    return;
  }

  showEntry(entry);
}

function showEntry(entry: DrawEntry): void {
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
  windowEl.style.zIndex = `${zIndexCounter++}`;

  const closeButton = document.createElement('button');
  closeButton.className = 'close-window';
  closeButton.type = 'button';
  closeButton.textContent = 'x';
  closeButton.addEventListener('click', () => windowEl.remove());
  windowEl.appendChild(closeButton);

  if (entry.kind === 'event') {
    const event = findAnyEvent(repository, entry.id);
    if (!event) {
      window.alert(`${t(settings.language, 'dialog.card.notFound.event')}: ${entry.id}`);
      return;
    }
    windowEl.insertAdjacentHTML('beforeend', renderEventCard(event, settings.language));
  } else {
    const monster = repository.monsters.get(entry.id);
    if (!monster) {
      window.alert(`${t(settings.language, 'dialog.card.notFound.monster')}: ${entry.id}`);
      return;
    }

    const number = getMonsterNumber(entry, settings.partySize);
    const title = number > 0 ? `${number} ${number > 1 ? monster.plural : monster.name}` : `* ${monster.name}`;
    windowEl.insertAdjacentHTML(
      'beforeend',
      renderMonsterCard(monster, title, entry, entry.appendSpecials, repository.rules, settings.language)
    );
  }

  windowEl.addEventListener('mousedown', () => {
    windowEl.style.zIndex = `${zIndexCounter++}`;
  });

  makeCardWindowDraggable(windowEl);

  windowEl.addEventListener('click', (event) => {
    const target = event.target as HTMLElement;
    if (target.matches('.rule-link')) {
      const id = target.getAttribute('data-rule-id') ?? '';
      const rule = repository.rules.get(id);
      if (rule?.text) {
        window.alert(`${rule.name || id}\n\n${rule.text}`);
      }
    }
  });

  container.appendChild(windowEl);
  requestAnimationFrame(() => fitTreasureHeaderText(windowEl));
}

function showEntries(entries: DrawEntry[]): void {
  entries.forEach((entry) => showEntry(entry));
}

function fitTreasureHeaderText(scope: ParentNode): void {
  scope.querySelectorAll<HTMLElement>('.card.treasure .treasure-title').forEach((title) => {
    const minPx = 20;
    let currentPx = Number.parseFloat(getComputedStyle(title).fontSize);

    while ((title.scrollHeight > title.clientHeight || title.scrollWidth > title.clientWidth) && currentPx > minPx) {
      currentPx -= 1;
      title.style.fontSize = `${currentPx}px`;
    }
  });
}

function makeCardWindowDraggable(windowEl: HTMLElement): void {
  let dragging = false;
  let offsetX = 0;
  let offsetY = 0;

  windowEl.addEventListener('mousedown', (event) => {
    const target = event.target as HTMLElement;
    if (target.closest('button, input, select, textarea, a, .rule-link')) {
      return;
    }
    dragging = true;
    const rect = windowEl.getBoundingClientRect();
    offsetX = event.clientX - rect.left;
    offsetY = event.clientY - rect.top;
    windowEl.style.zIndex = `${zIndexCounter++}`;
    event.preventDefault();
  });

  window.addEventListener('mousemove', (event) => {
    if (!dragging) {
      return;
    }
    const nextX = Math.max(0, Math.min(window.innerWidth - windowEl.offsetWidth, event.clientX - offsetX));
    const nextY = Math.max(0, Math.min(window.innerHeight - windowEl.offsetHeight, event.clientY - offsetY));
    windowEl.style.left = `${nextX}px`;
    windowEl.style.top = `${nextY}px`;
  });

  window.addEventListener('mouseup', () => {
    dragging = false;
  });
}

function openTableDialog(): void {
  const dialog = document.querySelector<HTMLDialogElement>('#tableDialog');
  const list = document.querySelector<HTMLElement>('#tableList');
  if (!dialog || !list) {
    return;
  }

  type TableTheme = 'events' | 'monsters' | 'settlement' | 'travel' | 'treasure' | 'objectiveTreasure';
  const themeOrder: TableTheme[] = ['events', 'monsters', 'settlement', 'travel', 'treasure', 'objectiveTreasure'];
  const themeLabelKey: Record<TableTheme, string> = {
    events: 'dialog.tableSettings.group.events',
    monsters: 'dialog.tableSettings.group.monsters',
    settlement: 'dialog.tableSettings.group.settlement',
    travel: 'dialog.tableSettings.group.travel',
    treasure: 'dialog.tableSettings.group.treasure',
    objectiveTreasure: 'dialog.tableSettings.group.objectiveTreasure'
  };

  const tableTheme = (table: TableModel): TableTheme => {
    const lowerName = table.name.toLowerCase();
    if (table.kind === 'settlement') {
      return 'settlement';
    }
    if (table.kind === 'travel') {
      return 'travel';
    }
    if (table.kind === 'treasure') {
      return lowerName.includes('objective') || lowerName.includes('objetive') ? 'objectiveTreasure' : 'treasure';
    }
    if (table.monsters.length > 0) {
      return 'monsters';
    }
    return 'events';
  };

  const tables = Array.from(repository.tables.values()).sort((a, b) => {
    const themeDiff = themeOrder.indexOf(tableTheme(a)) - themeOrder.indexOf(tableTheme(b));
    if (themeDiff !== 0) {
      return themeDiff;
    }
    return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });
  });

  const groupedTables = new Map<TableTheme, TableModel[]>();
  for (const theme of themeOrder) {
    groupedTables.set(theme, []);
  }
  for (const table of tables) {
    groupedTables.get(tableTheme(table))?.push(table);
  }

  list.innerHTML = themeOrder
    .map((theme) => {
      const items = groupedTables.get(theme) ?? [];
      if (items.length === 0) {
        return '';
      }
      return `
        <section class="table-section">
          <h3>${t(settings.language, themeLabelKey[theme])}</h3>
          ${items
            .map(
              (table) => `
                <label>
                  <input type="checkbox" data-table-name="${table.name}" ${table.active ? 'checked' : ''}>
                  ${table.name}
                </label>
              `
            )
            .join('')}
        </section>
      `;
    })
    .join('');

  const saveButton = dialog.querySelector<HTMLButtonElement>('#saveTables');
  const handleSave = (event: Event) => {
    event.preventDefault();
    list.querySelectorAll<HTMLInputElement>('input[type="checkbox"]').forEach((checkbox) => {
      const tableName = checkbox.dataset.tableName;
      if (!tableName) {
        return;
      }
      const table = repository.tables.get(tableName);
      if (!table) {
        return;
      }
      table.active = checkbox.checked;
      settings.tableActive[table.name] = checkbox.checked;
    });

    rebuildDecks();
    dialog.close();
  };

  saveButton?.addEventListener('click', handleSave, { once: true });
  dialog.showModal();
}

function refreshDungeonCards(): void {
  dungeonCards = dungeonStore.loadCards();
  if (!selectedDungeonCard || !dungeonCards.some((card) => card.id === selectedDungeonCard?.id)) {
    selectedDungeonCard = dungeonCards[0] ?? null;
  }
}

function openMaintenanceDialog(): void {
  const dialog = document.querySelector<HTMLDialogElement>('#maintenanceDialog');
  if (!dialog) {
    return;
  }

  const cards = dungeonStore
    .loadCards()
    .slice()
    .sort((left, right) => {
      const env = left.environment.localeCompare(right.environment);
      if (env !== 0) {
        return env;
      }
      const type = left.type.localeCompare(right.type);
      if (type !== 0) {
        return type;
      }
      return left.name.localeCompare(right.name);
    });

  dialog.innerHTML = `
    <form method="dialog" class="maintenance-grid">
      <h2>${t(settings.language, 'dialog.tileConfig.title')}</h2>
      <p>${t(settings.language, 'dialog.tileConfig.description')}</p>
      <div class="tile-config-toolbar">
        <label>
          ${t(settings.language, 'dialog.tileConfig.environment')}
          <select id="tileEnvSelect"></select>
        </label>
        <button type="button" id="tileEnableEnvBtn">${t(settings.language, 'dialog.tileConfig.enableEnvironment')}</button>
        <button type="button" id="tileDisableEnvBtn">${t(settings.language, 'dialog.tileConfig.disableEnvironment')}</button>
      </div>
      <section id="maintenanceList" class="table-list"></section>
      <menu>
        <button value="cancel">${t(settings.language, 'dialog.button.cancel')}</button>
        <button type="button" id="mSave">${t(settings.language, 'dialog.button.save')}</button>
      </menu>
    </form>
  `;

  const list = dialog.querySelector<HTMLElement>('#maintenanceList');
  const saveButton = dialog.querySelector<HTMLButtonElement>('#mSave');
  const environmentSelect = dialog.querySelector<HTMLSelectElement>('#tileEnvSelect');
  const enableEnvironmentButton = dialog.querySelector<HTMLButtonElement>('#tileEnableEnvBtn');
  const disableEnvironmentButton = dialog.querySelector<HTMLButtonElement>('#tileDisableEnvBtn');
  if (!list || !saveButton || !environmentSelect || !enableEnvironmentButton || !disableEnvironmentButton) {
    return;
  }

  if (cards.length === 0) {
    list.textContent = t(settings.language, 'dialog.tileConfig.empty');
    saveButton.disabled = true;
    environmentSelect.disabled = true;
    enableEnvironmentButton.disabled = true;
    disableEnvironmentButton.disabled = true;
    dialog.showModal();
    return;
  }

  const enabledById = new Map<number, boolean>(cards.map((card) => [card.id, card.enabled]));
  const checkboxById = new Map<number, HTMLInputElement>();
  const environments = [...new Set(cards.map((card) => card.environment))].sort((a, b) =>
    a.localeCompare(b, undefined, { sensitivity: 'base' })
  );
  environmentSelect.innerHTML = environments.map((environment) => `<option value="${environment}">${environment}</option>`).join('');

  for (const card of cards) {
    const row = document.createElement('label');
    row.className = 'tile-toggle';

    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.checked = enabledById.get(card.id) ?? false;
    checkbox.addEventListener('change', () => {
      enabledById.set(card.id, checkbox.checked);
    });
    checkboxById.set(card.id, checkbox);

    const text = document.createElement('span');
    text.textContent = tf(settings.language, 'dialog.tileConfig.tileEntry', {
      id: card.id,
      name: card.name,
      type: t(settings.language, `dungeon.cardType.${card.type}`),
      environment: card.environment
    });

    row.append(checkbox, text);
    list.append(row);
  }

  const applyEnvironmentValue = (enabled: boolean) => {
    const selectedEnvironment = environmentSelect.value.trim();
    if (!selectedEnvironment) {
      return;
    }

    for (const card of cards) {
      if (card.environment.localeCompare(selectedEnvironment, undefined, { sensitivity: 'base' }) !== 0) {
        continue;
      }
      enabledById.set(card.id, enabled);
      const checkbox = checkboxById.get(card.id);
      if (checkbox) {
        checkbox.checked = enabled;
      }
    }
  };

  enableEnvironmentButton.addEventListener('click', () => applyEnvironmentValue(true));
  disableEnvironmentButton.addEventListener('click', () => applyEnvironmentValue(false));

  saveButton.addEventListener('click', () => {
    let changed = false;
    for (const card of cards) {
      const enabled = enabledById.get(card.id) ?? false;
      if (enabled === card.enabled) {
        continue;
      }
      changed = true;
      dungeonStore.updateCard({
        ...card,
        enabled
      });
    }

    if (changed) {
      refreshDungeonCards();
      rebuildDecks();
    }
    dialog.close();
  });

  dialog.showModal();
}

async function openContentEditorDialog(): Promise<void> {
  const dialog = document.querySelector<HTMLDialogElement>('#contentEditorDialog');
  if (!dialog) {
    return;
  }

  const manifest = await loadContentManifest();
  const files = manifest.xmlFiles;
  const fileOptions = files.map((path) => `<option value="${path}">${path}</option>`).join('');

  dialog.innerHTML = `
    <form method="dialog" class="maintenance-grid">
      <h2>${t(settings.language, 'dialog.tableEditor.title')}</h2>
      <p>${t(settings.language, 'dialog.tableEditor.description')}</p>
      <label>${t(settings.language, 'dialog.tableEditor.file')}
        <select id="ceFile">${fileOptions}</select>
      </label>
      <textarea id="ceText" rows="24"></textarea>
      <menu>
        <button value="cancel">${t(settings.language, 'dialog.button.close')}</button>
        <button type="button" id="ceReset">${t(settings.language, 'dialog.button.reload')}</button>
        <button type="button" id="ceSave">${t(settings.language, 'dialog.button.saveReload')}</button>
      </menu>
    </form>
  `;

  const fileSelect = dialog.querySelector<HTMLSelectElement>('#ceFile')!;
  const textArea = dialog.querySelector<HTMLTextAreaElement>('#ceText')!;

  const loadFile = async (path: string) => {
    const override = getXmlOverride(path);
    if (override != null) {
      return override;
    }
    const response = await fetch(path);
    if (!response.ok) {
      throw new Error(`${t(settings.language, 'dialog.tableEditor.readError')} ${path}`);
    }
    return response.text();
  };

  const refreshText = async () => {
    textArea.value = await loadFile(fileSelect.value);
  };

  fileSelect.addEventListener('change', () => {
    refreshText().catch((error) => {
      window.alert(String(error));
    });
  });

  dialog.querySelector<HTMLButtonElement>('#ceReset')?.addEventListener('click', async () => {
    removeXmlOverride(fileSelect.value);
    await refreshText();
    repository = await loadContent(settings.language);
    rebuildDecks();
  });

  dialog.querySelector<HTMLButtonElement>('#ceSave')?.addEventListener('click', async () => {
    const parser = new DOMParser();
    const parsed = parser.parseFromString(textArea.value, 'text/xml');
    if (parsed.querySelector('parsererror')) {
      window.alert(t(settings.language, 'dialog.tableEditor.invalidXml'));
      return;
    }
    saveXmlOverride(fileSelect.value, textArea.value);
    repository = await loadContent(settings.language);
    rebuildDecks();
    window.alert(t(settings.language, 'dialog.tableEditor.saved'));
  });

  await refreshText();
  dialog.showModal();
}

function pickCardsByCopies(pool: DungeonCard[], count: number): DungeonCard[] {
  if (count <= 0) {
    return [];
  }

  const expanded: DungeonCard[] = [];
  for (const card of pool) {
    for (let i = 0; i < card.copyCount; i += 1) {
      expanded.push(card);
    }
  }

  for (let i = expanded.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [expanded[i], expanded[j]] = [expanded[j] as DungeonCard, expanded[i] as DungeonCard];
  }

  return expanded.slice(0, count);
}

function shuffleCards(cards: DungeonCard[]): void {
  for (let i = cards.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [cards[i], cards[j]] = [cards[j] as DungeonCard, cards[i] as DungeonCard];
  }
}

function collectAdventureCardIds(state: AdventureSimulatorState): Set<number> {
  const ids = new Set<number>();
  for (const pile of state.piles) {
    for (const card of pile) {
      ids.add(card.id);
    }
  }
  for (const history of state.histories) {
    for (const card of history) {
      ids.add(card.id);
    }
  }
  if (state.selectedCard) {
    ids.add(state.selectedCard.id);
  }
  return ids;
}

function pickAdditionalAdventureCards(environment: string, cards: DungeonCard[], existingIds: Set<number>): DungeonCard[] {
  const eligible = cards
    .filter((card) => card.environment.localeCompare(environment, undefined, { sensitivity: 'base' }) === 0)
    .filter((card) => card.enabled && card.copyCount > 0)
    .filter((card) => card.type !== 'OBJECTIVE_ROOM')
    .filter((card) => !existingIds.has(card.id));

  shuffleCards(eligible);
  return eligible;
}

function matchesMonsterAmbience(entry: MonsterEntry | GroupEntry, selectedAmbience: string): boolean {
  if (selectedAmbience === 'generic') {
    return true;
  }
  if (entry.kind === 'group') {
    return entry.entries.every((groupMember) => {
      if (groupMember.ambiences.length === 0) {
        return true;
      }
      return groupMember.ambiences.some(
        (ambience) => ambience.localeCompare(selectedAmbience, undefined, { sensitivity: 'base' }) === 0
      );
    });
  }
  if (entry.ambiences.length === 0) {
    return true;
  }
  return entry.ambiences.some(
    (ambience) => ambience.localeCompare(selectedAmbience, undefined, { sensitivity: 'base' }) === 0
  );
}

function activeDungeonMonsterEntries(
  repositoryToUse: ContentRepository,
  selectedAmbience: string
): Array<MonsterEntry | GroupEntry> {
  const activeEntries = Array.from(repositoryToUse.tables.values())
    .filter((table) => table.active && table.kind === 'dungeon')
    .flatMap((table) => table.monsters);

  if (selectedAmbience === 'generic') {
    return activeEntries;
  }

  const ambienceFiltered = activeEntries.filter((entry) => matchesMonsterAmbience(entry, selectedAmbience));
  return ambienceFiltered.length > 0 ? ambienceFiltered : activeEntries;
}

function availableObjectiveMonsterLevels(repositoryToUse: ContentRepository, selectedAmbience: string): number[] {
  return [...new Set(activeDungeonMonsterEntries(repositoryToUse, selectedAmbience).map((entry) => entry.level))].sort(
    (a, b) => a - b
  );
}

function pickRandomMonsterEncounter(
  repositoryToUse: ContentRepository,
  selectedAmbience: string,
  level: number
): DrawEntry | null {
  const entries = activeDungeonMonsterEntries(repositoryToUse, selectedAmbience).filter((entry) => entry.level === level);
  if (entries.length === 0) {
    return null;
  }
  return entries[Math.floor(Math.random() * entries.length)] ?? null;
}

function resolveObjectiveEncounterLevels(
  repositoryToUse: ContentRepository,
  selectedAmbience: string,
  requestedLevel: number
): number[] {
  const availableLevels = availableObjectiveMonsterLevels(repositoryToUse, selectedAmbience);
  if (availableLevels.length === 0) {
    return [];
  }
  if (availableLevels.includes(requestedLevel)) {
    return [requestedLevel];
  }

  const previousAvailable = [...availableLevels].reverse().find((level) => level <= requestedLevel);
  if (previousAvailable !== undefined) {
    return [previousAvailable, previousAvailable];
  }

  const fallbackLevel = availableLevels[0] as number;
  return [fallbackLevel, fallbackLevel];
}

function objectiveDifficultyWeight(settingsToUse: AppSettings, id: ObjectiveDifficultyId): number {
  if (id === 'easy') {
    return settingsToUse.objectiveMonsterEasyWeight;
  }
  if (id === 'normal') {
    return settingsToUse.objectiveMonsterNormalWeight;
  }
  if (id === 'hard') {
    return settingsToUse.objectiveMonsterHardWeight;
  }
  if (id === 'veryHard') {
    return settingsToUse.objectiveMonsterVeryHardWeight;
  }
  return settingsToUse.objectiveMonsterExtremeWeight;
}

function rollObjectiveDifficulty(settingsToUse: AppSettings): ObjectiveDifficulty | null {
  const totalWeight = OBJECTIVE_DIFFICULTIES.reduce(
    (sum, difficulty) => sum + Math.max(0, objectiveDifficultyWeight(settingsToUse, difficulty.id)),
    0
  );
  if (totalWeight <= 0) {
    return null;
  }

  let roll = Math.floor(Math.random() * totalWeight);
  for (const difficulty of OBJECTIVE_DIFFICULTIES) {
    roll -= Math.max(0, objectiveDifficultyWeight(settingsToUse, difficulty.id));
    if (roll < 0) {
      return difficulty;
    }
  }
  return OBJECTIVE_DIFFICULTIES[OBJECTIVE_DIFFICULTIES.length - 1] as ObjectiveDifficulty;
}

function objectiveDifficultyLabel(language: LanguageCode, id: ObjectiveDifficultyId): string {
  if (id === 'easy') {
    return t(language, 'newDungeon.objectiveMonsterEasy');
  }
  if (id === 'normal') {
    return t(language, 'newDungeon.objectiveMonsterNormal');
  }
  if (id === 'hard') {
    return t(language, 'newDungeon.objectiveMonsterHard');
  }
  if (id === 'veryHard') {
    return t(language, 'newDungeon.objectiveMonsterVeryHard');
  }
  return t(language, 'newDungeon.objectiveMonsterExtreme');
}

function generateObjectiveRoomMonsterEntries(
  repositoryToUse: ContentRepository,
  settingsToUse: AppSettings,
  dungeonLevel: number
): { difficulty: ObjectiveDifficulty; entries: DrawEntry[] } | null {
  const difficulty = rollObjectiveDifficulty(settingsToUse);
  if (!difficulty) {
    return null;
  }

  const entries: DrawEntry[] = [];
  for (const offset of difficulty.offsets) {
    const resolvedLevels = resolveObjectiveEncounterLevels(
      repositoryToUse,
      settingsToUse.adventureAmbience,
      dungeonLevel + offset
    );
    for (const resolvedLevel of resolvedLevels) {
      const entry = pickRandomMonsterEncounter(repositoryToUse, settingsToUse.adventureAmbience, resolvedLevel);
      if (entry) {
        entries.push(entry);
      }
    }
  }

  return { difficulty, entries };
}

function buildAdventureDeck(
  environment: string,
  objectiveRoom: DungeonCard,
  deckSize: number,
  roomCount: number,
  cards: DungeonCard[]
): DungeonCard[] {
  if (deckSize < 2) {
    throw new Error('El mazo debe tener al menos 2 cartas.');
  }
  if (roomCount < 1 || roomCount >= deckSize) {
    throw new Error('El número de habitaciones debe ser menor que el tamaño del mazo.');
  }

  const environmentCards = cards
    .filter((card) => card.environment.toLowerCase() === environment.toLowerCase())
    .filter((card) => card.enabled && card.copyCount > 0);

  const dungeonPool = environmentCards.filter((card) => card.type === 'DUNGEON_ROOM');
  const fillerPool = environmentCards.filter((card) => card.type === 'CORRIDOR' || card.type === 'SPECIAL');

  const availableDungeon = dungeonPool.reduce((sum, card) => sum + card.copyCount, 0);
  if (roomCount > availableDungeon) {
    throw new Error('No hay suficientes habitaciones de tipo DUNGEON ROOM para ese tamaño.');
  }

  const fillerCount = deckSize - roomCount - 1;
  const availableFiller = fillerPool.reduce((sum, card) => sum + card.copyCount, 0);
  if (fillerCount > availableFiller) {
    throw new Error('No hay suficientes cartas de pasillo/especial para completar el mazo.');
  }

  const dungeonRooms = pickCardsByCopies(dungeonPool, roomCount);
  const fillers = pickCardsByCopies(fillerPool, fillerCount);
  const nonObjective = [...dungeonRooms, ...fillers];

  for (let i = nonObjective.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [nonObjective[i], nonObjective[j]] = [nonObjective[j] as DungeonCard, nonObjective[i] as DungeonCard];
  }

  const deck: Array<DungeonCard | null> = new Array(deckSize).fill(null);
  const minObjectiveIndex = Math.max(0, deckSize - 5);
  const objectiveIndex = minObjectiveIndex + Math.floor(Math.random() * (deckSize - minObjectiveIndex));
  deck[objectiveIndex] = objectiveRoom;

  let idx = 0;
  for (let i = 0; i < deck.length; i += 1) {
    if (!deck[i]) {
      deck[i] = nonObjective[idx] as DungeonCard;
      idx += 1;
    }
  }

  return deck as DungeonCard[];
}

function openMissionDialog(adventure: ObjectiveRoomAdventure): void {
  const dialog = document.querySelector<HTMLDialogElement>('#missionDialog');
  if (!dialog) {
    return;
  }

  dialog.innerHTML = `
    <form method="dialog">
      <h2>${t(settings.language, 'dialog.mission.title')}: ${adventure.name}</h2>
      <p><strong>${t(settings.language, 'dialog.mission.ambience')}</strong></p>
      <p>${adventure.flavorText}</p>
      <p><strong>${t(settings.language, 'dialog.mission.specialRules')}</strong></p>
      <p>${adventure.rulesText}</p>
      <menu><button value="cancel">${t(settings.language, 'dialog.button.close')}</button></menu>
    </form>
  `;

  dialog.showModal();
}

function splitSelectedPile(piles: DungeonCard[][], selectedIndex: number, pileCount: number): DungeonCard[][] {
  const result: DungeonCard[][] = [];
  for (let i = 0; i < piles.length; i += 1) {
    if (i !== selectedIndex) {
      result.push([...piles[i]!]);
      continue;
    }

    const source = piles[i] as DungeonCard[];
    const split = Array.from({ length: pileCount }, () => [] as DungeonCard[]);
    source.forEach((card, idx) => {
      split[idx % pileCount]!.push(card);
    });
    result.push(...split);
  }
  return result;
}

function splitSelectedPileHistories(histories: DungeonCard[][], selectedIndex: number, pileCount: number): DungeonCard[][] {
  const result: DungeonCard[][] = [];
  for (let i = 0; i < histories.length; i += 1) {
    if (i !== selectedIndex) {
      result.push([...histories[i]!]);
      continue;
    }

    for (let j = 0; j < pileCount; j += 1) {
      result.push([]);
    }
    const source = histories[i] as DungeonCard[];
    if (source.length > 0) {
      result[result.length - pileCount] = [...source];
    }
  }
  return result;
}

function openAdventureSimulator(
  deck: DungeonCard[],
  adventure: ObjectiveRoomAdventure,
  ambienceLabel: string,
  dungeonLevel: number,
  environment: string
): void {
  const panel = document.querySelector<HTMLElement>('#simulatorPanel');
  if (!panel) {
    return;
  }

  const state: AdventureSimulatorState = {
    piles: [deck],
    histories: [[]],
    selectedCard: null,
    selectedPile: -1
  };

  panel.hidden = false;
  panel.classList.add('active');
  document.body.classList.add('simulator-active');

  panel.innerHTML = `
    <section class="simulator-layout">
      <header>
        <h2>${t(settings.language, 'simulator.title')}</h2>
        <p>${t(settings.language, 'dialog.mission.title')}: ${adventure.name} | ${t(settings.language, 'dialog.mission.ambience')}: ${ambienceLabel} | ${t(settings.language, 'simulator.level')}: ${dungeonLevel}</p>
      </header>
      <section class="simulator-body">
        <div>
          <h3>${t(settings.language, 'simulator.piles')}</h3>
          <p>${t(settings.language, 'simulator.pilesHint')}</p>
          <p id="simStatus"></p>
          <div id="pilesContainer" class="piles-grid"></div>
        </div>
        <div>
          <h3>${t(settings.language, 'simulator.revealedCard')}</h3>
          <p id="revealedStatus">${t(settings.language, 'simulator.revealedHint')}</p>
          <canvas id="simRevealCanvas" width="847" height="1264"></canvas>
        </div>
      </section>
      <menu>
        ${adventure.generic ? '' : `<button type="button" id="showMissionBtn">${t(settings.language, 'simulator.missionButton')}</button>`}
        <button type="button" id="objectiveRoomMonstersBtn">${t(settings.language, 'button.generateObjectiveRoomMonsters')}</button>
        <button type="button" id="closeSimulatorBtn">${t(settings.language, 'button.finishAdventure')}</button>
      </menu>
    </section>
  `;

  panel.querySelector<HTMLButtonElement>('#showMissionBtn')?.addEventListener('click', () => {
    openMissionDialog(adventure);
  });

  panel.querySelector<HTMLButtonElement>('#objectiveRoomMonstersBtn')?.addEventListener('click', () => {
    const result = generateObjectiveRoomMonsterEntries(repository, settings, dungeonLevel);
    if (!result) {
      window.alert(t(settings.language, 'simulator.objectiveMonstersInvalidWeights'));
      return;
    }
    if (result.entries.length === 0) {
      window.alert(t(settings.language, 'simulator.objectiveMonstersNoEntries'));
      return;
    }

    window.alert(
      tf(settings.language, 'simulator.objectiveMonstersDifficulty', {
        difficulty: objectiveDifficultyLabel(settings.language, result.difficulty.id)
      })
    );
    showEntries(result.entries);
  });

  panel.querySelector<HTMLButtonElement>('#closeSimulatorBtn')?.addEventListener('click', () => {
    settings.dungeonActive = false;
    saveSettings(settings);
    rebuildDecks();
    panel.hidden = true;
    panel.classList.remove('active');
    panel.innerHTML = '';
    document.body.classList.remove('simulator-active');
  });

  const status = panel.querySelector<HTMLElement>('#simStatus')!;
  const revealedStatus = panel.querySelector<HTMLElement>('#revealedStatus')!;
  const pilesContainer = panel.querySelector<HTMLElement>('#pilesContainer')!;
  const revealCanvas = panel.querySelector<HTMLCanvasElement>('#simRevealCanvas')!;

  const refreshReveal = () => {
    if (!state.selectedCard) {
      const ctx = revealCanvas.getContext('2d');
      if (ctx) {
        ctx.clearRect(0, 0, revealCanvas.width, revealCanvas.height);
      }
      return;
    }

    renderDungeonCardToCanvasLocalized(revealCanvas, state.selectedCard, settings.language).catch((error) =>
      console.error(error)
    );
  };

  const refresh = () => {
    const total = state.piles.reduce((sum, pile) => sum + pile.length, 0);
    status.textContent =
      state.piles.length === 1
        ? tf(settings.language, 'simulator.pileSingle', { count: state.piles[0]?.length ?? 0 })
        : tf(settings.language, 'simulator.pileSplit', { piles: state.piles.length, cards: total });

    pilesContainer.innerHTML = '';

    state.piles.forEach((pile, pileIndex) => {
      const card = document.createElement('article');
      card.className = 'pile-box';

      const title = document.createElement('h4');
      title.textContent = `Montón ${pileIndex + 1} (${pile.length} ${t(settings.language, 'controls.entries')})`;
      card.appendChild(title);

      const back = document.createElement('button');
      back.className = 'pile-back';
      back.type = 'button';
      back.innerHTML = pile.length > 0
        ? `<img src="/resources/dungeon-back.jpeg" alt="Deck"/>`
        : '<span>Sin cartas</span>';
      back.addEventListener('click', () => {
        if (pile.length === 0) {
          window.alert(tf(settings.language, 'simulator.emptyPile', { pile: pileIndex + 1 }));
          return;
        }
        const drawn = pile.shift() as DungeonCard;
        state.histories[pileIndex]!.unshift(drawn);
        state.selectedCard = drawn;
        state.selectedPile = pileIndex;
        revealedStatus.textContent = tf(settings.language, 'simulator.selectedCard', {
          name: drawn.name,
          pile: pileIndex + 1
        });
        refresh();
      });
      card.appendChild(back);

      const splitBtn = document.createElement('button');
      splitBtn.type = 'button';
      splitBtn.textContent = t(settings.language, 'simulator.splitPile');
      splitBtn.addEventListener('click', () => {
        if (pile.length < 2) {
          window.alert(tf(settings.language, 'simulator.splitInsufficient', { pile: pileIndex + 1 }));
          return;
        }

        const input = window.prompt(t(settings.language, 'simulator.splitPrompt'), '3');
        if (!input) {
          return;
        }
        const count = Number.parseInt(input, 10);
        if (!Number.isFinite(count) || count < 2 || count > pile.length) {
          window.alert(tf(settings.language, 'simulator.splitInvalid', { max: pile.length }));
          return;
        }

        state.piles = splitSelectedPile(state.piles, pileIndex, count);
        state.histories = splitSelectedPileHistories(state.histories, pileIndex, count);
        state.selectedCard = null;
        state.selectedPile = -1;
        revealedStatus.textContent = t(settings.language, 'simulator.splitDone');
        refresh();
      });
      card.appendChild(splitBtn);

      const addCardsBtn = document.createElement('button');
      addCardsBtn.type = 'button';
      addCardsBtn.textContent = t(settings.language, 'button.addCardsToDeck');
      addCardsBtn.addEventListener('click', () => {
        const available = pickAdditionalAdventureCards(environment, dungeonCards, collectAdventureCardIds(state));
        if (available.length === 0) {
          window.alert(tf(settings.language, 'simulator.addCardsUnavailable', { max: 0 }));
          return;
        }

        const input = window.prompt(t(settings.language, 'simulator.addCardsPrompt'), '1');
        if (!input) {
          return;
        }

        const count = Number.parseInt(input, 10);
        if (!Number.isFinite(count) || count < 1 || count > available.length) {
          window.alert(tf(settings.language, 'simulator.addCardsInvalid', { max: available.length }));
          return;
        }

        state.piles[pileIndex]!.push(...available.slice(0, count));
        shuffleCards(state.piles[pileIndex]!);
        state.selectedPile = pileIndex;
        revealedStatus.textContent = tf(settings.language, 'simulator.addCardsDone', {
          count,
          pile: pileIndex + 1
        });
        refresh();
      });
      card.appendChild(addCardsBtn);

      const historyLabel = document.createElement('p');
      historyLabel.textContent = t(settings.language, 'simulator.history');
      card.appendChild(historyLabel);

      const historyList = document.createElement('ul');
      historyList.className = 'history-list';
      state.histories[pileIndex]!.forEach((historyCard, idx) => {
        const item = document.createElement('li');
        item.className = state.selectedCard?.id === historyCard.id && state.selectedPile === pileIndex ? 'selected' : '';
        item.textContent = `${historyCard.name} [${cardTypeLabel(historyCard.type)}]`;
        item.addEventListener('click', () => {
          state.selectedCard = state.histories[pileIndex]![idx] as DungeonCard;
          state.selectedPile = pileIndex;
          revealedStatus.textContent = tf(settings.language, 'simulator.selectedCard', {
            name: state.selectedCard.name,
            pile: pileIndex + 1
          });
          refresh();
        });
        historyList.appendChild(item);
      });
      card.appendChild(historyList);

      pilesContainer.appendChild(card);
    });

    refreshReveal();
  };

  refresh();
}

function openNewDungeonDialog(): void {
  const dialog = document.querySelector<HTMLDialogElement>('#newDungeonDialog');
  if (!dialog) {
    return;
  }

  const environments = dungeonStore.loadEnvironments();
  if (environments.length === 0) {
    window.alert(t(settings.language, 'dialog.deck.noCardsForDungeon'));
    return;
  }

  const ambienceOptions = getAdventureAmbiences(settings.language);

  dialog.innerHTML = `
    <form method="dialog" class="maintenance-grid">
      <h2>${t(settings.language, 'newDungeon.title')}</h2>
      <p>${t(settings.language, 'newDungeon.description')}</p>
      <div class="new-dungeon-grid">
        <label>${t(settings.language, 'newDungeon.environment')}
          <select id="ndEnv"></select>
        </label>
        <label>${t(settings.language, 'newDungeon.objectiveRoom')}
          <select id="ndObjective"></select>
        </label>
        <label>${t(settings.language, 'newDungeon.mission')}
          <select id="ndMission"></select>
        </label>
        <label>${t(settings.language, 'newDungeon.ambience')}
          <select id="ndAmbience">
            ${ambienceOptions.map((item) => `<option value="${item.value}">${item.label}</option>`).join('')}
          </select>
        </label>
        <label>${t(settings.language, 'newDungeon.level')}
          <input id="ndLevel" type="number" min="1" max="10" value="${Math.max(1, Math.min(10, settings.activeDungeonLevel))}">
        </label>
        <label>${t(settings.language, 'newDungeon.deckSize')}
          <input id="ndDeckSize" type="number" min="2" max="200" value="12">
        </label>
        <label>${t(settings.language, 'newDungeon.roomCount')}
          <input id="ndRoomCount" type="number" min="1" max="11" value="5">
        </label>
      </div>
      <label>${t(settings.language, 'newDungeon.specialRules')}
        <textarea id="ndMissionRules" rows="5"  style="width: 100%; resize: none;" readonly></textarea>
      </label>
      <fieldset class="objective-monster-weights">
        <legend>${t(settings.language, 'newDungeon.objectiveMonsterWeights')}</legend>
        <p>${t(settings.language, 'newDungeon.objectiveMonsterWeightsHint')}</p>
        <div class="new-dungeon-grid">
          <label>${t(settings.language, 'newDungeon.objectiveMonsterEasy')}
            <input id="ndObjectiveEasy" type="number" min="0" max="99" value="${Math.max(0, settings.objectiveMonsterEasyWeight)}">
          </label>
          <label>${t(settings.language, 'newDungeon.objectiveMonsterNormal')}
            <input id="ndObjectiveNormal" type="number" min="0" max="99" value="${Math.max(0, settings.objectiveMonsterNormalWeight)}">
          </label>
          <label>${t(settings.language, 'newDungeon.objectiveMonsterHard')}
            <input id="ndObjectiveHard" type="number" min="0" max="99" value="${Math.max(0, settings.objectiveMonsterHardWeight)}">
          </label>
          <label>${t(settings.language, 'newDungeon.objectiveMonsterVeryHard')}
            <input id="ndObjectiveVeryHard" type="number" min="0" max="99" value="${Math.max(0, settings.objectiveMonsterVeryHardWeight)}">
          </label>
          <label>${t(settings.language, 'newDungeon.objectiveMonsterExtreme')}
            <input id="ndObjectiveExtreme" type="number" min="0" max="99" value="${Math.max(0, settings.objectiveMonsterExtremeWeight)}">
          </label>
        </div>
      </fieldset>
      <menu>
        <button value="cancel">${t(settings.language, 'dialog.button.cancel')}</button>
        <button type="button" id="ndStart">${t(settings.language, 'button.startAdventure')}</button>
      </menu>
    </form>
  `;

  const envSelect = dialog.querySelector<HTMLSelectElement>('#ndEnv')!;
  const objectiveSelect = dialog.querySelector<HTMLSelectElement>('#ndObjective')!;
  const missionSelect = dialog.querySelector<HTMLSelectElement>('#ndMission')!;
  const ambienceSelect = dialog.querySelector<HTMLSelectElement>('#ndAmbience')!;
  const levelInput = dialog.querySelector<HTMLInputElement>('#ndLevel')!;
  const deckSizeInput = dialog.querySelector<HTMLInputElement>('#ndDeckSize')!;
  const roomCountInput = dialog.querySelector<HTMLInputElement>('#ndRoomCount')!;
  const missionRules = dialog.querySelector<HTMLTextAreaElement>('#ndMissionRules')!;
  const objectiveEasyInput = dialog.querySelector<HTMLInputElement>('#ndObjectiveEasy')!;
  const objectiveNormalInput = dialog.querySelector<HTMLInputElement>('#ndObjectiveNormal')!;
  const objectiveHardInput = dialog.querySelector<HTMLInputElement>('#ndObjectiveHard')!;
  const objectiveVeryHardInput = dialog.querySelector<HTMLInputElement>('#ndObjectiveVeryHard')!;
  const objectiveExtremeInput = dialog.querySelector<HTMLInputElement>('#ndObjectiveExtreme')!;

  envSelect.innerHTML = environments.map((environment) => `<option value="${environment}">${environment}</option>`).join('');

  let objectiveRooms: DungeonCard[] = [];
  let missions: ObjectiveRoomAdventure[] = [];

  const syncMissionRules = () => {
    const selectedMission = missions.find((mission) => mission.name === missionSelect.value);
    missionRules.value = selectedMission?.rulesText ?? '';
  };

  const reloadMissions = () => {
    const objective = objectiveRooms.find((room) => room.name === objectiveSelect.value);
    if (!objective) {
      missions = [];
      missionSelect.innerHTML = '';
      missionRules.value = '';
      return;
    }

    missions = dungeonStore.loadAdventuresForObjectiveRoom(objective.name);
    missionSelect.innerHTML = missions.map((mission) => `<option value="${mission.name}">${mission.name}</option>`).join('');
    syncMissionRules();
  };

  const reloadObjectives = () => {
    objectiveRooms = dungeonStore.loadObjectiveRoomsByEnvironment(envSelect.value);
    objectiveSelect.innerHTML = objectiveRooms.map((room) => `<option value="${room.name}">${room.name}</option>`).join('');
    reloadMissions();
  };

  envSelect.addEventListener('change', reloadObjectives);
  objectiveSelect.addEventListener('change', reloadMissions);
  missionSelect.addEventListener('change', syncMissionRules);

  deckSizeInput.addEventListener('change', () => {
    const deckSize = Math.max(2, Number.parseInt(deckSizeInput.value, 10) || 2);
    deckSizeInput.value = String(deckSize);
    roomCountInput.max = String(Math.max(1, deckSize - 1));
    const rooms = Math.min(Number.parseInt(roomCountInput.value, 10) || 1, deckSize - 1);
    roomCountInput.value = String(Math.max(1, rooms));
  });

  reloadObjectives();

  dialog.querySelector<HTMLButtonElement>('#ndStart')?.addEventListener('click', () => {
    const objective = objectiveRooms.find((room) => room.name === objectiveSelect.value);
    const mission = missions.find((item) => item.name === missionSelect.value);
    if (!objective || !mission) {
      window.alert(t(settings.language, 'dialog.newDungeon.requiredSelection'));
      return;
    }

    const deckSize = Math.max(2, Number.parseInt(deckSizeInput.value, 10) || 2);
    const roomCount = Math.max(1, Number.parseInt(roomCountInput.value, 10) || 1);
    const dungeonLevel = Math.max(1, Math.min(10, Number.parseInt(levelInput.value, 10) || 1));

    try {
      settings.objectiveMonsterEasyWeight = Math.max(0, Number.parseInt(objectiveEasyInput.value, 10) || 0);
      settings.objectiveMonsterNormalWeight = Math.max(0, Number.parseInt(objectiveNormalInput.value, 10) || 0);
      settings.objectiveMonsterHardWeight = Math.max(0, Number.parseInt(objectiveHardInput.value, 10) || 0);
      settings.objectiveMonsterVeryHardWeight = Math.max(0, Number.parseInt(objectiveVeryHardInput.value, 10) || 0);
      settings.objectiveMonsterExtremeWeight = Math.max(0, Number.parseInt(objectiveExtremeInput.value, 10) || 0);

      const deck = buildAdventureDeck(envSelect.value, objective, deckSize, roomCount, dungeonCards);
      settings.adventureAmbience = ambienceSelect.value;
      settings.activeDungeonLevel = dungeonLevel;
      settings.dungeonActive = true;
      saveSettings(settings);
      rebuildDecks();
      dialog.close();
      openAdventureSimulator(
        deck,
        mission,
        ambienceSelect.options[ambienceSelect.selectedIndex]?.text ?? ambienceSelect.value,
        dungeonLevel,
        envSelect.value
      );
    } catch (error) {
      window.alert(String(error));
    }
  });

  dialog.showModal();
}

function rebuildDecks(): void {
  applyTableActiveState(repository, settings);
  decks = buildDecks(repository, settings);
  saveSettings(settings);
  renderDecks();
}

function render(): void {
  createAppShell(settings.language);
  wireHeroActions();
  buildControls();
  buildDeckToggles();
  refreshDungeonCards();
  rebuildDecks();
}

async function bootstrap(): Promise<void> {
  settings = await loadSettings();
  await Promise.all([dungeonStore.init(settings.language)]);
  repository = await loadContent(settings.language);
  settings.dungeonActive = false;

  applyTableActiveState(repository, settings);
  decks = buildDecks(repository, settings);
  dungeonCards = dungeonStore.loadCards();
  selectedDungeonCard = dungeonCards[0] ?? null;

  render();
}

bootstrap().catch((error) => {
  const app = document.querySelector<HTMLDivElement>('#app');
  if (app) {
    app.innerHTML = `<pre class="error">Error loading application:\n${String(error)}</pre>`;
  }
});

window.addEventListener('beforeunload', () => {
  saveSettings(settings);
});
