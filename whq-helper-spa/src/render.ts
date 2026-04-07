import { formatTreasureUsers, t } from './i18n';
import { toHitRollNeeded } from './deck';
import type { EventModel, LanguageCode, Monster, MonsterEntry, Rule, SettlementLocation } from './types';

function esc(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll('\n', '<br>');
}

function objectiveTreasure(event: EventModel): boolean {
  return event.id.trim().toLowerCase().includes('-objective-');
}

function eventBadge(event: EventModel): string {
  if (event.treasure) {
    return 'T';
  }
  if (event.category === 'travel') {
    return 'TR';
  }
  if (event.category === 'settlement') {
    return 'ST';
  }
  return 'E';
}

function buildSpecialLinks(
  monster: Monster,
  altSpecials: MonsterEntry,
  appendSpecials: boolean,
  rules: Map<string, Rule>
): Array<{ id: string; text: string; tooltip: string }> {
  const links: Array<{ id: string; text: string; tooltip: string }> = [];

  const includeAlt = !!(altSpecials.special || Object.keys(altSpecials.specialLinks).length > 0 || altSpecials.magicType);
  const includeMonster =
    !!(monster.special || Object.keys(monster.specialLinks).length > 0 || monster.magicType) && (appendSpecials || !includeAlt);

  const addMagic = (magicType: string, magicLevel: number, moreSpecials: boolean) => {
    if (!magicType) {
      return;
    }
    const rule = rules.get(magicType);
    const magicName = rule?.name ?? magicType;
    links.push({ id: magicType, text: `${magicName} `, tooltip: buildRuleTooltip(magicType, rules) });
    links.push({
      id: 'rpb-magic',
      text: `Magic ${magicLevel}${moreSpecials ? '; ' : '.'}`,
      tooltip: buildRuleTooltip('rpb-magic', rules)
    });
  };

  const addSpecials = (specials: Record<string, { text: string; parameter: string }>) => {
    const entries = Object.entries(specials);
    entries.forEach(([id, link], index) => {
      const punctuation = index < entries.length - 1 ? '; ' : '.';
      links.push({ id, text: `${link.text}${punctuation}`, tooltip: buildRuleTooltip(id, rules) });
    });
  };

  if (includeMonster) {
    const moreSpecials = Object.keys(monster.specialLinks).length > 0 || (includeAlt && !!altSpecials.magicType);
    addMagic(monster.magicType, monster.magicLevel, moreSpecials);
    addSpecials(monster.specialLinks);
  }

  if (includeAlt) {
    const moreSpecials = Object.keys(altSpecials.specialLinks).length > 0;
    addMagic(altSpecials.magicType, altSpecials.magicLevel, moreSpecials);
    addSpecials(altSpecials.specialLinks);
  }

  return links;
}

function buildRuleTooltip(id: string, rules: Map<string, Rule>): string {
  const rule = rules.get(id);
  if (!rule?.text) {
    return '';
  }
  const title = rule.type === 'magic' ? `${rule.name} Magic` : rule.name;
  return title ? `${title}\n\n${rule.text}` : rule.text;
}

function formatToughness(monster: Monster): string {
  if (monster.armor && monster.armor !== '-') {
    const t = Number.parseInt(monster.toughness, 10);
    const armor = Number.parseInt(monster.armor, 10);
    if (Number.isFinite(t) && Number.isFinite(armor)) {
      return `${monster.toughness} (${t + armor})`;
    }
  }
  return monster.toughness;
}

export function renderEventCard(event: EventModel, language: LanguageCode): string {
  if (event.treasure) {
    const usersText = event.users.trim() && event.users.trim() != 'BDEW' ? formatTreasureUsers(language, event.users) : '';
    const usersBox = usersText
      ? `
        <div class="treasure-users-box">
          <p class="label">${esc(t(language, 'card.treasure.users'))}</p>
          <p>${esc(usersText)}</p>
        </div>
      `
      : '';

    const goldBadge = event.goldValue.trim()
      ? `
        <div class="treasure-value-badge">
          <p class="label">${esc(t(language, 'card.treasure.goldValue'))}</p>
          <p class="value">${esc(event.goldValue)}</p>
        </div>
      `
      : '';

    const footerText = objectiveTreasure(event)
      ? t(language, 'card.treasure.footerObjective')
      : t(language, 'card.treasure.footerDungeon');

    return `
      <article class="card treasure ${objectiveTreasure(event) ? 'objective' : ''}">
        <img class="treasure-template" src="/resources/treasure-card-template.png" alt="Treasure Template" />
        <header>
          <h3>${esc(t(language, 'card.treasure'))}</h3>
          <h2 class="treasure-title">${esc(event.name || t(language, 'card.treasure.defaultName'))}</h2>
        </header>
        <section class="body">
          ${event.flavor ? `<p class="flavor">${esc(event.flavor)}</p>` : ''}
          ${event.rules ? `<p>${esc(event.rules)}</p>` : ''}
          ${event.special ? `<p>${esc(event.special)}</p>` : ''}
        </section>
        ${usersBox}
        ${goldBadge}
        <footer>${esc(footerText)}</footer>
      </article>
    `;
  }

  const metadata: string[] = [];
  if (event.goldValue.trim()) {
    metadata.push(`<p><strong>${esc(t(language, 'card.treasure.goldValue'))}:</strong> ${esc(event.goldValue)}</p>`);
  }
  if (event.users.trim()) {
    metadata.push(
      `<p><strong>${esc(t(language, 'card.treasure.users'))}:</strong> ${esc(
        formatTreasureUsers(language, event.users)
      )}</p>`
    );
  }

  const className = 'card event';
  const badge = eventBadge(event);

  return `
    <article class="${className}">
      <span class="event-badge">${esc(badge)}</span>
      <header>
        <h2>${esc(event.name || t(language, 'card.event.defaultName'))}</h2>
      </header>
      <section class="body">
        ${metadata.join('')}
        ${event.flavor ? `<p class="flavor">${esc(event.flavor)}</p>` : ''}
        ${event.rules ? `<p>${esc(event.rules)}</p>` : ''}
        ${event.special ? `<p>${esc(event.special)}</p>` : ''}
      </section>
      ${!event.treasure && event.category === 'dungeon' ? `<footer>${esc(t(language, 'card.noTreasure'))}</footer>` : ''}
    </article>
  `;
}

export function renderMonsterCard(
  monster: Monster,
  title: string,
  altSpecials: MonsterEntry,
  appendSpecials: boolean,
  rules: Map<string, Rule>,
  language: LanguageCode
): string {
  const specialText = [
    monster.special && (appendSpecials || !altSpecials.special) ? monster.special : '',
    altSpecials.special
  ]
    .filter(Boolean)
    .join('\n');

  const links = buildSpecialLinks(monster, altSpecials, appendSpecials, rules)
    .map(
      (entry) =>
        `<button class="rule-link" data-rule-id="${esc(entry.id)}" title="${esc(entry.tooltip)}">${esc(entry.text)}</button>`
    )
    .join('');

  const ws = Number.parseInt(monster.weaponskill, 10) || 0;

  const toHitHeader = Array.from({ length: 10 }, (_, i) => `<span>${i + 1}</span>`).join('');
  const toHitValues = Array.from({ length: 10 }, (_, i) => `<span>${toHitRollNeeded(ws, i + 1)}</span>`).join('');

  const imageSrcCandidates = [
    `/data/graphics/monsters/${monster.id.toLowerCase().replaceAll(' ', '-')}.png`,
    `/data/graphics/monsters/${monster.name.toLowerCase().replaceAll(' ', '-')}.png`
  ];

  return `
    <article class="card monster">
      <span class="event-badge">M</span>
      <header>
        <h2>${esc(title)}</h2>
      </header>
      <section class="monster-top">
        <img src="${esc(imageSrcCandidates[0])}" alt="" onerror="this.alt='';if(this.dataset.fallback!=='1'){this.dataset.fallback='1';this.src='${esc(
    imageSrcCandidates[1]
  )}';return;}this.style.display='none';this.removeAttribute('src');this.closest('.monster-top')?.classList.add('no-image');" />
        <div class="stats">
          <p><strong>M:</strong> ${esc(monster.move)}</p>
          <p><strong>WS:</strong> ${esc(monster.weaponskill)}</p>
          <p><strong>BS:</strong> ${esc(monster.ballisticskill)}</p>
          <p><strong>S:</strong> ${esc(monster.strength)}</p>
          <p><strong>T:</strong> ${esc(formatToughness(monster))}</p>
          <p><strong>W:</strong> ${esc(monster.wounds)}</p>
          <p><strong>I:</strong> ${esc(monster.initiative)}</p>
          <p><strong>A:</strong> ${esc(monster.attacks)}</p>
          <p><strong>Dmg:</strong> ${esc(monster.damage)}</p>
        </div>
      </section>
      <section class="to-hit">
        <p>${esc(t(language, 'card.monster.toHit'))}</p>
        <div class="row label"><span>WS</span>${toHitHeader}</div>
        <div class="row"><span>Hit</span>${toHitValues}</div>
      </section>
      <section class="specials">
        <h3>${esc(t(language, 'card.monster.specialRules'))}</h3>
        ${specialText ? `<p>${esc(specialText)}</p>` : ''}
        <div class="rule-links">${links}</div>
      </section>
      <footer>
        <img src="/data/graphics/gold.png" alt="Gold" />
        <span>${esc(monster.gold)}g</span>
      </footer>
    </article>
  `;
}

export function renderSettlementLocationCard(location: SettlementLocation, visitorLabels: string[]): string {
  const visitors = visitorLabels.join(', ');
  return `
    <article class="card settlement-location-card">
      <img class="settlement-location-template" src="/resources/dungeon-card-template.png" alt="" />
      <header>
        <h2>${esc(location.name)}</h2>
      </header>
      <section class="body">
        ${location.description ? `<p class="flavor">${esc(location.description)}</p>` : ''}
        ${location.rules ? `<div class="rules-scroll"><p>${esc(location.rules)}</p></div>` : ''}
      </section>
      <footer>${esc(visitors)}</footer>
    </article>
  `;
}
