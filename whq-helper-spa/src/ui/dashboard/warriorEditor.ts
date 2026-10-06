import { getCounterAssetDisplayName, resolveCounterAsset, saveCounterAsset } from '../../counterAssets';
import { t } from '../../i18n';
import { appState } from '../../state';
import { upsertUserContentItem, userContentItemXml } from '../../userContent';
import type { UserContentItem, UserWarriorData } from '../../userContent/types';
import { escapeHtml, readFileAsDataUrl } from '../formatting';
import { bindDashboardCommonActions, contentDashboardSubtitle } from './common';
import { dashboardState } from './state';

export function renderWarriorEditor(container: HTMLElement, item: Extract<UserContentItem, { kind: 'warrior' }>): void {
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
