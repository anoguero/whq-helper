import { getSettlementTypes, t } from '../i18n';
import { renderSettlementLocationCard } from '../render';
import { appState } from '../state';
import type { SettlementLocation, SettlementType } from '../types';
import { closeAllOpenCards } from './cardWindows';
import { drawFromDeck } from './decks';
import { escapeHtml } from './formatting';

export function locationVisitorLabel(visitorId: string): string {
  if (visitorId === 'all') {
    return t(appState.settings.language, 'settlement.type.any');
  }
  // Los ids de visitante en locations.xml van sin el prefijo "warrior-" con el que se registran los
  // guerreros (p.ej. "elf" -> "warrior-elf"). Intentamos ambas formas antes de caer al id crudo.
  const warrior = appState.repository.warriors.get(visitorId) ?? appState.repository.warriors.get(`warrior-${visitorId}`);
  return warrior?.name ?? visitorId;
}

export function settlementTypeLabel(type: SettlementType): string {
  return t(appState.settings.language, `settlement.type.${type}`);
}

export function normalizeSettlementLocationType(value: string): SettlementType {
  if (value === 'city' || value === 'town' || value === 'village' || value === 'outskirts' || value === 'special') {
    return value;
  }
  return 'any';
}

export function settlementLocationsForType(type: SettlementType): SettlementLocation[] {
  return Array.from(appState.repository.locations.values())
    .filter((location) => type === 'any' || location.availableTypes.includes(type))
    .sort((left, right) => left.name.localeCompare(right.name, undefined, { sensitivity: 'base' }));
}

export function openSettlementSimulatorPanel(): void {
  const panel = document.querySelector<HTMLElement>('#settlementSimulatorPanel');
  if (!panel) {
    return;
  }
  let currentType: SettlementType = 'any';
  let selectedLocationId = settlementLocationsForType(currentType)[0]?.id ?? '';

  const renderSettlement = (): void => {
    const locations = settlementLocationsForType(currentType);
    if (!locations.some((location) => location.id === selectedLocationId)) {
      selectedLocationId = locations[0]?.id ?? '';
    }
    const selected = locations.find((location) => location.id === selectedLocationId) ?? null;
    panel.innerHTML = `
      <section class="simulator-layout settlement-inline-layout">
        <header>
          <h2>${t(appState.settings.language, 'settlement.title')}</h2>
          <p>${t(appState.settings.language, 'deck.settlement.subtitle')}</p>
        </header>
        <section class="settlement-layout">
          <div class="settlement-left">
            <section class="settlement-top">
              <article class="deck settlement-settlement-deck">
              <label style="width: 100%; color: beige;">${t(appState.settings.language, 'settlement.type')}
                <select id="settlementTypeSelect" style="width: 100%">
                  ${getSettlementTypes(appState.settings.language)
                    .map((entry) => `<option value="${entry.value}" ${entry.value === currentType ? 'selected' : ''}>${escapeHtml(entry.label)}</option>`)
                    .join('')}
                </select>
              </label>
              <label style="width: 100%; color: beige;">${t(appState.settings.language, 'settlement.locations')}
                <select id="settlementLocationList" size="12" style="width: 100%">
                  ${
                    locations.length > 0
                      ? locations
                          .map((location) => `<option value="${escapeHtml(location.id)}" ${location.id === selectedLocationId ? 'selected' : ''}>${escapeHtml(location.name)}</option>`)
                          .join('')
                      : `<option value="">${escapeHtml(t(appState.settings.language, 'settlement.noLocations'))}</option>`
                  }
                </select>
              </label>
              </article>
            </section>
            <section class="settlement-bottom">
              <article class="deck settlement-settlement-deck">
                <button type="button" class="deck-button" id="settlementDrawBtn">
                  <img src="/data/graphics/settlement.png" alt="${escapeHtml(t(appState.settings.language, 'deck.settlement.title'))}" />
                  <span>${t(appState.settings.language, 'button.clickHere')}</span>
                </button>
                <small>${appState.decks.settlement.size()} ${t(appState.settings.language, 'controls.entries')}</small>
                <div class="dashboard-inline-actions">
                  <button type="button" class="secondary-button" id="settlementCloseCardsBtn">${t(appState.settings.language, 'menu.item.closeAllCards')}</button>
                  <button type="button" class="secondary-button" id="closeSettlementPanelBtn">${t(appState.settings.language, 'dialog.button.close')}</button>
                </div>
              </article>
            </section>
          </div>
          <div class="settlement-preview">
            ${
              selected
                ? renderSettlementLocationCard(selected, selected.visitors.map((visitor) => locationVisitorLabel(visitor)))
                : `<p>${escapeHtml(t(appState.settings.language, 'settlement.noLocations'))}</p>`
            }
          </div>
        </section>
      </section>
    `;

    panel.querySelector<HTMLSelectElement>('#settlementTypeSelect')?.addEventListener('change', (event) => {
      currentType = normalizeSettlementLocationType((event.currentTarget as HTMLSelectElement).value);
      renderSettlement();
    });
    panel.querySelector<HTMLSelectElement>('#settlementLocationList')?.addEventListener('change', (event) => {
      selectedLocationId = (event.currentTarget as HTMLSelectElement).value;
      renderSettlement();
    });
    panel.querySelector<HTMLButtonElement>('#settlementDrawBtn')?.addEventListener('click', () => {
      drawFromDeck('settlement');
      const entries = panel.querySelector<HTMLElement>('.settlement-settlement-deck small');
      if (entries) {
        entries.textContent = `${appState.decks.settlement.size()} ${t(appState.settings.language, 'controls.entries')}`;
      }
    });
    panel.querySelector<HTMLButtonElement>('#settlementCloseCardsBtn')?.addEventListener('click', () => {
      closeAllOpenCards();
    });
    panel.querySelector<HTMLButtonElement>('#closeSettlementPanelBtn')?.addEventListener('click', () => {
      closeSettlementSimulatorPanel();
    });
  };

  panel.hidden = false;
  panel.classList.add('active');
  document.body.classList.add('settlement-panel-active');
  renderSettlement();
}

export function closeSettlementSimulatorPanel(): void {
  const panel = document.querySelector<HTMLElement>('#settlementSimulatorPanel');
  if (!panel) {
    return;
  }
  panel.hidden = true;
  panel.classList.remove('active');
  panel.innerHTML = '';
  document.body.classList.remove('settlement-panel-active');
}
