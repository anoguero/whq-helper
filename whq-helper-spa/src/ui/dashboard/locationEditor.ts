import { getSettlementTypes, t } from '../../i18n';
import { renderSettlementLocationCard } from '../../render';
import { appState } from '../../state';
import type { SettlementLocation, SettlementType } from '../../types';
import { upsertUserContentItem, userContentItemXml } from '../../userContent';
import type { UserContentItem, UserLocationData } from '../../userContent/types';
import { escapeHtml } from '../formatting';
import { locationVisitorLabel, settlementTypeLabel } from '../settlementPanel';
import { bindDashboardCommonActions, contentDashboardSubtitle } from './common';
import { dashboardState } from './state';

export function renderLocationEditor(container: HTMLElement, item: Extract<UserContentItem, { kind: 'location' }>): void {
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
