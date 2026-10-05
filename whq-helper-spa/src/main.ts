import './styles.css';

import { getAdventureAmbiences, getSettlementTypes, loadUiTranslations, t, tf } from './i18n';
import { loadContent } from './content';
import { applyTableActiveState, buildDecks } from './deck';
import { loadSettings, saveSettings } from './settings';
import { renderEventCard, renderSettlementLocationCard } from './render';
import { appState, refreshDungeonCards } from './state';
import { DASHBOARD_CATEGORIES, DASHBOARD_CREATE_PREFIX, dashboardState } from './ui/dashboard/state';
import { getCounterAssetDisplayName, resolveCounterAsset, saveCounterAsset } from './counterAssets';
import {
  parseTableMetadata,
  type UserContentItem,
  type UserContentKind,
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
  GroupEntry,
  LanguageCode,
  MonsterEntry,
  SettlementLocation,
  SettlementType
} from './types';
import {
  buildSpecialRuleText,
  escapeHtml,
  formatRuleLinks,
  inferRuleParameters,
  joinCsv,
  readFileAsDataUrl,
  ruleParameterLabels,
  treasureUsersFromFlags,
  treasureUsersToFlags
} from './ui/formatting';
import {
  closeAllOpenCards,
  fitTreasureHeaderText
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
  locationVisitorLabel,
  openSettlementSimulatorPanel,
  settlementTypeLabel
} from './ui/settlementPanel';
import { openMaintenanceDialog } from './ui/maintenanceDialog';
import { openNewDungeonDialog } from './ui/adventureSimulator';
import {
  availableEventItemsForTable,
  availableMagicRules,
  availableMonsterFactions,
  availableMonsterRules,
  availableObjectiveRoomNames,
  createBlankDashboardItem,
  createBlankEventTableItem,
  createModifiedDashboardItem,
  currentDashboardItem,
  dashboardItemsByKind,
  dashboardSourceOptions,
  monsterEntryLabel,
  parseEventOnlyTable,
  parseMonsterOnlyTable,
  serializeEventOnlyTable,
  serializeMonsterOnlyTable,
  tableEncounterLabel
} from './ui/dashboard/data';
import {
  bindDashboardCommonActions,
  contentDashboardSubtitle,
  renderDashboardEditorShell
} from './ui/dashboard/common';
import { renderDungeonCardEditor } from './ui/dashboard/dungeonCardEditor';

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
            <h2>${t(appState.settings.language, DASHBOARD_CATEGORIES.find((entry) => entry.kind === item.kind)?.titleKey ?? 'contentDashboard.title')}</h2>
            <p>${contentDashboardSubtitle(item)}</p>
          </div>
          <div class="dashboard-editor-actions">
            <button type="button" id="dashboardDownloadBtn">${t(appState.settings.language, 'contentDashboard.downloadXml')}</button>
            <button type="button" id="dashboardDeleteBtn">${t(appState.settings.language, 'contentDashboard.delete')}</button>
            <button type="submit" id="dashboardSaveBtn">${t(appState.settings.language, 'dialog.button.save')}</button>
          </div>
        </header>
        <div class="dashboard-editor-body treasure-editor-layout">
          <div class="dashboard-form">
            <label>${t(appState.settings.language, 'contentDashboard.field.name')}<input id="ucName" value="${escapeHtml(data.name)}"></label>
            <label>${t(appState.settings.language, 'contentDashboard.field.flavor')}<textarea id="ucFlavor" rows="4">${escapeHtml(data.flavor)}</textarea></label>
            <label>${t(appState.settings.language, 'contentDashboard.field.rules')}<textarea id="ucRules" rows="8">${escapeHtml(data.rules)}</textarea></label>
            <label>${t(appState.settings.language, 'contentDashboard.field.special')}<textarea id="ucSpecial" rows="4">${escapeHtml(data.special)}</textarea></label>
            <label>${t(appState.settings.language, 'contentDashboard.field.goldValue')}<input id="ucGoldValue" value="${escapeHtml(data.goldValue)}"></label>
            <fieldset class="dashboard-checkbox-group">
              <legend>${t(appState.settings.language, 'contentDashboard.field.users')}</legend>
              <label class="dashboard-checkbox"><input id="ucUserB" type="checkbox" ${userFlags.B ? 'checked' : ''}>${t(appState.settings.language, 'card.treasure.user.barbarian')}</label>
              <label class="dashboard-checkbox"><input id="ucUserD" type="checkbox" ${userFlags.D ? 'checked' : ''}>${t(appState.settings.language, 'card.treasure.user.dwarf')}</label>
              <label class="dashboard-checkbox"><input id="ucUserE" type="checkbox" ${userFlags.E ? 'checked' : ''}>${t(appState.settings.language, 'card.treasure.user.elf')}</label>
              <label class="dashboard-checkbox"><input id="ucUserW" type="checkbox" ${userFlags.W ? 'checked' : ''}>${t(appState.settings.language, 'card.treasure.user.wizard')}</label>
            </fieldset>
          </div>
          <div class="dashboard-preview-column">
            <section class="dashboard-card-preview treasure-card-preview">
              <h3>${t(appState.settings.language, 'contentDashboard.cardPreview')}</h3>
              <div id="ucTreasurePreview"></div>
            </section>
            <section class="dashboard-xml-panel">
              <button type="button" id="ucToggleXmlBtn">${t(appState.settings.language, 'contentDashboard.showXml')}</button>
              <div id="ucXmlPanel" hidden>
                <label>${t(appState.settings.language, 'contentDashboard.xmlPreview')}
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
          appState.settings.language
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
        ? t(appState.settings.language, 'contentDashboard.showXml')
        : t(appState.settings.language, 'contentDashboard.hideXml');
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
      dashboardState.activeDashboardItemUid = nextItem.uid;
      dashboardState.dashboardDraftItem = null;
      await appState.hooks.refreshRuntimeContent();
      dashboardState.renderContentDashboard(container);
    });
    return;
  }

  const eventCategory = item.kind === 'travelEvent' ? 'travel' : item.kind === 'settlementEvent' ? 'settlement' : 'dungeon';
  editor.innerHTML = `
    <form class="dashboard-editor-shell">
      <header class="dashboard-editor-header">
        <div>
          <h2>${t(appState.settings.language, DASHBOARD_CATEGORIES.find((entry) => entry.kind === item.kind)?.titleKey ?? 'contentDashboard.title')}</h2>
          <p>${contentDashboardSubtitle(item)}</p>
        </div>
        <div class="dashboard-editor-actions">
          <button type="button" id="dashboardDownloadBtn">${t(appState.settings.language, 'contentDashboard.downloadXml')}</button>
          <button type="button" id="dashboardDeleteBtn">${t(appState.settings.language, 'contentDashboard.delete')}</button>
          <button type="submit" id="dashboardSaveBtn">${t(appState.settings.language, 'dialog.button.save')}</button>
        </div>
      </header>
      <div class="dashboard-editor-body event-editor-layout">
        <div class="dashboard-form">
          <label>${t(appState.settings.language, 'contentDashboard.field.name')}<input id="ucName" value="${escapeHtml(data.name)}"></label>
          ${isTravelLike ? '' : `<label>${t(appState.settings.language, 'contentDashboard.field.flavor')}<textarea id="ucFlavor" rows="4">${escapeHtml(data.flavor)}</textarea></label>`}
          <label>${t(appState.settings.language, 'contentDashboard.field.rules')}<textarea id="ucRules" rows="8">${escapeHtml(data.rules)}</textarea></label>
          ${isTravelLike ? '' : `<label>${t(appState.settings.language, 'contentDashboard.field.special')}<textarea id="ucSpecial" rows="4">${escapeHtml(data.special)}</textarea></label>`}
        </div>
        <div class="dashboard-preview-column">
          <section class="dashboard-card-preview event-card-preview">
            <h3>${t(appState.settings.language, 'contentDashboard.cardPreview')}</h3>
            <div id="ucEventPreview"></div>
          </section>
          <section class="dashboard-xml-panel">
            <button type="button" id="ucToggleXmlBtn">${t(appState.settings.language, 'contentDashboard.showXml')}</button>
            <div id="ucXmlPanel" hidden>
              <label>${t(appState.settings.language, 'contentDashboard.xmlPreview')}
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
        appState.settings.language
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
      ? t(appState.settings.language, 'contentDashboard.showXml')
      : t(appState.settings.language, 'contentDashboard.hideXml');
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
    dashboardState.activeDashboardItemUid = nextItem.uid;
    dashboardState.dashboardDraftItem = null;
    await appState.hooks.refreshRuntimeContent();
    dashboardState.renderContentDashboard(container);
  });
}

function renderRuleEditor(container: HTMLElement, item: Extract<UserContentItem, { kind: 'rule' }>): void {
  const editor = container.querySelector<HTMLElement>('#contentDashboardEditor');
  if (!editor) {
    return;
  }
  const rawData = item.data as Partial<UserRuleData>;
  const sourceRule = appState.repository.rules.get(item.sourceId ?? rawData.id ?? '');
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
          <h2>${t(appState.settings.language, 'contentDashboard.category.rule')}</h2>
          <p>${contentDashboardSubtitle(item)}</p>
        </div>
        <div class="dashboard-editor-actions">
          <button type="button" id="dashboardDownloadBtn">${t(appState.settings.language, 'contentDashboard.downloadXml')}</button>
          <button type="button" id="dashboardDeleteBtn">${t(appState.settings.language, 'contentDashboard.delete')}</button>
          <button type="submit" id="dashboardSaveBtn">${t(appState.settings.language, 'dialog.button.save')}</button>
        </div>
      </header>
      <div class="dashboard-editor-body">
        <div class="dashboard-form">
          <label>${t(appState.settings.language, 'contentDashboard.field.id')}<input id="ucId" value="${escapeHtml(data.id)}" readonly></label>
          <label>${t(appState.settings.language, 'contentDashboard.field.ruleType')}
            <select id="ucType">
              <option value="rule" ${data.type === 'rule' ? 'selected' : ''}>rule</option>
              <option value="magic" ${data.type === 'magic' ? 'selected' : ''}>magic</option>
            </select>
          </label>
          <label>${t(appState.settings.language, 'contentDashboard.field.name')}<input id="ucName" value="${escapeHtml(data.name)}"></label>
          <label>${t(appState.settings.language, 'contentDashboard.field.parameterName')}<input id="ucParameterName" value="${escapeHtml(data.parameterName)}"></label>
          <label>${t(appState.settings.language, 'contentDashboard.field.parameterNames')}<input id="ucParameterNames" value="${escapeHtml((data.parameterNames ?? []).join(', '))}"></label>
          <label>${t(appState.settings.language, 'contentDashboard.field.parameterFormat')}<input id="ucParameterFormat" value="${escapeHtml(data.parameterFormat)}"></label>
          <label>${t(appState.settings.language, 'contentDashboard.field.text')}<textarea id="ucText" rows="12">${escapeHtml(data.text)}</textarea></label>
        </div>
        <div class="dashboard-xml-panel">
          <button type="button" id="ucToggleXmlBtn">${t(appState.settings.language, 'contentDashboard.showXml')}</button>
          <div id="ucXmlPanel" hidden>
            <label>${t(appState.settings.language, 'contentDashboard.xmlPreview')}
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
      ? t(appState.settings.language, 'contentDashboard.showXml')
      : t(appState.settings.language, 'contentDashboard.hideXml');
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
    dashboardState.activeDashboardItemUid = nextItem.uid;
    dashboardState.dashboardDraftItem = null;
    await appState.hooks.refreshRuntimeContent();
    dashboardState.renderContentDashboard(container);
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
    t(appState.settings.language, 'contentDashboard.category.monster'),
    contentDashboardSubtitle(item),
    `
      <label>${t(appState.settings.language, 'contentDashboard.field.name')}<input id="ucName" value="${escapeHtml(data.name)}"></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.plural')}<input id="ucPlural" value="${escapeHtml(data.plural)}"></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.factions')}<input id="ucFactions" value="${escapeHtml(joinCsv(selectedFactions))}" readonly></label>
      <div class="dashboard-inline-actions">
        <select id="ucFactionSelect">
          ${factionOptions.map((faction) => `<option value="${escapeHtml(faction)}">${escapeHtml(faction)}</option>`).join('')}
        </select>
        <button type="button" id="ucAddFactionBtn">+</button>
        <button type="button" id="ucRemoveFactionBtn">-</button>
      </div>
      <label>${t(appState.settings.language, 'contentDashboard.field.move')}<input id="ucMove" value="${escapeHtml(data.move)}"></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.weaponSkill')}<input id="ucWeaponSkill" value="${escapeHtml(data.weaponskill)}"></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.ballisticSkill')}
        <select id="ucBallisticSkill">
          ${ballisticSkillOptions
            .map(
              (skill) =>
                `<option value="${skill}" ${data.ballisticskill === skill || (!data.ballisticskill && skill === '-') ? 'selected' : ''}>${skill}</option>`
            )
            .join('')}
        </select>
      </label>
      <label>${t(appState.settings.language, 'contentDashboard.field.strength')}<input id="ucStrength" value="${escapeHtml(data.strength)}"></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.toughness')}<input id="ucToughness" value="${escapeHtml(data.toughness)}"></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.wounds')}<input id="ucWounds" value="${escapeHtml(data.wounds)}"></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.initiative')}<input id="ucInitiative" value="${escapeHtml(data.initiative)}"></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.attacks')}<input id="ucAttacks" value="${escapeHtml(data.attacks)}"></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.gold')}<input id="ucGold" value="${escapeHtml(data.gold)}"></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.armor')}<input id="ucArmor" value="${escapeHtml(data.armor)}"></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.damage')}
        <select id="ucDamage">
          ${damageOptions
            .map(
              (damage) =>
                `<option value="${damage}" ${data.damage === damage || (!data.damage && damage === '1D6') ? 'selected' : ''}>${damage}</option>`
            )
            .join('')}
        </select>
      </label>
      <label>${t(appState.settings.language, 'contentDashboard.field.special')}<textarea id="ucSpecial" rows="5">${escapeHtml(data.special)}</textarea></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.specialLinks')}
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
      <label>${t(appState.settings.language, 'contentDashboard.field.magicType')}
        <select id="ucMagicType">
          <option value=""></option>
          ${magicOptions.map((rule) => `<option value="${escapeHtml(rule.id)}" ${data.magicType === rule.id ? 'selected' : ''}>${escapeHtml(rule.name)}</option>`).join('')}
        </select>
      </label>
      <label>${t(appState.settings.language, 'contentDashboard.field.magicLevel')}<input id="ucMagicLevel" type="number" min="0" value="${data.magicLevel}"></label>
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
        t(appState.settings.language, 'contentDashboard.field.ruleParameter')
      )}</div>`;
      const values = Array.from({ length: labels.length }, (_, index) => selectedRuleDraftParameters[index] ?? '');
      ruleParametersContainer.innerHTML =
        heading +
        labels
          .map(
            (label, index) =>
              `<label>${escapeHtml(label)}<input class="uc-rule-parameter" data-index="${index}" value="${escapeHtml(values[index] ?? '')}" placeholder="${escapeHtml(
                label || t(appState.settings.language, 'contentDashboard.field.ruleParameter')
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
    dashboardState.activeDashboardItemUid = nextItem.uid;
    dashboardState.dashboardDraftItem = null;
    await appState.hooks.refreshRuntimeContent();
    dashboardState.renderContentDashboard(container);
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
      const selectedEventIds = parsedEventTable.eventIds;
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
              <h2>${t(appState.settings.language, 'contentDashboard.category.table')}</h2>
              <p>${contentDashboardSubtitle(item)}</p>
            </div>
            <div class="dashboard-editor-actions">
              <button type="button" id="dashboardDownloadBtn">${t(appState.settings.language, 'contentDashboard.downloadXml')}</button>
              <button type="button" id="dashboardDeleteBtn">${t(appState.settings.language, 'contentDashboard.delete')}</button>
              <button type="submit" id="dashboardSaveBtn">${t(appState.settings.language, 'dialog.button.save')}</button>
            </div>
          </header>
          <div class="dashboard-editor-body event-table-editor-layout">
            <div class="dashboard-form">
              <label>${t(appState.settings.language, 'contentDashboard.field.name')}
                <input id="ucTableName" value="${escapeHtml(parsedEventTable.name)}">
              </label>
              <label>${t(appState.settings.language, 'contentDashboard.field.type')}
                <input value="${escapeHtml(t(appState.settings.language, `contentDashboard.tableType.${parsedEventTable.kind === 'dungeon' ? 'dungeonEvents' : parsedEventTable.kind === 'travel' ? 'travelEvents' : 'settlementEvents'}`))}" readonly>
              </label>
              <div class="dashboard-table-event-picker">
                <label>${t(appState.settings.language, 'contentDashboard.field.availableEvents')}
                  <select id="ucAvailableEventId">${availableOptions}</select>
                </label>
                <div class="dashboard-inline-actions dashboard-table-event-actions">
                  <button type="button" id="ucAddEventBtn">+</button>
                  <button type="button" id="ucRemoveEventBtn">-</button>
                </div>
              </div>
              <label>${t(appState.settings.language, 'contentDashboard.field.selectedEvents')}
                <select id="ucSelectedEventIds" size="12">${selectedOptions}</select>
              </label>
            </div>
            <div class="dashboard-preview-column">
              <section class="dashboard-xml-panel">
                <button type="button" id="ucToggleXmlBtn">${t(appState.settings.language, 'contentDashboard.showXml')}</button>
                <div id="ucXmlPanel" hidden>
                  <label>${t(appState.settings.language, 'contentDashboard.xmlPreview')}
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
          toggleXmlBtn.textContent = t(appState.settings.language, hidden ? 'contentDashboard.showXml' : 'contentDashboard.hideXml');
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
          window.alert(t(appState.settings.language, 'dialog.tableEditor.invalidXml'));
          return;
        }
        if (item.mode === 'new' && !tableName.toLowerCase().startsWith('userdefined-')) {
          window.alert(t(appState.settings.language, 'contentDashboard.tablePrefixError'));
          return;
        }
        const xml = serializeEventOnlyTable(tableName, parsedEventTable.kind, parsedEventTable.eventIds);
        const metadata = parseTableMetadata(xml);
        if (!metadata) {
          window.alert(t(appState.settings.language, 'dialog.tableEditor.invalidXml'));
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
        dashboardState.activeDashboardItemUid = nextItem.uid;
        dashboardState.dashboardDraftItem = null;
        await appState.hooks.refreshRuntimeContent();
        dashboardState.renderContentDashboard(container);
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
      const monsterOptions = Array.from(appState.repository.monsters.values())
        .sort((left, right) => left.name.localeCompare(right.name, undefined, { sensitivity: 'base' }))
        .map((monster) => `<option value="${escapeHtml(monster.id)}">${escapeHtml(`${monster.name} (${monster.id})`)}</option>`)
        .join('');
      const ambienceOptions = getAdventureAmbiences(appState.settings.language)
        .filter((ambience) => !state.draftAmbiences.includes(ambience.value))
        .map((ambience) => `<option value="${escapeHtml(ambience.value)}">${escapeHtml(ambience.label)}</option>`)
        .join('');
      const draftMonsterOptions = state.draftMembers
        .map((entry, index) => `<option value="${index}">${escapeHtml(monsterEntryLabel(entry))}</option>`)
        .join('');
      const draftAmbienceOptions = state.draftAmbiences
        .map((ambience) => {
          const label = getAdventureAmbiences(appState.settings.language).find((entry) => entry.value === ambience)?.label ?? ambience;
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
              <h2>${t(appState.settings.language, 'contentDashboard.category.table')}</h2>
              <p>${contentDashboardSubtitle(item)}</p>
            </div>
            <div class="dashboard-editor-actions">
              <button type="button" id="dashboardDownloadBtn">${t(appState.settings.language, 'contentDashboard.downloadXml')}</button>
              <button type="button" id="dashboardDeleteBtn">${t(appState.settings.language, 'contentDashboard.delete')}</button>
              <button type="submit" id="dashboardSaveBtn">${t(appState.settings.language, 'dialog.button.save')}</button>
            </div>
          </header>
          <div class="dashboard-editor-body monster-table-editor-layout">
            <div class="dashboard-form">
              <label>${t(appState.settings.language, 'contentDashboard.field.name')}
                <input id="ucTableName" value="${escapeHtml(state.name)}">
              </label>
              <label>${t(appState.settings.language, 'contentDashboard.field.monsterType')}
                <select id="ucEncounterMonsterId">${monsterOptions}</select>
              </label>
              <div class="dashboard-inline-fields">
                <label>${t(appState.settings.language, 'contentDashboard.field.monsterMin')}
                  <input id="ucEncounterMin" type="number" min="1" value="1">
                </label>
                <label>${t(appState.settings.language, 'contentDashboard.field.monsterMax')}
                  <input id="ucEncounterMax" type="number" min="1" value="1">
                </label>
              </div>
              <div class="dashboard-inline-actions">
                <button type="button" id="ucAddMonsterToEncounterBtn">${t(appState.settings.language, 'contentDashboard.addMonsterToEncounter')}</button>
                <button type="button" id="ucRemoveMonsterFromEncounterBtn">${t(appState.settings.language, 'contentDashboard.removeMonsterFromEncounter')}</button>
              </div>
              <label>${t(appState.settings.language, 'contentDashboard.field.encounterMonsters')}
                <select id="ucEncounterMonsterList" size="6">${draftMonsterOptions}</select>
              </label>
              <label>${t(appState.settings.language, 'contentDashboard.field.encounterLevel')}
                <select id="ucEncounterLevel">
                  ${Array.from({ length: 10 }, (_, index) => index + 1)
                    .map((level) => `<option value="${level}" ${level === state.draftLevel ? 'selected' : ''}>${level}</option>`)
                    .join('')}
                </select>
              </label>
              <div class="dashboard-table-event-picker">
                <label>${t(appState.settings.language, 'contentDashboard.field.availableAmbiences')}
                  <select id="ucEncounterAmbience">${ambienceOptions}</select>
                </label>
                <div class="dashboard-inline-actions dashboard-table-event-actions">
                  <button type="button" id="ucAddAmbienceBtn">+</button>
                  <button type="button" id="ucRemoveAmbienceBtn">-</button>
                </div>
              </div>
              <label>${t(appState.settings.language, 'contentDashboard.field.encounterAmbiences')}
                <select id="ucEncounterAmbienceList" size="5">${draftAmbienceOptions}</select>
              </label>
              <div class="dashboard-inline-actions">
                <button type="button" id="ucAddEncounterToTableBtn">${t(appState.settings.language, 'contentDashboard.addEncounterToTable')}</button>
                <button type="button" id="ucRemoveEncounterFromTableBtn">${t(appState.settings.language, 'contentDashboard.removeEncounterFromTable')}</button>
              </div>
              <label>${t(appState.settings.language, 'contentDashboard.field.tableEncounters')}
                <select id="ucTableEncounterList" size="10">${encounterOptions}</select>
              </label>
            </div>
            <div class="dashboard-preview-column">
              <section class="dashboard-xml-panel">
                <button type="button" id="ucToggleXmlBtn">${t(appState.settings.language, 'contentDashboard.showXml')}</button>
                <div id="ucXmlPanel" hidden>
                  <label>${t(appState.settings.language, 'contentDashboard.xmlPreview')}
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
          toggleXmlBtn.textContent = t(appState.settings.language, hidden ? 'contentDashboard.showXml' : 'contentDashboard.hideXml');
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
          window.alert(t(appState.settings.language, 'dialog.tableEditor.invalidXml'));
          return;
        }
        if (item.mode === 'new' && !tableName.toLowerCase().startsWith('userdefined-')) {
          window.alert(t(appState.settings.language, 'contentDashboard.tablePrefixError'));
          return;
        }
        const xml = serializeMonsterOnlyTable(tableName, state.entries);
        const metadata = parseTableMetadata(xml);
        if (!metadata) {
          window.alert(t(appState.settings.language, 'dialog.tableEditor.invalidXml'));
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
        dashboardState.activeDashboardItemUid = nextItem.uid;
        dashboardState.dashboardDraftItem = null;
        await appState.hooks.refreshRuntimeContent();
        dashboardState.renderContentDashboard(container);
      });
    };

    renderMonsterTableEditor();
    return;
  }

  editor.innerHTML = renderDashboardEditorShell(
    t(appState.settings.language, 'contentDashboard.category.table'),
    contentDashboardSubtitle(item),
    `
      <label>${t(appState.settings.language, 'contentDashboard.field.tableXml')}
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
      window.alert(t(appState.settings.language, 'dialog.tableEditor.invalidXml'));
      return;
    }
    if (item.mode === 'new' && !metadata.name.trim().toLowerCase().startsWith('userdefined-')) {
      window.alert(t(appState.settings.language, 'contentDashboard.tablePrefixError'));
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
    dashboardState.activeDashboardItemUid = nextItem.uid;
    dashboardState.dashboardDraftItem = null;
    await appState.hooks.refreshRuntimeContent();
    dashboardState.renderContentDashboard(container);
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
          <h2>${t(appState.settings.language, 'contentDashboard.category.objectiveRoomAdventure')}</h2>
          <p>${contentDashboardSubtitle(item)}</p>
        </div>
        <div class="dashboard-editor-actions">
          <button type="button" id="dashboardDownloadBtn">${t(appState.settings.language, 'contentDashboard.downloadXml')}</button>
          <button type="button" id="dashboardDeleteBtn">${t(appState.settings.language, 'contentDashboard.delete')}</button>
          <button type="submit" id="dashboardSaveBtn">${t(appState.settings.language, 'dialog.button.save')}</button>
        </div>
      </header>
      <div class="dashboard-editor-body event-editor-layout">
        <div class="dashboard-form">
          <label>${t(appState.settings.language, 'contentDashboard.field.objectiveRoomName')}
            <select id="ucObjectiveRoomName">${roomOptions}</select>
          </label>
          <label>${t(appState.settings.language, 'contentDashboard.field.name')}<input id="ucName" value="${escapeHtml(data.name)}"></label>
          <label>${t(appState.settings.language, 'contentDashboard.field.flavor')}<textarea id="ucFlavor" rows="6">${escapeHtml(data.flavorText)}</textarea></label>
          <label>${t(appState.settings.language, 'contentDashboard.field.rules')}<textarea id="ucRules" rows="10">${escapeHtml(data.rulesText)}</textarea></label>
          <label class="dashboard-checkbox"><input id="ucGeneric" type="checkbox" ${data.generic ? 'checked' : ''}>${t(appState.settings.language, 'contentDashboard.field.genericMission')}</label>
        </div>
        <div class="dashboard-preview-column">
          <section class="dashboard-xml-panel">
            <button type="button" id="ucToggleXmlBtn">${t(appState.settings.language, 'contentDashboard.showXml')}</button>
            <div id="ucXmlPanel" hidden>
              <label>${t(appState.settings.language, 'contentDashboard.xmlPreview')}
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
      ? t(appState.settings.language, 'contentDashboard.showXml')
      : t(appState.settings.language, 'contentDashboard.hideXml');
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
    dashboardState.activeDashboardItemUid = nextItem.uid;
    dashboardState.dashboardDraftItem = null;
    await appState.hooks.refreshRuntimeContent();
    dashboardState.renderContentDashboard(container);
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
          <h2>${t(appState.settings.language, 'contentDashboard.category.warrior')}</h2>
          <p>${contentDashboardSubtitle(item)}</p>
        </div>
        <div class="dashboard-editor-actions">
          <button type="button" id="dashboardDownloadBtn">${t(appState.settings.language, 'contentDashboard.downloadXml')}</button>
          <button type="button" id="dashboardDeleteBtn">${t(appState.settings.language, 'contentDashboard.delete')}</button>
          <button type="submit" id="dashboardSaveBtn">${t(appState.settings.language, 'dialog.button.save')}</button>
        </div>
      </header>
      <div class="dashboard-editor-body event-editor-layout">
        <div class="dashboard-form">
          <label>${t(appState.settings.language, 'contentDashboard.field.name')}<input id="ucName" value="${escapeHtml(data.name)}"></label>
          <label>${t(appState.settings.language, 'contentDashboard.field.race')}<input id="ucRace" value="${escapeHtml(data.race)}"></label>
          <div class="dashboard-tile-upload">
            <label>${t(appState.settings.language, 'contentDashboard.field.counterPath')}<input id="ucCounterPath" value="${escapeHtml(getCounterAssetDisplayName(data.counterPath))}" readonly></label>
            <div class="dashboard-inline-actions">
              <button type="button" id="ucUploadCounterBtn">${t(appState.settings.language, 'contentDashboard.uploadCounter')}</button>
              <input id="ucCounterFile" type="file" accept="image/*" hidden>
            </div>
          </div>
          <label>${t(appState.settings.language, 'contentDashboard.field.rulesPath')}<input id="ucRulesPath" value="${escapeHtml(data.rulesPath)}"></label>
        </div>
        <div class="dashboard-preview-column">
          <section class="dashboard-card-preview warrior-counter-preview">
            <h3>${t(appState.settings.language, 'contentDashboard.cardPreview')}</h3>
            <div id="ucWarriorPreview"></div>
          </section>
          <section class="dashboard-xml-panel">
            <button type="button" id="ucToggleXmlBtn">${t(appState.settings.language, 'contentDashboard.showXml')}</button>
            <div id="ucXmlPanel" hidden>
              <label>${t(appState.settings.language, 'contentDashboard.xmlPreview')}
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
        name: draft.name || t(appState.settings.language, 'contentDashboard.category.warrior'),
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
      ? t(appState.settings.language, 'contentDashboard.showXml')
      : t(appState.settings.language, 'contentDashboard.hideXml');
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
    dashboardState.activeDashboardItemUid = nextItem.uid;
    dashboardState.dashboardDraftItem = null;
    await appState.hooks.refreshRuntimeContent();
    dashboardState.renderContentDashboard(container);
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
      typeSelect.innerHTML = getSettlementTypes(appState.settings.language)
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
        ...Array.from(appState.repository.warriors.values())
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
          <h2>${t(appState.settings.language, 'contentDashboard.category.location')}</h2>
          <p>${contentDashboardSubtitle(item)}</p>
        </div>
        <div class="dashboard-editor-actions">
          <button type="button" id="dashboardDownloadBtn">${t(appState.settings.language, 'contentDashboard.downloadXml')}</button>
          <button type="button" id="dashboardDeleteBtn">${t(appState.settings.language, 'contentDashboard.delete')}</button>
          <button type="submit" id="dashboardSaveBtn">${t(appState.settings.language, 'dialog.button.save')}</button>
        </div>
      </header>
      <div class="dashboard-editor-body event-editor-layout">
        <div class="dashboard-form">
          <label>${t(appState.settings.language, 'contentDashboard.field.name')}<input id="ucName" value="${escapeHtml(state.name)}"></label>
          <label>${t(appState.settings.language, 'contentDashboard.field.description')}<textarea id="ucDescription" rows="5">${escapeHtml(state.description)}</textarea></label>
          <label>${t(appState.settings.language, 'contentDashboard.field.rules')}<textarea id="ucRules" rows="10">${escapeHtml(state.rules)}</textarea></label>
          <div class="dashboard-selector-block">
            <label>${t(appState.settings.language, 'contentDashboard.field.availableTypeOption')}
              <select id="ucLocationTypeSelect"></select>
            </label>
            <div class="dashboard-inline-actions">
              <button type="button" id="ucAddLocationTypeBtn">+</button>
              <button type="button" id="ucRemoveLocationTypeBtn">-</button>
            </div>
            <label>${t(appState.settings.language, 'contentDashboard.field.availableTypes')}
              <select id="ucLocationTypesList" size="5"></select>
            </label>
          </div>
          <div class="dashboard-selector-block">
            <label>${t(appState.settings.language, 'contentDashboard.field.availableVisitorOption')}
              <select id="ucVisitorSelect"></select>
            </label>
            <div class="dashboard-inline-actions">
              <button type="button" id="ucAddVisitorBtn">+</button>
              <button type="button" id="ucRemoveVisitorBtn">-</button>
            </div>
            <label>${t(appState.settings.language, 'contentDashboard.field.visitors')}
              <select id="ucVisitorsList" size="6"></select>
            </label>
          </div>
        </div>
        <div class="dashboard-preview-column">
          <section class="dashboard-card-preview settlement-location-preview">
            <h3>${t(appState.settings.language, 'contentDashboard.cardPreview')}</h3>
            <div id="ucLocationPreview"></div>
          </section>
          <section class="dashboard-xml-panel">
            <button type="button" id="ucToggleXmlBtn">${t(appState.settings.language, 'contentDashboard.showXml')}</button>
            <div id="ucXmlPanel" hidden>
              <label>${t(appState.settings.language, 'contentDashboard.xmlPreview')}
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
      ? t(appState.settings.language, 'contentDashboard.showXml')
      : t(appState.settings.language, 'contentDashboard.hideXml');
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
    dashboardState.activeDashboardItemUid = nextItem.uid;
    dashboardState.dashboardDraftItem = null;
    await appState.hooks.refreshRuntimeContent();
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
