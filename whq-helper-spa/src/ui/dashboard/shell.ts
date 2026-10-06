import { t } from '../../i18n';
import { appState } from '../../state';
import type { UserContentKind } from '../../userContent/types';
import { escapeHtml } from '../formatting';
import { openMaintenanceDialog } from '../maintenanceDialog';
import { renderObjectiveRoomAdventureEditor } from './adventureEditor';
import { contentDashboardSubtitle } from './common';
import {
  createBlankDashboardItem,
  createBlankEventTableItem,
  createModifiedDashboardItem,
  currentDashboardItem,
  dashboardItemsByKind,
  dashboardSourceOptions
} from './data';
import { renderDungeonCardEditor } from './dungeonCardEditor';
import { renderEventEditor } from './eventEditor';
import { renderLocationEditor } from './locationEditor';
import { renderMonsterEditor } from './monsterEditor';
import { renderRuleEditor } from './ruleEditor';
import { DASHBOARD_CATEGORIES, DASHBOARD_CREATE_PREFIX, dashboardState } from './state';
import { renderTableEditor } from './tableEditor';
import { renderWarriorEditor } from './warriorEditor';

export function renderDashboardTree(container: HTMLElement): void {
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

export function renderDashboardHome(editor: HTMLElement): void {
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

export function renderDashboardCreateSelector(container: HTMLElement, editor: HTMLElement, kind: UserContentKind): void {
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

export function renderDashboardEditor(container: HTMLElement): void {
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

export function renderContentDashboard(container: HTMLElement): void {
  renderDashboardTree(container);
  renderDashboardEditor(container);
}

export async function openContentDashboardDialog(): Promise<void> {
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

export async function closeContentDashboardView(): Promise<void> {
  const container = document.querySelector<HTMLElement>('#contentDashboardView');
  if (!container) {
    return;
  }

  await appState.hooks.refreshRuntimeContent();
  document.body.classList.remove('dashboard-active');
  container.hidden = true;
  container.innerHTML = '';
}
