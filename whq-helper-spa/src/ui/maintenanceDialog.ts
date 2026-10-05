import { t, tf } from '../i18n';
import { appState, refreshDungeonCards } from '../state';
import { escapeHtml } from './formatting';

export function openMaintenanceDialog(): void {
  const dialog = document.querySelector<HTMLDialogElement>('#maintenanceDialog');
  if (!dialog) {
    return;
  }

  const cards = appState.dungeonStore
    .loadCards()
    .slice()
    .sort((left, right) => {
      const env = left.environment.localeCompare(right.environment);
      if (env !== 0) {
        return env;
      }
      const type = left.type.localeCompare(right.type);
      if (type !== 0) {
        return type;
      }
      return left.name.localeCompare(right.name);
    });

  dialog.innerHTML = `
    <form method="dialog" class="maintenance-grid">
      <h2>${t(appState.settings.language, 'dialog.tileConfig.title')}</h2>
      <p>${t(appState.settings.language, 'dialog.tileConfig.description')}</p>
      <div class="tile-config-toolbar">
        <label>
          ${t(appState.settings.language, 'dialog.tileConfig.environment')}
          <select id="tileEnvSelect"></select>
        </label>
        <button type="button" id="tileEnableEnvBtn">${t(appState.settings.language, 'dialog.tileConfig.enableEnvironment')}</button>
        <button type="button" id="tileDisableEnvBtn">${t(appState.settings.language, 'dialog.tileConfig.disableEnvironment')}</button>
      </div>
      <section id="maintenanceList" class="table-list"></section>
      <menu>
        <button value="cancel">${t(appState.settings.language, 'dialog.button.cancel')}</button>
        <button type="button" id="mSave">${t(appState.settings.language, 'dialog.button.save')}</button>
      </menu>
    </form>
  `;

  const list = dialog.querySelector<HTMLElement>('#maintenanceList');
  const saveButton = dialog.querySelector<HTMLButtonElement>('#mSave');
  const environmentSelect = dialog.querySelector<HTMLSelectElement>('#tileEnvSelect');
  const enableEnvironmentButton = dialog.querySelector<HTMLButtonElement>('#tileEnableEnvBtn');
  const disableEnvironmentButton = dialog.querySelector<HTMLButtonElement>('#tileDisableEnvBtn');
  if (!list || !saveButton || !environmentSelect || !enableEnvironmentButton || !disableEnvironmentButton) {
    return;
  }

  if (cards.length === 0) {
    list.textContent = t(appState.settings.language, 'dialog.tileConfig.empty');
    saveButton.disabled = true;
    environmentSelect.disabled = true;
    enableEnvironmentButton.disabled = true;
    disableEnvironmentButton.disabled = true;
    dialog.showModal();
    return;
  }

  const enabledById = new Map<number, boolean>(cards.map((card) => [card.id, card.enabled]));
  const checkboxById = new Map<number, HTMLInputElement>();
  const environments = [...new Set(cards.map((card) => card.environment))].sort((a, b) =>
    a.localeCompare(b, undefined, { sensitivity: 'base' })
  );
  environmentSelect.innerHTML = environments.map((environment) => `<option value="${escapeHtml(environment)}">${escapeHtml(environment)}</option>`).join('');

  for (const card of cards) {
    const row = document.createElement('label');
    row.className = 'tile-toggle';

    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.checked = enabledById.get(card.id) ?? false;
    checkbox.addEventListener('change', () => {
      enabledById.set(card.id, checkbox.checked);
    });
    checkboxById.set(card.id, checkbox);

    const text = document.createElement('span');
    text.textContent = tf(appState.settings.language, 'dialog.tileConfig.tileEntry', {
      id: card.id,
      name: card.name,
      type: t(appState.settings.language, `dungeon.cardType.${card.type}`),
      environment: card.environment
    });

    row.append(checkbox, text);
    list.append(row);
  }

  const applyEnvironmentValue = (enabled: boolean) => {
    const selectedEnvironment = environmentSelect.value.trim();
    if (!selectedEnvironment) {
      return;
    }

    for (const card of cards) {
      if (card.environment.localeCompare(selectedEnvironment, undefined, { sensitivity: 'base' }) !== 0) {
        continue;
      }
      enabledById.set(card.id, enabled);
      const checkbox = checkboxById.get(card.id);
      if (checkbox) {
        checkbox.checked = enabled;
      }
    }
  };

  enableEnvironmentButton.addEventListener('click', () => applyEnvironmentValue(true));
  disableEnvironmentButton.addEventListener('click', () => applyEnvironmentValue(false));

  saveButton.addEventListener('click', () => {
    let changed = false;
    for (const card of cards) {
      const enabled = enabledById.get(card.id) ?? false;
      if (enabled === card.enabled) {
        continue;
      }
      changed = true;
      appState.dungeonStore.updateCard({
        ...card,
        enabled
      });
    }

    if (changed) {
      refreshDungeonCards();
      appState.hooks.rebuildDecks();
    }
    dialog.close();
  });

  dialog.showModal();
}
