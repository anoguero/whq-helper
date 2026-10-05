import './styles.css';

import { getAdventureAmbiences, loadUiTranslations, t, tf } from './i18n';
import { loadContent } from './content';
import { applyTableActiveState, buildDecks } from './deck';
import { loadSettings, saveSettings } from './settings';
import { appState, refreshDungeonCards } from './state';
import { DASHBOARD_CATEGORIES, DASHBOARD_CREATE_PREFIX, dashboardState } from './ui/dashboard/state';
import {
  type UserContentKind
} from './userContent';
import type {
  LanguageCode
} from './types';
import {
  escapeHtml
} from './ui/formatting';
import {
  closeAllOpenCards
} from './ui/cardWindows';
import {
  closeAllWarriorCounters,
  drawWarriorCounter,
  openPartyDialog,
  resetWarriorCounterPool,
  syncPartySize
} from './ui/partyPanel';
import { openTableDialog } from './ui/tableDialog';
import { DECKS, renderDecks, type DeckMeta } from './ui/decks';
import {
  openSettlementSimulatorPanel
} from './ui/settlementPanel';
import { openMaintenanceDialog } from './ui/maintenanceDialog';
import { openNewDungeonDialog } from './ui/adventureSimulator';
import {
  createBlankDashboardItem,
  createBlankEventTableItem,
  createModifiedDashboardItem,
  currentDashboardItem,
  dashboardItemsByKind,
  dashboardSourceOptions
} from './ui/dashboard/data';
import {
  contentDashboardSubtitle
} from './ui/dashboard/common';
import { renderDungeonCardEditor } from './ui/dashboard/dungeonCardEditor';
import { renderEventEditor } from './ui/dashboard/eventEditor';
import { renderRuleEditor } from './ui/dashboard/ruleEditor';
import { renderMonsterEditor } from './ui/dashboard/monsterEditor';
import { renderTableEditor } from './ui/dashboard/tableEditor';
import { renderObjectiveRoomAdventureEditor } from './ui/dashboard/adventureEditor';
import { renderWarriorEditor } from './ui/dashboard/warriorEditor';
import { renderLocationEditor } from './ui/dashboard/locationEditor';

function clampProbability(value: number): number {
  return Math.max(0, Math.min(100, value));
}

async function applyLanguageChange(language: LanguageCode): Promise<void> {
  appState.settings.language = language;
  await Promise.all([appState.dungeonStore.setLanguage(language)]);
  appState.repository = await loadContent(language);
  syncPartySize();
  resetWarriorCounterPool();
  saveSettings(appState.settings);
  render();
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
            <button type="button" id="closeWarriorCountersBtn">${t(language, 'menu.item.closeWarriorCounters')}</button>
            <button type="button" id="activateTablesHeroBtn">${t(language, 'menu.item.activateTables')}</button>
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
      <dialog id="whiteDwarfReferenceDialog" class="table-dialog wide-dialog"></dialog>
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

  document.querySelector<HTMLButtonElement>('#closeWarriorCountersBtn')?.addEventListener('click', () => {
    closeAllWarriorCounters();
  });

  document.querySelector<HTMLButtonElement>('#activateTablesHeroBtn')?.addEventListener('click', () => {
    openTableDialog();
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
      ${t(appState.settings.language, 'controls.language')}
      <select id="languageSelect">
        <option value="ES" ${appState.settings.language === 'ES' ? 'selected' : ''}>Español</option>
        <option value="EN" ${appState.settings.language === 'EN' ? 'selected' : ''}>English</option>
      </select>
    </label>

    <label>
      ${t(appState.settings.language, 'controls.ambience')}
      <select id="ambienceSelect">
        ${getAdventureAmbiences(appState.settings.language)
          .map(
          (ambience) =>
            `<option value="${ambience.value}" ${appState.settings.adventureAmbience === ambience.value ? 'selected' : ''}>${ambience.label}</option>`
          )
          .join('')}
      </select>
    </label>

    <div class="party-summary-control">
      <span class="party-summary-label">${t(appState.settings.language, 'controls.partyMembers')}</span>
      <strong>${appState.settings.partyWarriors
        .map((id) => appState.repository.warriors.get(id)?.name ?? id)
        .join(', ')}</strong>
      <small>${tf(appState.settings.language, 'party.size', { count: appState.settings.partySize })}</small>
    </div>

    <label>
      ${t(appState.settings.language, 'controls.eventProbability')}
      <input id="eventProbabilityInput" type="number" min="0" max="100" value="${appState.settings.eventProbability}">
    </label>

    <label>
      ${t(appState.settings.language, 'controls.goldProbability')}
      <input id="goldProbabilityInput" type="number" min="0" max="100" value="${appState.settings.treasureGoldProbability}">
    </label>

    <fieldset class="mode-field">
      <label>
        <input type="radio" name="mode" value="table" ${appState.settings.simulateDeck ? '' : 'checked'}>
        ${t(appState.settings.language, 'menu.item.simulateTable')}
      </label>
      <label>
        <input type="radio" name="mode" value="deck" ${appState.settings.simulateDeck ? 'checked' : ''}>
        ${t(appState.settings.language, 'menu.item.simulateDeck')}
      </label>
    </fieldset>

    <div class="control-actions">
      <button type="button" id="setPartyBtn">${t(appState.settings.language, 'controls.setParty')}</button>
      <button type="button" id="activateTablesBtn">${t(appState.settings.language, 'menu.item.activateTables')}</button>
      <button type="button" id="closeCardsBtn">${t(appState.settings.language, 'menu.item.closeAllCards')}</button>
    </div>
  `;

  controls.querySelector<HTMLSelectElement>('#languageSelect')?.addEventListener('change', (event) => {
    const value = (event.target as HTMLSelectElement).value === 'EN' ? 'EN' : 'ES';
    appState.hooks.applyLanguageChange(value).catch((error) => window.alert(String(error)));
  });

  controls.querySelector<HTMLSelectElement>('#ambienceSelect')?.addEventListener('change', (event) => {
    appState.settings.adventureAmbience = (event.target as HTMLSelectElement).value;
    appState.hooks.rebuildDecks();
  });

  controls.querySelector<HTMLInputElement>('#eventProbabilityInput')?.addEventListener('change', (event) => {
    const value = Number.parseInt((event.target as HTMLInputElement).value, 10);
    appState.settings.eventProbability = clampProbability(Number.isFinite(value) ? value : appState.settings.eventProbability);
    appState.hooks.rebuildDecks();
  });

  controls.querySelector<HTMLInputElement>('#goldProbabilityInput')?.addEventListener('change', (event) => {
    const value = Number.parseInt((event.target as HTMLInputElement).value, 10);
    appState.settings.treasureGoldProbability = clampProbability(Number.isFinite(value) ? value : appState.settings.treasureGoldProbability);
    appState.hooks.rebuildDecks();
  });

  controls.querySelectorAll<HTMLInputElement>('input[name="mode"]').forEach((radio) => {
    radio.addEventListener('change', () => {
      appState.settings.simulateDeck = radio.value === 'deck';
      appState.hooks.rebuildDecks();
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

function buildDeckToggles(): void {
  const container = document.querySelector<HTMLElement>('#deckToggles');
  if (!container) {
    return;
  }

  container.innerHTML = DECKS.map((deck) => {
    const checked = appState.settings[deck.toggleKey] ? 'checked' : '';
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
        ${t(appState.settings.language, labelKey)}
      </label>
    `;
  }).join('');

  container.querySelectorAll<HTMLInputElement>('input[type="checkbox"]').forEach((checkbox) => {
    checkbox.addEventListener('change', () => {
      const key = checkbox.dataset.toggle as DeckMeta['toggleKey'];
      appState.settings[key] = checkbox.checked;
      saveSettings(appState.settings);
      renderDecks();
    });
  });
}

async function refreshRuntimeContent(): Promise<void> {
  await appState.dungeonStore.init(appState.settings.language);
  appState.repository = await loadContent(appState.settings.language);
  syncPartySize();
  refreshDungeonCards();
  rebuildDecks();
}

function renderDashboardTree(container: HTMLElement): void {
  const tree = container.querySelector<HTMLElement>('#contentDashboardTree');
  if (!tree) {
    return;
  }

  tree.innerHTML = DASHBOARD_CATEGORIES.map((category) => {
    const items = dashboardItemsByKind(category.kind);
    const selectedCreate = dashboardState.activeDashboardItemUid === `${DASHBOARD_CREATE_PREFIX}${category.kind}`;
    return `
      <section class="dashboard-tree-section">
        <div class="dashboard-tree-header ${selectedCreate ? 'selected' : ''}" data-create-kind="${category.kind}">
          <button type="button" class="dashboard-tree-label" data-create-kind="${category.kind}">
            ${t(appState.settings.language, category.titleKey)}
          </button>
          <button type="button" class="dashboard-tree-add" data-create-kind="${category.kind}">+</button>
        </div>
        <ul class="dashboard-tree-list">
          ${items
            .map(
              (item) => `
                <li class="${item.uid === dashboardState.activeDashboardItemUid ? 'selected' : ''}" data-item-uid="${escapeHtml(item.uid)}" title="${escapeHtml(contentDashboardSubtitle(item))}">
                  <span>${escapeHtml(item.title)}</span>
                  <small>${escapeHtml(contentDashboardSubtitle(item))}</small>
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
      dashboardState.activeDashboardItemUid = `${DASHBOARD_CREATE_PREFIX}${element.dataset.createKind as UserContentKind}`;
      dashboardState.dashboardDraftItem = null;
      dashboardState.renderContentDashboard(container);
    });
  });

  tree.querySelectorAll<HTMLElement>('[data-item-uid]').forEach((element) => {
    element.addEventListener('click', () => {
      dashboardState.activeDashboardItemUid = element.dataset.itemUid ?? null;
      dashboardState.dashboardDraftItem = null;
      dashboardState.renderContentDashboard(container);
    });
    element.addEventListener('dblclick', () => {
      dashboardState.activeDashboardItemUid = element.dataset.itemUid ?? null;
      dashboardState.dashboardDraftItem = null;
      dashboardState.renderContentDashboard(container);
    });
  });
}

function renderDashboardHome(editor: HTMLElement): void {
  editor.innerHTML = `
    <div class="dashboard-empty">
      <h2>${t(appState.settings.language, 'contentDashboard.title')}</h2>
      <p>${t(appState.settings.language, 'contentDashboard.description')}</p>
      <div class="dashboard-create-actions">
        <button type="button" id="dashboardTileConfigBtn">${t(appState.settings.language, 'contentDashboard.tileConfig')}</button>
      </div>
    </div>
  `;

  editor.querySelector<HTMLButtonElement>('#dashboardTileConfigBtn')?.addEventListener('click', () => {
    openMaintenanceDialog();
  });
}

function renderDashboardCreateSelector(container: HTMLElement, editor: HTMLElement, kind: UserContentKind): void {
  const options = dashboardSourceOptions(kind);
  const tableTypePicker =
    kind === 'table'
      ? `
      <div id="dashboardTableTypePicker" hidden>
        <p>${t(appState.settings.language, 'contentDashboard.tableTypePrompt')}</p>
        <div class="dashboard-create-actions">
          <button type="button" data-table-kind="monster">${t(appState.settings.language, 'contentDashboard.tableType.monsterEncounters')}</button>
          <button type="button" data-table-kind="dungeon">${t(appState.settings.language, 'contentDashboard.tableType.dungeonEvents')}</button>
          <button type="button" data-table-kind="travel">${t(appState.settings.language, 'contentDashboard.tableType.travelEvents')}</button>
          <button type="button" data-table-kind="settlement">${t(appState.settings.language, 'contentDashboard.tableType.settlementEvents')}</button>
        </div>
      </div>
    `
      : '';
  editor.innerHTML = `
    <div class="dashboard-editor-shell">
      <h2>${t(appState.settings.language, 'contentDashboard.createPromptTitle')}</h2>
      <p>${t(appState.settings.language, 'contentDashboard.createPromptText')}</p>
      <div class="dashboard-create-actions">
        <button type="button" id="dashboardCreateNewBtn">${t(appState.settings.language, 'contentDashboard.createNew')}</button>
        <button type="button" id="dashboardCreateModifyBtn">${t(appState.settings.language, 'contentDashboard.createModify')}</button>
      </div>
      ${tableTypePicker}
      <div id="dashboardSourcePicker" hidden>
        <label>
          ${t(appState.settings.language, 'contentDashboard.source')}
          <select id="dashboardSourceSelect">
            ${options.map((option) => `<option value="${escapeHtml(option.id)}">${escapeHtml(option.label)}</option>`).join('')}
          </select>
        </label>
        <button type="button" id="dashboardCreateFromSourceBtn">${t(appState.settings.language, 'contentDashboard.openEditor')}</button>
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
    dashboardState.dashboardDraftItem = createBlankDashboardItem(kind);
    dashboardState.activeDashboardItemUid = 'draft';
    dashboardState.renderContentDashboard(container);
  });

  editor.querySelectorAll<HTMLButtonElement>('[data-table-kind]').forEach((button) => {
    button.addEventListener('click', () => {
      const tableKind = button.dataset.tableKind ?? 'monster';
      dashboardState.dashboardDraftItem =
        tableKind === 'dungeon' || tableKind === 'travel' || tableKind === 'settlement'
          ? createBlankEventTableItem(tableKind)
          : createBlankDashboardItem('table');
      dashboardState.activeDashboardItemUid = 'draft';
      dashboardState.renderContentDashboard(container);
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
    dashboardState.dashboardDraftItem = createModifiedDashboardItem(kind, sourceId);
    if (!dashboardState.dashboardDraftItem) {
      window.alert(t(appState.settings.language, 'contentDashboard.sourceNotFound'));
      return;
    }
    dashboardState.activeDashboardItemUid = 'draft';
    dashboardState.renderContentDashboard(container);
  });
}

function renderDashboardEditor(container: HTMLElement): void {
  const editor = container.querySelector<HTMLElement>('#contentDashboardEditor');
  if (!editor) {
    return;
  }

  if (dashboardState.activeDashboardItemUid?.startsWith(DASHBOARD_CREATE_PREFIX)) {
    renderDashboardCreateSelector(container, editor, dashboardState.activeDashboardItemUid.slice(DASHBOARD_CREATE_PREFIX.length) as UserContentKind);
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

  document.body.classList.add('dashboard-active');
  container.hidden = false;
  container.innerHTML = `
    <div class="dashboard-layout">
      <aside class="dashboard-sidebar">
        <header class="dashboard-sidebar-header">
          <div>
            <h2>${t(appState.settings.language, 'contentDashboard.title')}</h2>
            <p>${t(appState.settings.language, 'contentDashboard.description')}</p>
          </div>
          <button type="button" id="contentDashboardBackBtn">${t(appState.settings.language, 'contentDashboard.back')}</button>
        </header>
        <div id="contentDashboardTree" class="dashboard-tree"></div>
      </aside>
      <section id="contentDashboardEditor" class="dashboard-editor"></section>
    </div>
  `;

  container.querySelector<HTMLButtonElement>('#contentDashboardBackBtn')?.addEventListener('click', async () => {
    await closeContentDashboardView();
  });
  dashboardState.renderContentDashboard(container);
}

async function closeContentDashboardView(): Promise<void> {
  const container = document.querySelector<HTMLElement>('#contentDashboardView');
  if (!container) {
    return;
  }

  await appState.hooks.refreshRuntimeContent();
  document.body.classList.remove('dashboard-active');
  container.hidden = true;
  container.innerHTML = '';
}

function rebuildDecks(): void {
  applyTableActiveState(appState.repository, appState.settings);
  appState.decks = buildDecks(appState.repository, appState.settings);
  saveSettings(appState.settings);
  renderDecks();
}

function render(): void {
  syncPartySize();
  createAppShell(appState.settings.language);
  wireHeroActions();
  buildControls();
  buildDeckToggles();
  refreshDungeonCards();
  rebuildDecks();
}

async function bootstrap(): Promise<void> {
  appState.settings = await loadSettings();
  await Promise.all([appState.dungeonStore.init(appState.settings.language), loadUiTranslations()]);
  appState.repository = await loadContent(appState.settings.language);
  syncPartySize();
  resetWarriorCounterPool();
  appState.settings.dungeonActive = false;

  applyTableActiveState(appState.repository, appState.settings);
  appState.decks = buildDecks(appState.repository, appState.settings);
  appState.dungeonCards = appState.dungeonStore.loadCards();

  render();
}

appState.hooks = { render, applyLanguageChange, refreshRuntimeContent, rebuildDecks, buildControls };
dashboardState.renderContentDashboard = renderContentDashboard;

bootstrap().catch((error) => {
  const app = document.querySelector<HTMLDivElement>('#app');
  if (app) {
    app.innerHTML = `<pre class="error">Error loading application:\n${String(error)}</pre>`;
  }
});

window.addEventListener('beforeunload', () => {
  saveSettings(appState.settings);
});
