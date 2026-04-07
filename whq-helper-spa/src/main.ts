import './styles.css';

import { getAdventureAmbiences, getSettlementTypes, t, tf } from './i18n';
import { findAnyEvent, loadContent, loadContentManifest } from './content';
import { applyTableActiveState, buildDecks, getMonsterNumber } from './deck';
import { loadSettings, saveSettings } from './settings';
import { renderEventCard, renderMonsterCard, renderSettlementLocationCard } from './render';
import { DungeonCardStore, downloadCsv, exportCardsToCsv, importCardsFromCsvFile } from './dungeonStore';
import { renderDungeonCardToCanvasLocalized } from './dungeonRenderer';
import { getTileAssetDisplayName, saveTileAsset } from './tileAssets';
import { getCounterAssetDisplayName, resolveCounterAsset, saveCounterAsset } from './counterAssets';
import { getXmlOverride, removeXmlOverride, saveXmlOverride } from './contentOverrides';
import {
  createDefaultDungeonCard,
  createDefaultEvent,
  createDefaultMonster,
  createDefaultObjectiveRoomAdventure,
  createDefaultLocation,
  createDefaultRule,
  createDefaultTable,
  createDefaultWarrior,
  createUserContentUid,
  deleteUserContentItem,
  loadUserContentItems,
  mapDungeonCardToUserData,
  mapEventToUserData,
  mapLocationToUserData,
  mapMonsterToUserData,
  mapObjectiveRoomAdventureToUserData,
  mapRuleToUserData,
  mapTableToUserData,
  mapWarriorToUserData,
  parseTableMetadata,
  type UserContentItem,
  type UserContentKind,
  type UserDungeonCardData,
  type UserEventData,
  type UserLocationData,
  type UserMonsterData,
  type UserObjectiveRoomAdventureData,
  type UserRuleData,
  type UserTableData,
  type UserWarriorData,
  upsertUserContentItem,
  userContentItemXml
} from './userContent';
import type {
  AppSettings,
  ContentRepository,
  DeckBundle,
  DrawEntry,
  DungeonCard,
  EventModel,
  GroupEntry,
  LanguageCode,
  MonsterEntry,
  ObjectiveRoomAdventure,
  Rule,
  SettlementLocation,
  SettlementType,
  TableRefEntry,
  TableModel,
  WarriorDefinition
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
const WARRIOR_COUNTER_CASCADE = 28;

interface DashboardCategoryMeta {
  kind: UserContentKind;
  titleKey: string;
}

const DASHBOARD_CATEGORIES: DashboardCategoryMeta[] = [
  { kind: 'dungeonCard', titleKey: 'contentDashboard.category.dungeonCard' },
  { kind: 'dungeonEvent', titleKey: 'contentDashboard.category.dungeonEvent' },
  { kind: 'treasure', titleKey: 'contentDashboard.category.treasure' },
  { kind: 'objectiveTreasure', titleKey: 'contentDashboard.category.objectiveTreasure' },
  { kind: 'travelEvent', titleKey: 'contentDashboard.category.travelEvent' },
  { kind: 'settlementEvent', titleKey: 'contentDashboard.category.settlementEvent' },
  { kind: 'rule', titleKey: 'contentDashboard.category.rule' },
  { kind: 'monster', titleKey: 'contentDashboard.category.monster' },
  { kind: 'table', titleKey: 'contentDashboard.category.table' },
  { kind: 'objectiveRoomAdventure', titleKey: 'contentDashboard.category.objectiveRoomAdventure' },
  { kind: 'warrior', titleKey: 'contentDashboard.category.warrior' },
  { kind: 'location', titleKey: 'contentDashboard.category.location' }
];

let activeDashboardItemUid: string | null = null;
const DASHBOARD_CREATE_PREFIX = 'create:';
let dashboardDraftItem: UserContentItem | null = null;
let dashboardOpen = false;
let warriorCounterAvailableIds = new Set<string>();
let warriorCounterOpenIds = new Set<string>();
let warriorCounterCascadeIndex = 0;

type EventTableKind = 'dungeon' | 'travel' | 'settlement';

function clampProbability(value: number): number {
  return Math.max(0, Math.min(100, value));
}

function syncPartySize(): void {
  settings.partyWarriors = settings.partyWarriors.filter((id, index, array) => !!repository.warriors.get(id) && array.indexOf(id) === index);
  if (settings.partyWarriors.length === 0) {
    settings.partyWarriors = ['warrior-barbarian', 'warrior-dwarf', 'warrior-elf', 'warrior-wizard'].filter((id) =>
      repository.warriors.has(id)
    );
  }
  if (settings.partyWarriors.length === 0) {
    settings.partyWarriors = Array.from(repository.warriors.keys()).slice(0, 4);
  }
  settings.partySize = Math.max(1, settings.partyWarriors.length);
}

function resetWarriorCounterPool(): void {
  warriorCounterAvailableIds = new Set(settings.partyWarriors);
  warriorCounterOpenIds = new Set<string>();
  warriorCounterCascadeIndex = 0;
}

function warriorDisplayLabel(warrior: WarriorDefinition): string {
  return `${warrior.name} (${warrior.race})`;
}

function locationVisitorLabel(visitorId: string): string {
  if (visitorId === 'all') {
    return t(settings.language, 'settlement.type.any');
  }
  return repository.warriors.get(visitorId)?.name ?? visitorId;
}

function settlementTypeLabel(type: SettlementType): string {
  return t(settings.language, `settlement.type.${type}`);
}

function normalizeSettlementLocationType(value: string): SettlementType {
  if (value === 'city' || value === 'town' || value === 'village' || value === 'outskirts' || value === 'special') {
    return value;
  }
  return 'any';
}

async function applyLanguageChange(language: LanguageCode): Promise<void> {
  settings.language = language;
  await Promise.all([dungeonStore.setLanguage(language)]);
  repository = await loadContent(language);
  syncPartySize();
  resetWarriorCounterPool();
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
            <button type="button" id="newSettlementBtn">${t(language, 'deck.newSettlement')}</button>
            <button type="button" id="warriorCountersBtn">${t(language, 'deck.warriorCounters')}</button>
            <button type="button" id="tileConfigBtn">${t(language, 'deck.configureTiles')}</button>
            <button type="button" id="contentDashboardBtn">${t(language, 'deck.contentCreation')}</button>
          </div>
        </div>
      </header>

      <section class="controls" id="controls"></section>
      <section class="deck-toggles" id="deckToggles"></section>
      <section class="decks" id="decks"></section>
      <section id="settlementSimulatorPanel" class="simulator-panel" hidden></section>
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
      <dialog id="partyDialog" class="table-dialog wide-dialog"></dialog>
      <dialog id="missionDialog" class="table-dialog"></dialog>
      <dialog id="maintenanceDialog" class="table-dialog wide-dialog"></dialog>
      <dialog id="treasureSearchDialog" class="table-dialog ultra-dialog"></dialog>
      <section id="contentDashboardView" class="dashboard-view" hidden></section>
    </div>
  `;
}

function wireHeroActions(): void {
  document.querySelector<HTMLButtonElement>('#newDungeonBtn')?.addEventListener('click', () => {
    openNewDungeonDialog();
  });

  document.querySelector<HTMLButtonElement>('#newSettlementBtn')?.addEventListener('click', () => {
    openSettlementSimulatorPanel();
  });

  document.querySelector<HTMLButtonElement>('#warriorCountersBtn')?.addEventListener('click', () => {
    drawWarriorCounter();
  });

  document.querySelector<HTMLButtonElement>('#tileConfigBtn')?.addEventListener('click', () => {
    openMaintenanceDialog();
  });

  document.querySelector<HTMLButtonElement>('#contentDashboardBtn')?.addEventListener('click', () => {
    openContentDashboardDialog().catch((error) => window.alert(String(error)));
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

    <div class="party-summary-control">
      <span class="party-summary-label">${t(settings.language, 'controls.partyMembers')}</span>
      <strong>${settings.partyWarriors
        .map((id) => repository.warriors.get(id)?.name ?? id)
        .join(', ')}</strong>
      <small>${tf(settings.language, 'party.size', { count: settings.partySize })}</small>
    </div>

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
      <button type="button" id="setPartyBtn">${t(settings.language, 'controls.setParty')}</button>
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
    closeAllOpenCards();
  });

  controls.querySelector<HTMLButtonElement>('#setPartyBtn')?.addEventListener('click', () => {
    openPartyDialog();
  });

  controls.querySelector<HTMLButtonElement>('#activateTablesBtn')?.addEventListener('click', () => {
    openTableDialog();
  });
}

function closeAllOpenCards(): void {
  document.querySelector<HTMLElement>('#windows')!.innerHTML = '';
}

function openPartyDialog(): void {
  const dialog = document.querySelector<HTMLDialogElement>('#partyDialog');
  if (!dialog) {
    return;
  }
  const available = Array.from(repository.warriors.values()).sort((left, right) =>
    left.name.localeCompare(right.name, undefined, { sensitivity: 'base' })
  );
  const selected = [...settings.partyWarriors];

  const renderPartyDialog = (): void => {
    dialog.innerHTML = `
      <form method="dialog" class="party-dialog">
        <h2>${t(settings.language, 'party.title')}</h2>
        <div class="party-dialog-layout">
          <section>
            <label>${t(settings.language, 'party.available')}
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
            <label>${t(settings.language, 'party.selected')}
              <select id="partySelectedList" size="10">
                ${selected
                  .map((warriorId) => {
                    const warrior = repository.warriors.get(warriorId);
                    const label = warrior ? warriorDisplayLabel(warrior) : warriorId;
                    return `<option value="${escapeHtml(warriorId)}">${escapeHtml(label)}</option>`;
                  })
                  .join('')}
              </select>
            </label>
            <p class="party-dialog-size">${tf(settings.language, 'party.size', { count: selected.length })}</p>
          </section>
        </div>
        <menu>
          <button value="cancel">${t(settings.language, 'dialog.button.cancel')}</button>
          <button type="button" id="partySaveBtn">${t(settings.language, 'dialog.button.save')}</button>
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
        window.alert(t(settings.language, 'party.selectAtLeastOne'));
        return;
      }
      settings.partyWarriors = [...selected];
      syncPartySize();
      resetWarriorCounterPool();
      saveSettings(settings);
      buildControls();
      dialog.close();
    });
  };

  renderPartyDialog();
  dialog.showModal();
}

function openWarriorCounterWindow(warrior: WarriorDefinition): void {
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
  windowEl.style.zIndex = `${zIndexCounter++}`;
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
    windowEl.style.zIndex = `${zIndexCounter++}`;
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

function drawWarriorCounter(): void {
  const availableIds = [...warriorCounterAvailableIds].filter((id) => settings.partyWarriors.includes(id) && !warriorCounterOpenIds.has(id));
  if (availableIds.length === 0) {
    window.alert(t(settings.language, 'warriorCounter.noneLeft'));
    return;
  }
  const selectedId = availableIds[Math.floor(Math.random() * availableIds.length)] ?? '';
  const warrior = repository.warriors.get(selectedId);
  if (!warrior) {
    return;
  }
  warriorCounterAvailableIds.delete(selectedId);
  warriorCounterOpenIds.add(selectedId);
  openWarriorCounterWindow(warrior);
}

function settlementLocationsForType(type: SettlementType): SettlementLocation[] {
  return Array.from(repository.locations.values())
    .filter((location) => type === 'any' || location.availableTypes.includes(type))
    .sort((left, right) => left.name.localeCompare(right.name, undefined, { sensitivity: 'base' }));
}

function openSettlementSimulatorPanel(): void {
  const panel = document.querySelector<HTMLElement>('#settlementSimulatorPanel');
  if (!panel) {
    return;
  }
  let currentType: SettlementType = 'any';
  let selectedLocationId = settlementLocationsForType(currentType)[0]?.id ?? '';

  const renderSettlement = (): void => {
    const locations = settlementLocationsForType(currentType);
    if (!locations.some((location) => location.id === selectedLocationId)) {
      selectedLocationId = locations[0]?.id ?? '';
    }
    const selected = locations.find((location) => location.id === selectedLocationId) ?? null;
    panel.innerHTML = `
      <section class="simulator-layout settlement-inline-layout">
        <header>
          <h2>${t(settings.language, 'settlement.title')}</h2>
          <p>${t(settings.language, 'deck.settlement.subtitle')}</p>
        </header>
        <section class="settlement-layout">
          <div class="settlement-left">
            <section class="settlement-top">
              <article class="deck settlement-settlement-deck">
              <label style="width: 100%; color: beige;">${t(settings.language, 'settlement.type')}
                <select id="settlementTypeSelect" style="width: 100%">
                  ${getSettlementTypes(settings.language)
                    .map((entry) => `<option value="${entry.value}" ${entry.value === currentType ? 'selected' : ''}>${escapeHtml(entry.label)}</option>`)
                    .join('')}
                </select>
              </label>
              <label style="width: 100%; color: beige;">${t(settings.language, 'settlement.locations')}
                <select id="settlementLocationList" size="12" style="width: 100%">
                  ${
                    locations.length > 0
                      ? locations
                          .map((location) => `<option value="${escapeHtml(location.id)}" ${location.id === selectedLocationId ? 'selected' : ''}>${escapeHtml(location.name)}</option>`)
                          .join('')
                      : `<option value="">${escapeHtml(t(settings.language, 'settlement.noLocations'))}</option>`
                  }
                </select>
              </label>
              </article>
            </section>
            <section class="settlement-bottom">
              <article class="deck settlement-settlement-deck">
                <button type="button" class="deck-button" id="settlementDrawBtn">
                  <img src="/data/graphics/settlement.png" alt="${escapeHtml(t(settings.language, 'deck.settlement.title'))}" />
                  <span>${t(settings.language, 'button.clickHere')}</span>
                </button>
                <small>${decks.settlement.size()} ${t(settings.language, 'controls.entries')}</small>
                <div class="dashboard-inline-actions">
                  <button type="button" class="secondary-button" id="settlementCloseCardsBtn">${t(settings.language, 'menu.item.closeAllCards')}</button>
                  <button type="button" class="secondary-button" id="closeSettlementPanelBtn">${t(settings.language, 'dialog.button.close')}</button>
                </div>
              </article>
            </section>
          </div>
          <div class="settlement-preview">
            ${
              selected
                ? renderSettlementLocationCard(selected, selected.visitors.map((visitor) => locationVisitorLabel(visitor)))
                : `<p>${escapeHtml(t(settings.language, 'settlement.noLocations'))}</p>`
            }
          </div>
        </section>
      </section>
    `;

    panel.querySelector<HTMLSelectElement>('#settlementTypeSelect')?.addEventListener('change', (event) => {
      currentType = normalizeSettlementLocationType((event.currentTarget as HTMLSelectElement).value);
      renderSettlement();
    });
    panel.querySelector<HTMLSelectElement>('#settlementLocationList')?.addEventListener('change', (event) => {
      selectedLocationId = (event.currentTarget as HTMLSelectElement).value;
      renderSettlement();
    });
    panel.querySelector<HTMLButtonElement>('#settlementDrawBtn')?.addEventListener('click', () => {
      drawFromDeck('settlement');
      const entries = panel.querySelector<HTMLElement>('.settlement-settlement-deck small');
      if (entries) {
        entries.textContent = `${decks.settlement.size()} ${t(settings.language, 'controls.entries')}`;
      }
    });
    panel.querySelector<HTMLButtonElement>('#settlementCloseCardsBtn')?.addEventListener('click', () => {
      closeAllOpenCards();
    });
    panel.querySelector<HTMLButtonElement>('#closeSettlementPanelBtn')?.addEventListener('click', () => {
      closeSettlementSimulatorPanel();
    });
  };

  panel.hidden = false;
  panel.classList.add('active');
  document.body.classList.add('settlement-panel-active');
  renderSettlement();
}

function closeSettlementSimulatorPanel(): void {
  const panel = document.querySelector<HTMLElement>('#settlementSimulatorPanel');
  if (!panel) {
    return;
  }
  panel.hidden = true;
  panel.classList.remove('active');
  panel.innerHTML = '';
  document.body.classList.remove('settlement-panel-active');
}

function getActiveTreasureEvents(): EventModel[] {
  const treasures = new Map<string, EventModel>();
  for (const table of repository.tables.values()) {
    if (!table.active || table.kind !== 'treasure') {
      continue;
    }
    for (const entry of table.events) {
      const event = repository.events.get(entry.id);
      if (event?.treasure) {
        treasures.set(event.id, event);
      }
    }
  }
  return Array.from(treasures.values()).sort((left, right) => left.name.localeCompare(right.name, undefined, { sensitivity: 'base' }));
}

function openTreasureSearchDialog(): void {
  const dialog = document.querySelector<HTMLDialogElement>('#treasureSearchDialog');
  if (!dialog) {
    return;
  }

  const treasures = getActiveTreasureEvents();
  dialog.innerHTML = `
    <form method="dialog" class="treasure-search-dialog">
      <h2>${t(settings.language, 'treasureSearch.title')}</h2>
      <label>${t(settings.language, 'treasureSearch.filter')}
        <input id="treasureSearchInput" type="text" autocomplete="off" placeholder="${t(settings.language, 'treasureSearch.placeholder')}">
      </label>
      <div class="treasure-search-layout">
        <section class="treasure-search-results">
          <h3>${t(settings.language, 'treasureSearch.results')}</h3>
          <div id="treasureSearchList" class="table-list"></div>
        </section>
        <section class="treasure-search-preview">
          <h3>${t(settings.language, 'contentDashboard.cardPreview')}</h3>
          <div id="treasureSearchPreview" class="treasure-card-preview"></div>
        </section>
      </div>
      <menu>
        <button value="cancel">${t(settings.language, 'dialog.button.close')}</button>
      </menu>
    </form>
  `;

  const input = dialog.querySelector<HTMLInputElement>('#treasureSearchInput')!;
  const list = dialog.querySelector<HTMLElement>('#treasureSearchList')!;
  const preview = dialog.querySelector<HTMLElement>('#treasureSearchPreview')!;
  let selectedId = treasures[0]?.id ?? '';

  const renderPreview = (): void => {
    const selected = treasures.find((event) => event.id === selectedId) ?? null;
    preview.innerHTML = selected ? renderEventCard(selected, settings.language) : `<p>${t(settings.language, 'treasureSearch.empty')}</p>`;
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
      : `<p>${t(settings.language, 'treasureSearch.noResults')}</p>`;

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
  } else if (entry.kind === 'tableRef') {
    window.alert(`Unresolved table reference: ${entry.tableName}`);
    return;
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

function splitCsv(value: string): string[] {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function joinCsv(values: string[]): string {
  return values.join(', ');
}

function formatRuleLinks(links: Record<string, { text: string; parameter: string }>): string {
  return Object.entries(links)
    .map(([key, value]) => {
      const text = value?.text ?? '';
      const parameters = (value as { parameters?: string[] } | undefined)?.parameters ?? [];
      const parameter = value?.parameter?.trim?.() ?? '';
      const suffix = parameters.length > 1 ? ` [${parameters.join(' | ')}]` : parameter ? ` [${parameter}]` : '';
      return `${text} (${key})${suffix}`;
    })
    .join('\n');
}

function buildSpecialRuleText(name: string, parameters: string[], parameterFormat = ''): string {
  const normalizedName = name?.trim?.() ?? '';
  const normalizedParameters = parameters.map((value) => value?.trim?.() ?? '');
  const normalizedFormat = parameterFormat?.trim?.() ?? '';
  if (normalizedParameters.every((value) => !value)) {
    return normalizedName;
  }
  if (normalizedFormat) {
    let rendered = normalizedFormat.replaceAll('{name}', normalizedName);
    rendered = rendered.replaceAll('{param}', normalizedParameters[0] ?? '');
    normalizedParameters.forEach((value, index) => {
      rendered = rendered.replaceAll(`{${index}}`, value);
    });
    return rendered.trim();
  }
  return `${normalizedName} ${normalizedParameters[0] ?? ''}`.trim();
}

function inferRuleParameters(name: string, text: string, parameterFormat = ''): string[] {
  const normalizedName = name?.trim?.() ?? '';
  const normalizedText = text?.trim?.() ?? '';
  const normalizedFormat = parameterFormat?.trim?.() ?? '';
  if (!normalizedName || !normalizedText) {
    return [];
  }
  if (normalizedFormat) {
    const source = normalizedFormat.replaceAll('{name}', normalizedName);
    const captureRegex = /(\{(?:\d+|param)\})/g;
    const parts = source.split(captureRegex).filter(Boolean);
    const placeholders = parts.filter((part) => /^\{(?:\d+|param)\}$/.test(part));
    if (placeholders.length > 0) {
      const pattern = parts
        .map((part) => (/^\{(?:\d+|param)\}$/.test(part) ? '(.*?)' : part.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
        .join('');
      const match = new RegExp(`^${pattern}$`, 'i').exec(normalizedText);
      if (match) {
        const values: string[] = [];
        placeholders.forEach((placeholder, index) => {
          const targetIndex = placeholder === '{param}' ? 0 : Number.parseInt(placeholder.slice(1, -1), 10);
          values[targetIndex] = match[index + 1]?.trim?.() ?? '';
        });
        return values;
      }
    }
  }
  if (!normalizedText.toLowerCase().startsWith(normalizedName.toLowerCase())) {
    return [];
  }
  return [normalizedText.slice(normalizedName.length).trim()];
}

function ruleParameterLabels(rule?: Pick<Rule, 'parameterName' | 'parameterNames'> | undefined): string[] {
  if (!rule) {
    return [];
  }
  if (rule.parameterNames?.length) {
    return rule.parameterNames;
  }
  return rule.parameterName ? [rule.parameterName] : [];
}

function treasureUsersToFlags(users: string): Record<'B' | 'D' | 'E' | 'W', boolean> {
  const normalized = users.trim().toUpperCase();
  return {
    B: normalized.includes('B'),
    D: normalized.includes('D'),
    E: normalized.includes('E'),
    W: normalized.includes('W')
  };
}

function treasureUsersFromFlags(flags: Record<'B' | 'D' | 'E' | 'W', boolean>): string {
  return `${flags.B ? 'B' : ''}${flags.D ? 'D' : ''}${flags.E ? 'E' : ''}${flags.W ? 'W' : ''}`;
}

function readFileAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(typeof reader.result === 'string' ? reader.result : '');
    reader.onerror = () => reject(reader.error ?? new Error('Unable to read file'));
    reader.readAsDataURL(file);
  });
}

async function refreshRuntimeContent(): Promise<void> {
  await dungeonStore.init(settings.language);
  repository = await loadContent(settings.language);
  syncPartySize();
  refreshDungeonCards();
  rebuildDecks();
}

function nextUserDungeonCardId(): number {
  const fromCards = dungeonCards.map((card) => card.id);
  const fromUserItems = loadUserContentItems()
    .filter((item): item is Extract<UserContentItem, { kind: 'dungeonCard' }> => item.kind === 'dungeonCard')
    .map((item) => item.data.id);
  return Math.max(0, ...fromCards, ...fromUserItems) + 1;
}

function isHiddenManagedTreasureTable(item: UserContentItem): boolean {
  return (
    item.kind === 'table' &&
    (item.data.name.trim() === 'userdefined-treasure' || item.data.name.trim() === 'userdefined-objective-treasure')
  );
}

function dashboardItemsByKind(kind: UserContentKind): UserContentItem[] {
  return loadUserContentItems()
    .filter((item) => item.kind === kind)
    .filter((item) => !isHiddenManagedTreasureTable(item))
    .sort((left, right) => left.title.localeCompare(right.title, undefined, { sensitivity: 'base' }));
}

function isDashboardDraftSelected(): boolean {
  return activeDashboardItemUid === 'draft' && dashboardDraftItem !== null;
}

function currentDashboardItem(): UserContentItem | null {
  if (isDashboardDraftSelected()) {
    return dashboardDraftItem;
  }
  if (!activeDashboardItemUid || activeDashboardItemUid.startsWith(DASHBOARD_CREATE_PREFIX)) {
    return null;
  }
  return loadUserContentItems().find((item) => item.uid === activeDashboardItemUid) ?? null;
}

function availableObjectiveRoomNames(): string[] {
  return [...new Set(dungeonStore.loadCards().filter((card) => card.type === 'OBJECTIVE_ROOM').map((card) => card.name))]
    .sort((left, right) => left.localeCompare(right, undefined, { sensitivity: 'base' }));
}

function availableMonsterFactions(): string[] {
  return [...new Set(Array.from(repository.monsters.values()).flatMap((monster) => monster.factions).filter(Boolean))].sort((left, right) =>
    left.localeCompare(right, undefined, { sensitivity: 'base' })
  );
}

function availableMonsterRules(): Rule[] {
  return Array.from(repository.rules.values())
    .filter((rule) => rule.type !== 'magic')
    .sort((left, right) => left.name.localeCompare(right.name, undefined, { sensitivity: 'base' }));
}

function availableMagicRules(): Rule[] {
  return Array.from(repository.rules.values())
    .filter((rule) => rule.type === 'magic')
    .sort((left, right) => left.name.localeCompare(right.name, undefined, { sensitivity: 'base' }));
}

function eventTableUserContentKind(kind: EventTableKind): Extract<UserContentKind, 'dungeonEvent' | 'travelEvent' | 'settlementEvent'> {
  return kind === 'travel' ? 'travelEvent' : kind === 'settlement' ? 'settlementEvent' : 'dungeonEvent';
}

function parseEventOnlyTable(xml: string): { name: string; kind: EventTableKind; eventIds: string[] } | null {
  const doc = new DOMParser().parseFromString(xml, 'text/xml');
  if (doc.querySelector('parsererror')) {
    return null;
  }
  const table = Array.from(doc.documentElement.children).find((node) => node.tagName === 'table');
  if (!table) {
    return null;
  }
  const kindRaw = (table.getAttribute('kind') ?? '').trim().toLowerCase();
  const kind: EventTableKind =
    kindRaw === 'travel' ? 'travel' : kindRaw === 'settlement' ? 'settlement' : kindRaw === '' ? 'dungeon' : kindRaw === 'dungeon' ? 'dungeon' : 'dungeon';

  const eventIds: string[] = [];
  for (const child of Array.from(table.children)) {
    if (child.tagName !== 'event') {
      return null;
    }
    const eventId = (child.getAttribute('id') ?? '').trim();
    if (eventId) {
      eventIds.push(eventId);
    }
  }

  return {
    name: (table.getAttribute('name') ?? '').trim(),
    kind,
    eventIds
  };
}

function serializeEventOnlyTable(name: string, kind: EventTableKind, eventIds: string[]): string {
  const tableAttrs = [`name="${escapeHtml(name)}"`];
  if (kind !== 'dungeon') {
    tableAttrs.push(`kind="${kind}"`);
  }
  return [
    '<?xml version="1.0"?>',
    '<tables>',
    `  <table ${tableAttrs.join(' ')}>`,
    ...eventIds.map((eventId) => `    <event id="${escapeHtml(eventId)}" />`),
    '  </table>',
    '</tables>'
  ].join('\n');
}

function parseMonsterTableSpecial(node: Element): Pick<MonsterEntry, 'special' | 'specialLinks' | 'magicType' | 'magicLevel' | 'appendSpecials'> {
  const result: Pick<MonsterEntry, 'special' | 'specialLinks' | 'magicType' | 'magicLevel' | 'appendSpecials'> = {
    special: '',
    specialLinks: {},
    magicType: '',
    magicLevel: 0,
    appendSpecials: true
  };
  const specialNode = Array.from(node.children).find((child) => child.tagName === 'special');
  if (!specialNode) {
    return result;
  }
  result.appendSpecials = specialNode.getAttribute('append') !== 'false';
  for (const child of Array.from(specialNode.children)) {
    if (child.tagName === 'rule') {
      const id = (child.getAttribute('id') ?? '').trim();
      const text = child.textContent?.trim() ?? '';
      const parameter = (child.getAttribute('param') ?? '').trim();
      const parameters = parameter ? [parameter] : [];
      if (id && text) {
        result.specialLinks[id] = { text, parameter, parameters };
      }
    } else if (child.tagName === 'magic') {
      result.magicType = (child.getAttribute('id') ?? '').trim();
      result.magicLevel = Number.parseInt(child.getAttribute('level') ?? '0', 10) || 0;
    } else if (child.tagName === 'text') {
      result.special = child.textContent?.trim() ?? '';
    }
  }
  return result;
}

function parseMonsterTableEntry(node: Element, defaultLevel: number): MonsterEntry {
  const numberRaw = (node.getAttribute('number') ?? '1').trim();
  const [min, max] = numberRaw.includes('-')
    ? numberRaw.split('-').map((part) => Number.parseInt(part, 10) || 0)
    : [Number.parseInt(numberRaw, 10) || 0, Number.parseInt(numberRaw, 10) || 0];
  const level = Math.max(1, Math.min(10, Number.parseInt(node.getAttribute('level') ?? String(defaultLevel), 10) || defaultLevel));
  const ambiences = (node.getAttribute('ambiences') ?? '')
    .trim()
    .split(/\s+/)
    .filter(Boolean);
  return {
    kind: 'monster',
    id: (node.getAttribute('id') ?? '').trim(),
    level,
    min,
    max,
    ambiences,
    ...parseMonsterTableSpecial(node)
  };
}

function parseMonsterOnlyTable(xml: string): { name: string; entries: Array<MonsterEntry | GroupEntry> } | null {
  const doc = new DOMParser().parseFromString(xml, 'text/xml');
  if (doc.querySelector('parsererror')) {
    return null;
  }
  const table = Array.from(doc.documentElement.children).find((node) => node.tagName === 'table');
  if (!table) {
    return null;
  }
  const entries: Array<MonsterEntry | GroupEntry> = [];
  for (const child of Array.from(table.children)) {
    if (child.tagName === 'monster') {
      entries.push(parseMonsterTableEntry(child, 1));
      continue;
    }
    if (child.tagName === 'group') {
      const level = Math.max(1, Math.min(10, Number.parseInt(child.getAttribute('level') ?? '1', 10) || 1));
      const monsters = Array.from(child.children)
        .filter((member) => member.tagName === 'monster')
        .map((member) => parseMonsterTableEntry(member, level));
      if (monsters.length === 0 || monsters.length !== child.children.length) {
        return null;
      }
      entries.push({ kind: 'group', level, entries: monsters });
      continue;
    }
    return null;
  }
  return {
    name: (table.getAttribute('name') ?? '').trim(),
    entries
  };
}

function serializeMonsterOnlyTable(name: string, entries: Array<MonsterEntry | GroupEntry>): string {
  const serializeSpecial = (entry: MonsterEntry, indent: string): string[] => {
    const lines: string[] = [];
    if (!entry.special.trim() && Object.keys(entry.specialLinks).length === 0 && !entry.magicType.trim()) {
      return lines;
    }
    const attrs = entry.appendSpecials ? '' : ' append="false"';
    lines.push(`${indent}<special${attrs}>`);
    if (entry.special.trim()) {
      lines.push(`${indent}  <text>${escapeHtml(entry.special.trim())}</text>`);
    }
    for (const [id, link] of Object.entries(entry.specialLinks)) {
      const attrs = [`id="${escapeHtml(id)}"`];
      if (link.parameter.trim()) {
        attrs.push(`param="${escapeHtml(link.parameter.trim())}"`);
      }
      lines.push(`${indent}  <rule ${attrs.join(' ')}>${escapeHtml(link.text)}</rule>`);
    }
    if (entry.magicType.trim()) {
      lines.push(`${indent}  <magic id="${escapeHtml(entry.magicType.trim())}" level="${Math.max(0, entry.magicLevel)}" />`);
    }
    lines.push(`${indent}</special>`);
    return lines;
  };

  const serializeMonster = (entry: MonsterEntry, indent: string): string[] => {
    const number = entry.min === entry.max ? String(entry.min) : `${entry.min}-${entry.max}`;
    const attrs = [
      `id="${escapeHtml(entry.id)}"`,
      `number="${escapeHtml(number)}"`,
      `level="${Math.max(1, entry.level)}"`
    ];
    if (entry.ambiences.length > 0) {
      attrs.push(`ambiences="${escapeHtml(entry.ambiences.join(' '))}"`);
    }
    const lines = [`${indent}<monster ${attrs.join(' ')}>`];
    lines.push(...serializeSpecial(entry, `${indent}  `));
    lines.push(`${indent}</monster>`);
    return lines;
  };

  const lines = ['<?xml version="1.0"?>', '<tables>', `  <table name="${escapeHtml(name)}">`];
  for (const entry of entries) {
    if (entry.kind === 'monster') {
      lines.push(...serializeMonster(entry, '    '));
      continue;
    }
    lines.push(`    <group level="${Math.max(1, entry.level)}">`);
    for (const member of entry.entries) {
      lines.push(...serializeMonster(member, '      '));
    }
    lines.push('    </group>');
  }
  lines.push('  </table>', '</tables>');
  return lines.join('\n');
}

function summarizeMonsterCount(min: number, max: number): string {
  return min === max ? String(min) : `${min}-${max}`;
}

function monsterEntryLabel(entry: MonsterEntry): string {
  const monster = repository.monsters.get(entry.id);
  const name = monster?.name ?? entry.id;
  return `${name} (${entry.id}) x ${summarizeMonsterCount(entry.min, entry.max)}`;
}

function tableEncounterLabel(entry: MonsterEntry | GroupEntry, index: number): string {
  if (entry.kind === 'monster') {
    const ambiences = entry.ambiences.join(', ') || '-';
    return `${index + 1}. ${monsterEntryLabel(entry)} | L${entry.level} | ${ambiences}`;
  }
  const monsters = entry.entries.map((member) => monsterEntryLabel(member)).join(' + ');
  const ambiences = entry.entries[0]?.ambiences.join(', ') || '-';
  return `${index + 1}. ${monsters} | L${entry.level} | ${ambiences}`;
}

function availableEventItemsForTable(kind: EventTableKind, currentItemUid: string, currentIds: string[]): Array<{ id: string; label: string }> {
  const usedIds = new Set<string>();

  for (const item of loadUserContentItems()) {
    if (item.kind !== 'table' || item.uid === currentItemUid) {
      continue;
    }
    const parsed = parseEventOnlyTable(item.data.xml);
    if (!parsed || parsed.kind !== kind) {
      continue;
    }
    for (const eventId of parsed.eventIds) {
      usedIds.add(eventId);
    }
  }

  const selectedIds = new Set(currentIds);
  const eventItems = dashboardItemsByKind(eventTableUserContentKind(kind)) as Array<
    Extract<UserContentItem, { kind: 'dungeonEvent' | 'travelEvent' | 'settlementEvent' }>
  >;
  return eventItems
    .map((entry) => ({ id: entry.data.id, label: `${entry.data.name} (${entry.data.id})` }))
    .filter((entry) => selectedIds.has(entry.id) || !usedIds.has(entry.id))
    .sort((left, right) => left.label.localeCompare(right.label, undefined, { sensitivity: 'base' }));
}

function createBlankEventTableItem(kind: EventTableKind): Extract<UserContentItem, { kind: 'table' }> {
  const name =
    kind === 'travel'
      ? 'userdefined-travel-events-table'
      : kind === 'settlement'
      ? 'userdefined-settlement-events-table'
      : 'userdefined-dungeon-events-table';

  return {
    uid: createUserContentUid('table'),
    kind: 'table',
    mode: 'new',
    title: name,
    updatedAt: new Date().toISOString(),
    data: {
      name,
      kind,
      xml: serializeEventOnlyTable(name, kind, [])
    }
  };
}

function dashboardSourceOptions(kind: UserContentKind): Array<{ id: string; label: string }> {
  if (kind === 'dungeonCard') {
    return dungeonStore.loadCards().map((card) => ({ id: String(card.id), label: `${card.name} (#${card.id})` }));
  }
  if (kind === 'treasure') {
    return Array.from(repository.events.values())
      .filter((event) => event.treasure && !event.id.toLowerCase().includes('-objective-'))
      .map((event) => ({ id: event.id, label: `${event.name} (${event.id})` }));
  }
  if (kind === 'dungeonEvent') {
    return Array.from(repository.events.values())
      .filter((event) => !event.treasure)
      .map((event) => ({ id: event.id, label: `${event.name} (${event.id})` }));
  }
  if (kind === 'objectiveTreasure') {
    return Array.from(repository.events.values())
      .filter((event) => event.treasure && event.id.toLowerCase().includes('-objective-'))
      .map((event) => ({ id: event.id, label: `${event.name} (${event.id})` }));
  }
  if (kind === 'travelEvent') {
    return Array.from(repository.travelEvents.values()).map((event) => ({ id: event.id, label: `${event.name} (${event.id})` }));
  }
  if (kind === 'settlementEvent') {
    return Array.from(repository.settlementEvents.values()).map((event) => ({ id: event.id, label: `${event.name} (${event.id})` }));
  }
  if (kind === 'rule') {
    return Array.from(repository.rules.values()).map((rule) => ({ id: rule.id, label: `${rule.name} (${rule.id})` }));
  }
  if (kind === 'monster') {
    return Array.from(repository.monsters.values()).map((monster) => ({ id: monster.id, label: `${monster.name} (${monster.id})` }));
  }
  if (kind === 'objectiveRoomAdventure') {
    return dungeonStore
      .loadAllAdventures()
      .map((adventure) => ({
        id: `${adventure.objectiveRoomName}::${adventure.id}`,
        label: `${adventure.objectiveRoomName} - ${adventure.name} (${adventure.id})`
      }));
  }
  if (kind === 'warrior') {
    return Array.from(repository.warriors.values()).map((warrior) => ({
      id: warrior.id,
      label: `${warrior.name} (${warrior.id})`
    }));
  }
  if (kind === 'location') {
    return Array.from(repository.locations.values()).map((location) => ({
      id: location.id,
      label: `${location.name} (${location.id})`
    }));
  }
  return Array.from(repository.tables.values()).map((table) => ({ id: table.name, label: table.name }));
}

function createBlankDashboardItem(kind: UserContentKind): UserContentItem {
  const uid = createUserContentUid(kind);
  const updatedAt = new Date().toISOString();
  switch (kind) {
    case 'dungeonCard':
      return { uid, kind, mode: 'new', title: '', updatedAt, data: createDefaultDungeonCard(nextUserDungeonCardId()) };
    case 'treasure':
    case 'dungeonEvent':
    case 'objectiveTreasure':
    case 'travelEvent':
    case 'settlementEvent':
      return { uid, kind, mode: 'new', title: '', updatedAt, data: createDefaultEvent(kind) };
    case 'rule':
      return { uid, kind, mode: 'new', title: '', updatedAt, data: createDefaultRule() };
    case 'monster':
      return { uid, kind, mode: 'new', title: '', updatedAt, data: createDefaultMonster() };
    case 'table':
      return { uid, kind, mode: 'new', title: '', updatedAt, data: createDefaultTable() };
    case 'objectiveRoomAdventure':
      return { uid, kind, mode: 'new', title: '', updatedAt, data: createDefaultObjectiveRoomAdventure() };
    case 'warrior':
      return { uid, kind, mode: 'new', title: '', updatedAt, data: createDefaultWarrior() };
    case 'location':
      return { uid, kind, mode: 'new', title: '', updatedAt, data: createDefaultLocation() };
  }
}

function createModifiedDashboardItem(kind: UserContentKind, sourceId: string): UserContentItem | null {
  const uid = createUserContentUid(kind);
  const updatedAt = new Date().toISOString();
  switch (kind) {
    case 'dungeonCard': {
      const source = dungeonStore.loadCards().find((card) => String(card.id) === sourceId);
      return source ? { uid, kind, mode: 'modified', sourceId, title: source.name, updatedAt, data: mapDungeonCardToUserData(source) } : null;
    }
    case 'treasure':
    case 'dungeonEvent':
    case 'objectiveTreasure': {
      const source = Array.from(repository.events.values()).find((event) => event.id === sourceId);
      return source ? { uid, kind, mode: 'modified', sourceId, title: source.name, updatedAt, data: mapEventToUserData(source) } : null;
    }
    case 'travelEvent': {
      const source = Array.from(repository.travelEvents.values()).find((event) => event.id === sourceId);
      return source ? { uid, kind, mode: 'modified', sourceId, title: source.name, updatedAt, data: mapEventToUserData(source) } : null;
    }
    case 'settlementEvent': {
      const source = Array.from(repository.settlementEvents.values()).find((event) => event.id === sourceId);
      return source ? { uid, kind, mode: 'modified', sourceId, title: source.name, updatedAt, data: mapEventToUserData(source) } : null;
    }
    case 'rule': {
      const source = repository.rules.get(sourceId);
      return source ? { uid, kind, mode: 'modified', sourceId, title: source.name, updatedAt, data: mapRuleToUserData(source) } : null;
    }
    case 'monster': {
      const source = repository.monsters.get(sourceId);
      return source ? { uid, kind, mode: 'modified', sourceId, title: source.name, updatedAt, data: mapMonsterToUserData(source) } : null;
    }
    case 'table': {
      const source = repository.tables.get(sourceId);
      return source ? { uid, kind, mode: 'modified', sourceId, title: source.name, updatedAt, data: mapTableToUserData(source) } : null;
    }
    case 'objectiveRoomAdventure': {
      const [roomName, adventureId] = sourceId.split('::');
      const source = dungeonStore
        .loadAllAdventures()
        .find((adventure) => adventure.objectiveRoomName === roomName && adventure.id === adventureId);
      return source
        ? {
            uid,
            kind,
            mode: 'modified',
            sourceId,
            title: `${source.objectiveRoomName} - ${source.name}`,
            updatedAt,
            data: mapObjectiveRoomAdventureToUserData(source)
          }
        : null;
    }
    case 'warrior': {
      const source = repository.warriors.get(sourceId);
      return source ? { uid, kind, mode: 'modified', sourceId, title: source.name, updatedAt, data: mapWarriorToUserData(source) } : null;
    }
    case 'location': {
      const source = repository.locations.get(sourceId);
      return source ? { uid, kind, mode: 'modified', sourceId, title: source.name, updatedAt, data: mapLocationToUserData(source) } : null;
    }
  }
}

function downloadUserXml(item: UserContentItem): void {
  const xml = userContentItemXml(item);
  const blob = new Blob([xml], { type: 'application/xml;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  const label = item.kind === 'table' ? item.data.name : item.title || item.kind;
  anchor.href = url;
  anchor.download = `${label || 'user-content'}.xml`;
  anchor.click();
  URL.revokeObjectURL(url);
}

function contentDashboardSubtitle(item: UserContentItem): string {
  return item.mode === 'modified'
    ? t(settings.language, 'contentDashboard.mode.modified')
    : t(settings.language, 'contentDashboard.mode.new');
}

function renderDashboardTree(container: HTMLElement): void {
  const tree = container.querySelector<HTMLElement>('#contentDashboardTree');
  if (!tree) {
    return;
  }

  tree.innerHTML = DASHBOARD_CATEGORIES.map((category) => {
    const items = dashboardItemsByKind(category.kind);
    const selectedCreate = activeDashboardItemUid === `${DASHBOARD_CREATE_PREFIX}${category.kind}`;
    return `
      <section class="dashboard-tree-section">
        <div class="dashboard-tree-header ${selectedCreate ? 'selected' : ''}" data-create-kind="${category.kind}">
          <button type="button" class="dashboard-tree-label" data-create-kind="${category.kind}">
            ${t(settings.language, category.titleKey)}
          </button>
          <button type="button" class="dashboard-tree-add" data-create-kind="${category.kind}">+</button>
        </div>
        <ul class="dashboard-tree-list">
          ${items
            .map(
              (item) => `
                <li class="${item.uid === activeDashboardItemUid ? 'selected' : ''}" data-item-uid="${item.uid}" title="${contentDashboardSubtitle(item)}">
                  <span>${item.title}</span>
                  <small>${contentDashboardSubtitle(item)}</small>
                </li>
              `
            )
            .join('')}
        </ul>
      </section>
    `;
  }).join('');

  tree.querySelectorAll<HTMLElement>('[data-create-kind]').forEach((element) => {
    element.addEventListener('click', () => {
      activeDashboardItemUid = `${DASHBOARD_CREATE_PREFIX}${element.dataset.createKind as UserContentKind}`;
      dashboardDraftItem = null;
      renderContentDashboard(container);
    });
  });

  tree.querySelectorAll<HTMLElement>('[data-item-uid]').forEach((element) => {
    element.addEventListener('click', () => {
      activeDashboardItemUid = element.dataset.itemUid ?? null;
      dashboardDraftItem = null;
      renderContentDashboard(container);
    });
    element.addEventListener('dblclick', () => {
      activeDashboardItemUid = element.dataset.itemUid ?? null;
      dashboardDraftItem = null;
      renderContentDashboard(container);
    });
  });
}

function renderDashboardHome(editor: HTMLElement): void {
  editor.innerHTML = `
    <div class="dashboard-empty">
      <h2>${t(settings.language, 'contentDashboard.title')}</h2>
      <p>${t(settings.language, 'contentDashboard.description')}</p>
    </div>
  `;
}

function renderDashboardCreateSelector(container: HTMLElement, editor: HTMLElement, kind: UserContentKind): void {
  const options = dashboardSourceOptions(kind);
  const tableTypePicker =
    kind === 'table'
      ? `
      <div id="dashboardTableTypePicker" hidden>
        <p>${t(settings.language, 'contentDashboard.tableTypePrompt')}</p>
        <div class="dashboard-create-actions">
          <button type="button" data-table-kind="monster">${t(settings.language, 'contentDashboard.tableType.monsterEncounters')}</button>
          <button type="button" data-table-kind="dungeon">${t(settings.language, 'contentDashboard.tableType.dungeonEvents')}</button>
          <button type="button" data-table-kind="travel">${t(settings.language, 'contentDashboard.tableType.travelEvents')}</button>
          <button type="button" data-table-kind="settlement">${t(settings.language, 'contentDashboard.tableType.settlementEvents')}</button>
        </div>
      </div>
    `
      : '';
  editor.innerHTML = `
    <div class="dashboard-editor-shell">
      <h2>${t(settings.language, 'contentDashboard.createPromptTitle')}</h2>
      <p>${t(settings.language, 'contentDashboard.createPromptText')}</p>
      <div class="dashboard-create-actions">
        <button type="button" id="dashboardCreateNewBtn">${t(settings.language, 'contentDashboard.createNew')}</button>
        <button type="button" id="dashboardCreateModifyBtn">${t(settings.language, 'contentDashboard.createModify')}</button>
      </div>
      ${tableTypePicker}
      <div id="dashboardSourcePicker" hidden>
        <label>
          ${t(settings.language, 'contentDashboard.source')}
          <select id="dashboardSourceSelect">
            ${options.map((option) => `<option value="${option.id}">${option.label}</option>`).join('')}
          </select>
        </label>
        <button type="button" id="dashboardCreateFromSourceBtn">${t(settings.language, 'contentDashboard.openEditor')}</button>
      </div>
    </div>
  `;

  editor.querySelector<HTMLButtonElement>('#dashboardCreateNewBtn')?.addEventListener('click', () => {
    if (kind === 'table') {
      const picker = editor.querySelector<HTMLElement>('#dashboardTableTypePicker');
      if (picker) {
        picker.hidden = false;
      }
      return;
    }
    dashboardDraftItem = createBlankDashboardItem(kind);
    activeDashboardItemUid = 'draft';
    renderContentDashboard(container);
  });

  editor.querySelectorAll<HTMLButtonElement>('[data-table-kind]').forEach((button) => {
    button.addEventListener('click', () => {
      const tableKind = button.dataset.tableKind ?? 'monster';
      dashboardDraftItem =
        tableKind === 'dungeon' || tableKind === 'travel' || tableKind === 'settlement'
          ? createBlankEventTableItem(tableKind)
          : createBlankDashboardItem('table');
      activeDashboardItemUid = 'draft';
      renderContentDashboard(container);
    });
  });

  editor.querySelector<HTMLButtonElement>('#dashboardCreateModifyBtn')?.addEventListener('click', () => {
    const picker = editor.querySelector<HTMLElement>('#dashboardSourcePicker');
    if (picker) {
      picker.hidden = false;
    }
  });

  editor.querySelector<HTMLButtonElement>('#dashboardCreateFromSourceBtn')?.addEventListener('click', () => {
    const sourceId = editor.querySelector<HTMLSelectElement>('#dashboardSourceSelect')?.value ?? '';
    dashboardDraftItem = createModifiedDashboardItem(kind, sourceId);
    if (!dashboardDraftItem) {
      window.alert(t(settings.language, 'contentDashboard.sourceNotFound'));
      return;
    }
    activeDashboardItemUid = 'draft';
    renderContentDashboard(container);
  });
}

function bindDashboardCommonActions(container: HTMLElement, item: UserContentItem): void {
  container.querySelector<HTMLButtonElement>('#dashboardDeleteBtn')?.addEventListener('click', async () => {
    if (isDashboardDraftSelected()) {
      dashboardDraftItem = null;
      activeDashboardItemUid = null;
      renderContentDashboard(container);
      return;
    }
    deleteUserContentItem(item.uid);
    dashboardDraftItem = null;
    activeDashboardItemUid = null;
    await refreshRuntimeContent();
    renderContentDashboard(container);
  });

  container.querySelector<HTMLButtonElement>('#dashboardDownloadBtn')?.addEventListener('click', () => {
    downloadUserXml(item);
  });
}

function renderDashboardEditorShell(title: string, subtitle: string, body: string, xmlPreview: string): string {
  return `
    <form class="dashboard-editor-shell">
      <header class="dashboard-editor-header">
        <div>
          <h2>${title}</h2>
          <p>${subtitle}</p>
        </div>
        <div class="dashboard-editor-actions">
          <button type="button" id="dashboardDownloadBtn">${t(settings.language, 'contentDashboard.downloadXml')}</button>
          <button type="button" id="dashboardDeleteBtn">${t(settings.language, 'contentDashboard.delete')}</button>
          <button type="submit" id="dashboardSaveBtn">${t(settings.language, 'dialog.button.save')}</button>
        </div>
      </header>
      <div class="dashboard-editor-body">
        <div class="dashboard-form">${body}</div>
        <div class="dashboard-xml-preview">
          <label>${t(settings.language, 'contentDashboard.xmlPreview')}
            <textarea rows="18" readonly>${escapeHtml(xmlPreview)}</textarea>
          </label>
        </div>
      </div>
    </form>
  `;
}

function renderDungeonCardEditor(container: HTMLElement, item: Extract<UserContentItem, { kind: 'dungeonCard' }>): void {
  const editor = container.querySelector<HTMLElement>('#contentDashboardEditor');
  if (!editor) {
    return;
  }
  const data = item.data as UserDungeonCardData;
  editor.innerHTML = `
    <form class="dashboard-editor-shell">
      <header class="dashboard-editor-header">
        <div>
          <h2>${t(settings.language, 'contentDashboard.category.dungeonCard')}</h2>
          <p>${contentDashboardSubtitle(item)}</p>
        </div>
        <div class="dashboard-editor-actions">
          <button type="button" id="dashboardDownloadBtn">${t(settings.language, 'contentDashboard.downloadXml')}</button>
          <button type="button" id="dashboardDeleteBtn">${t(settings.language, 'contentDashboard.delete')}</button>
          <button type="submit" id="dashboardSaveBtn">${t(settings.language, 'dialog.button.save')}</button>
        </div>
      </header>
      <div class="dashboard-editor-body dungeon-card-editor-layout">
        <div class="dashboard-form">
          <label>${t(settings.language, 'contentDashboard.field.name')}<input id="ucName" value="${escapeHtml(data.name)}"></label>
          <label>${t(settings.language, 'contentDashboard.field.type')}
            <select id="ucType">
              <option value="DUNGEON_ROOM" ${data.type === 'DUNGEON_ROOM' ? 'selected' : ''}>${t(settings.language, 'dungeon.cardType.DUNGEON_ROOM')}</option>
              <option value="OBJECTIVE_ROOM" ${data.type === 'OBJECTIVE_ROOM' ? 'selected' : ''}>${t(settings.language, 'dungeon.cardType.OBJECTIVE_ROOM')}</option>
              <option value="CORRIDOR" ${data.type === 'CORRIDOR' ? 'selected' : ''}>${t(settings.language, 'dungeon.cardType.CORRIDOR')}</option>
              <option value="SPECIAL" ${data.type === 'SPECIAL' ? 'selected' : ''}>${t(settings.language, 'dungeon.cardType.SPECIAL')}</option>
            </select>
          </label>
          <label>${t(settings.language, 'contentDashboard.field.environment')}<input id="ucEnvironment" value="${escapeHtml(data.environment)}"></label>
          <div class="dashboard-tile-upload">
            <label>${t(settings.language, 'contentDashboard.field.tileImagePath')}<input id="ucTileImagePath" value="${escapeHtml(getTileAssetDisplayName(data.tileImagePath))}" readonly></label>
            <div class="dashboard-inline-actions">
              <button type="button" id="ucUploadTileBtn">${t(settings.language, 'contentDashboard.uploadTile')}</button>
              <input id="ucTileFile" type="file" accept="image/*" hidden>
            </div>
          </div>
          <label>${t(settings.language, 'contentDashboard.field.description')}<textarea id="ucDescription" rows="6">${escapeHtml(data.descriptionText)}</textarea></label>
          <label>${t(settings.language, 'contentDashboard.field.rules')}<textarea id="ucRules" rows="8">${escapeHtml(data.rulesText)}</textarea></label>
          <div class="dashboard-inline-fields">
            <label>${t(settings.language, 'contentDashboard.field.copyCount')}<input id="ucCopyCount" type="number" min="0" value="${data.copyCount}"></label>
            <label class="dashboard-checkbox dashboard-inline-checkbox"><input id="ucEnabled" type="checkbox" ${data.enabled ? 'checked' : ''}>${t(settings.language, 'contentDashboard.field.enabled')}</label>
          </div>
        </div>
        <div class="dashboard-preview-column">
          <section class="dashboard-card-preview">
            <h3>${t(settings.language, 'contentDashboard.cardPreview')}</h3>
            <canvas id="ucPreviewCanvas" width="847" height="1264"></canvas>
          </section>
          <section class="dashboard-xml-panel">
            <button type="button" id="ucToggleXmlBtn">${t(settings.language, 'contentDashboard.showXml')}</button>
            <div id="ucXmlPanel" hidden>
              <label>${t(settings.language, 'contentDashboard.xmlPreview')}
                <textarea id="ucXmlPreview" rows="18" readonly></textarea>
              </label>
            </div>
          </section>
        </div>
      </div>
    </form>
  `;

  bindDashboardCommonActions(container, item);
  const form = editor.querySelector<HTMLFormElement>('form');
  const previewCanvas = editor.querySelector<HTMLCanvasElement>('#ucPreviewCanvas');
  const xmlPreview = editor.querySelector<HTMLTextAreaElement>('#ucXmlPreview');
  const xmlPanel = editor.querySelector<HTMLElement>('#ucXmlPanel');
  const toggleXmlButton = editor.querySelector<HTMLButtonElement>('#ucToggleXmlBtn');
  const tilePathInput = editor.querySelector<HTMLInputElement>('#ucTileImagePath');
  const tileFileInput = editor.querySelector<HTMLInputElement>('#ucTileFile');

  const buildDraftCard = (): DungeonCard => ({
    id: data.id,
    name: editor.querySelector<HTMLInputElement>('#ucName')?.value ?? '',
    type: (editor.querySelector<HTMLSelectElement>('#ucType')?.value as DungeonCard['type']) ?? data.type,
    environment: editor.querySelector<HTMLInputElement>('#ucEnvironment')?.value ?? '',
    copyCount: Number.parseInt(editor.querySelector<HTMLInputElement>('#ucCopyCount')?.value ?? '0', 10) || 0,
    enabled: editor.querySelector<HTMLInputElement>('#ucEnabled')?.checked ?? true,
    tileImagePath: tilePathInput?.dataset.tilePath ?? data.tileImagePath,
    descriptionText: editor.querySelector<HTMLTextAreaElement>('#ucDescription')?.value ?? '',
    rulesText: editor.querySelector<HTMLTextAreaElement>('#ucRules')?.value ?? ''
  });

  const refreshCardEditorPreview = () => {
    const draftCard = buildDraftCard();
    if (previewCanvas) {
      renderDungeonCardToCanvasLocalized(previewCanvas, draftCard, settings.language).catch((error) => console.error(error));
    }
    if (xmlPreview) {
      xmlPreview.value = userContentItemXml({
        ...item,
        data: draftCard
      });
    }
  };

  toggleXmlButton?.addEventListener('click', () => {
    if (!xmlPanel) {
      return;
    }
    const nextHidden = !xmlPanel.hidden;
    xmlPanel.hidden = nextHidden;
    toggleXmlButton.textContent = nextHidden
      ? t(settings.language, 'contentDashboard.showXml')
      : t(settings.language, 'contentDashboard.hideXml');
  });

  editor.querySelector<HTMLButtonElement>('#ucUploadTileBtn')?.addEventListener('click', () => {
    tileFileInput?.click();
  });

  tileFileInput?.addEventListener('change', async () => {
    const file = tileFileInput.files?.[0];
    if (!file) {
      return;
    }
    const dataUrl = await readFileAsDataUrl(file);
    saveTileAsset(file.name, dataUrl);
    if (tilePathInput) {
      tilePathInput.value = file.name;
      tilePathInput.dataset.tilePath = file.name;
    }
    refreshCardEditorPreview();
  });

  form?.querySelectorAll<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>('input, textarea, select').forEach((field) => {
    if (field.id === 'ucTileFile') {
      return;
    }
    field.addEventListener('input', refreshCardEditorPreview);
    field.addEventListener('change', refreshCardEditorPreview);
  });

  if (tilePathInput) {
    tilePathInput.dataset.tilePath = data.tileImagePath;
  }
  refreshCardEditorPreview();

  form?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const draftCard = buildDraftCard();
    const nextItem: UserContentItem = {
      ...item,
      updatedAt: new Date().toISOString(),
      data: draftCard
    };
    upsertUserContentItem(nextItem);
    activeDashboardItemUid = nextItem.uid;
    dashboardDraftItem = null;
    await refreshRuntimeContent();
    renderContentDashboard(container);
  });
}

function renderEventEditor(
  container: HTMLElement,
  item: Extract<UserContentItem, { kind: 'dungeonEvent' | 'treasure' | 'objectiveTreasure' | 'travelEvent' | 'settlementEvent' }>
): void {
  const editor = container.querySelector<HTMLElement>('#contentDashboardEditor');
  if (!editor) {
    return;
  }
  const data = item.data as UserEventData;
  const isTravelLike = item.kind === 'travelEvent' || item.kind === 'settlementEvent';
  const isTreasure = item.kind === 'treasure' || item.kind === 'objectiveTreasure';

  if (isTreasure) {
    const userFlags = treasureUsersToFlags(data.users);
    editor.innerHTML = `
      <form class="dashboard-editor-shell">
        <header class="dashboard-editor-header">
          <div>
            <h2>${t(settings.language, DASHBOARD_CATEGORIES.find((entry) => entry.kind === item.kind)?.titleKey ?? 'contentDashboard.title')}</h2>
            <p>${contentDashboardSubtitle(item)}</p>
          </div>
          <div class="dashboard-editor-actions">
            <button type="button" id="dashboardDownloadBtn">${t(settings.language, 'contentDashboard.downloadXml')}</button>
            <button type="button" id="dashboardDeleteBtn">${t(settings.language, 'contentDashboard.delete')}</button>
            <button type="submit" id="dashboardSaveBtn">${t(settings.language, 'dialog.button.save')}</button>
          </div>
        </header>
        <div class="dashboard-editor-body treasure-editor-layout">
          <div class="dashboard-form">
            <label>${t(settings.language, 'contentDashboard.field.name')}<input id="ucName" value="${escapeHtml(data.name)}"></label>
            <label>${t(settings.language, 'contentDashboard.field.flavor')}<textarea id="ucFlavor" rows="4">${escapeHtml(data.flavor)}</textarea></label>
            <label>${t(settings.language, 'contentDashboard.field.rules')}<textarea id="ucRules" rows="8">${escapeHtml(data.rules)}</textarea></label>
            <label>${t(settings.language, 'contentDashboard.field.special')}<textarea id="ucSpecial" rows="4">${escapeHtml(data.special)}</textarea></label>
            <label>${t(settings.language, 'contentDashboard.field.goldValue')}<input id="ucGoldValue" value="${escapeHtml(data.goldValue)}"></label>
            <fieldset class="dashboard-checkbox-group">
              <legend>${t(settings.language, 'contentDashboard.field.users')}</legend>
              <label class="dashboard-checkbox"><input id="ucUserB" type="checkbox" ${userFlags.B ? 'checked' : ''}>${t(settings.language, 'card.treasure.user.barbarian')}</label>
              <label class="dashboard-checkbox"><input id="ucUserD" type="checkbox" ${userFlags.D ? 'checked' : ''}>${t(settings.language, 'card.treasure.user.dwarf')}</label>
              <label class="dashboard-checkbox"><input id="ucUserE" type="checkbox" ${userFlags.E ? 'checked' : ''}>${t(settings.language, 'card.treasure.user.elf')}</label>
              <label class="dashboard-checkbox"><input id="ucUserW" type="checkbox" ${userFlags.W ? 'checked' : ''}>${t(settings.language, 'card.treasure.user.wizard')}</label>
            </fieldset>
          </div>
          <div class="dashboard-preview-column">
            <section class="dashboard-card-preview treasure-card-preview">
              <h3>${t(settings.language, 'contentDashboard.cardPreview')}</h3>
              <div id="ucTreasurePreview"></div>
            </section>
            <section class="dashboard-xml-panel">
              <button type="button" id="ucToggleXmlBtn">${t(settings.language, 'contentDashboard.showXml')}</button>
              <div id="ucXmlPanel" hidden>
                <label>${t(settings.language, 'contentDashboard.xmlPreview')}
                  <textarea id="ucXmlPreview" rows="18" readonly></textarea>
                </label>
              </div>
            </section>
          </div>
        </div>
      </form>
    `;

    bindDashboardCommonActions(container, item);
    const form = editor.querySelector<HTMLFormElement>('form');
    const preview = editor.querySelector<HTMLElement>('#ucTreasurePreview');
    const xmlPreview = editor.querySelector<HTMLTextAreaElement>('#ucXmlPreview');
    const xmlPanel = editor.querySelector<HTMLElement>('#ucXmlPanel');
    const toggleXmlButton = editor.querySelector<HTMLButtonElement>('#ucToggleXmlBtn');

    const buildDraftEvent = (): UserEventData => {
      const flags = {
        B: editor.querySelector<HTMLInputElement>('#ucUserB')?.checked ?? false,
        D: editor.querySelector<HTMLInputElement>('#ucUserD')?.checked ?? false,
        E: editor.querySelector<HTMLInputElement>('#ucUserE')?.checked ?? false,
        W: editor.querySelector<HTMLInputElement>('#ucUserW')?.checked ?? false
      };
      return {
        ...data,
        name: editor.querySelector<HTMLInputElement>('#ucName')?.value ?? '',
        flavor: editor.querySelector<HTMLTextAreaElement>('#ucFlavor')?.value ?? '',
        rules: editor.querySelector<HTMLTextAreaElement>('#ucRules')?.value ?? '',
        special: editor.querySelector<HTMLTextAreaElement>('#ucSpecial')?.value ?? '',
        goldValue: editor.querySelector<HTMLInputElement>('#ucGoldValue')?.value ?? '',
        users: treasureUsersFromFlags(flags)
      };
    };

    const refreshTreasureEditorPreview = () => {
      const draftEvent = buildDraftEvent();
      if (preview) {
        preview.innerHTML = renderEventCard(
          {
            ...draftEvent,
            category: 'dungeon',
            treasure: true,
            id: data.id || (item.kind === 'objectiveTreasure' ? 'preview-objective-item' : 'preview-treasure-item')
          },
          settings.language
        );
        fitTreasureHeaderText(preview);
      }
      if (xmlPreview) {
        xmlPreview.value = userContentItemXml({
          ...item,
          data: draftEvent
        });
      }
    };

    toggleXmlButton?.addEventListener('click', () => {
      if (!xmlPanel) {
        return;
      }
      const nextHidden = !xmlPanel.hidden;
      xmlPanel.hidden = nextHidden;
      toggleXmlButton.textContent = nextHidden
        ? t(settings.language, 'contentDashboard.showXml')
        : t(settings.language, 'contentDashboard.hideXml');
    });

    form?.querySelectorAll<HTMLInputElement | HTMLTextAreaElement>('input, textarea').forEach((field) => {
      field.addEventListener('input', refreshTreasureEditorPreview);
      field.addEventListener('change', refreshTreasureEditorPreview);
    });

    refreshTreasureEditorPreview();

    form?.addEventListener('submit', async (event) => {
      event.preventDefault();
      const draftEvent = buildDraftEvent();
      const nextItem: UserContentItem = {
        ...item,
        updatedAt: new Date().toISOString(),
        data: draftEvent
      };
      upsertUserContentItem(nextItem);
      activeDashboardItemUid = nextItem.uid;
      dashboardDraftItem = null;
      await refreshRuntimeContent();
      renderContentDashboard(container);
    });
    return;
  }

  const eventCategory = item.kind === 'travelEvent' ? 'travel' : item.kind === 'settlementEvent' ? 'settlement' : 'dungeon';
  editor.innerHTML = `
    <form class="dashboard-editor-shell">
      <header class="dashboard-editor-header">
        <div>
          <h2>${t(settings.language, DASHBOARD_CATEGORIES.find((entry) => entry.kind === item.kind)?.titleKey ?? 'contentDashboard.title')}</h2>
          <p>${contentDashboardSubtitle(item)}</p>
        </div>
        <div class="dashboard-editor-actions">
          <button type="button" id="dashboardDownloadBtn">${t(settings.language, 'contentDashboard.downloadXml')}</button>
          <button type="button" id="dashboardDeleteBtn">${t(settings.language, 'contentDashboard.delete')}</button>
          <button type="submit" id="dashboardSaveBtn">${t(settings.language, 'dialog.button.save')}</button>
        </div>
      </header>
      <div class="dashboard-editor-body event-editor-layout">
        <div class="dashboard-form">
          <label>${t(settings.language, 'contentDashboard.field.name')}<input id="ucName" value="${escapeHtml(data.name)}"></label>
          ${isTravelLike ? '' : `<label>${t(settings.language, 'contentDashboard.field.flavor')}<textarea id="ucFlavor" rows="4">${escapeHtml(data.flavor)}</textarea></label>`}
          <label>${t(settings.language, 'contentDashboard.field.rules')}<textarea id="ucRules" rows="8">${escapeHtml(data.rules)}</textarea></label>
          ${isTravelLike ? '' : `<label>${t(settings.language, 'contentDashboard.field.special')}<textarea id="ucSpecial" rows="4">${escapeHtml(data.special)}</textarea></label>`}
        </div>
        <div class="dashboard-preview-column">
          <section class="dashboard-card-preview event-card-preview">
            <h3>${t(settings.language, 'contentDashboard.cardPreview')}</h3>
            <div id="ucEventPreview"></div>
          </section>
          <section class="dashboard-xml-panel">
            <button type="button" id="ucToggleXmlBtn">${t(settings.language, 'contentDashboard.showXml')}</button>
            <div id="ucXmlPanel" hidden>
              <label>${t(settings.language, 'contentDashboard.xmlPreview')}
                <textarea id="ucXmlPreview" rows="18" readonly></textarea>
              </label>
            </div>
          </section>
        </div>
      </div>
    </form>
  `;

  bindDashboardCommonActions(container, item);
  const form = editor.querySelector<HTMLFormElement>('form');
  const preview = editor.querySelector<HTMLElement>('#ucEventPreview');
  const xmlPreview = editor.querySelector<HTMLTextAreaElement>('#ucXmlPreview');
  const xmlPanel = editor.querySelector<HTMLElement>('#ucXmlPanel');
  const toggleXmlButton = editor.querySelector<HTMLButtonElement>('#ucToggleXmlBtn');

  const buildDraftEvent = (): UserEventData => ({
    ...data,
    name: editor.querySelector<HTMLInputElement>('#ucName')?.value ?? '',
    flavor: editor.querySelector<HTMLTextAreaElement>('#ucFlavor')?.value ?? '',
    rules: editor.querySelector<HTMLTextAreaElement>('#ucRules')?.value ?? '',
    special: editor.querySelector<HTMLTextAreaElement>('#ucSpecial')?.value ?? ''
  });

  const refreshEventEditorPreview = () => {
    const draftEvent = buildDraftEvent();
    if (preview) {
      preview.innerHTML = renderEventCard(
        {
          ...draftEvent,
          category: eventCategory,
          treasure: false,
          id: data.id || `preview-${eventCategory}-event`
        },
        settings.language
      );
    }
    if (xmlPreview) {
      xmlPreview.value = userContentItemXml({
        ...item,
        data: draftEvent
      });
    }
  };

  toggleXmlButton?.addEventListener('click', () => {
    if (!xmlPanel) {
      return;
    }
    const nextHidden = !xmlPanel.hidden;
    xmlPanel.hidden = nextHidden;
    toggleXmlButton.textContent = nextHidden
      ? t(settings.language, 'contentDashboard.showXml')
      : t(settings.language, 'contentDashboard.hideXml');
  });

  form?.querySelectorAll<HTMLInputElement | HTMLTextAreaElement>('input, textarea').forEach((field) => {
    field.addEventListener('input', refreshEventEditorPreview);
    field.addEventListener('change', refreshEventEditorPreview);
  });

  refreshEventEditorPreview();

  form?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const nextItem: UserContentItem = {
      ...item,
      updatedAt: new Date().toISOString(),
      data: buildDraftEvent()
    };
    upsertUserContentItem(nextItem);
    activeDashboardItemUid = nextItem.uid;
    dashboardDraftItem = null;
    await refreshRuntimeContent();
    renderContentDashboard(container);
  });
}

function renderRuleEditor(container: HTMLElement, item: Extract<UserContentItem, { kind: 'rule' }>): void {
  const editor = container.querySelector<HTMLElement>('#contentDashboardEditor');
  if (!editor) {
    return;
  }
  const rawData = item.data as Partial<UserRuleData>;
  const sourceRule = repository.rules.get(item.sourceId ?? rawData.id ?? '');
  const rawParameterNames = rawData.parameterNames as string[] | string | undefined;
  const parameterNames = Array.isArray(rawParameterNames)
    ? (rawParameterNames.length > 0 ? rawParameterNames : sourceRule?.parameterNames ?? [])
    : typeof rawParameterNames === 'string'
      ? (() => {
          const values = rawParameterNames
            .split(',')
            .map((value: string) => value.trim())
            .filter(Boolean);
          return values.length > 0 ? values : sourceRule?.parameterNames ?? [];
        })()
      : sourceRule?.parameterNames ?? [];
  const data: UserRuleData = {
    id: rawData.id ?? '',
    type: rawData.type === 'magic' ? 'magic' : 'rule',
    name: rawData.name ?? '',
    text: rawData.text ?? '',
    parameterName: rawData.parameterName || sourceRule?.parameterName || '',
    parameterNames,
    parameterFormat: rawData.parameterFormat || sourceRule?.parameterFormat || ''
  };
  editor.innerHTML = `
    <form class="dashboard-editor-shell">
      <header class="dashboard-editor-header">
        <div>
          <h2>${t(settings.language, 'contentDashboard.category.rule')}</h2>
          <p>${contentDashboardSubtitle(item)}</p>
        </div>
        <div class="dashboard-editor-actions">
          <button type="button" id="dashboardDownloadBtn">${t(settings.language, 'contentDashboard.downloadXml')}</button>
          <button type="button" id="dashboardDeleteBtn">${t(settings.language, 'contentDashboard.delete')}</button>
          <button type="submit" id="dashboardSaveBtn">${t(settings.language, 'dialog.button.save')}</button>
        </div>
      </header>
      <div class="dashboard-editor-body">
        <div class="dashboard-form">
          <label>${t(settings.language, 'contentDashboard.field.id')}<input id="ucId" value="${escapeHtml(data.id)}" readonly></label>
          <label>${t(settings.language, 'contentDashboard.field.ruleType')}
            <select id="ucType">
              <option value="rule" ${data.type === 'rule' ? 'selected' : ''}>rule</option>
              <option value="magic" ${data.type === 'magic' ? 'selected' : ''}>magic</option>
            </select>
          </label>
          <label>${t(settings.language, 'contentDashboard.field.name')}<input id="ucName" value="${escapeHtml(data.name)}"></label>
          <label>${t(settings.language, 'contentDashboard.field.parameterName')}<input id="ucParameterName" value="${escapeHtml(data.parameterName)}"></label>
          <label>${t(settings.language, 'contentDashboard.field.parameterNames')}<input id="ucParameterNames" value="${escapeHtml((data.parameterNames ?? []).join(', '))}"></label>
          <label>${t(settings.language, 'contentDashboard.field.parameterFormat')}<input id="ucParameterFormat" value="${escapeHtml(data.parameterFormat)}"></label>
          <label>${t(settings.language, 'contentDashboard.field.text')}<textarea id="ucText" rows="12">${escapeHtml(data.text)}</textarea></label>
        </div>
        <div class="dashboard-xml-panel">
          <button type="button" id="ucToggleXmlBtn">${t(settings.language, 'contentDashboard.showXml')}</button>
          <div id="ucXmlPanel" hidden>
            <label>${t(settings.language, 'contentDashboard.xmlPreview')}
              <textarea id="ucXmlPreview" rows="18" readonly></textarea>
            </label>
          </div>
        </div>
      </div>
    </form>
  `;

  bindDashboardCommonActions(container, item);
  const form = editor.querySelector<HTMLFormElement>('form');
  const xmlPreview = editor.querySelector<HTMLTextAreaElement>('#ucXmlPreview');
  const xmlPanel = editor.querySelector<HTMLElement>('#ucXmlPanel');
  const toggleXmlButton = editor.querySelector<HTMLButtonElement>('#ucToggleXmlBtn');

  const refreshRuleXmlPreview = () => {
    if (xmlPreview) {
      xmlPreview.value = userContentItemXml({
        ...item,
        data: {
          ...data,
          id: editor.querySelector<HTMLInputElement>('#ucId')?.value ?? '',
          type: (editor.querySelector<HTMLSelectElement>('#ucType')?.value as UserRuleData['type']) ?? 'rule',
          name: editor.querySelector<HTMLInputElement>('#ucName')?.value ?? '',
          parameterName: editor.querySelector<HTMLInputElement>('#ucParameterName')?.value ?? '',
          parameterNames: (editor.querySelector<HTMLInputElement>('#ucParameterNames')?.value ?? '')
            .split(',')
            .map((value) => value.trim())
            .filter(Boolean),
          parameterFormat: editor.querySelector<HTMLInputElement>('#ucParameterFormat')?.value ?? '',
          text: editor.querySelector<HTMLTextAreaElement>('#ucText')?.value ?? ''
        }
      });
    }
  };

  toggleXmlButton?.addEventListener('click', () => {
    if (!xmlPanel) {
      return;
    }
    const nextHidden = !xmlPanel.hidden;
    xmlPanel.hidden = nextHidden;
    toggleXmlButton.textContent = nextHidden
      ? t(settings.language, 'contentDashboard.showXml')
      : t(settings.language, 'contentDashboard.hideXml');
  });

  form?.querySelectorAll<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>('input, textarea, select').forEach((field) => {
    field.addEventListener('input', refreshRuleXmlPreview);
    field.addEventListener('change', refreshRuleXmlPreview);
  });

  refreshRuleXmlPreview();

  form?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const nextItem: UserContentItem = {
      ...item,
      updatedAt: new Date().toISOString(),
      data: {
        ...data,
        id: editor.querySelector<HTMLInputElement>('#ucId')?.value ?? '',
        type: (editor.querySelector<HTMLSelectElement>('#ucType')?.value as UserRuleData['type']) ?? 'rule',
        name: editor.querySelector<HTMLInputElement>('#ucName')?.value ?? '',
        parameterName: editor.querySelector<HTMLInputElement>('#ucParameterName')?.value ?? '',
        parameterNames: (editor.querySelector<HTMLInputElement>('#ucParameterNames')?.value ?? '')
          .split(',')
          .map((value) => value.trim())
          .filter(Boolean),
        parameterFormat: editor.querySelector<HTMLInputElement>('#ucParameterFormat')?.value ?? '',
        text: editor.querySelector<HTMLTextAreaElement>('#ucText')?.value ?? ''
      }
    };
    upsertUserContentItem(nextItem);
    activeDashboardItemUid = nextItem.uid;
    dashboardDraftItem = null;
    await refreshRuntimeContent();
    renderContentDashboard(container);
  });
}

function renderMonsterEditor(container: HTMLElement, item: Extract<UserContentItem, { kind: 'monster' }>): void {
  const editor = container.querySelector<HTMLElement>('#contentDashboardEditor');
  if (!editor) {
    return;
  }
  const rawData = item.data as Partial<UserMonsterData>;
  const data: UserMonsterData = {
    id: rawData.id ?? '',
    name: rawData.name ?? '',
    plural: rawData.plural ?? '',
    factions: Array.isArray(rawData.factions) ? rawData.factions : [],
    move: rawData.move ?? '',
    weaponskill: rawData.weaponskill ?? '',
    ballisticskill: rawData.ballisticskill ?? '',
    strength: rawData.strength ?? '',
    toughness: rawData.toughness ?? '',
    wounds: rawData.wounds ?? '',
    initiative: rawData.initiative ?? '',
    attacks: rawData.attacks ?? '',
    gold: rawData.gold ?? '',
    armor: rawData.armor ?? '',
    damage: rawData.damage ?? '1D6',
    special: rawData.special ?? '',
    specialLinks: (rawData.specialLinks as UserMonsterData['specialLinks']) ?? {},
    magicType: rawData.magicType ?? '',
    magicLevel: rawData.magicLevel ?? 0
  };
  const factionOptions = availableMonsterFactions();
  const ruleOptions = availableMonsterRules();
  const magicOptions = availableMagicRules();
  const damageOptions = ['S', ...Array.from({ length: 10 }, (_, index) => `${index + 1}D6`)];
  const ballisticSkillOptions = ['-', 'S', 'A', '1+', '2+', '3+', '4+', '5+', '6+'];
  const selectedFactions = [...data.factions];
  const selectedRuleLinks = Object.fromEntries(
    Object.entries(data.specialLinks).map(([id, link]) => {
      const rule = ruleOptions.find((entry) => entry.id === id);
      const normalizedLink =
        typeof link === 'string'
          ? { text: link, parameter: '', parameters: [] }
          : {
              text: link?.text ?? '',
              parameter: link?.parameter ?? '',
              parameters: link?.parameters ?? (link?.parameter ? [link.parameter] : [])
            };
      const parameters =
        normalizedLink.parameters.length > 0
          ? normalizedLink.parameters
          : ruleParameterLabels(rule).length > 0
            ? inferRuleParameters(rule!.name, normalizedLink.text, rule?.parameterFormat)
            : [];
      return [id, { text: normalizedLink.text, parameter: parameters[0] ?? normalizedLink.parameter, parameters }];
    })
  );

  editor.innerHTML = renderDashboardEditorShell(
    t(settings.language, 'contentDashboard.category.monster'),
    contentDashboardSubtitle(item),
    `
      <label>${t(settings.language, 'contentDashboard.field.name')}<input id="ucName" value="${escapeHtml(data.name)}"></label>
      <label>${t(settings.language, 'contentDashboard.field.plural')}<input id="ucPlural" value="${escapeHtml(data.plural)}"></label>
      <label>${t(settings.language, 'contentDashboard.field.factions')}<input id="ucFactions" value="${escapeHtml(joinCsv(selectedFactions))}" readonly></label>
      <div class="dashboard-inline-actions">
        <select id="ucFactionSelect">
          ${factionOptions.map((faction) => `<option value="${escapeHtml(faction)}">${escapeHtml(faction)}</option>`).join('')}
        </select>
        <button type="button" id="ucAddFactionBtn">+</button>
        <button type="button" id="ucRemoveFactionBtn">-</button>
      </div>
      <label>${t(settings.language, 'contentDashboard.field.move')}<input id="ucMove" value="${escapeHtml(data.move)}"></label>
      <label>${t(settings.language, 'contentDashboard.field.weaponSkill')}<input id="ucWeaponSkill" value="${escapeHtml(data.weaponskill)}"></label>
      <label>${t(settings.language, 'contentDashboard.field.ballisticSkill')}
        <select id="ucBallisticSkill">
          ${ballisticSkillOptions
            .map(
              (skill) =>
                `<option value="${skill}" ${data.ballisticskill === skill || (!data.ballisticskill && skill === '-') ? 'selected' : ''}>${skill}</option>`
            )
            .join('')}
        </select>
      </label>
      <label>${t(settings.language, 'contentDashboard.field.strength')}<input id="ucStrength" value="${escapeHtml(data.strength)}"></label>
      <label>${t(settings.language, 'contentDashboard.field.toughness')}<input id="ucToughness" value="${escapeHtml(data.toughness)}"></label>
      <label>${t(settings.language, 'contentDashboard.field.wounds')}<input id="ucWounds" value="${escapeHtml(data.wounds)}"></label>
      <label>${t(settings.language, 'contentDashboard.field.initiative')}<input id="ucInitiative" value="${escapeHtml(data.initiative)}"></label>
      <label>${t(settings.language, 'contentDashboard.field.attacks')}<input id="ucAttacks" value="${escapeHtml(data.attacks)}"></label>
      <label>${t(settings.language, 'contentDashboard.field.gold')}<input id="ucGold" value="${escapeHtml(data.gold)}"></label>
      <label>${t(settings.language, 'contentDashboard.field.armor')}<input id="ucArmor" value="${escapeHtml(data.armor)}"></label>
      <label>${t(settings.language, 'contentDashboard.field.damage')}
        <select id="ucDamage">
          ${damageOptions
            .map(
              (damage) =>
                `<option value="${damage}" ${data.damage === damage || (!data.damage && damage === '1D6') ? 'selected' : ''}>${damage}</option>`
            )
            .join('')}
        </select>
      </label>
      <label>${t(settings.language, 'contentDashboard.field.special')}<textarea id="ucSpecial" rows="5">${escapeHtml(data.special)}</textarea></label>
      <label>${t(settings.language, 'contentDashboard.field.specialLinks')}
        <select id="ucSpecialLinks" size="6">
          ${Object.entries(selectedRuleLinks)
            .map(([id, link]) => `<option value="${escapeHtml(id)}">${escapeHtml(formatRuleLinks({ [id]: link }))}</option>`)
            .join('')}
        </select>
      </label>
      <div class="dashboard-inline-actions">
        <select id="ucRuleSelect">
          ${ruleOptions.map((rule) => `<option value="${escapeHtml(rule.id)}">${escapeHtml(rule.name)} (${escapeHtml(rule.id)})</option>`).join('')}
        </select>
        <button type="button" id="ucAddRuleBtn">+</button>
        <button type="button" id="ucRemoveRuleBtn">-</button>
      </div>
      <div id="ucRuleParameters"></div>
      <label>${t(settings.language, 'contentDashboard.field.magicType')}
        <select id="ucMagicType">
          <option value=""></option>
          ${magicOptions.map((rule) => `<option value="${escapeHtml(rule.id)}" ${data.magicType === rule.id ? 'selected' : ''}>${escapeHtml(rule.name)}</option>`).join('')}
        </select>
      </label>
      <label>${t(settings.language, 'contentDashboard.field.magicLevel')}<input id="ucMagicLevel" type="number" min="0" value="${data.magicLevel}"></label>
    `,
    userContentItemXml(item)
  );

  bindDashboardCommonActions(container, item);
  const form = editor.querySelector<HTMLFormElement>('form');
  const factionsInput = editor.querySelector<HTMLInputElement>('#ucFactions');
  const ruleSelect = editor.querySelector<HTMLSelectElement>('#ucRuleSelect');
  const specialLinksInput = editor.querySelector<HTMLSelectElement>('#ucSpecialLinks');
  const ruleParametersContainer = editor.querySelector<HTMLElement>('#ucRuleParameters');
  const xmlPreview = editor.querySelector<HTMLTextAreaElement>('.dashboard-xml-preview textarea');
  let selectedLinkedRuleId = ruleSelect?.value ?? '';
  let selectedRuleDraftId = ruleSelect?.value ?? '';
  let selectedRuleDraftParameters: string[] = [];
  const currentRuleParameters = (): string[] =>
    Array.from(editor.querySelectorAll<HTMLInputElement>('.uc-rule-parameter'))
      .sort((a, b) => Number.parseInt(a.dataset.index ?? '0', 10) - Number.parseInt(b.dataset.index ?? '0', 10))
      .map((input) => input.value ?? '');
  const loadDraftParametersForRule = (ruleId: string): string[] => {
    const rule = ruleOptions.find((entry) => entry.id === ruleId);
    if (!rule) {
      return [];
    }
    const linkedRule = selectedRuleLinks[ruleId];
    if (!linkedRule) {
      return Array.from({ length: ruleParameterLabels(rule).length }, () => '');
    }
    const linkedParameters =
      linkedRule.parameters.length > 0
        ? linkedRule.parameters
        : linkedRule.parameter
          ? [linkedRule.parameter]
          : inferRuleParameters(rule.name, linkedRule.text, rule.parameterFormat);
    const expectedLength = ruleParameterLabels(rule).length;
    return Array.from({ length: expectedLength }, (_, index) => linkedParameters[index] ?? '');
  };
  const syncDraftRuleSelection = (ruleId: string) => {
    selectedRuleDraftId = ruleId;
    selectedRuleDraftParameters = loadDraftParametersForRule(ruleId);
  };
  const renderRuleParameterFields = () => {
    const ruleId = selectedRuleDraftId || ruleSelect?.value || '';
    const rule = ruleOptions.find((entry) => entry.id === ruleId);
    const labels = ruleParameterLabels(rule);
    if (ruleParametersContainer) {
      if (labels.length === 0) {
        ruleParametersContainer.innerHTML = '';
        return;
      }
      const heading = `<div class="dashboard-parameter-heading">${escapeHtml(
        t(settings.language, 'contentDashboard.field.ruleParameter')
      )}</div>`;
      const values = Array.from({ length: labels.length }, (_, index) => selectedRuleDraftParameters[index] ?? '');
      ruleParametersContainer.innerHTML =
        heading +
        labels
          .map(
            (label, index) =>
              `<label>${escapeHtml(label)}<input class="uc-rule-parameter" data-index="${index}" value="${escapeHtml(values[index] ?? '')}" placeholder="${escapeHtml(
                label || t(settings.language, 'contentDashboard.field.ruleParameter')
              )}"></label>`
          )
          .join('');
      ruleParametersContainer.querySelectorAll<HTMLInputElement>('.uc-rule-parameter').forEach((input) =>
        input.addEventListener('input', () => {
          const selectedRuleId = selectedRuleDraftId || ruleSelect?.value || '';
          const selectedRule = ruleOptions.find((entry) => entry.id === selectedRuleId);
          if (selectedRule && selectedRuleLinks[selectedRule.id]) {
            const parameters = currentRuleParameters();
            selectedRuleDraftParameters = [...parameters];
            selectedRuleLinks[selectedRule.id] = {
              text: buildSpecialRuleText(selectedRule.name, parameters, selectedRule.parameterFormat),
              parameter: parameters[0] ?? '',
              parameters
            };
            refreshSelections(false);
            return;
          }
          selectedRuleDraftParameters = currentRuleParameters();
        })
      );
    }
  };

  const refreshSelections = (rerenderParameters = true) => {
    if (factionsInput) {
      factionsInput.value = joinCsv(selectedFactions);
    }
    if (specialLinksInput) {
      const currentSelection = selectedLinkedRuleId && selectedRuleLinks[selectedLinkedRuleId] ? selectedLinkedRuleId : '';
      specialLinksInput.innerHTML = Object.entries(selectedRuleLinks)
        .map(([id, link]) => `<option value="${escapeHtml(id)}" ${currentSelection === id ? 'selected' : ''}>${escapeHtml(formatRuleLinks({ [id]: link }))}</option>`)
        .join('');
    }
    if (ruleSelect && selectedRuleDraftId) {
      ruleSelect.value = selectedRuleDraftId;
    }
    if (rerenderParameters) {
      renderRuleParameterFields();
    }
    if (xmlPreview) {
      xmlPreview.value = userContentItemXml({
        ...item,
        data: {
          ...data,
          name: editor.querySelector<HTMLInputElement>('#ucName')?.value ?? '',
          plural: editor.querySelector<HTMLInputElement>('#ucPlural')?.value ?? '',
          factions: [...selectedFactions],
          move: editor.querySelector<HTMLInputElement>('#ucMove')?.value ?? '',
          weaponskill: editor.querySelector<HTMLInputElement>('#ucWeaponSkill')?.value ?? '',
          ballisticskill: editor.querySelector<HTMLSelectElement>('#ucBallisticSkill')?.value ?? '-',
          strength: editor.querySelector<HTMLInputElement>('#ucStrength')?.value ?? '',
          toughness: editor.querySelector<HTMLInputElement>('#ucToughness')?.value ?? '',
          wounds: editor.querySelector<HTMLInputElement>('#ucWounds')?.value ?? '',
          initiative: editor.querySelector<HTMLInputElement>('#ucInitiative')?.value ?? '',
          attacks: editor.querySelector<HTMLInputElement>('#ucAttacks')?.value ?? '',
          gold: editor.querySelector<HTMLInputElement>('#ucGold')?.value ?? '',
          armor: editor.querySelector<HTMLInputElement>('#ucArmor')?.value ?? '',
          damage: editor.querySelector<HTMLSelectElement>('#ucDamage')?.value ?? '1D6',
          special: editor.querySelector<HTMLTextAreaElement>('#ucSpecial')?.value ?? '',
          specialLinks: { ...selectedRuleLinks },
          magicType: editor.querySelector<HTMLSelectElement>('#ucMagicType')?.value ?? '',
          magicLevel: Number.parseInt(editor.querySelector<HTMLInputElement>('#ucMagicLevel')?.value ?? '0', 10) || 0
        }
      });
    }
  };

  editor.querySelector<HTMLButtonElement>('#ucAddFactionBtn')?.addEventListener('click', () => {
    const value = editor.querySelector<HTMLSelectElement>('#ucFactionSelect')?.value ?? '';
    if (value && !selectedFactions.includes(value)) {
      selectedFactions.push(value);
      refreshSelections();
    }
  });

  editor.querySelector<HTMLButtonElement>('#ucRemoveFactionBtn')?.addEventListener('click', () => {
    const value = editor.querySelector<HTMLSelectElement>('#ucFactionSelect')?.value ?? '';
    const index = selectedFactions.indexOf(value);
    if (index >= 0) {
      selectedFactions.splice(index, 1);
      refreshSelections();
    }
  });

  editor.querySelector<HTMLButtonElement>('#ucAddRuleBtn')?.addEventListener('click', () => {
    const value = selectedRuleDraftId || ruleSelect?.value || '';
    const rule = ruleOptions.find((entry) => entry.id === value);
    if (rule) {
      const parameters = ruleParameterLabels(rule).length > 0 ? [...selectedRuleDraftParameters] : [];
      selectedRuleLinks[rule.id] = {
        text: buildSpecialRuleText(rule.name, parameters, rule.parameterFormat),
        parameter: parameters[0] ?? '',
        parameters
      };
      selectedLinkedRuleId = rule.id;
      refreshSelections();
    }
  });

  editor.querySelector<HTMLButtonElement>('#ucRemoveRuleBtn')?.addEventListener('click', () => {
    const value =
      specialLinksInput?.value ||
      selectedRuleDraftId ||
      ruleSelect?.value ||
      '';
    if (selectedRuleLinks[value]) {
      delete selectedRuleLinks[value];
      selectedLinkedRuleId = '';
      if (selectedRuleDraftId === value) {
        selectedRuleDraftParameters = loadDraftParametersForRule(selectedRuleDraftId);
      }
      refreshSelections();
    }
  });

  ruleSelect?.addEventListener('change', () => {
    const ruleId = ruleSelect.value ?? '';
    syncDraftRuleSelection(ruleId);
    selectedLinkedRuleId = selectedRuleLinks[ruleId] ? ruleId : '';
    renderRuleParameterFields();
    refreshSelections(false);
  });
  specialLinksInput?.addEventListener('change', () => {
    const ruleId = specialLinksInput.value;
    selectedLinkedRuleId = ruleId;
    if (ruleSelect && ruleId) {
      ruleSelect.value = ruleId;
    }
    syncDraftRuleSelection(ruleId);
    renderRuleParameterFields();
    refreshSelections(false);
  });

  form?.querySelectorAll<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>('input, textarea, select').forEach((field) => {
    if (field.classList.contains('uc-rule-parameter') || field.id === 'ucRuleSelect' || field.id === 'ucSpecialLinks') {
      return;
    }
    field.addEventListener('input', () => refreshSelections());
    field.addEventListener('change', () => refreshSelections());
  });

  syncDraftRuleSelection(selectedRuleDraftId);
  renderRuleParameterFields();
  refreshSelections(false);

  form?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const nextItem: UserContentItem = {
      ...item,
      updatedAt: new Date().toISOString(),
      data: {
        ...data,
        name: editor.querySelector<HTMLInputElement>('#ucName')?.value ?? '',
        plural: editor.querySelector<HTMLInputElement>('#ucPlural')?.value ?? '',
        factions: [...selectedFactions],
        move: editor.querySelector<HTMLInputElement>('#ucMove')?.value ?? '',
        weaponskill: editor.querySelector<HTMLInputElement>('#ucWeaponSkill')?.value ?? '',
        ballisticskill: editor.querySelector<HTMLSelectElement>('#ucBallisticSkill')?.value ?? '-',
        strength: editor.querySelector<HTMLInputElement>('#ucStrength')?.value ?? '',
        toughness: editor.querySelector<HTMLInputElement>('#ucToughness')?.value ?? '',
        wounds: editor.querySelector<HTMLInputElement>('#ucWounds')?.value ?? '',
        initiative: editor.querySelector<HTMLInputElement>('#ucInitiative')?.value ?? '',
        attacks: editor.querySelector<HTMLInputElement>('#ucAttacks')?.value ?? '',
        gold: editor.querySelector<HTMLInputElement>('#ucGold')?.value ?? '',
        armor: editor.querySelector<HTMLInputElement>('#ucArmor')?.value ?? '',
        damage: editor.querySelector<HTMLSelectElement>('#ucDamage')?.value ?? '1D6',
        special: editor.querySelector<HTMLTextAreaElement>('#ucSpecial')?.value ?? '',
        specialLinks: { ...selectedRuleLinks },
        magicType: editor.querySelector<HTMLSelectElement>('#ucMagicType')?.value ?? '',
        magicLevel: Number.parseInt(editor.querySelector<HTMLInputElement>('#ucMagicLevel')?.value ?? '0', 10) || 0
      }
    };
    upsertUserContentItem(nextItem);
    activeDashboardItemUid = nextItem.uid;
    dashboardDraftItem = null;
    await refreshRuntimeContent();
    renderContentDashboard(container);
  });
}

function renderTableEditor(container: HTMLElement, item: Extract<UserContentItem, { kind: 'table' }>): void {
  const editor = container.querySelector<HTMLElement>('#contentDashboardEditor');
  if (!editor) {
    return;
  }
  const data = item.data as UserTableData;
  const parsedEventTable = parseEventOnlyTable(data.xml);

  if (parsedEventTable) {
    const renderEventTableEditor = (): void => {
      const availableItems = availableEventItemsForTable(parsedEventTable.kind, item.uid, parsedEventTable.eventIds);
      const selectedItemId = editor.querySelector<HTMLSelectElement>('#ucSelectedEventIds')?.value ?? parsedEventTable.eventIds[0] ?? '';
      const selectedEventIds = selectedItemId && parsedEventTable.eventIds.includes(selectedItemId)
        ? parsedEventTable.eventIds
        : parsedEventTable.eventIds;
      const availableOptions = availableItems
        .map((entry) => `<option value="${escapeHtml(entry.id)}">${escapeHtml(entry.label)}</option>`)
        .join('');
      const selectedOptions = selectedEventIds
        .map((eventId) => {
          const available = availableItems.find((entry) => entry.id === eventId);
          const label = available?.label ?? eventId;
          return `<option value="${escapeHtml(eventId)}">${escapeHtml(label)}</option>`;
        })
        .join('');
      const xmlPreview = serializeEventOnlyTable(
        parsedEventTable.name,
        parsedEventTable.kind,
        selectedEventIds
      );

      editor.innerHTML = `
        <form class="dashboard-editor-shell">
          <header class="dashboard-editor-header">
            <div>
              <h2>${t(settings.language, 'contentDashboard.category.table')}</h2>
              <p>${contentDashboardSubtitle(item)}</p>
            </div>
            <div class="dashboard-editor-actions">
              <button type="button" id="dashboardDownloadBtn">${t(settings.language, 'contentDashboard.downloadXml')}</button>
              <button type="button" id="dashboardDeleteBtn">${t(settings.language, 'contentDashboard.delete')}</button>
              <button type="submit" id="dashboardSaveBtn">${t(settings.language, 'dialog.button.save')}</button>
            </div>
          </header>
          <div class="dashboard-editor-body event-table-editor-layout">
            <div class="dashboard-form">
              <label>${t(settings.language, 'contentDashboard.field.name')}
                <input id="ucTableName" value="${escapeHtml(parsedEventTable.name)}">
              </label>
              <label>${t(settings.language, 'contentDashboard.field.type')}
                <input value="${escapeHtml(t(settings.language, `contentDashboard.tableType.${parsedEventTable.kind === 'dungeon' ? 'dungeonEvents' : parsedEventTable.kind === 'travel' ? 'travelEvents' : 'settlementEvents'}`))}" readonly>
              </label>
              <div class="dashboard-table-event-picker">
                <label>${t(settings.language, 'contentDashboard.field.availableEvents')}
                  <select id="ucAvailableEventId">${availableOptions}</select>
                </label>
                <div class="dashboard-inline-actions dashboard-table-event-actions">
                  <button type="button" id="ucAddEventBtn">+</button>
                  <button type="button" id="ucRemoveEventBtn">-</button>
                </div>
              </div>
              <label>${t(settings.language, 'contentDashboard.field.selectedEvents')}
                <select id="ucSelectedEventIds" size="12">${selectedOptions}</select>
              </label>
            </div>
            <div class="dashboard-preview-column">
              <section class="dashboard-xml-panel">
                <button type="button" id="ucToggleXmlBtn">${t(settings.language, 'contentDashboard.showXml')}</button>
                <div id="ucXmlPanel" hidden>
                  <label>${t(settings.language, 'contentDashboard.xmlPreview')}
                    <textarea id="ucXmlPreview" rows="18" readonly>${escapeHtml(xmlPreview)}</textarea>
                  </label>
                </div>
              </section>
            </div>
          </div>
        </form>
      `;

      bindDashboardCommonActions(container, item);

      const xmlPanel = editor.querySelector<HTMLElement>('#ucXmlPanel');
      const toggleXmlBtn = editor.querySelector<HTMLButtonElement>('#ucToggleXmlBtn');
      toggleXmlBtn?.addEventListener('click', () => {
        const hidden = !(xmlPanel?.hidden ?? true);
        if (xmlPanel) {
          xmlPanel.hidden = hidden;
        }
        if (toggleXmlBtn) {
          toggleXmlBtn.textContent = t(settings.language, hidden ? 'contentDashboard.showXml' : 'contentDashboard.hideXml');
        }
      });

      editor.querySelector<HTMLButtonElement>('#ucAddEventBtn')?.addEventListener('click', () => {
        const eventId = editor.querySelector<HTMLSelectElement>('#ucAvailableEventId')?.value ?? '';
        if (!eventId || parsedEventTable.eventIds.includes(eventId)) {
          return;
        }
        parsedEventTable.eventIds = [...parsedEventTable.eventIds, eventId];
        renderEventTableEditor();
      });

      editor.querySelector<HTMLButtonElement>('#ucRemoveEventBtn')?.addEventListener('click', () => {
        const eventId = editor.querySelector<HTMLSelectElement>('#ucSelectedEventIds')?.value ?? '';
        if (!eventId) {
          return;
        }
        parsedEventTable.eventIds = parsedEventTable.eventIds.filter((entry) => entry !== eventId);
        renderEventTableEditor();
      });

      editor.querySelector<HTMLInputElement>('#ucTableName')?.addEventListener('input', (event) => {
        parsedEventTable.name = (event.currentTarget as HTMLInputElement).value;
        const preview = editor.querySelector<HTMLTextAreaElement>('#ucXmlPreview');
        if (preview) {
          preview.value = serializeEventOnlyTable(parsedEventTable.name, parsedEventTable.kind, parsedEventTable.eventIds);
        }
      });

      editor.querySelector<HTMLFormElement>('form')?.addEventListener('submit', async (event) => {
        event.preventDefault();
        const tableName = editor.querySelector<HTMLInputElement>('#ucTableName')?.value.trim() ?? '';
        if (!tableName) {
          window.alert(t(settings.language, 'dialog.tableEditor.invalidXml'));
          return;
        }
        if (item.mode === 'new' && !tableName.toLowerCase().startsWith('userdefined-')) {
          window.alert(t(settings.language, 'contentDashboard.tablePrefixError'));
          return;
        }
        const xml = serializeEventOnlyTable(tableName, parsedEventTable.kind, parsedEventTable.eventIds);
        const metadata = parseTableMetadata(xml);
        if (!metadata) {
          window.alert(t(settings.language, 'dialog.tableEditor.invalidXml'));
          return;
        }
        const nextItem: UserContentItem = {
          ...item,
          title: metadata.name,
          updatedAt: new Date().toISOString(),
          data: {
            name: metadata.name,
            kind: metadata.kind,
            xml
          }
        };
        upsertUserContentItem(nextItem);
        activeDashboardItemUid = nextItem.uid;
        dashboardDraftItem = null;
        await refreshRuntimeContent();
        renderContentDashboard(container);
      });
    };

    renderEventTableEditor();
    return;
  }

  const parsedMonsterTable = parseMonsterOnlyTable(data.xml);
  if (parsedMonsterTable) {
    const state = {
      name: parsedMonsterTable.name,
      entries: [...parsedMonsterTable.entries] as Array<MonsterEntry | GroupEntry>,
      draftMembers: [] as MonsterEntry[],
      draftLevel: 1,
      draftAmbiences: [] as string[]
    };

    const renderMonsterTableEditor = (): void => {
      const monsterOptions = Array.from(repository.monsters.values())
        .sort((left, right) => left.name.localeCompare(right.name, undefined, { sensitivity: 'base' }))
        .map((monster) => `<option value="${escapeHtml(monster.id)}">${escapeHtml(`${monster.name} (${monster.id})`)}</option>`)
        .join('');
      const ambienceOptions = getAdventureAmbiences(settings.language)
        .filter((ambience) => !state.draftAmbiences.includes(ambience.value))
        .map((ambience) => `<option value="${escapeHtml(ambience.value)}">${escapeHtml(ambience.label)}</option>`)
        .join('');
      const draftMonsterOptions = state.draftMembers
        .map((entry, index) => `<option value="${index}">${escapeHtml(monsterEntryLabel(entry))}</option>`)
        .join('');
      const draftAmbienceOptions = state.draftAmbiences
        .map((ambience) => {
          const label = getAdventureAmbiences(settings.language).find((entry) => entry.value === ambience)?.label ?? ambience;
          return `<option value="${escapeHtml(ambience)}">${escapeHtml(label)}</option>`;
        })
        .join('');
      const encounterOptions = state.entries
        .map((entry, index) => `<option value="${index}">${escapeHtml(tableEncounterLabel(entry, index))}</option>`)
        .join('');
      const xmlPreview = serializeMonsterOnlyTable(state.name, state.entries);

      editor.innerHTML = `
        <form class="dashboard-editor-shell">
          <header class="dashboard-editor-header">
            <div>
              <h2>${t(settings.language, 'contentDashboard.category.table')}</h2>
              <p>${contentDashboardSubtitle(item)}</p>
            </div>
            <div class="dashboard-editor-actions">
              <button type="button" id="dashboardDownloadBtn">${t(settings.language, 'contentDashboard.downloadXml')}</button>
              <button type="button" id="dashboardDeleteBtn">${t(settings.language, 'contentDashboard.delete')}</button>
              <button type="submit" id="dashboardSaveBtn">${t(settings.language, 'dialog.button.save')}</button>
            </div>
          </header>
          <div class="dashboard-editor-body monster-table-editor-layout">
            <div class="dashboard-form">
              <label>${t(settings.language, 'contentDashboard.field.name')}
                <input id="ucTableName" value="${escapeHtml(state.name)}">
              </label>
              <label>${t(settings.language, 'contentDashboard.field.monsterType')}
                <select id="ucEncounterMonsterId">${monsterOptions}</select>
              </label>
              <div class="dashboard-inline-fields">
                <label>${t(settings.language, 'contentDashboard.field.monsterMin')}
                  <input id="ucEncounterMin" type="number" min="1" value="1">
                </label>
                <label>${t(settings.language, 'contentDashboard.field.monsterMax')}
                  <input id="ucEncounterMax" type="number" min="1" value="1">
                </label>
              </div>
              <div class="dashboard-inline-actions">
                <button type="button" id="ucAddMonsterToEncounterBtn">${t(settings.language, 'contentDashboard.addMonsterToEncounter')}</button>
                <button type="button" id="ucRemoveMonsterFromEncounterBtn">${t(settings.language, 'contentDashboard.removeMonsterFromEncounter')}</button>
              </div>
              <label>${t(settings.language, 'contentDashboard.field.encounterMonsters')}
                <select id="ucEncounterMonsterList" size="6">${draftMonsterOptions}</select>
              </label>
              <label>${t(settings.language, 'contentDashboard.field.encounterLevel')}
                <select id="ucEncounterLevel">
                  ${Array.from({ length: 10 }, (_, index) => index + 1)
                    .map((level) => `<option value="${level}" ${level === state.draftLevel ? 'selected' : ''}>${level}</option>`)
                    .join('')}
                </select>
              </label>
              <div class="dashboard-table-event-picker">
                <label>${t(settings.language, 'contentDashboard.field.availableAmbiences')}
                  <select id="ucEncounterAmbience">${ambienceOptions}</select>
                </label>
                <div class="dashboard-inline-actions dashboard-table-event-actions">
                  <button type="button" id="ucAddAmbienceBtn">+</button>
                  <button type="button" id="ucRemoveAmbienceBtn">-</button>
                </div>
              </div>
              <label>${t(settings.language, 'contentDashboard.field.encounterAmbiences')}
                <select id="ucEncounterAmbienceList" size="5">${draftAmbienceOptions}</select>
              </label>
              <div class="dashboard-inline-actions">
                <button type="button" id="ucAddEncounterToTableBtn">${t(settings.language, 'contentDashboard.addEncounterToTable')}</button>
                <button type="button" id="ucRemoveEncounterFromTableBtn">${t(settings.language, 'contentDashboard.removeEncounterFromTable')}</button>
              </div>
              <label>${t(settings.language, 'contentDashboard.field.tableEncounters')}
                <select id="ucTableEncounterList" size="10">${encounterOptions}</select>
              </label>
            </div>
            <div class="dashboard-preview-column">
              <section class="dashboard-xml-panel">
                <button type="button" id="ucToggleXmlBtn">${t(settings.language, 'contentDashboard.showXml')}</button>
                <div id="ucXmlPanel" hidden>
                  <label>${t(settings.language, 'contentDashboard.xmlPreview')}
                    <textarea id="ucXmlPreview" rows="18" readonly>${escapeHtml(xmlPreview)}</textarea>
                  </label>
                </div>
              </section>
            </div>
          </div>
        </form>
      `;

      bindDashboardCommonActions(container, item);

      const xmlPanel = editor.querySelector<HTMLElement>('#ucXmlPanel');
      const toggleXmlBtn = editor.querySelector<HTMLButtonElement>('#ucToggleXmlBtn');
      toggleXmlBtn?.addEventListener('click', () => {
        const hidden = !(xmlPanel?.hidden ?? true);
        if (xmlPanel) {
          xmlPanel.hidden = hidden;
        }
        if (toggleXmlBtn) {
          toggleXmlBtn.textContent = t(settings.language, hidden ? 'contentDashboard.showXml' : 'contentDashboard.hideXml');
        }
      });

      editor.querySelector<HTMLInputElement>('#ucTableName')?.addEventListener('input', (event) => {
        state.name = (event.currentTarget as HTMLInputElement).value;
        const preview = editor.querySelector<HTMLTextAreaElement>('#ucXmlPreview');
        if (preview) {
          preview.value = serializeMonsterOnlyTable(state.name, state.entries);
        }
      });

      editor.querySelector<HTMLSelectElement>('#ucEncounterLevel')?.addEventListener('change', (event) => {
        state.draftLevel = Math.max(1, Math.min(10, Number.parseInt((event.currentTarget as HTMLSelectElement).value, 10) || 1));
      });

      editor.querySelector<HTMLButtonElement>('#ucAddMonsterToEncounterBtn')?.addEventListener('click', () => {
        const monsterId = editor.querySelector<HTMLSelectElement>('#ucEncounterMonsterId')?.value ?? '';
        const min = Math.max(1, Number.parseInt(editor.querySelector<HTMLInputElement>('#ucEncounterMin')?.value ?? '1', 10) || 1);
        const maxRaw = Math.max(1, Number.parseInt(editor.querySelector<HTMLInputElement>('#ucEncounterMax')?.value ?? '1', 10) || 1);
        const max = Math.max(min, maxRaw);
        if (!monsterId) {
          return;
        }
        state.draftMembers.push({
          kind: 'monster',
          id: monsterId,
          level: state.draftLevel,
          min,
          max,
          ambiences: [...state.draftAmbiences],
          special: '',
          specialLinks: {},
          magicType: '',
          magicLevel: 0,
          appendSpecials: true
        });
        renderMonsterTableEditor();
      });

      editor.querySelector<HTMLButtonElement>('#ucRemoveMonsterFromEncounterBtn')?.addEventListener('click', () => {
        const index = Number.parseInt(editor.querySelector<HTMLSelectElement>('#ucEncounterMonsterList')?.value ?? '-1', 10);
        if (index < 0 || index >= state.draftMembers.length) {
          return;
        }
        state.draftMembers.splice(index, 1);
        renderMonsterTableEditor();
      });

      editor.querySelector<HTMLButtonElement>('#ucAddAmbienceBtn')?.addEventListener('click', () => {
        const ambience = editor.querySelector<HTMLSelectElement>('#ucEncounterAmbience')?.value ?? '';
        if (!ambience || state.draftAmbiences.includes(ambience)) {
          return;
        }
        state.draftAmbiences.push(ambience);
        renderMonsterTableEditor();
      });

      editor.querySelector<HTMLButtonElement>('#ucRemoveAmbienceBtn')?.addEventListener('click', () => {
        const ambience = editor.querySelector<HTMLSelectElement>('#ucEncounterAmbienceList')?.value ?? '';
        if (!ambience) {
          return;
        }
        state.draftAmbiences = state.draftAmbiences.filter((entry) => entry !== ambience);
        renderMonsterTableEditor();
      });

      editor.querySelector<HTMLButtonElement>('#ucAddEncounterToTableBtn')?.addEventListener('click', () => {
        if (state.draftMembers.length === 0) {
          return;
        }
        const level = Math.max(1, Math.min(10, Number.parseInt(editor.querySelector<HTMLSelectElement>('#ucEncounterLevel')?.value ?? '1', 10) || 1));
        const members = state.draftMembers.map((member) => ({
          ...member,
          level,
          ambiences: [...state.draftAmbiences]
        }));
        state.entries.push(
          members.length === 1
            ? members[0]
            : {
                kind: 'group',
                level,
                entries: members
              }
        );
        state.draftMembers = [];
        state.draftAmbiences = [];
        state.draftLevel = 1;
        renderMonsterTableEditor();
      });

      editor.querySelector<HTMLButtonElement>('#ucRemoveEncounterFromTableBtn')?.addEventListener('click', () => {
        const index = Number.parseInt(editor.querySelector<HTMLSelectElement>('#ucTableEncounterList')?.value ?? '-1', 10);
        if (index < 0 || index >= state.entries.length) {
          return;
        }
        state.entries.splice(index, 1);
        renderMonsterTableEditor();
      });

      editor.querySelector<HTMLFormElement>('form')?.addEventListener('submit', async (event) => {
        event.preventDefault();
        const tableName = editor.querySelector<HTMLInputElement>('#ucTableName')?.value.trim() ?? '';
        if (!tableName) {
          window.alert(t(settings.language, 'dialog.tableEditor.invalidXml'));
          return;
        }
        if (item.mode === 'new' && !tableName.toLowerCase().startsWith('userdefined-')) {
          window.alert(t(settings.language, 'contentDashboard.tablePrefixError'));
          return;
        }
        const xml = serializeMonsterOnlyTable(tableName, state.entries);
        const metadata = parseTableMetadata(xml);
        if (!metadata) {
          window.alert(t(settings.language, 'dialog.tableEditor.invalidXml'));
          return;
        }
        const nextItem: UserContentItem = {
          ...item,
          title: metadata.name,
          updatedAt: new Date().toISOString(),
          data: {
            name: metadata.name,
            kind: metadata.kind,
            xml
          }
        };
        upsertUserContentItem(nextItem);
        activeDashboardItemUid = nextItem.uid;
        dashboardDraftItem = null;
        await refreshRuntimeContent();
        renderContentDashboard(container);
      });
    };

    renderMonsterTableEditor();
    return;
  }

  editor.innerHTML = renderDashboardEditorShell(
    t(settings.language, 'contentDashboard.category.table'),
    contentDashboardSubtitle(item),
    `
      <label>${t(settings.language, 'contentDashboard.field.tableXml')}
        <textarea id="ucTableXml" rows="22">${escapeHtml(data.xml)}</textarea>
      </label>
    `,
    userContentItemXml(item)
  );

  bindDashboardCommonActions(container, item);
  editor.querySelector<HTMLFormElement>('form')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const xml = editor.querySelector<HTMLTextAreaElement>('#ucTableXml')?.value ?? '';
    const metadata = parseTableMetadata(xml);
    if (!metadata) {
      window.alert(t(settings.language, 'dialog.tableEditor.invalidXml'));
      return;
    }
    if (item.mode === 'new' && !metadata.name.trim().toLowerCase().startsWith('userdefined-')) {
      window.alert(t(settings.language, 'contentDashboard.tablePrefixError'));
      return;
    }
    const nextItem: UserContentItem = {
      ...item,
      title: metadata.name,
      updatedAt: new Date().toISOString(),
      data: {
        name: metadata.name,
        kind: metadata.kind,
        xml
      }
    };
    upsertUserContentItem(nextItem);
    activeDashboardItemUid = nextItem.uid;
    dashboardDraftItem = null;
    await refreshRuntimeContent();
    renderContentDashboard(container);
  });
}

function renderObjectiveRoomAdventureEditor(
  container: HTMLElement,
  item: Extract<UserContentItem, { kind: 'objectiveRoomAdventure' }>
): void {
  const editor = container.querySelector<HTMLElement>('#contentDashboardEditor');
  if (!editor) {
    return;
  }
  const data = item.data as UserObjectiveRoomAdventureData;
  const objectiveRooms = availableObjectiveRoomNames();
  const hasCurrent = data.objectiveRoomName.trim() && objectiveRooms.includes(data.objectiveRoomName);
  const roomOptions = [
    ...objectiveRooms.map(
      (room) => `<option value="${escapeHtml(room)}" ${room === data.objectiveRoomName ? 'selected' : ''}>${escapeHtml(room)}</option>`
    ),
    ...(!hasCurrent && data.objectiveRoomName.trim()
      ? [`<option value="${escapeHtml(data.objectiveRoomName)}" selected>${escapeHtml(data.objectiveRoomName)}</option>`]
      : [])
  ].join('');

  editor.innerHTML = `
    <form class="dashboard-editor-shell">
      <header class="dashboard-editor-header">
        <div>
          <h2>${t(settings.language, 'contentDashboard.category.objectiveRoomAdventure')}</h2>
          <p>${contentDashboardSubtitle(item)}</p>
        </div>
        <div class="dashboard-editor-actions">
          <button type="button" id="dashboardDownloadBtn">${t(settings.language, 'contentDashboard.downloadXml')}</button>
          <button type="button" id="dashboardDeleteBtn">${t(settings.language, 'contentDashboard.delete')}</button>
          <button type="submit" id="dashboardSaveBtn">${t(settings.language, 'dialog.button.save')}</button>
        </div>
      </header>
      <div class="dashboard-editor-body event-editor-layout">
        <div class="dashboard-form">
          <label>${t(settings.language, 'contentDashboard.field.objectiveRoomName')}
            <select id="ucObjectiveRoomName">${roomOptions}</select>
          </label>
          <label>${t(settings.language, 'contentDashboard.field.name')}<input id="ucName" value="${escapeHtml(data.name)}"></label>
          <label>${t(settings.language, 'contentDashboard.field.flavor')}<textarea id="ucFlavor" rows="6">${escapeHtml(data.flavorText)}</textarea></label>
          <label>${t(settings.language, 'contentDashboard.field.rules')}<textarea id="ucRules" rows="10">${escapeHtml(data.rulesText)}</textarea></label>
          <label class="dashboard-checkbox"><input id="ucGeneric" type="checkbox" ${data.generic ? 'checked' : ''}>${t(settings.language, 'contentDashboard.field.genericMission')}</label>
        </div>
        <div class="dashboard-preview-column">
          <section class="dashboard-xml-panel">
            <button type="button" id="ucToggleXmlBtn">${t(settings.language, 'contentDashboard.showXml')}</button>
            <div id="ucXmlPanel" hidden>
              <label>${t(settings.language, 'contentDashboard.xmlPreview')}
                <textarea id="ucXmlPreview" rows="18" readonly></textarea>
              </label>
            </div>
          </section>
        </div>
      </div>
    </form>
  `;

  bindDashboardCommonActions(container, item);
  const form = editor.querySelector<HTMLFormElement>('form');
  const xmlPreview = editor.querySelector<HTMLTextAreaElement>('#ucXmlPreview');
  const xmlPanel = editor.querySelector<HTMLElement>('#ucXmlPanel');
  const toggleXmlButton = editor.querySelector<HTMLButtonElement>('#ucToggleXmlBtn');

  const buildDraftAdventure = (): UserObjectiveRoomAdventureData => ({
    ...data,
    objectiveRoomName: editor.querySelector<HTMLSelectElement>('#ucObjectiveRoomName')?.value ?? '',
    name: editor.querySelector<HTMLInputElement>('#ucName')?.value ?? '',
    flavorText: editor.querySelector<HTMLTextAreaElement>('#ucFlavor')?.value ?? '',
    rulesText: editor.querySelector<HTMLTextAreaElement>('#ucRules')?.value ?? '',
    generic: editor.querySelector<HTMLInputElement>('#ucGeneric')?.checked ?? false
  });

  const refreshAdventureXmlPreview = () => {
    if (xmlPreview) {
      xmlPreview.value = userContentItemXml({
        ...item,
        data: buildDraftAdventure()
      });
    }
  };

  toggleXmlButton?.addEventListener('click', () => {
    if (!xmlPanel) {
      return;
    }
    const nextHidden = !xmlPanel.hidden;
    xmlPanel.hidden = nextHidden;
    toggleXmlButton.textContent = nextHidden
      ? t(settings.language, 'contentDashboard.showXml')
      : t(settings.language, 'contentDashboard.hideXml');
  });

  form?.querySelectorAll<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>('input, textarea, select').forEach((field) => {
    field.addEventListener('input', refreshAdventureXmlPreview);
    field.addEventListener('change', refreshAdventureXmlPreview);
  });

  refreshAdventureXmlPreview();

  form?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const nextItem: UserContentItem = {
      ...item,
      updatedAt: new Date().toISOString(),
      data: buildDraftAdventure()
    };
    upsertUserContentItem(nextItem);
    activeDashboardItemUid = nextItem.uid;
    dashboardDraftItem = null;
    await refreshRuntimeContent();
    renderContentDashboard(container);
  });
}

function renderWarriorEditor(container: HTMLElement, item: Extract<UserContentItem, { kind: 'warrior' }>): void {
  const editor = container.querySelector<HTMLElement>('#contentDashboardEditor');
  if (!editor) {
    return;
  }
  const data = item.data as UserWarriorData;
  editor.innerHTML = `
    <form class="dashboard-editor-shell">
      <header class="dashboard-editor-header">
        <div>
          <h2>${t(settings.language, 'contentDashboard.category.warrior')}</h2>
          <p>${contentDashboardSubtitle(item)}</p>
        </div>
        <div class="dashboard-editor-actions">
          <button type="button" id="dashboardDownloadBtn">${t(settings.language, 'contentDashboard.downloadXml')}</button>
          <button type="button" id="dashboardDeleteBtn">${t(settings.language, 'contentDashboard.delete')}</button>
          <button type="submit" id="dashboardSaveBtn">${t(settings.language, 'dialog.button.save')}</button>
        </div>
      </header>
      <div class="dashboard-editor-body event-editor-layout">
        <div class="dashboard-form">
          <label>${t(settings.language, 'contentDashboard.field.name')}<input id="ucName" value="${escapeHtml(data.name)}"></label>
          <label>${t(settings.language, 'contentDashboard.field.race')}<input id="ucRace" value="${escapeHtml(data.race)}"></label>
          <div class="dashboard-tile-upload">
            <label>${t(settings.language, 'contentDashboard.field.counterPath')}<input id="ucCounterPath" value="${escapeHtml(getCounterAssetDisplayName(data.counterPath))}" readonly></label>
            <div class="dashboard-inline-actions">
              <button type="button" id="ucUploadCounterBtn">${t(settings.language, 'contentDashboard.uploadCounter')}</button>
              <input id="ucCounterFile" type="file" accept="image/*" hidden>
            </div>
          </div>
          <label>${t(settings.language, 'contentDashboard.field.rulesPath')}<input id="ucRulesPath" value="${escapeHtml(data.rulesPath)}"></label>
        </div>
        <div class="dashboard-preview-column">
          <section class="dashboard-card-preview warrior-counter-preview">
            <h3>${t(settings.language, 'contentDashboard.cardPreview')}</h3>
            <div id="ucWarriorPreview"></div>
          </section>
          <section class="dashboard-xml-panel">
            <button type="button" id="ucToggleXmlBtn">${t(settings.language, 'contentDashboard.showXml')}</button>
            <div id="ucXmlPanel" hidden>
              <label>${t(settings.language, 'contentDashboard.xmlPreview')}
                <textarea id="ucXmlPreview" rows="18" readonly></textarea>
              </label>
            </div>
          </section>
        </div>
      </div>
    </form>
  `;

  bindDashboardCommonActions(container, item);
  const form = editor.querySelector<HTMLFormElement>('form');
  const counterInput = editor.querySelector<HTMLInputElement>('#ucCounterPath');
  const counterFile = editor.querySelector<HTMLInputElement>('#ucCounterFile');
  const preview = editor.querySelector<HTMLElement>('#ucWarriorPreview');
  const xmlPreview = editor.querySelector<HTMLTextAreaElement>('#ucXmlPreview');
  const xmlPanel = editor.querySelector<HTMLElement>('#ucXmlPanel');
  const toggleXmlButton = editor.querySelector<HTMLButtonElement>('#ucToggleXmlBtn');

  const buildDraftWarrior = (): UserWarriorData => ({
    ...data,
    name: editor.querySelector<HTMLInputElement>('#ucName')?.value ?? '',
    race: editor.querySelector<HTMLInputElement>('#ucRace')?.value ?? '',
    counterPath: counterInput?.dataset.counterPath ?? data.counterPath,
    rulesPath: editor.querySelector<HTMLInputElement>('#ucRulesPath')?.value ?? ''
  });

  const refreshPreview = (): void => {
    const draft = buildDraftWarrior();
    if (preview) {
      const warrior = {
        id: draft.id || 'preview-warrior',
        name: draft.name || t(settings.language, 'contentDashboard.category.warrior'),
        race: draft.race,
        counterPath: draft.counterPath,
        rulesPath: draft.rulesPath
      };
      preview.innerHTML = `
        <div class="warrior-counter-card">
          <img src="${escapeHtml(resolveCounterAsset(warrior.counterPath))}" alt="${escapeHtml(warrior.name)}" />
          <div class="warrior-counter-name"><span>${escapeHtml(warrior.name)}</span></div>
        </div>
      `;
    }
    if (xmlPreview) {
      xmlPreview.value = userContentItemXml({ ...item, data: draft });
    }
  };

  toggleXmlButton?.addEventListener('click', () => {
    if (!xmlPanel) {
      return;
    }
    xmlPanel.hidden = !xmlPanel.hidden;
    toggleXmlButton.textContent = xmlPanel.hidden
      ? t(settings.language, 'contentDashboard.showXml')
      : t(settings.language, 'contentDashboard.hideXml');
  });
  editor.querySelector<HTMLButtonElement>('#ucUploadCounterBtn')?.addEventListener('click', () => counterFile?.click());
  counterFile?.addEventListener('change', async () => {
    const file = counterFile.files?.[0];
    if (!file) {
      return;
    }
    const dataUrl = await readFileAsDataUrl(file);
    saveCounterAsset(file.name, dataUrl);
    if (counterInput) {
      counterInput.value = file.name;
      counterInput.dataset.counterPath = file.name;
    }
    refreshPreview();
  });
  form?.querySelectorAll<HTMLInputElement>('input').forEach((field) => {
    if (field.id === 'ucCounterFile') {
      return;
    }
    field.addEventListener('input', refreshPreview);
    field.addEventListener('change', refreshPreview);
  });
  if (counterInput) {
    counterInput.dataset.counterPath = data.counterPath;
  }
  refreshPreview();
  form?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const nextItem: UserContentItem = {
      ...item,
      updatedAt: new Date().toISOString(),
      data: buildDraftWarrior()
    };
    upsertUserContentItem(nextItem);
    activeDashboardItemUid = nextItem.uid;
    dashboardDraftItem = null;
    await refreshRuntimeContent();
    renderContentDashboard(container);
  });
}

function renderLocationEditor(container: HTMLElement, item: Extract<UserContentItem, { kind: 'location' }>): void {
  const editor = container.querySelector<HTMLElement>('#contentDashboardEditor');
  if (!editor) {
    return;
  }
  const data = item.data as UserLocationData;
  const state: UserLocationData = {
    ...data,
    availableTypes: [...data.availableTypes],
    visitors: [...data.visitors]
  };

  const buildDraftLocation = (): SettlementLocation => ({
    id: state.id || 'preview-location',
    name: state.name,
    availableTypes: [...state.availableTypes],
    description: state.description,
    visitors: [...state.visitors],
    rules: state.rules
  });

  const renderTypeOptions = (): void => {
    const typeSelect = editor.querySelector<HTMLSelectElement>('#ucLocationTypeSelect');
    const typesList = editor.querySelector<HTMLSelectElement>('#ucLocationTypesList');
    if (typeSelect) {
      typeSelect.innerHTML = getSettlementTypes(settings.language)
        .filter((entry) => entry.value !== 'any' && !state.availableTypes.includes(entry.value as SettlementType))
        .map((entry) => `<option value="${escapeHtml(entry.value)}">${escapeHtml(entry.label)}</option>`)
        .join('');
    }
    if (typesList) {
      typesList.innerHTML = state.availableTypes
        .map((type) => `<option value="${escapeHtml(type)}">${escapeHtml(settlementTypeLabel(type))}</option>`)
        .join('');
    }
  };

  const renderVisitorOptions = (): void => {
    const visitorSelect = editor.querySelector<HTMLSelectElement>('#ucVisitorSelect');
    const visitorsList = editor.querySelector<HTMLSelectElement>('#ucVisitorsList');
    if (visitorSelect) {
      visitorSelect.innerHTML = [
        { id: 'all', label: 'All' },
        ...Array.from(repository.warriors.values())
          .sort((left, right) => left.name.localeCompare(right.name, undefined, { sensitivity: 'base' }))
          .map((warrior) => ({ id: warrior.id, label: warrior.name }))
      ]
        .filter((entry) => !state.visitors.includes(entry.id))
        .map((entry) => `<option value="${escapeHtml(entry.id)}">${escapeHtml(entry.label)}</option>`)
        .join('');
    }
    if (visitorsList) {
      visitorsList.innerHTML = state.visitors
        .map((visitor) => `<option value="${escapeHtml(visitor)}">${escapeHtml(locationVisitorLabel(visitor))}</option>`)
        .join('');
    }
  };

  const refreshPreview = (): void => {
    const draftLocation = buildDraftLocation();
    const visitorLabels = draftLocation.visitors.map((visitor) => locationVisitorLabel(visitor));
    const preview = editor.querySelector<HTMLElement>('#ucLocationPreview');
    const xmlPreview = editor.querySelector<HTMLTextAreaElement>('#ucXmlPreview');
    if (preview) {
      preview.innerHTML = renderSettlementLocationCard(draftLocation, visitorLabels);
    }
    if (xmlPreview) {
      xmlPreview.value = userContentItemXml({ ...item, data: state });
    }
  };

  editor.innerHTML = `
    <form class="dashboard-editor-shell">
      <header class="dashboard-editor-header">
        <div>
          <h2>${t(settings.language, 'contentDashboard.category.location')}</h2>
          <p>${contentDashboardSubtitle(item)}</p>
        </div>
        <div class="dashboard-editor-actions">
          <button type="button" id="dashboardDownloadBtn">${t(settings.language, 'contentDashboard.downloadXml')}</button>
          <button type="button" id="dashboardDeleteBtn">${t(settings.language, 'contentDashboard.delete')}</button>
          <button type="submit" id="dashboardSaveBtn">${t(settings.language, 'dialog.button.save')}</button>
        </div>
      </header>
      <div class="dashboard-editor-body event-editor-layout">
        <div class="dashboard-form">
          <label>${t(settings.language, 'contentDashboard.field.name')}<input id="ucName" value="${escapeHtml(state.name)}"></label>
          <label>${t(settings.language, 'contentDashboard.field.description')}<textarea id="ucDescription" rows="5">${escapeHtml(state.description)}</textarea></label>
          <label>${t(settings.language, 'contentDashboard.field.rules')}<textarea id="ucRules" rows="10">${escapeHtml(state.rules)}</textarea></label>
          <div class="dashboard-selector-block">
            <label>${t(settings.language, 'contentDashboard.field.availableTypeOption')}
              <select id="ucLocationTypeSelect"></select>
            </label>
            <div class="dashboard-inline-actions">
              <button type="button" id="ucAddLocationTypeBtn">+</button>
              <button type="button" id="ucRemoveLocationTypeBtn">-</button>
            </div>
            <label>${t(settings.language, 'contentDashboard.field.availableTypes')}
              <select id="ucLocationTypesList" size="5"></select>
            </label>
          </div>
          <div class="dashboard-selector-block">
            <label>${t(settings.language, 'contentDashboard.field.availableVisitorOption')}
              <select id="ucVisitorSelect"></select>
            </label>
            <div class="dashboard-inline-actions">
              <button type="button" id="ucAddVisitorBtn">+</button>
              <button type="button" id="ucRemoveVisitorBtn">-</button>
            </div>
            <label>${t(settings.language, 'contentDashboard.field.visitors')}
              <select id="ucVisitorsList" size="6"></select>
            </label>
          </div>
        </div>
        <div class="dashboard-preview-column">
          <section class="dashboard-card-preview settlement-location-preview">
            <h3>${t(settings.language, 'contentDashboard.cardPreview')}</h3>
            <div id="ucLocationPreview"></div>
          </section>
          <section class="dashboard-xml-panel">
            <button type="button" id="ucToggleXmlBtn">${t(settings.language, 'contentDashboard.showXml')}</button>
            <div id="ucXmlPanel" hidden>
              <label>${t(settings.language, 'contentDashboard.xmlPreview')}
                <textarea id="ucXmlPreview" rows="18" readonly></textarea>
              </label>
            </div>
          </section>
        </div>
      </div>
    </form>
  `;

  bindDashboardCommonActions(container, item);
  const form = editor.querySelector<HTMLFormElement>('form');
  const xmlPanel = editor.querySelector<HTMLElement>('#ucXmlPanel');
  const toggleXmlButton = editor.querySelector<HTMLButtonElement>('#ucToggleXmlBtn');
  toggleXmlButton?.addEventListener('click', () => {
    if (!xmlPanel) {
      return;
    }
    xmlPanel.hidden = !xmlPanel.hidden;
    toggleXmlButton.textContent = xmlPanel.hidden
      ? t(settings.language, 'contentDashboard.showXml')
      : t(settings.language, 'contentDashboard.hideXml');
  });

  editor.querySelector<HTMLInputElement>('#ucName')?.addEventListener('input', (event) => {
    state.name = (event.currentTarget as HTMLInputElement).value;
    refreshPreview();
  });
  editor.querySelector<HTMLTextAreaElement>('#ucDescription')?.addEventListener('input', (event) => {
    state.description = (event.currentTarget as HTMLTextAreaElement).value;
    refreshPreview();
  });
  editor.querySelector<HTMLTextAreaElement>('#ucRules')?.addEventListener('input', (event) => {
    state.rules = (event.currentTarget as HTMLTextAreaElement).value;
    refreshPreview();
  });
  editor.querySelector<HTMLButtonElement>('#ucAddLocationTypeBtn')?.addEventListener('click', () => {
    const value = editor.querySelector<HTMLSelectElement>('#ucLocationTypeSelect')?.value as SettlementType;
    if (value && !state.availableTypes.includes(value)) {
      state.availableTypes.push(value);
      renderTypeOptions();
      refreshPreview();
    }
  });
  editor.querySelector<HTMLButtonElement>('#ucRemoveLocationTypeBtn')?.addEventListener('click', () => {
    const value = editor.querySelector<HTMLSelectElement>('#ucLocationTypesList')?.value as SettlementType;
    const index = state.availableTypes.indexOf(value);
    if (index >= 0) {
      state.availableTypes.splice(index, 1);
      renderTypeOptions();
      refreshPreview();
    }
  });
  editor.querySelector<HTMLButtonElement>('#ucAddVisitorBtn')?.addEventListener('click', () => {
    const value = editor.querySelector<HTMLSelectElement>('#ucVisitorSelect')?.value ?? '';
    if (value && !state.visitors.includes(value)) {
      state.visitors.push(value);
      renderVisitorOptions();
      refreshPreview();
    }
  });
  editor.querySelector<HTMLButtonElement>('#ucRemoveVisitorBtn')?.addEventListener('click', () => {
    const value = editor.querySelector<HTMLSelectElement>('#ucVisitorsList')?.value ?? '';
    const index = state.visitors.indexOf(value);
    if (index >= 0) {
      state.visitors.splice(index, 1);
      renderVisitorOptions();
      refreshPreview();
    }
  });

  renderTypeOptions();
  renderVisitorOptions();
  refreshPreview();

  form?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const nextItem: UserContentItem = {
      ...item,
      updatedAt: new Date().toISOString(),
      data: {
        ...state,
        availableTypes: [...state.availableTypes],
        visitors: [...state.visitors]
      }
    };
    upsertUserContentItem(nextItem);
    activeDashboardItemUid = nextItem.uid;
    dashboardDraftItem = null;
    await refreshRuntimeContent();
    renderContentDashboard(container);
  });
}

function renderDashboardEditor(container: HTMLElement): void {
  const editor = container.querySelector<HTMLElement>('#contentDashboardEditor');
  if (!editor) {
    return;
  }

  if (activeDashboardItemUid?.startsWith(DASHBOARD_CREATE_PREFIX)) {
    renderDashboardCreateSelector(container, editor, activeDashboardItemUid.slice(DASHBOARD_CREATE_PREFIX.length) as UserContentKind);
    return;
  }

  const item = currentDashboardItem();
  if (!item) {
    renderDashboardHome(editor);
    return;
  }

  if (item.kind === 'dungeonCard') {
    renderDungeonCardEditor(container, item);
    return;
  }
  if (
    item.kind === 'dungeonEvent' ||
    item.kind === 'treasure' ||
    item.kind === 'objectiveTreasure' ||
    item.kind === 'travelEvent' ||
    item.kind === 'settlementEvent'
  ) {
    renderEventEditor(container, item);
    return;
  }
  if (item.kind === 'rule') {
    renderRuleEditor(container, item);
    return;
  }
  if (item.kind === 'monster') {
    renderMonsterEditor(container, item);
    return;
  }
  if (item.kind === 'objectiveRoomAdventure') {
    renderObjectiveRoomAdventureEditor(container, item);
    return;
  }
  if (item.kind === 'warrior') {
    renderWarriorEditor(container, item);
    return;
  }
  if (item.kind === 'location') {
    renderLocationEditor(container, item);
    return;
  }
  renderTableEditor(container, item);
}

function renderContentDashboard(container: HTMLElement): void {
  renderDashboardTree(container);
  renderDashboardEditor(container);
}

async function openContentDashboardDialog(): Promise<void> {
  const container = document.querySelector<HTMLElement>('#contentDashboardView');
  if (!container) {
    return;
  }

  dashboardOpen = true;
  document.body.classList.add('dashboard-active');
  container.hidden = false;
  container.innerHTML = `
    <div class="dashboard-layout">
      <aside class="dashboard-sidebar">
        <header class="dashboard-sidebar-header">
          <div>
            <h2>${t(settings.language, 'contentDashboard.title')}</h2>
            <p>${t(settings.language, 'contentDashboard.description')}</p>
          </div>
          <button type="button" id="contentDashboardBackBtn">${t(settings.language, 'contentDashboard.back')}</button>
        </header>
        <div id="contentDashboardTree" class="dashboard-tree"></div>
      </aside>
      <section id="contentDashboardEditor" class="dashboard-editor"></section>
    </div>
  `;

  container.querySelector<HTMLButtonElement>('#contentDashboardBackBtn')?.addEventListener('click', async () => {
    await closeContentDashboardView();
  });
  renderContentDashboard(container);
}

async function closeContentDashboardView(): Promise<void> {
  const container = document.querySelector<HTMLElement>('#contentDashboardView');
  if (!container) {
    return;
  }

  await refreshRuntimeContent();
  dashboardOpen = false;
  document.body.classList.remove('dashboard-active');
  container.hidden = true;
  container.innerHTML = '';
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

function matchesMonsterAmbience(entry: MonsterEntry | GroupEntry | TableRefEntry, selectedAmbience: string): boolean {
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
  if (entry.kind === 'tableRef') {
    if (entry.ambiences.length === 0) {
      return true;
    }
    return entry.ambiences.some(
      (ambience) => ambience.localeCompare(selectedAmbience, undefined, { sensitivity: 'base' }) === 0
    );
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
): Array<MonsterEntry | GroupEntry | TableRefEntry> {
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
  const selected = entries[Math.floor(Math.random() * entries.length)] ?? null;
  if (!selected || selected.kind !== 'tableRef') {
    return selected;
  }
  return resolveObjectiveTableRef(repositoryToUse, selectedAmbience, selected, new Set<string>());
}

function resolveObjectiveTableRef(
  repositoryToUse: ContentRepository,
  selectedAmbience: string,
  entry: TableRefEntry,
  visited: Set<string>
): DrawEntry | null {
  const referencedTable = repositoryToUse.tables.get(entry.tableName);
  if (!referencedTable || visited.has(entry.tableName)) {
    return null;
  }

  visited.add(entry.tableName);
  try {
    for (let attempt = 0; attempt < 32; attempt += 1) {
      const referencedEntries = referencedTable.monsters.filter((candidate) => {
        if (candidate.level !== entry.targetLevel) {
          return false;
        }
        return matchesMonsterAmbience(candidate, selectedAmbience);
      });
      if (referencedEntries.length === 0) {
        return null;
      }

      const drawn = referencedEntries[Math.floor(Math.random() * referencedEntries.length)] ?? null;
      if (!drawn) {
        continue;
      }
      if (drawn.kind !== 'tableRef') {
        return drawn;
      }

      const resolved = resolveObjectiveTableRef(repositoryToUse, selectedAmbience, drawn, visited);
      if (resolved) {
        return resolved;
      }
    }
    return null;
  } finally {
    visited.delete(entry.tableName);
  }
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
        <button type="button" id="treasureSearchBtn">${t(settings.language, 'treasureSearch.button')}</button>
        <button type="button" id="closeAllAdventureCardsBtn">${t(settings.language, 'menu.item.closeAllCards')}</button>
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

  panel.querySelector<HTMLButtonElement>('#closeAllAdventureCardsBtn')?.addEventListener('click', () => {
    closeAllOpenCards();
  });

  panel.querySelector<HTMLButtonElement>('#treasureSearchBtn')?.addEventListener('click', () => {
    openTreasureSearchDialog();
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
  syncPartySize();
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
  syncPartySize();
  resetWarriorCounterPool();
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
