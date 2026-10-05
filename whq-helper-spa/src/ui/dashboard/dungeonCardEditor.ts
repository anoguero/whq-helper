import { renderDungeonCardToCanvasLocalized } from '../../dungeonRenderer';
import { t } from '../../i18n';
import { appState } from '../../state';
import { getTileAssetDisplayName, saveTileAsset } from '../../tileAssets';
import type { DungeonCard } from '../../types';
import {
  upsertUserContentItem,
  userContentItemXml,
  type UserContentItem,
  type UserDungeonCardData
} from '../../userContent';
import { getWhiteDwarfReference } from '../../whiteDwarfReferences';
import { escapeHtml, readFileAsDataUrl } from '../formatting';
import { openWhiteDwarfReferenceDialog } from '../whiteDwarfReferenceDialog';
import { bindDashboardCommonActions, contentDashboardSubtitle } from './common';
import { dashboardState } from './state';

export function renderDungeonCardEditor(container: HTMLElement, item: Extract<UserContentItem, { kind: 'dungeonCard' }>): void {
  const editor = container.querySelector<HTMLElement>('#contentDashboardEditor');
  if (!editor) {
    return;
  }
  const data = item.data as UserDungeonCardData;
  editor.innerHTML = `
    <form class="dashboard-editor-shell">
      <header class="dashboard-editor-header">
        <div>
          <h2>${t(appState.settings.language, 'contentDashboard.category.dungeonCard')}</h2>
          <p>${contentDashboardSubtitle(item)}</p>
        </div>
        <div class="dashboard-editor-actions">
          <button type="button" id="dashboardDownloadBtn">${t(appState.settings.language, 'contentDashboard.downloadXml')}</button>
          <button type="button" id="dashboardDeleteBtn">${t(appState.settings.language, 'contentDashboard.delete')}</button>
          <button type="submit" id="dashboardSaveBtn">${t(appState.settings.language, 'dialog.button.save')}</button>
        </div>
      </header>
      <div class="dashboard-editor-body dungeon-card-editor-layout">
        <div class="dashboard-form">
          <label>${t(appState.settings.language, 'contentDashboard.field.name')}<input id="ucName" value="${escapeHtml(data.name)}"></label>
          <label>${t(appState.settings.language, 'contentDashboard.field.type')}
            <select id="ucType">
              <option value="DUNGEON_ROOM" ${data.type === 'DUNGEON_ROOM' ? 'selected' : ''}>${t(appState.settings.language, 'dungeon.cardType.DUNGEON_ROOM')}</option>
              <option value="OBJECTIVE_ROOM" ${data.type === 'OBJECTIVE_ROOM' ? 'selected' : ''}>${t(appState.settings.language, 'dungeon.cardType.OBJECTIVE_ROOM')}</option>
              <option value="CORRIDOR" ${data.type === 'CORRIDOR' ? 'selected' : ''}>${t(appState.settings.language, 'dungeon.cardType.CORRIDOR')}</option>
              <option value="SPECIAL" ${data.type === 'SPECIAL' ? 'selected' : ''}>${t(appState.settings.language, 'dungeon.cardType.SPECIAL')}</option>
            </select>
          </label>
          <label>${t(appState.settings.language, 'contentDashboard.field.environment')}<input id="ucEnvironment" value="${escapeHtml(data.environment)}"></label>
          <div class="dashboard-tile-upload">
            <label>${t(appState.settings.language, 'contentDashboard.field.tileImagePath')}<input id="ucTileImagePath" value="${escapeHtml(getTileAssetDisplayName(data.tileImagePath))}" readonly></label>
            <div class="dashboard-inline-actions">
              <button type="button" id="ucUploadTileBtn">${t(appState.settings.language, 'contentDashboard.uploadTile')}</button>
              <input id="ucTileFile" type="file" accept="image/*" hidden>
            </div>
          </div>
          <label>${t(appState.settings.language, 'contentDashboard.field.description')}<textarea id="ucDescription" rows="6">${escapeHtml(data.descriptionText)}</textarea></label>
          <label>${t(appState.settings.language, 'contentDashboard.field.rules')}<textarea id="ucRules" rows="8">${escapeHtml(data.rulesText)}</textarea></label>
          <div class="dashboard-inline-fields">
            <label>${t(appState.settings.language, 'contentDashboard.field.copyCount')}<input id="ucCopyCount" type="number" min="0" value="${data.copyCount}"></label>
            <label class="dashboard-checkbox dashboard-inline-checkbox"><input id="ucEnabled" type="checkbox" ${data.enabled ? 'checked' : ''}>${t(appState.settings.language, 'contentDashboard.field.enabled')}</label>
          </div>
        </div>
        <div class="dashboard-preview-column">
          <section class="dashboard-card-preview">
            <h3>${t(appState.settings.language, 'contentDashboard.cardPreview')}</h3>
            <button type="button" id="ucWhiteDwarfReferenceBtn" ${getWhiteDwarfReference({
              id: data.id,
              name: data.name,
              type: data.type,
              environment: data.environment,
              copyCount: data.copyCount,
              enabled: data.enabled,
              tileImagePath: data.tileImagePath,
              descriptionText: data.descriptionText,
              rulesText: data.rulesText
            } as DungeonCard) ? '' : 'disabled'}>${t(appState.settings.language, 'dialog.whiteDwarfReference.button')}</button>
            <canvas id="ucPreviewCanvas" width="847" height="1264"></canvas>
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
  const previewCanvas = editor.querySelector<HTMLCanvasElement>('#ucPreviewCanvas');
  const xmlPreview = editor.querySelector<HTMLTextAreaElement>('#ucXmlPreview');
  const xmlPanel = editor.querySelector<HTMLElement>('#ucXmlPanel');
  const toggleXmlButton = editor.querySelector<HTMLButtonElement>('#ucToggleXmlBtn');
  const tilePathInput = editor.querySelector<HTMLInputElement>('#ucTileImagePath');
  const tileFileInput = editor.querySelector<HTMLInputElement>('#ucTileFile');
  const whiteDwarfReferenceButton = editor.querySelector<HTMLButtonElement>('#ucWhiteDwarfReferenceBtn');

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
    if (whiteDwarfReferenceButton) {
      whiteDwarfReferenceButton.disabled = !getWhiteDwarfReference(draftCard);
    }
    if (previewCanvas) {
      renderDungeonCardToCanvasLocalized(previewCanvas, draftCard, appState.settings.language).catch((error) => console.error(error));
    }
    if (xmlPreview) {
      xmlPreview.value = userContentItemXml({
        ...item,
        data: draftCard
      });
    }
  };

  whiteDwarfReferenceButton?.addEventListener('click', () => {
    openWhiteDwarfReferenceDialog(buildDraftCard());
  });

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
    dashboardState.activeDashboardItemUid = nextItem.uid;
    dashboardState.dashboardDraftItem = null;
    await appState.hooks.refreshRuntimeContent();
    dashboardState.renderContentDashboard(container);
  });
}
