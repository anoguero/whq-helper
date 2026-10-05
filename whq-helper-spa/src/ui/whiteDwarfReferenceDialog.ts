import { t } from '../i18n';
import { appState } from '../state';
import type { DungeonCard } from '../types';
import { getWhiteDwarfReference } from '../whiteDwarfReferences';
import { escapeHtml } from './formatting';

export function openWhiteDwarfReferenceDialog(card: DungeonCard): void {
  const dialog = document.querySelector<HTMLDialogElement>('#whiteDwarfReferenceDialog');
  const reference = getWhiteDwarfReference(card);
  if (!dialog || !reference) {
    return;
  }

  dialog.innerHTML = `
    <form method="dialog" class="white-dwarf-reference-dialog">
      <h2>${escapeHtml(reference.title[appState.settings.language])}</h2>
      <p class="white-dwarf-reference-source">${escapeHtml(reference.source)}</p>
      <div class="white-dwarf-reference-body">${escapeHtml(reference.text[appState.settings.language]).replaceAll('\n', '<br>')}</div>
      <menu>
        <button value="cancel">${t(appState.settings.language, 'dialog.button.close')}</button>
      </menu>
    </form>
  `;

  dialog.showModal();
}
