import { t } from '../i18n';
import { appState } from '../state';
import type { AppSettings, DeckBundle } from '../types';
import { showEntry } from './cardWindows';
import { openTableDialog } from './tableDialog';

export interface DeckMeta {
  key: keyof DeckBundle;
  titleKey: string;
  subtitleKey: string;
  image: string;
  toggleKey: keyof Pick<
    AppSettings,
    'showEventDeck' | 'showSettlementDeck' | 'showTravelDeck' | 'showTreasureDeck' | 'showObjectiveTreasureDeck'
  >;
}

export const DECKS: DeckMeta[] = [
  {
    key: 'dungeon',
    titleKey: 'deck.events.title',
    subtitleKey: 'deck.events.subtitle',
    image: '/data/graphics/eventback.png',
    toggleKey: 'showEventDeck'
  },
  {
    key: 'settlement',
    titleKey: 'deck.settlement.title',
    subtitleKey: 'deck.settlement.subtitle',
    image: '/data/graphics/settlement.png',
    toggleKey: 'showSettlementDeck'
  },
  {
    key: 'travel',
    titleKey: 'deck.travel.title',
    subtitleKey: 'deck.travel.subtitle',
    image: '/data/graphics/travel.png',
    toggleKey: 'showTravelDeck'
  },
  {
    key: 'treasure',
    titleKey: 'deck.treasure.title',
    subtitleKey: 'deck.treasure.subtitle',
    image: '/data/graphics/treasureback.png',
    toggleKey: 'showTreasureDeck'
  },
  {
    key: 'objectiveTreasure',
    titleKey: 'deck.objectiveTreasure.title',
    subtitleKey: 'deck.objectiveTreasure.subtitle',
    image: '/data/graphics/objective-treasureback.png',
    toggleKey: 'showObjectiveTreasureDeck'
  }
];

export function deckVisible(meta: DeckMeta): boolean {
  return appState.settings[meta.toggleKey];
}

export function renderDecks(): void {
  const section = document.querySelector<HTMLElement>('#decks');
  if (!section) {
    return;
  }

  section.innerHTML = DECKS.filter(deckVisible)
    .map((deck) => {
      const list = appState.decks[deck.key];
      return `
        <article class="deck" data-deck="${deck.key}">
          <h3>${t(appState.settings.language, deck.titleKey)}</h3>
          <p>${t(appState.settings.language, deck.subtitleKey)}</p>
          <button class="deck-button" data-draw="${deck.key}">
            <img src="${deck.image}" alt="${t(appState.settings.language, deck.titleKey)}" />
            <span>${t(appState.settings.language, 'button.clickHere')}</span>
          </button>
          <small>${list.size()} ${t(appState.settings.language, 'controls.entries')}</small>
        </article>
      `;
    })
    .join('');

  section.querySelectorAll<HTMLButtonElement>('[data-draw]').forEach((button) => {
    button.addEventListener('click', () => {
      const key = button.dataset.draw as keyof DeckBundle;
      drawFromDeck(key);
    });
  });
}

export function drawFromDeck(deckKey: keyof DeckBundle): void {
  const list = appState.decks[deckKey];
  if (list.size() < 1) {
    const wantsActivate = window.confirm(
      `${t(appState.settings.language, 'dialog.deck.emptyTitle')}\n\n${t(appState.settings.language, 'dialog.deck.emptyMessage')}`
    );
    if (wantsActivate) {
      openTableDialog();
    }
    return;
  }

  const entry = list.draw();
  if (!entry) {
    return;
  }

  showEntry(entry);
}
