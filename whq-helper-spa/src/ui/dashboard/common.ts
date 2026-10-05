import { t } from '../../i18n';
import { appState } from '../../state';
import { deleteUserContentItem, userContentItemXml, type UserContentItem } from '../../userContent';
import { escapeHtml } from '../formatting';
import { isDashboardDraftSelected } from './data';
import { dashboardState } from './state';

export function downloadUserXml(item: UserContentItem): void {
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

export function contentDashboardSubtitle(item: UserContentItem): string {
  return item.mode === 'modified'
    ? t(appState.settings.language, 'contentDashboard.mode.modified')
    : t(appState.settings.language, 'contentDashboard.mode.new');
}

export function bindDashboardCommonActions(container: HTMLElement, item: UserContentItem): void {
  container.querySelector<HTMLButtonElement>('#dashboardDeleteBtn')?.addEventListener('click', async () => {
    if (isDashboardDraftSelected()) {
      dashboardState.dashboardDraftItem = null;
      dashboardState.activeDashboardItemUid = null;
      dashboardState.renderContentDashboard(container);
      return;
    }
    deleteUserContentItem(item.uid);
    dashboardState.dashboardDraftItem = null;
    dashboardState.activeDashboardItemUid = null;
    await appState.hooks.refreshRuntimeContent();
    dashboardState.renderContentDashboard(container);
  });

  container.querySelector<HTMLButtonElement>('#dashboardDownloadBtn')?.addEventListener('click', () => {
    downloadUserXml(item);
  });
}

export function renderDashboardEditorShell(title: string, subtitle: string, body: string, xmlPreview: string): string {
  return `
    <form class="dashboard-editor-shell">
      <header class="dashboard-editor-header">
        <div>
          <h2>${title}</h2>
          <p>${subtitle}</p>
        </div>
        <div class="dashboard-editor-actions">
          <button type="button" id="dashboardDownloadBtn">${t(appState.settings.language, 'contentDashboard.downloadXml')}</button>
          <button type="button" id="dashboardDeleteBtn">${t(appState.settings.language, 'contentDashboard.delete')}</button>
          <button type="submit" id="dashboardSaveBtn">${t(appState.settings.language, 'dialog.button.save')}</button>
        </div>
      </header>
      <div class="dashboard-editor-body">
        <div class="dashboard-form">${body}</div>
        <div class="dashboard-xml-preview">
          <label>${t(appState.settings.language, 'contentDashboard.xmlPreview')}
            <textarea rows="18" readonly>${escapeHtml(xmlPreview)}</textarea>
          </label>
        </div>
      </div>
    </form>
  `;
}
