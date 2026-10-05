import { t } from '../../i18n';
import { appState } from '../../state';
import {
  upsertUserContentItem,
  userContentItemXml,
  type UserContentItem,
  type UserMonsterData
} from '../../userContent';
import {
  buildSpecialRuleText,
  escapeHtml,
  formatRuleLinks,
  inferRuleParameters,
  joinCsv,
  ruleParameterLabels
} from '../formatting';
import { bindDashboardCommonActions, contentDashboardSubtitle, renderDashboardEditorShell } from './common';
import { availableMagicRules, availableMonsterFactions, availableMonsterRules } from './data';
import { dashboardState } from './state';

export function renderMonsterEditor(container: HTMLElement, item: Extract<UserContentItem, { kind: 'monster' }>): void {
  const editor = container.querySelector<HTMLElement>('#contentDashboardEditor');
  if (!editor) {
    return;
  }
  const rawData = item.data as Partial<UserMonsterData>;
  const data: UserMonsterData = {
    id: rawData.id ?? '',
    name: rawData.name ?? '',
    plural: rawData.plural ?? '',
    factions: Array.isArray(rawData.factions) ? rawData.factions : [],
    move: rawData.move ?? '',
    weaponskill: rawData.weaponskill ?? '',
    ballisticskill: rawData.ballisticskill ?? '',
    strength: rawData.strength ?? '',
    toughness: rawData.toughness ?? '',
    wounds: rawData.wounds ?? '',
    initiative: rawData.initiative ?? '',
    attacks: rawData.attacks ?? '',
    gold: rawData.gold ?? '',
    armor: rawData.armor ?? '',
    damage: rawData.damage ?? '1D6',
    special: rawData.special ?? '',
    specialLinks: (rawData.specialLinks as UserMonsterData['specialLinks']) ?? {},
    magicType: rawData.magicType ?? '',
    magicLevel: rawData.magicLevel ?? 0
  };
  const factionOptions = availableMonsterFactions();
  const ruleOptions = availableMonsterRules();
  const magicOptions = availableMagicRules();
  const damageOptions = ['S', ...Array.from({ length: 10 }, (_, index) => `${index + 1}D6`)];
  const ballisticSkillOptions = ['-', 'S', 'A', '1+', '2+', '3+', '4+', '5+', '6+'];
  const selectedFactions = [...data.factions];
  const selectedRuleLinks = Object.fromEntries(
    Object.entries(data.specialLinks).map(([id, link]) => {
      const rule = ruleOptions.find((entry) => entry.id === id);
      const normalizedLink =
        typeof link === 'string'
          ? { text: link, parameter: '', parameters: [] }
          : {
              text: link?.text ?? '',
              parameter: link?.parameter ?? '',
              parameters: link?.parameters ?? (link?.parameter ? [link.parameter] : [])
            };
      const parameters =
        normalizedLink.parameters.length > 0
          ? normalizedLink.parameters
          : ruleParameterLabels(rule).length > 0
            ? inferRuleParameters(rule!.name, normalizedLink.text, rule?.parameterFormat)
            : [];
      return [id, { text: normalizedLink.text, parameter: parameters[0] ?? normalizedLink.parameter, parameters }];
    })
  );

  editor.innerHTML = renderDashboardEditorShell(
    t(appState.settings.language, 'contentDashboard.category.monster'),
    contentDashboardSubtitle(item),
    `
      <label>${t(appState.settings.language, 'contentDashboard.field.name')}<input id="ucName" value="${escapeHtml(data.name)}"></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.plural')}<input id="ucPlural" value="${escapeHtml(data.plural)}"></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.factions')}<input id="ucFactions" value="${escapeHtml(joinCsv(selectedFactions))}" readonly></label>
      <div class="dashboard-inline-actions">
        <select id="ucFactionSelect">
          ${factionOptions.map((faction) => `<option value="${escapeHtml(faction)}">${escapeHtml(faction)}</option>`).join('')}
        </select>
        <button type="button" id="ucAddFactionBtn">+</button>
        <button type="button" id="ucRemoveFactionBtn">-</button>
      </div>
      <label>${t(appState.settings.language, 'contentDashboard.field.move')}<input id="ucMove" value="${escapeHtml(data.move)}"></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.weaponSkill')}<input id="ucWeaponSkill" value="${escapeHtml(data.weaponskill)}"></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.ballisticSkill')}
        <select id="ucBallisticSkill">
          ${ballisticSkillOptions
            .map(
              (skill) =>
                `<option value="${skill}" ${data.ballisticskill === skill || (!data.ballisticskill && skill === '-') ? 'selected' : ''}>${skill}</option>`
            )
            .join('')}
        </select>
      </label>
      <label>${t(appState.settings.language, 'contentDashboard.field.strength')}<input id="ucStrength" value="${escapeHtml(data.strength)}"></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.toughness')}<input id="ucToughness" value="${escapeHtml(data.toughness)}"></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.wounds')}<input id="ucWounds" value="${escapeHtml(data.wounds)}"></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.initiative')}<input id="ucInitiative" value="${escapeHtml(data.initiative)}"></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.attacks')}<input id="ucAttacks" value="${escapeHtml(data.attacks)}"></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.gold')}<input id="ucGold" value="${escapeHtml(data.gold)}"></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.armor')}<input id="ucArmor" value="${escapeHtml(data.armor)}"></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.damage')}
        <select id="ucDamage">
          ${damageOptions
            .map(
              (damage) =>
                `<option value="${damage}" ${data.damage === damage || (!data.damage && damage === '1D6') ? 'selected' : ''}>${damage}</option>`
            )
            .join('')}
        </select>
      </label>
      <label>${t(appState.settings.language, 'contentDashboard.field.special')}<textarea id="ucSpecial" rows="5">${escapeHtml(data.special)}</textarea></label>
      <label>${t(appState.settings.language, 'contentDashboard.field.specialLinks')}
        <select id="ucSpecialLinks" size="6">
          ${Object.entries(selectedRuleLinks)
            .map(([id, link]) => `<option value="${escapeHtml(id)}">${escapeHtml(formatRuleLinks({ [id]: link }))}</option>`)
            .join('')}
        </select>
      </label>
      <div class="dashboard-inline-actions">
        <select id="ucRuleSelect">
          ${ruleOptions.map((rule) => `<option value="${escapeHtml(rule.id)}">${escapeHtml(rule.name)} (${escapeHtml(rule.id)})</option>`).join('')}
        </select>
        <button type="button" id="ucAddRuleBtn">+</button>
        <button type="button" id="ucRemoveRuleBtn">-</button>
      </div>
      <div id="ucRuleParameters"></div>
      <label>${t(appState.settings.language, 'contentDashboard.field.magicType')}
        <select id="ucMagicType">
          <option value=""></option>
          ${magicOptions.map((rule) => `<option value="${escapeHtml(rule.id)}" ${data.magicType === rule.id ? 'selected' : ''}>${escapeHtml(rule.name)}</option>`).join('')}
        </select>
      </label>
      <label>${t(appState.settings.language, 'contentDashboard.field.magicLevel')}<input id="ucMagicLevel" type="number" min="0" value="${data.magicLevel}"></label>
    `,
    userContentItemXml(item)
  );

  bindDashboardCommonActions(container, item);
  const form = editor.querySelector<HTMLFormElement>('form');
  const factionsInput = editor.querySelector<HTMLInputElement>('#ucFactions');
  const ruleSelect = editor.querySelector<HTMLSelectElement>('#ucRuleSelect');
  const specialLinksInput = editor.querySelector<HTMLSelectElement>('#ucSpecialLinks');
  const ruleParametersContainer = editor.querySelector<HTMLElement>('#ucRuleParameters');
  const xmlPreview = editor.querySelector<HTMLTextAreaElement>('.dashboard-xml-preview textarea');
  let selectedLinkedRuleId = ruleSelect?.value ?? '';
  let selectedRuleDraftId = ruleSelect?.value ?? '';
  let selectedRuleDraftParameters: string[] = [];
  const currentRuleParameters = (): string[] =>
    Array.from(editor.querySelectorAll<HTMLInputElement>('.uc-rule-parameter'))
      .sort((a, b) => Number.parseInt(a.dataset.index ?? '0', 10) - Number.parseInt(b.dataset.index ?? '0', 10))
      .map((input) => input.value ?? '');
  const loadDraftParametersForRule = (ruleId: string): string[] => {
    const rule = ruleOptions.find((entry) => entry.id === ruleId);
    if (!rule) {
      return [];
    }
    const linkedRule = selectedRuleLinks[ruleId];
    if (!linkedRule) {
      return Array.from({ length: ruleParameterLabels(rule).length }, () => '');
    }
    const linkedParameters =
      linkedRule.parameters.length > 0
        ? linkedRule.parameters
        : linkedRule.parameter
          ? [linkedRule.parameter]
          : inferRuleParameters(rule.name, linkedRule.text, rule.parameterFormat);
    const expectedLength = ruleParameterLabels(rule).length;
    return Array.from({ length: expectedLength }, (_, index) => linkedParameters[index] ?? '');
  };
  const syncDraftRuleSelection = (ruleId: string) => {
    selectedRuleDraftId = ruleId;
    selectedRuleDraftParameters = loadDraftParametersForRule(ruleId);
  };
  const renderRuleParameterFields = () => {
    const ruleId = selectedRuleDraftId || ruleSelect?.value || '';
    const rule = ruleOptions.find((entry) => entry.id === ruleId);
    const labels = ruleParameterLabels(rule);
    if (ruleParametersContainer) {
      if (labels.length === 0) {
        ruleParametersContainer.innerHTML = '';
        return;
      }
      const heading = `<div class="dashboard-parameter-heading">${escapeHtml(
        t(appState.settings.language, 'contentDashboard.field.ruleParameter')
      )}</div>`;
      const values = Array.from({ length: labels.length }, (_, index) => selectedRuleDraftParameters[index] ?? '');
      ruleParametersContainer.innerHTML =
        heading +
        labels
          .map(
            (label, index) =>
              `<label>${escapeHtml(label)}<input class="uc-rule-parameter" data-index="${index}" value="${escapeHtml(values[index] ?? '')}" placeholder="${escapeHtml(
                label || t(appState.settings.language, 'contentDashboard.field.ruleParameter')
              )}"></label>`
          )
          .join('');
      ruleParametersContainer.querySelectorAll<HTMLInputElement>('.uc-rule-parameter').forEach((input) =>
        input.addEventListener('input', () => {
          const selectedRuleId = selectedRuleDraftId || ruleSelect?.value || '';
          const selectedRule = ruleOptions.find((entry) => entry.id === selectedRuleId);
          if (selectedRule && selectedRuleLinks[selectedRule.id]) {
            const parameters = currentRuleParameters();
            selectedRuleDraftParameters = [...parameters];
            selectedRuleLinks[selectedRule.id] = {
              text: buildSpecialRuleText(selectedRule.name, parameters, selectedRule.parameterFormat),
              parameter: parameters[0] ?? '',
              parameters
            };
            refreshSelections(false);
            return;
          }
          selectedRuleDraftParameters = currentRuleParameters();
        })
      );
    }
  };

  const refreshSelections = (rerenderParameters = true) => {
    if (factionsInput) {
      factionsInput.value = joinCsv(selectedFactions);
    }
    if (specialLinksInput) {
      const currentSelection = selectedLinkedRuleId && selectedRuleLinks[selectedLinkedRuleId] ? selectedLinkedRuleId : '';
      specialLinksInput.innerHTML = Object.entries(selectedRuleLinks)
        .map(([id, link]) => `<option value="${escapeHtml(id)}" ${currentSelection === id ? 'selected' : ''}>${escapeHtml(formatRuleLinks({ [id]: link }))}</option>`)
        .join('');
    }
    if (ruleSelect && selectedRuleDraftId) {
      ruleSelect.value = selectedRuleDraftId;
    }
    if (rerenderParameters) {
      renderRuleParameterFields();
    }
    if (xmlPreview) {
      xmlPreview.value = userContentItemXml({
        ...item,
        data: {
          ...data,
          name: editor.querySelector<HTMLInputElement>('#ucName')?.value ?? '',
          plural: editor.querySelector<HTMLInputElement>('#ucPlural')?.value ?? '',
          factions: [...selectedFactions],
          move: editor.querySelector<HTMLInputElement>('#ucMove')?.value ?? '',
          weaponskill: editor.querySelector<HTMLInputElement>('#ucWeaponSkill')?.value ?? '',
          ballisticskill: editor.querySelector<HTMLSelectElement>('#ucBallisticSkill')?.value ?? '-',
          strength: editor.querySelector<HTMLInputElement>('#ucStrength')?.value ?? '',
          toughness: editor.querySelector<HTMLInputElement>('#ucToughness')?.value ?? '',
          wounds: editor.querySelector<HTMLInputElement>('#ucWounds')?.value ?? '',
          initiative: editor.querySelector<HTMLInputElement>('#ucInitiative')?.value ?? '',
          attacks: editor.querySelector<HTMLInputElement>('#ucAttacks')?.value ?? '',
          gold: editor.querySelector<HTMLInputElement>('#ucGold')?.value ?? '',
          armor: editor.querySelector<HTMLInputElement>('#ucArmor')?.value ?? '',
          damage: editor.querySelector<HTMLSelectElement>('#ucDamage')?.value ?? '1D6',
          special: editor.querySelector<HTMLTextAreaElement>('#ucSpecial')?.value ?? '',
          specialLinks: { ...selectedRuleLinks },
          magicType: editor.querySelector<HTMLSelectElement>('#ucMagicType')?.value ?? '',
          magicLevel: Number.parseInt(editor.querySelector<HTMLInputElement>('#ucMagicLevel')?.value ?? '0', 10) || 0
        }
      });
    }
  };

  editor.querySelector<HTMLButtonElement>('#ucAddFactionBtn')?.addEventListener('click', () => {
    const value = editor.querySelector<HTMLSelectElement>('#ucFactionSelect')?.value ?? '';
    if (value && !selectedFactions.includes(value)) {
      selectedFactions.push(value);
      refreshSelections();
    }
  });

  editor.querySelector<HTMLButtonElement>('#ucRemoveFactionBtn')?.addEventListener('click', () => {
    const value = editor.querySelector<HTMLSelectElement>('#ucFactionSelect')?.value ?? '';
    const index = selectedFactions.indexOf(value);
    if (index >= 0) {
      selectedFactions.splice(index, 1);
      refreshSelections();
    }
  });

  editor.querySelector<HTMLButtonElement>('#ucAddRuleBtn')?.addEventListener('click', () => {
    const value = selectedRuleDraftId || ruleSelect?.value || '';
    const rule = ruleOptions.find((entry) => entry.id === value);
    if (rule) {
      const parameters = ruleParameterLabels(rule).length > 0 ? [...selectedRuleDraftParameters] : [];
      selectedRuleLinks[rule.id] = {
        text: buildSpecialRuleText(rule.name, parameters, rule.parameterFormat),
        parameter: parameters[0] ?? '',
        parameters
      };
      selectedLinkedRuleId = rule.id;
      refreshSelections();
    }
  });

  editor.querySelector<HTMLButtonElement>('#ucRemoveRuleBtn')?.addEventListener('click', () => {
    const value =
      specialLinksInput?.value ||
      selectedRuleDraftId ||
      ruleSelect?.value ||
      '';
    if (selectedRuleLinks[value]) {
      delete selectedRuleLinks[value];
      selectedLinkedRuleId = '';
      if (selectedRuleDraftId === value) {
        selectedRuleDraftParameters = loadDraftParametersForRule(selectedRuleDraftId);
      }
      refreshSelections();
    }
  });

  ruleSelect?.addEventListener('change', () => {
    const ruleId = ruleSelect.value ?? '';
    syncDraftRuleSelection(ruleId);
    selectedLinkedRuleId = selectedRuleLinks[ruleId] ? ruleId : '';
    renderRuleParameterFields();
    refreshSelections(false);
  });
  specialLinksInput?.addEventListener('change', () => {
    const ruleId = specialLinksInput.value;
    selectedLinkedRuleId = ruleId;
    if (ruleSelect && ruleId) {
      ruleSelect.value = ruleId;
    }
    syncDraftRuleSelection(ruleId);
    renderRuleParameterFields();
    refreshSelections(false);
  });

  form?.querySelectorAll<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>('input, textarea, select').forEach((field) => {
    if (field.classList.contains('uc-rule-parameter') || field.id === 'ucRuleSelect' || field.id === 'ucSpecialLinks') {
      return;
    }
    field.addEventListener('input', () => refreshSelections());
    field.addEventListener('change', () => refreshSelections());
  });

  syncDraftRuleSelection(selectedRuleDraftId);
  renderRuleParameterFields();
  refreshSelections(false);

  form?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const nextItem: UserContentItem = {
      ...item,
      updatedAt: new Date().toISOString(),
      data: {
        ...data,
        name: editor.querySelector<HTMLInputElement>('#ucName')?.value ?? '',
        plural: editor.querySelector<HTMLInputElement>('#ucPlural')?.value ?? '',
        factions: [...selectedFactions],
        move: editor.querySelector<HTMLInputElement>('#ucMove')?.value ?? '',
        weaponskill: editor.querySelector<HTMLInputElement>('#ucWeaponSkill')?.value ?? '',
        ballisticskill: editor.querySelector<HTMLSelectElement>('#ucBallisticSkill')?.value ?? '-',
        strength: editor.querySelector<HTMLInputElement>('#ucStrength')?.value ?? '',
        toughness: editor.querySelector<HTMLInputElement>('#ucToughness')?.value ?? '',
        wounds: editor.querySelector<HTMLInputElement>('#ucWounds')?.value ?? '',
        initiative: editor.querySelector<HTMLInputElement>('#ucInitiative')?.value ?? '',
        attacks: editor.querySelector<HTMLInputElement>('#ucAttacks')?.value ?? '',
        gold: editor.querySelector<HTMLInputElement>('#ucGold')?.value ?? '',
        armor: editor.querySelector<HTMLInputElement>('#ucArmor')?.value ?? '',
        damage: editor.querySelector<HTMLSelectElement>('#ucDamage')?.value ?? '1D6',
        special: editor.querySelector<HTMLTextAreaElement>('#ucSpecial')?.value ?? '',
        specialLinks: { ...selectedRuleLinks },
        magicType: editor.querySelector<HTMLSelectElement>('#ucMagicType')?.value ?? '',
        magicLevel: Number.parseInt(editor.querySelector<HTMLInputElement>('#ucMagicLevel')?.value ?? '0', 10) || 0
      }
    };
    upsertUserContentItem(nextItem);
    dashboardState.activeDashboardItemUid = nextItem.uid;
    dashboardState.dashboardDraftItem = null;
    await appState.hooks.refreshRuntimeContent();
    dashboardState.renderContentDashboard(container);
  });
}
