import { t } from '../../i18n';
import { renderEventCard } from '../../render';
import { appState } from '../../state';
import {
  upsertUserContentItem,
  userContentItemXml,
  type UserContentItem,
  type UserEventData
} from '../../userContent';
import { fitTreasureHeaderText } from '../cardWindows';
import { escapeHtml, treasureUsersFromFlags, treasureUsersToFlags } from '../formatting';
import { bindDashboardCommonActions, contentDashboardSubtitle } from './common';
import { DASHBOARD_CATEGORIES, dashboardState } from './state';

export function renderEventEditor(
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
