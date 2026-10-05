import type { UserContentItem, UserContentKind } from '../../userContent';

export interface DashboardCategoryMeta {
  kind: UserContentKind;
  titleKey: string;
}

export const DASHBOARD_CATEGORIES: DashboardCategoryMeta[] = [
  { kind: 'dungeonCard', titleKey: 'contentDashboard.category.dungeonCard' },
  { kind: 'dungeonEvent', titleKey: 'contentDashboard.category.dungeonEvent' },
  { kind: 'treasure', titleKey: 'contentDashboard.category.treasure' },
  { kind: 'objectiveTreasure', titleKey: 'contentDashboard.category.objectiveTreasure' },
  { kind: 'travelEvent', titleKey: 'contentDashboard.category.travelEvent' },
  { kind: 'settlementEvent', titleKey: 'contentDashboard.category.settlementEvent' },
  { kind: 'rule', titleKey: 'contentDashboard.category.rule' },
  { kind: 'monster', titleKey: 'contentDashboard.category.monster' },
  { kind: 'table', titleKey: 'contentDashboard.category.table' },
  { kind: 'objectiveRoomAdventure', titleKey: 'contentDashboard.category.objectiveRoomAdventure' },
  { kind: 'warrior', titleKey: 'contentDashboard.category.warrior' },
  { kind: 'location', titleKey: 'contentDashboard.category.location' }
];

export const DASHBOARD_CREATE_PREFIX = 'create:';

/**
 * Estado del panel de contenido de usuario, compartido por el árbol, el selector de creación y los
 * editores. renderContentDashboard lo registra main.ts para que los editores no importen el panel
 * que, a su vez, los despacha.
 */
export interface DashboardState {
  activeDashboardItemUid: string | null;
  dashboardDraftItem: UserContentItem | null;
  renderContentDashboard(container: HTMLElement): void;
}

export const dashboardState: DashboardState = {
  activeDashboardItemUid: null,
  dashboardDraftItem: null,
  renderContentDashboard: () => {
    throw new Error('dashboardState.renderContentDashboard no se ha registrado todavía.');
  }
};
