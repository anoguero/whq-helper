import { DungeonCardStore } from './dungeonStore';
import type { AppSettings, ContentRepository, DeckBundle, DungeonCard, LanguageCode } from './types';

/**
 * Funciones de orquestación que viven en main.ts. Los módulos de UI las llaman a través de
 * appState.hooks para no importar main.ts (y no crear dependencias circulares).
 */
export interface AppHooks {
  render(): void;
  applyLanguageChange(language: LanguageCode): Promise<void>;
  refreshRuntimeContent(): Promise<void>;
}

/** Estado compartido de la aplicación. main.ts lo inicializa en bootstrap() antes del primer render. */
export interface AppState {
  repository: ContentRepository;
  settings: AppSettings;
  decks: DeckBundle;
  readonly dungeonStore: DungeonCardStore;
  dungeonCards: DungeonCard[];
  hooks: AppHooks;
}

function hookNotRegistered(): never {
  throw new Error('appState.hooks no se ha registrado todavía (main.ts lo hace al cargar).');
}

export const appState: AppState = {
  // Se asignan en bootstrap(), igual que antes los `let` sin inicializar de main.ts.
  repository: undefined!,
  settings: undefined!,
  decks: undefined!,
  dungeonStore: new DungeonCardStore(),
  dungeonCards: [],
  hooks: {
    render: hookNotRegistered,
    applyLanguageChange: hookNotRegistered,
    refreshRuntimeContent: hookNotRegistered
  }
};
