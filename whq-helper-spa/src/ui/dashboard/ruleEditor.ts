import { t } from '../../i18n';
import { appState } from '../../state';
import {
  upsertUserContentItem,
  userContentItemXml,
  type UserContentItem,
  type UserRuleData
} from '../../userContent';
import { escapeHtml } from '../formatting';
import { bindDashboardCommonActions, contentDashboardSubtitle } from './common';
import { dashboardState } from './state';

export function renderRuleEditor(container: HTMLElement, item: Extract<UserContentItem, { kind: 'rule' }>): void {
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
