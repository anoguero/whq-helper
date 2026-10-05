package com.whq.app.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import com.whq.app.model.CardType;
import com.whq.app.model.DungeonCard;

/**
 * Construye mazos de aventura a partir del catálogo de cartas de mazmorra.
 * Sin dependencias de SWT ni de I18n: los errores se señalan mediante
 * {@link AdventureDeckException}, que lleva la clave i18n del error.
 */
public final class AdventureDeckBuilder {

    private final List<DungeonCard> cards;
    private final Random random;

    public AdventureDeckBuilder(List<DungeonCard> cards, Random random) {
        this.cards = cards;
        this.random = random;
    }

    public List<DungeonCard> buildAdventureDeck(
            String environment,
            DungeonCard objectiveRoom,
            int deckSize,
            int dungeonRoomCount) {
        if (deckSize < 2) {
            throw new AdventureDeckException("dialog.newDungeon.error.deckTooSmall");
        }
        if (dungeonRoomCount < 1) {
            throw new AdventureDeckException("dialog.newDungeon.error.noRoomCards");
        }
        if (dungeonRoomCount > deckSize - 1) {
            throw new AdventureDeckException("dialog.newDungeon.error.roomCountTooLarge");
        }

        List<DungeonCard> environmentCards = cards.stream()
                .filter(card -> environment.equalsIgnoreCase(card.getEnvironment()))
                .filter(DungeonCard::isEnabled)
                .filter(card -> card.getCopyCount() > 0)
                .collect(Collectors.toList());

        if (!objectiveRoom.isEnabled() || objectiveRoom.getCopyCount() <= 0) {
            throw new AdventureDeckException("dialog.newDungeon.error.objectiveRoomUnavailable");
        }

        List<DungeonCard> dungeonRoomPool = environmentCards.stream()
                .filter(card -> card.getType() == CardType.DUNGEON_ROOM)
                .collect(Collectors.toList());
        int availableDungeonRoomCopies = dungeonRoomPool.stream().mapToInt(DungeonCard::getCopyCount).sum();
        if (availableDungeonRoomCopies <= 0) {
            throw new AdventureDeckException("dialog.newDungeon.error.noDungeonRoomCards");
        }
        if (dungeonRoomCount > availableDungeonRoomCopies) {
            throw new AdventureDeckException("dialog.newDungeon.error.notEnoughDungeonRoomCopies");
        }

        List<DungeonCard> corridorAndSpecialPool = environmentCards.stream()
                .filter(card -> card.getType() == CardType.CORRIDOR || card.getType() == CardType.SPECIAL)
                .collect(Collectors.toList());
        int fillerCount = deckSize - dungeonRoomCount - 1;
        int availableFillerCopies = corridorAndSpecialPool.stream().mapToInt(DungeonCard::getCopyCount).sum();
        if (fillerCount > 0 && availableFillerCopies <= 0) {
            throw new AdventureDeckException("dialog.newDungeon.error.noFillerCards");
        }
        if (fillerCount > availableFillerCopies) {
            throw new AdventureDeckException("dialog.newDungeon.error.notEnoughFillerCopies");
        }

        List<DungeonCard> dungeonRooms = pickCardsByAvailableCopies(dungeonRoomPool, dungeonRoomCount);
        List<DungeonCard> fillers = pickCardsByAvailableCopies(corridorAndSpecialPool, fillerCount);

        List<DungeonCard> nonObjectiveCards = new ArrayList<>(deckSize - 1);
        nonObjectiveCards.addAll(dungeonRooms);
        nonObjectiveCards.addAll(fillers);
        Collections.shuffle(nonObjectiveCards, random);

        List<DungeonCard> deck = new ArrayList<>(Collections.nCopies(deckSize, null));
        // Regla deliberada: la sala objetivo siempre cae en las últimas 5 posiciones del mazo.
        int minObjectiveIndex = Math.max(0, deckSize - 5);
        int objectiveIndex = minObjectiveIndex + random.nextInt(deckSize - minObjectiveIndex);
        deck.set(objectiveIndex, objectiveRoom);

        int nonObjectiveIndex = 0;
        for (int i = 0; i < deck.size(); i++) {
            if (deck.get(i) == null) {
                deck.set(i, nonObjectiveCards.get(nonObjectiveIndex));
                nonObjectiveIndex++;
            }
        }
        return deck;
    }

    private List<DungeonCard> pickCardsByAvailableCopies(List<DungeonCard> pool, int count) {
        if (count <= 0) {
            return new ArrayList<>();
        }

        List<DungeonCard> expandedPool = new ArrayList<>();
        for (DungeonCard card : pool) {
            for (int i = 0; i < card.getCopyCount(); i++) {
                expandedPool.add(card);
            }
        }
        Collections.shuffle(expandedPool, random);
        return new ArrayList<>(expandedPool.subList(0, count));
    }

    public List<DungeonCard> pickAdditionalAdventureCards(String environment, Set<Long> existingIds) {
        List<DungeonCard> eligible = cards.stream()
                .filter(card -> environment.equalsIgnoreCase(card.getEnvironment()))
                .filter(DungeonCard::isEnabled)
                .filter(card -> card.getCopyCount() > 0)
                .filter(card -> card.getType() != CardType.OBJECTIVE_ROOM)
                .filter(card -> !existingIds.contains(card.getId()))
                .collect(Collectors.toCollection(ArrayList::new));

        Collections.shuffle(eligible, random);
        return eligible;
    }
}
