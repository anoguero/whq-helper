package com.whq.app.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.whq.app.model.DungeonCard;

/**
 * Estado de una partida de aventura en curso: los montones de cartas, su historial de
 * cartas reveladas, y la carta/montón actualmente seleccionados. Sin dependencias de SWT.
 */
public final class AdventureSession {

    private List<List<DungeonCard>> piles = new ArrayList<>();
    private List<List<DungeonCard>> histories = new ArrayList<>();
    private DungeonCard selectedCard;
    private int selectedPile = -1;

    public AdventureSession() {
    }

    public AdventureSession(List<DungeonCard> initialDeck) {
        piles.add(new ArrayList<>(initialDeck));
        histories.add(new ArrayList<>());
    }

    public int pileCount() {
        return piles.size();
    }

    public List<DungeonCard> pile(int pileIndex) {
        return piles.get(pileIndex);
    }

    public List<DungeonCard> history(int pileIndex) {
        return histories.get(pileIndex);
    }

    public int totalRemainingCards() {
        return piles.stream().mapToInt(List::size).sum();
    }

    public DungeonCard selectedCard() {
        return selectedCard;
    }

    public int selectedPile() {
        return selectedPile;
    }

    /** Revela (roba) la carta superior del montón indicado y la registra en su historial. */
    public DungeonCard drawFrom(int pileIndex) {
        List<DungeonCard> pile = piles.get(pileIndex);
        DungeonCard drawn = pile.remove(0);
        histories.get(pileIndex).add(0, drawn);
        selectedCard = drawn;
        selectedPile = pileIndex;
        return drawn;
    }

    /** Selecciona una carta ya revelada anteriormente (desde el historial de un montón). */
    public void selectFromHistory(int pileIndex, DungeonCard card) {
        selectedCard = card;
        selectedPile = pileIndex;
    }

    /** Divide el montón indicado en {@code pileCount} montones nuevos, conservando el resto. */
    public void splitPile(int pileIndex, int pileCount) {
        piles = splitSelectedPile(piles, pileIndex, pileCount);
        histories = splitSelectedPileHistories(histories, pileIndex, pileCount);
        selectedCard = null;
        selectedPile = -1;
    }

    /** Añade cartas al montón indicado y lo baraja. */
    public void addCardsToPile(int pileIndex, List<DungeonCard> cardsToAdd, Random random) {
        List<DungeonCard> pile = piles.get(pileIndex);
        pile.addAll(cardsToAdd);
        java.util.Collections.shuffle(pile, random);
        selectedPile = pileIndex;
    }

    /** ids de todas las cartas ya presentes en la sesión (montones, historiales y carta seleccionada). */
    public Set<Long> collectAdventureCardIds() {
        Set<Long> ids = new HashSet<>();
        for (List<DungeonCard> pile : piles) {
            for (DungeonCard card : pile) {
                ids.add(card.getId());
            }
        }
        for (List<DungeonCard> history : histories) {
            for (DungeonCard card : history) {
                ids.add(card.getId());
            }
        }
        if (selectedCard != null) {
            ids.add(selectedCard.getId());
        }
        return ids;
    }

    private static List<List<DungeonCard>> splitSelectedPile(
            List<List<DungeonCard>> piles,
            int selectedPileIndex,
            int pileCount) {
        List<List<DungeonCard>> result = new ArrayList<>();

        for (int i = 0; i < piles.size(); i++) {
            List<DungeonCard> sourcePile = piles.get(i);
            if (i != selectedPileIndex) {
                result.add(new ArrayList<>(sourcePile));
                continue;
            }

            List<List<DungeonCard>> splitPiles = new ArrayList<>();
            for (int j = 0; j < pileCount; j++) {
                splitPiles.add(new ArrayList<>());
            }
            for (int cardIndex = 0; cardIndex < sourcePile.size(); cardIndex++) {
                splitPiles.get(cardIndex % pileCount).add(sourcePile.get(cardIndex));
            }
            result.addAll(splitPiles);
        }

        return result;
    }

    private static List<List<DungeonCard>> splitSelectedPileHistories(
            List<List<DungeonCard>> currentHistories,
            int selectedPileIndex,
            int pileCount) {
        List<List<DungeonCard>> result = new ArrayList<>();

        for (int i = 0; i < currentHistories.size(); i++) {
            List<DungeonCard> sourceHistory = currentHistories.get(i);
            if (i != selectedPileIndex) {
                result.add(new ArrayList<>(sourceHistory));
                continue;
            }

            for (int j = 0; j < pileCount; j++) {
                result.add(new ArrayList<>());
            }
            if (!sourceHistory.isEmpty()) {
                result.get(result.size() - pileCount).addAll(sourceHistory);
            }
        }

        return result;
    }
}
