import { getAdventureAmbiences, t } from '../../i18n';
import { appState } from '../../state';
import type { GroupEntry, MonsterEntry } from '../../types';
import { upsertUserContentItem, userContentItemXml } from '../../userContent';
import type { UserContentItem, UserTableData } from '../../userContent/types';
import { parseTableMetadata } from '../../userContent/xml';
import { escapeHtml } from '../formatting';
import { bindDashboardCommonActions, contentDashboardSubtitle, renderDashboardEditorShell } from './common';
import {
  availableEventItemsForTable,
  monsterEntryLabel,
  parseEventOnlyTable,
  parseMonsterOnlyTable,
  serializeEventOnlyTable,
  serializeMonsterOnlyTable,
  tableEncounterLabel
} from './data';
import { dashboardState } from './state';

export function renderTableEditor(container: HTMLElement, item: Extract<UserContentItem, { kind: 'table' }>): void {
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
