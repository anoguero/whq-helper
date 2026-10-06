import { isContentPackageAvailable } from '../content';
import { t } from '../i18n';
import type { LanguageCode } from '../types';
import { escapeHtml } from './formatting';

/** Aviso, encima de la aplicacion, de que se ha generado sin paquete de contenido. */
export function renderMissingContentNotice(language: LanguageCode): void {
  if (isContentPackageAvailable()) {
    return;
  }
  const app = document.querySelector<HTMLDivElement>('#app');
  if (!app) {
    return;
  }
  const notice = document.createElement('section');
  notice.className = 'content-notice';
  notice.setAttribute('role', 'status');
  notice.innerHTML = `
    <strong>${escapeHtml(t(language, 'content.missing.title'))}</strong>
    <p>${escapeHtml(t(language, 'content.missing.spa.body'))}</p>
  `;
  app.prepend(notice);
}
