import { t } from '../../i18n';
import { appState } from '../../state';
import { upsertUserContentItem, userContentItemXml } from '../../userContent';
import type { UserContentItem, UserObjectiveRoomAdventureData } from '../../userContent/types';
import { escapeHtml } from '../formatting';
import { bindDashboardCommonActions, contentDashboardSubtitle } from './common';
import { availableObjectiveRoomNames } from './data';
import { dashboardState } from './state';

export function renderObjectiveRoomAdventureEditor(
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
