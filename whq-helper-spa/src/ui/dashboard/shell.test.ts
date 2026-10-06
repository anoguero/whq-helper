// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { appState } from '../../state';
import { settings } from '../../testing/fixtures';
import type { UserContentItem, UserContentKind } from '../../userContent/types';

const editors = vi.hoisted(() => ({
  renderDungeonCardEditor: vi.fn(),
  renderEventEditor: vi.fn(),
  renderRuleEditor: vi.fn(),
  renderMonsterEditor: vi.fn(),
  renderTableEditor: vi.fn(),
  renderObjectiveRoomAdventureEditor: vi.fn(),
  renderWarriorEditor: vi.fn(),
  renderLocationEditor: vi.fn()
}));

vi.mock('./dungeonCardEditor', () => ({ renderDungeonCardEditor: editors.renderDungeonCardEditor }));
vi.mock('./eventEditor', () => ({ renderEventEditor: editors.renderEventEditor }));
vi.mock('./ruleEditor', () => ({ renderRuleEditor: editors.renderRuleEditor }));
vi.mock('./monsterEditor', () => ({ renderMonsterEditor: editors.renderMonsterEditor }));
vi.mock('./tableEditor', () => ({ renderTableEditor: editors.renderTableEditor }));
vi.mock('./adventureEditor', () => ({ renderObjectiveRoomAdventureEditor: editors.renderObjectiveRoomAdventureEditor }));
vi.mock('./warriorEditor', () => ({ renderWarriorEditor: editors.renderWarriorEditor }));
vi.mock('./locationEditor', () => ({ renderLocationEditor: editors.renderLocationEditor }));

const { renderDashboardEditor } = await import('./shell');
const { DASHBOARD_CATEGORIES, dashboardState } = await import('./state');

const EDITOR_FOR_KIND: Record<UserContentKind, keyof typeof editors> = {
  dungeonCard: 'renderDungeonCardEditor',
  dungeonEvent: 'renderEventEditor',
  treasure: 'renderEventEditor',
  objectiveTreasure: 'renderEventEditor',
  travelEvent: 'renderEventEditor',
  settlementEvent: 'renderEventEditor',
  rule: 'renderRuleEditor',
  monster: 'renderMonsterEditor',
  table: 'renderTableEditor',
  objectiveRoomAdventure: 'renderObjectiveRoomAdventureEditor',
  warrior: 'renderWarriorEditor',
  location: 'renderLocationEditor'
};

function dashboard(): HTMLElement {
  document.body.innerHTML = '<div id="dashboard"><div id="contentDashboardEditor"></div></div>';
  return document.querySelector<HTMLElement>('#dashboard')!;
}

beforeEach(() => {
  Object.values(editors).forEach((editor) => editor.mockClear());
  appState.settings = settings();
  dashboardState.activeDashboardItemUid = null;
  dashboardState.dashboardDraftItem = null;
});

describe('renderDashboardEditor', () => {
  it.each(DASHBOARD_CATEGORIES.map((category) => category.kind))('routes a %s item to its editor', (kind) => {
    const item = { uid: 'draft-item', kind, mode: 'new', title: '', updatedAt: '', data: {} } as unknown as UserContentItem;
    dashboardState.activeDashboardItemUid = 'draft';
    dashboardState.dashboardDraftItem = item;
    const container = dashboard();

    renderDashboardEditor(container);

    const expected = EDITOR_FOR_KIND[kind];
    expect(editors[expected]).toHaveBeenCalledExactlyOnceWith(container, item);
    for (const [name, editor] of Object.entries(editors)) {
      if (name !== expected) {
        expect(editor, name).not.toHaveBeenCalled();
      }
    }
  });

  it('shows the dashboard home, not an editor, when nothing is selected', () => {
    const container = dashboard();

    renderDashboardEditor(container);

    expect(container.querySelector('.dashboard-empty')).not.toBeNull();
    Object.values(editors).forEach((editor) => expect(editor).not.toHaveBeenCalled());
  });
});
