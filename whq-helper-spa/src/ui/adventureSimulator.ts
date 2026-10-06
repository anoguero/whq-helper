import {
  buildAdventureDeck,
  collectAdventureCardIds,
  generateObjectiveRoomMonsterEntries,
  objectiveDifficultyLabel,
  pickAdditionalAdventureCards,
  shuffleCards,
  splitSelectedPile,
  splitSelectedPileHistories,
  type AdventureSimulatorState
} from '../adventure/adventureDeck';
import { renderDungeonCardToCanvasLocalized } from '../dungeonRenderer';
import { getAdventureAmbiences, t, tf } from '../i18n';
import { saveSettings } from '../settings';
import { appState } from '../state';
import type { DungeonCard, ObjectiveRoomAdventure } from '../types';
import { getWhiteDwarfReference } from '../whiteDwarfReferences';
import { closeAllOpenCards, showEntries } from './cardWindows';
import { escapeHtml } from './formatting';
import { openTreasureSearchDialog } from './treasureSearch';
import { openWhiteDwarfReferenceDialog } from './whiteDwarfReferenceDialog';

export function cardTypeLabel(type: DungeonCard['type']): string {
  return type.replaceAll('_', ' ');
}

export function openMissionDialog(adventure: ObjectiveRoomAdventure): void {
  const dialog = document.querySelector<HTMLDialogElement>('#missionDialog');
  if (!dialog) {
    return;
  }

  dialog.innerHTML = `
    <form method="dialog">
      <h2>${t(appState.settings.language, 'dialog.mission.title')}: ${escapeHtml(adventure.name)}</h2>
      <p><strong>${t(appState.settings.language, 'dialog.mission.ambience')}</strong></p>
      <p>${escapeHtml(adventure.flavorText)}</p>
      <p><strong>${t(appState.settings.language, 'dialog.mission.specialRules')}</strong></p>
      <p>${escapeHtml(adventure.rulesText)}</p>
      <menu><button value="cancel">${t(appState.settings.language, 'dialog.button.close')}</button></menu>
    </form>
  `;

  dialog.showModal();
}

export function openAdventureSimulator(
  deck: DungeonCard[],
  adventure: ObjectiveRoomAdventure,
  ambienceLabel: string,
  dungeonLevel: number,
  environment: string
): void {
  const panel = document.querySelector<HTMLElement>('#simulatorPanel');
  if (!panel) {
    return;
  }

  const state: AdventureSimulatorState = {
    piles: [deck],
    histories: [[]],
    selectedCard: null,
    selectedPile: -1
  };

  panel.hidden = false;
  panel.classList.add('active');
  document.body.classList.add('simulator-active');

  panel.innerHTML = `
    <section class="simulator-layout">
      <header>
        <h2>${t(appState.settings.language, 'simulator.title')}</h2>
        <p>${t(appState.settings.language, 'dialog.mission.title')}: ${escapeHtml(adventure.name)} | ${t(appState.settings.language, 'dialog.mission.ambience')}: ${ambienceLabel} | ${t(appState.settings.language, 'simulator.level')}: ${dungeonLevel}</p>
      </header>
      <section class="simulator-body">
        <div>
          <h3>${t(appState.settings.language, 'simulator.piles')}</h3>
          <p>${t(appState.settings.language, 'simulator.pilesHint')}</p>
          <p id="simStatus"></p>
          <div id="pilesContainer" class="piles-grid"></div>
        </div>
        <div>
          <h3>${t(appState.settings.language, 'simulator.revealedCard')}</h3>
          <p id="revealedStatus">${t(appState.settings.language, 'simulator.revealedHint')}</p>
          <button type="button" id="whiteDwarfReferenceBtn" disabled>${t(appState.settings.language, 'dialog.whiteDwarfReference.button')}</button>
          <canvas id="simRevealCanvas" width="847" height="1264"></canvas>
        </div>
      </section>
      <menu>
        ${adventure.generic ? '' : `<button type="button" id="showMissionBtn">${t(appState.settings.language, 'simulator.missionButton')}</button>`}
        <button type="button" id="objectiveRoomMonstersBtn">${t(appState.settings.language, 'button.generateObjectiveRoomMonsters')}</button>
        <button type="button" id="treasureSearchBtn">${t(appState.settings.language, 'treasureSearch.button')}</button>
        <button type="button" id="closeAllAdventureCardsBtn">${t(appState.settings.language, 'menu.item.closeAllCards')}</button>
        <button type="button" id="closeSimulatorBtn">${t(appState.settings.language, 'button.finishAdventure')}</button>
      </menu>
    </section>
  `;

  panel.querySelector<HTMLButtonElement>('#showMissionBtn')?.addEventListener('click', () => {
    openMissionDialog(adventure);
  });

  panel.querySelector<HTMLButtonElement>('#objectiveRoomMonstersBtn')?.addEventListener('click', () => {
    const result = generateObjectiveRoomMonsterEntries(appState.repository, appState.settings, dungeonLevel);
    if (!result) {
      window.alert(t(appState.settings.language, 'simulator.objectiveMonstersInvalidWeights'));
      return;
    }
    if (result.entries.length === 0) {
      window.alert(t(appState.settings.language, 'simulator.objectiveMonstersNoEntries'));
      return;
    }

    window.alert(
      tf(appState.settings.language, 'simulator.objectiveMonstersDifficulty', {
        difficulty: objectiveDifficultyLabel(appState.settings.language, result.difficulty.id)
      })
    );
    showEntries(result.entries);
  });

  panel.querySelector<HTMLButtonElement>('#closeAllAdventureCardsBtn')?.addEventListener('click', () => {
    closeAllOpenCards();
  });

  panel.querySelector<HTMLButtonElement>('#treasureSearchBtn')?.addEventListener('click', () => {
    openTreasureSearchDialog();
  });

  panel.querySelector<HTMLButtonElement>('#closeSimulatorBtn')?.addEventListener('click', () => {
    appState.settings.dungeonActive = false;
    saveSettings(appState.settings);
    appState.hooks.rebuildDecks();
    panel.hidden = true;
    panel.classList.remove('active');
    panel.innerHTML = '';
    document.body.classList.remove('simulator-active');
  });

  const status = panel.querySelector<HTMLElement>('#simStatus')!;
  const revealedStatus = panel.querySelector<HTMLElement>('#revealedStatus')!;
  const whiteDwarfReferenceButton = panel.querySelector<HTMLButtonElement>('#whiteDwarfReferenceBtn')!;
  const pilesContainer = panel.querySelector<HTMLElement>('#pilesContainer')!;
  const revealCanvas = panel.querySelector<HTMLCanvasElement>('#simRevealCanvas')!;

  const refreshReveal = () => {
    whiteDwarfReferenceButton.disabled = !state.selectedCard || !getWhiteDwarfReference(state.selectedCard);
    if (!state.selectedCard) {
      const ctx = revealCanvas.getContext('2d');
      if (ctx) {
        ctx.clearRect(0, 0, revealCanvas.width, revealCanvas.height);
      }
      return;
    }

    renderDungeonCardToCanvasLocalized(revealCanvas, state.selectedCard, appState.settings.language).catch((error) =>
      console.error(error)
    );
  };

  whiteDwarfReferenceButton.addEventListener('click', () => {
    if (state.selectedCard && getWhiteDwarfReference(state.selectedCard)) {
      openWhiteDwarfReferenceDialog(state.selectedCard);
    }
  });

  const refresh = () => {
    const total = state.piles.reduce((sum, pile) => sum + pile.length, 0);
    status.textContent =
      state.piles.length === 1
        ? tf(appState.settings.language, 'simulator.pileSingle', { count: state.piles[0]?.length ?? 0 })
        : tf(appState.settings.language, 'simulator.pileSplit', { piles: state.piles.length, cards: total });

    pilesContainer.innerHTML = '';

    state.piles.forEach((pile, pileIndex) => {
      const card = document.createElement('article');
      card.className = 'pile-box';

      const title = document.createElement('h4');
      title.textContent = `Montón ${pileIndex + 1} (${pile.length} ${t(appState.settings.language, 'controls.entries')})`;
      card.appendChild(title);

      const back = document.createElement('button');
      back.className = 'pile-back';
      back.type = 'button';
      back.innerHTML = pile.length > 0
        ? `<img src="/resources/dungeon-back.jpeg" alt="Deck"/>`
        : '<span>Sin cartas</span>';
      back.addEventListener('click', () => {
        if (pile.length === 0) {
          window.alert(tf(appState.settings.language, 'simulator.emptyPile', { pile: pileIndex + 1 }));
          return;
        }
        const drawn = pile.shift() as DungeonCard;
        state.histories[pileIndex]!.unshift(drawn);
        state.selectedCard = drawn;
        state.selectedPile = pileIndex;
        revealedStatus.textContent = tf(appState.settings.language, 'simulator.selectedCard', {
          name: drawn.name,
          pile: pileIndex + 1
        });
        refresh();
      });
      card.appendChild(back);

      const splitBtn = document.createElement('button');
      splitBtn.type = 'button';
      splitBtn.textContent = t(appState.settings.language, 'simulator.splitPile');
      splitBtn.addEventListener('click', () => {
        if (pile.length < 2) {
          window.alert(tf(appState.settings.language, 'simulator.splitInsufficient', { pile: pileIndex + 1 }));
          return;
        }

        const input = window.prompt(t(appState.settings.language, 'simulator.splitPrompt'), '3');
        if (!input) {
          return;
        }
        const count = Number.parseInt(input, 10);
        if (!Number.isFinite(count) || count < 2 || count > pile.length) {
          window.alert(tf(appState.settings.language, 'simulator.splitInvalid', { max: pile.length }));
          return;
        }

        state.piles = splitSelectedPile(state.piles, pileIndex, count);
        state.histories = splitSelectedPileHistories(state.histories, pileIndex, count);
        state.selectedCard = null;
        state.selectedPile = -1;
        revealedStatus.textContent = t(appState.settings.language, 'simulator.splitDone');
        refresh();
      });
      card.appendChild(splitBtn);

      const addCardsBtn = document.createElement('button');
      addCardsBtn.type = 'button';
      addCardsBtn.textContent = t(appState.settings.language, 'button.addCardsToDeck');
      addCardsBtn.addEventListener('click', () => {
        const available = pickAdditionalAdventureCards(environment, appState.dungeonCards, collectAdventureCardIds(state));
        if (available.length === 0) {
          window.alert(tf(appState.settings.language, 'simulator.addCardsUnavailable', { max: 0 }));
          return;
        }

        const input = window.prompt(t(appState.settings.language, 'simulator.addCardsPrompt'), '1');
        if (!input) {
          return;
        }

        const count = Number.parseInt(input, 10);
        if (!Number.isFinite(count) || count < 1 || count > available.length) {
          window.alert(tf(appState.settings.language, 'simulator.addCardsInvalid', { max: available.length }));
          return;
        }

        state.piles[pileIndex]!.push(...available.slice(0, count));
        shuffleCards(state.piles[pileIndex]!);
        state.selectedPile = pileIndex;
        revealedStatus.textContent = tf(appState.settings.language, 'simulator.addCardsDone', {
          count,
          pile: pileIndex + 1
        });
        refresh();
      });
      card.appendChild(addCardsBtn);

      const historyLabel = document.createElement('p');
      historyLabel.textContent = t(appState.settings.language, 'simulator.history');
      card.appendChild(historyLabel);

      const historyList = document.createElement('ul');
      historyList.className = 'history-list';
      state.histories[pileIndex]!.forEach((historyCard, idx) => {
        const item = document.createElement('li');
        item.className = state.selectedCard?.id === historyCard.id && state.selectedPile === pileIndex ? 'selected' : '';
        item.textContent = `${historyCard.name} [${cardTypeLabel(historyCard.type)}]`;
        item.addEventListener('click', () => {
          state.selectedCard = state.histories[pileIndex]![idx] as DungeonCard;
          state.selectedPile = pileIndex;
          revealedStatus.textContent = tf(appState.settings.language, 'simulator.selectedCard', {
            name: state.selectedCard.name,
            pile: pileIndex + 1
          });
          refresh();
        });
        historyList.appendChild(item);
      });
      card.appendChild(historyList);

      pilesContainer.appendChild(card);
    });

    refreshReveal();
  };

  refresh();
}

export function openNewDungeonDialog(): void {
  const dialog = document.querySelector<HTMLDialogElement>('#newDungeonDialog');
  if (!dialog) {
    return;
  }

  const environments = appState.dungeonStore.loadEnvironments();
  if (environments.length === 0) {
    window.alert(t(appState.settings.language, 'dialog.deck.noCardsForDungeon'));
    return;
  }

  const ambienceOptions = getAdventureAmbiences(appState.settings.language);

  dialog.innerHTML = `
    <form method="dialog" class="maintenance-grid">
      <h2>${t(appState.settings.language, 'newDungeon.title')}</h2>
      <p>${t(appState.settings.language, 'newDungeon.description')}</p>
      <div class="new-dungeon-grid">
        <label>${t(appState.settings.language, 'newDungeon.environment')}
          <select id="ndEnv"></select>
        </label>
        <label>${t(appState.settings.language, 'newDungeon.objectiveRoom')}
          <select id="ndObjective"></select>
        </label>
        <label>${t(appState.settings.language, 'newDungeon.mission')}
          <select id="ndMission"></select>
        </label>
        <label>${t(appState.settings.language, 'newDungeon.ambience')}
          <select id="ndAmbience">
            ${ambienceOptions.map((item) => `<option value="${item.value}">${item.label}</option>`).join('')}
          </select>
        </label>
        <label>${t(appState.settings.language, 'newDungeon.level')}
          <input id="ndLevel" type="number" min="1" max="10" value="${Math.max(1, Math.min(10, appState.settings.activeDungeonLevel))}">
        </label>
        <label>${t(appState.settings.language, 'newDungeon.deckSize')}
          <input id="ndDeckSize" type="number" min="2" max="200" value="12">
        </label>
        <label>${t(appState.settings.language, 'newDungeon.roomCount')}
          <input id="ndRoomCount" type="number" min="1" max="11" value="5">
        </label>
      </div>
      <label>${t(appState.settings.language, 'newDungeon.specialRules')}
        <textarea id="ndMissionRules" rows="5"  style="width: 100%; resize: none;" readonly></textarea>
      </label>
      <fieldset class="objective-monster-weights">
        <legend>${t(appState.settings.language, 'newDungeon.objectiveMonsterWeights')}</legend>
        <p>${t(appState.settings.language, 'newDungeon.objectiveMonsterWeightsHint')}</p>
        <div class="new-dungeon-grid">
          <label>${t(appState.settings.language, 'newDungeon.objectiveMonsterEasy')}
            <input id="ndObjectiveEasy" type="number" min="0" max="99" value="${Math.max(0, appState.settings.objectiveMonsterEasyWeight)}">
          </label>
          <label>${t(appState.settings.language, 'newDungeon.objectiveMonsterNormal')}
            <input id="ndObjectiveNormal" type="number" min="0" max="99" value="${Math.max(0, appState.settings.objectiveMonsterNormalWeight)}">
          </label>
          <label>${t(appState.settings.language, 'newDungeon.objectiveMonsterHard')}
            <input id="ndObjectiveHard" type="number" min="0" max="99" value="${Math.max(0, appState.settings.objectiveMonsterHardWeight)}">
          </label>
          <label>${t(appState.settings.language, 'newDungeon.objectiveMonsterVeryHard')}
            <input id="ndObjectiveVeryHard" type="number" min="0" max="99" value="${Math.max(0, appState.settings.objectiveMonsterVeryHardWeight)}">
          </label>
          <label>${t(appState.settings.language, 'newDungeon.objectiveMonsterExtreme')}
            <input id="ndObjectiveExtreme" type="number" min="0" max="99" value="${Math.max(0, appState.settings.objectiveMonsterExtremeWeight)}">
          </label>
        </div>
      </fieldset>
      <menu>
        <button value="cancel">${t(appState.settings.language, 'dialog.button.cancel')}</button>
        <button type="button" id="ndStart">${t(appState.settings.language, 'button.startAdventure')}</button>
      </menu>
    </form>
  `;

  const envSelect = dialog.querySelector<HTMLSelectElement>('#ndEnv')!;
  const objectiveSelect = dialog.querySelector<HTMLSelectElement>('#ndObjective')!;
  const missionSelect = dialog.querySelector<HTMLSelectElement>('#ndMission')!;
  const ambienceSelect = dialog.querySelector<HTMLSelectElement>('#ndAmbience')!;
  const levelInput = dialog.querySelector<HTMLInputElement>('#ndLevel')!;
  const deckSizeInput = dialog.querySelector<HTMLInputElement>('#ndDeckSize')!;
  const roomCountInput = dialog.querySelector<HTMLInputElement>('#ndRoomCount')!;
  const missionRules = dialog.querySelector<HTMLTextAreaElement>('#ndMissionRules')!;
  const objectiveEasyInput = dialog.querySelector<HTMLInputElement>('#ndObjectiveEasy')!;
  const objectiveNormalInput = dialog.querySelector<HTMLInputElement>('#ndObjectiveNormal')!;
  const objectiveHardInput = dialog.querySelector<HTMLInputElement>('#ndObjectiveHard')!;
  const objectiveVeryHardInput = dialog.querySelector<HTMLInputElement>('#ndObjectiveVeryHard')!;
  const objectiveExtremeInput = dialog.querySelector<HTMLInputElement>('#ndObjectiveExtreme')!;

  envSelect.innerHTML = environments.map((environment) => `<option value="${escapeHtml(environment)}">${escapeHtml(environment)}</option>`).join('');

  let objectiveRooms: DungeonCard[] = [];
  let missions: ObjectiveRoomAdventure[] = [];

  const syncMissionRules = () => {
    const selectedMission = missions.find((mission) => mission.name === missionSelect.value);
    missionRules.value = selectedMission?.rulesText ?? '';
  };

  const reloadMissions = () => {
    const objective = objectiveRooms.find((room) => room.name === objectiveSelect.value);
    if (!objective) {
      missions = [];
      missionSelect.innerHTML = '';
      missionRules.value = '';
      return;
    }

    missions = appState.dungeonStore.loadAdventuresForObjectiveRoom(objective);
    missionSelect.innerHTML = missions.map((mission) => `<option value="${escapeHtml(mission.name)}">${escapeHtml(mission.name)}</option>`).join('');
    syncMissionRules();
  };

  const reloadObjectives = () => {
    objectiveRooms = appState.dungeonStore.loadObjectiveRoomsByEnvironment(envSelect.value);
    objectiveSelect.innerHTML = objectiveRooms.map((room) => `<option value="${escapeHtml(room.name)}">${escapeHtml(room.name)}</option>`).join('');
    reloadMissions();
  };

  envSelect.addEventListener('change', reloadObjectives);
  objectiveSelect.addEventListener('change', reloadMissions);
  missionSelect.addEventListener('change', syncMissionRules);

  deckSizeInput.addEventListener('change', () => {
    const deckSize = Math.max(2, Number.parseInt(deckSizeInput.value, 10) || 2);
    deckSizeInput.value = String(deckSize);
    roomCountInput.max = String(Math.max(1, deckSize - 1));
    const rooms = Math.min(Number.parseInt(roomCountInput.value, 10) || 1, deckSize - 1);
    roomCountInput.value = String(Math.max(1, rooms));
  });

  reloadObjectives();

  dialog.querySelector<HTMLButtonElement>('#ndStart')?.addEventListener('click', () => {
    const objective = objectiveRooms.find((room) => room.name === objectiveSelect.value);
    const mission = missions.find((item) => item.name === missionSelect.value);
    if (!objective || !mission) {
      window.alert(t(appState.settings.language, 'dialog.newDungeon.requiredSelection'));
      return;
    }

    const deckSize = Math.max(2, Number.parseInt(deckSizeInput.value, 10) || 2);
    const roomCount = Math.max(1, Number.parseInt(roomCountInput.value, 10) || 1);
    const dungeonLevel = Math.max(1, Math.min(10, Number.parseInt(levelInput.value, 10) || 1));

    try {
      appState.settings.objectiveMonsterEasyWeight = Math.max(0, Number.parseInt(objectiveEasyInput.value, 10) || 0);
      appState.settings.objectiveMonsterNormalWeight = Math.max(0, Number.parseInt(objectiveNormalInput.value, 10) || 0);
      appState.settings.objectiveMonsterHardWeight = Math.max(0, Number.parseInt(objectiveHardInput.value, 10) || 0);
      appState.settings.objectiveMonsterVeryHardWeight = Math.max(0, Number.parseInt(objectiveVeryHardInput.value, 10) || 0);
      appState.settings.objectiveMonsterExtremeWeight = Math.max(0, Number.parseInt(objectiveExtremeInput.value, 10) || 0);

      const deck = buildAdventureDeck(envSelect.value, objective, deckSize, roomCount, appState.dungeonCards);
      appState.settings.adventureAmbience = ambienceSelect.value;
      appState.settings.activeDungeonLevel = dungeonLevel;
      appState.settings.dungeonActive = true;
      saveSettings(appState.settings);
      appState.hooks.rebuildDecks();
      dialog.close();
      openAdventureSimulator(
        deck,
        mission,
        ambienceSelect.options[ambienceSelect.selectedIndex]?.text ?? ambienceSelect.value,
        dungeonLevel,
        envSelect.value
      );
    } catch (error) {
      window.alert(String(error));
    }
  });

  dialog.showModal();
}
