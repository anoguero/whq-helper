import { describe, expect, it } from 'vitest';
import { appState } from './state';
import type { AppHooks } from './state';

describe('appState.hooks before bootstrap()', () => {
  // main.ts registra los hooks al cargarse; sin el, cualquier llamada debe fallar con un error claro
  // en lugar de no hacer nada en silencio.
  it.each<keyof AppHooks>(['render', 'applyLanguageChange', 'refreshRuntimeContent', 'rebuildDecks', 'buildControls'])(
    '%s throws because it has not been registered',
    (hook) => {
      const call = appState.hooks[hook] as (...args: unknown[]) => unknown;
      expect(() => call('ES')).toThrow('appState.hooks no se ha registrado');
    }
  );
});
