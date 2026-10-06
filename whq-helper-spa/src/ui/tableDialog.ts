import { t } from '../i18n';
import { appState } from '../state';
import type { TableModel } from '../types';
import { escapeHtml } from './formatting';

export function openTableDialog(): void {
  const dialog = document.querySelector<HTMLDialogElement>('#tableDialog');
  const list = document.querySelector<HTMLElement>('#tableList');
  if (!dialog || !list) {
    return;
  }

  type TableTheme = 'events' | 'monsters' | 'settlement' | 'travel' | 'treasure' | 'objectiveTreasure';
  const themeOrder: TableTheme[] = ['events', 'monsters', 'settlement', 'travel', 'treasure', 'objectiveTreasure'];
  const themeLabelKey: Record<TableTheme, string> = {
    events: 'dialog.tableSettings.group.events',
    monsters: 'dialog.tableSettings.group.monsters',
    settlement: 'dialog.tableSettings.group.settlement',
    travel: 'dialog.tableSettings.group.travel',
    treasure: 'dialog.tableSettings.group.treasure',
    objectiveTreasure: 'dialog.tableSettings.group.objectiveTreasure'
  };

  const tableTheme = (table: TableModel): TableTheme => {
    const lowerName = table.name.toLowerCase();
    if (table.kind === 'settlement') {
      return 'settlement';
    }
    if (table.kind === 'travel') {
      return 'travel';
    }
    if (table.kind === 'treasure') {
      return lowerName.includes('objective') || lowerName.includes('objetive') ? 'objectiveTreasure' : 'treasure';
    }
    if (table.monsters.length > 0) {
      return 'monsters';
    }
    return 'events';
  };

  const tables = Array.from(appState.repository.tables.values()).sort((a, b) => {
    const themeDiff = themeOrder.indexOf(tableTheme(a)) - themeOrder.indexOf(tableTheme(b));
    if (themeDiff !== 0) {
      return themeDiff;
    }
    return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });
  });

  const groupedTables = new Map<TableTheme, TableModel[]>();
  for (const theme of themeOrder) {
    groupedTables.set(theme, []);
  }
  for (const table of tables) {
    groupedTables.get(tableTheme(table))?.push(table);
  }

  list.innerHTML = themeOrder
    .map((theme) => {
      const items = groupedTables.get(theme) ?? [];
      if (items.length === 0) {
        return '';
      }
      return `
        <section class="table-section">
          <h3>${t(appState.settings.language, themeLabelKey[theme])}</h3>
          ${items
            .map(
              (table) => `
                <label>
                  <input type="checkbox" data-table-name="${escapeHtml(table.name)}" ${table.active ? 'checked' : ''}>
                  ${escapeHtml(table.name)}
                </label>
              `
            )
            .join('')}
        </section>
      `;
    })
    .join('');

  const saveButton = dialog.querySelector<HTMLButtonElement>('#saveTables');
  const handleSave = (event: Event) => {
    event.preventDefault();
    list.querySelectorAll<HTMLInputElement>('input[type="checkbox"]').forEach((checkbox) => {
      const tableName = checkbox.dataset.tableName;
      if (!tableName) {
        return;
      }
      const table = appState.repository.tables.get(tableName);
      if (!table) {
        return;
      }
      table.active = checkbox.checked;
      appState.settings.tableActiveById[table.id] = checkbox.checked;
    });

    appState.hooks.rebuildDecks();
    dialog.close();
  };

  // Asignamos onclick (en vez de addEventListener con { once: true }) para reemplazar el handler
  // en cada apertura: con addEventListener, cerrar sin guardar dejaba el listener vivo y las
  // siguientes aperturas los acumulaban, ejecutando el guardado varias veces.
  if (saveButton) {
    saveButton.onclick = handleSave;
  }
  dialog.showModal();
}
