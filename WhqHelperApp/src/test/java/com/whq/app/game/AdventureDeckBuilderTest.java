package com.whq.app.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.whq.app.model.CardType;
import com.whq.app.model.DungeonCard;

class AdventureDeckBuilderTest {

    private static final String ENV = "sewers";

    private static long nextId = 1;

    private static DungeonCard card(CardType type, int copyCount, boolean enabled) {
        return new DungeonCard(
                nextId++,
                type.getLabel() + "-" + nextId,
                type,
                ENV,
                copyCount,
                enabled,
                "descripcion",
                "reglas",
                "tile.png");
    }

    private static List<DungeonCard> basePool(int dungeonRoomCount, int corridorCount) {
        List<DungeonCard> pool = new ArrayList<>();
        for (int i = 0; i < dungeonRoomCount; i++) {
            pool.add(card(CardType.DUNGEON_ROOM, 3, true));
        }
        for (int i = 0; i < corridorCount; i++) {
            pool.add(card(CardType.CORRIDOR, 3, true));
        }
        return pool;
    }

    @Test
    void deckHasExactlyDeckSizeCards() {
        List<DungeonCard> pool = basePool(4, 4);
        DungeonCard objectiveRoom = card(CardType.OBJECTIVE_ROOM, 1, true);
        AdventureDeckBuilder builder = new AdventureDeckBuilder(pool, new Random(1));

        List<DungeonCard> deck = builder.buildAdventureDeck(ENV, objectiveRoom, 10, 3);

        assertEquals(10, deck.size());
    }

    @Test
    void objectiveRoomAlwaysFallsInLastFivePositions() {
        List<DungeonCard> pool = basePool(6, 6);
        DungeonCard objectiveRoom = card(CardType.OBJECTIVE_ROOM, 1, true);

        for (long seed = 0; seed < 30; seed++) {
            AdventureDeckBuilder builder = new AdventureDeckBuilder(pool, new Random(seed));
            List<DungeonCard> deck = builder.buildAdventureDeck(ENV, objectiveRoom, 12, 4);
            int objectiveIndex = deck.indexOf(objectiveRoom);
            assertTrue(objectiveIndex >= 12 - 5, "seed " + seed + " objectiveIndex=" + objectiveIndex);
        }
    }

    @Test
    void respectsRequestedDungeonRoomCount() {
        List<DungeonCard> pool = basePool(6, 6);
        DungeonCard objectiveRoom = card(CardType.OBJECTIVE_ROOM, 1, true);
        AdventureDeckBuilder builder = new AdventureDeckBuilder(pool, new Random(7));

        List<DungeonCard> deck = builder.buildAdventureDeck(ENV, objectiveRoom, 12, 5);

        long dungeonRoomsInDeck = deck.stream().filter(c -> c.getType() == CardType.DUNGEON_ROOM).count();
        assertEquals(5, dungeonRoomsInDeck);
    }

    @Test
    void copyCountLimitsHowManyTimesACardAppears() {
        List<DungeonCard> pool = new ArrayList<>();
        DungeonCard limitedRoom = card(CardType.DUNGEON_ROOM, 1, true);
        pool.add(limitedRoom);
        pool.add(card(CardType.DUNGEON_ROOM, 5, true));
        pool.addAll(basePool(0, 6));
        DungeonCard objectiveRoom = card(CardType.OBJECTIVE_ROOM, 1, true);
        AdventureDeckBuilder builder = new AdventureDeckBuilder(pool, new Random(3));

        List<DungeonCard> deck = builder.buildAdventureDeck(ENV, objectiveRoom, 12, 4);

        long limitedRoomCopiesInDeck = deck.stream().filter(c -> c == limitedRoom).count();
        assertTrue(limitedRoomCopiesInDeck <= 1);
    }

    @Test
    void throwsWhenDeckIsTooSmall() {
        List<DungeonCard> pool = basePool(2, 2);
        DungeonCard objectiveRoom = card(CardType.OBJECTIVE_ROOM, 1, true);
        AdventureDeckBuilder builder = new AdventureDeckBuilder(pool, new Random(1));

        AdventureDeckException ex = assertThrows(AdventureDeckException.class,
                () -> builder.buildAdventureDeck(ENV, objectiveRoom, 1, 1));

        assertEquals("dialog.newDungeon.error.deckTooSmall", ex.i18nKey());
    }

    @Test
    void throwsWhenNoRoomCardsRequested() {
        List<DungeonCard> pool = basePool(2, 2);
        DungeonCard objectiveRoom = card(CardType.OBJECTIVE_ROOM, 1, true);
        AdventureDeckBuilder builder = new AdventureDeckBuilder(pool, new Random(1));

        AdventureDeckException ex = assertThrows(AdventureDeckException.class,
                () -> builder.buildAdventureDeck(ENV, objectiveRoom, 8, 0));

        assertEquals("dialog.newDungeon.error.noRoomCards", ex.i18nKey());
    }

    @Test
    void throwsWhenRoomCountIsTooLarge() {
        List<DungeonCard> pool = basePool(6, 6);
        DungeonCard objectiveRoom = card(CardType.OBJECTIVE_ROOM, 1, true);
        AdventureDeckBuilder builder = new AdventureDeckBuilder(pool, new Random(1));

        AdventureDeckException ex = assertThrows(AdventureDeckException.class,
                () -> builder.buildAdventureDeck(ENV, objectiveRoom, 5, 5));

        assertEquals("dialog.newDungeon.error.roomCountTooLarge", ex.i18nKey());
    }

    @Test
    void throwsWhenObjectiveRoomIsDisabled() {
        List<DungeonCard> pool = basePool(4, 4);
        DungeonCard objectiveRoom = card(CardType.OBJECTIVE_ROOM, 1, false);
        AdventureDeckBuilder builder = new AdventureDeckBuilder(pool, new Random(1));

        AdventureDeckException ex = assertThrows(AdventureDeckException.class,
                () -> builder.buildAdventureDeck(ENV, objectiveRoom, 8, 3));

        assertEquals("dialog.newDungeon.error.objectiveRoomUnavailable", ex.i18nKey());
    }

    @Test
    void throwsWhenObjectiveRoomHasNoCopiesLeft() {
        List<DungeonCard> pool = basePool(4, 4);
        DungeonCard objectiveRoom = card(CardType.OBJECTIVE_ROOM, 0, true);
        AdventureDeckBuilder builder = new AdventureDeckBuilder(pool, new Random(1));

        AdventureDeckException ex = assertThrows(AdventureDeckException.class,
                () -> builder.buildAdventureDeck(ENV, objectiveRoom, 8, 3));

        assertEquals("dialog.newDungeon.error.objectiveRoomUnavailable", ex.i18nKey());
    }

    @Test
    void throwsWhenThereAreNoDungeonRoomCards() {
        List<DungeonCard> pool = basePool(0, 6);
        DungeonCard objectiveRoom = card(CardType.OBJECTIVE_ROOM, 1, true);
        AdventureDeckBuilder builder = new AdventureDeckBuilder(pool, new Random(1));

        AdventureDeckException ex = assertThrows(AdventureDeckException.class,
                () -> builder.buildAdventureDeck(ENV, objectiveRoom, 8, 3));

        assertEquals("dialog.newDungeon.error.noDungeonRoomCards", ex.i18nKey());
    }

    @Test
    void throwsWhenNotEnoughDungeonRoomCopies() {
        List<DungeonCard> pool = new ArrayList<>();
        pool.add(card(CardType.DUNGEON_ROOM, 2, true));
        pool.addAll(basePool(0, 6));
        DungeonCard objectiveRoom = card(CardType.OBJECTIVE_ROOM, 1, true);
        AdventureDeckBuilder builder = new AdventureDeckBuilder(pool, new Random(1));

        AdventureDeckException ex = assertThrows(AdventureDeckException.class,
                () -> builder.buildAdventureDeck(ENV, objectiveRoom, 8, 3));

        assertEquals("dialog.newDungeon.error.notEnoughDungeonRoomCopies", ex.i18nKey());
    }

    @Test
    void throwsWhenThereAreNoFillerCards() {
        List<DungeonCard> pool = basePool(6, 0);
        DungeonCard objectiveRoom = card(CardType.OBJECTIVE_ROOM, 1, true);
        AdventureDeckBuilder builder = new AdventureDeckBuilder(pool, new Random(1));

        // deckSize=8, dungeonRoomCount=3 -> fillerCount = 8 - 3 - 1 = 4, but no corridor/special cards.
        AdventureDeckException ex = assertThrows(AdventureDeckException.class,
                () -> builder.buildAdventureDeck(ENV, objectiveRoom, 8, 3));

        assertEquals("dialog.newDungeon.error.noFillerCards", ex.i18nKey());
    }

    @Test
    void throwsWhenNotEnoughFillerCopies() {
        List<DungeonCard> pool = new ArrayList<>();
        pool.addAll(basePool(6, 0));
        pool.add(card(CardType.CORRIDOR, 1, true));
        DungeonCard objectiveRoom = card(CardType.OBJECTIVE_ROOM, 1, true);
        AdventureDeckBuilder builder = new AdventureDeckBuilder(pool, new Random(1));

        // fillerCount = 8 - 3 - 1 = 4, but only 1 filler copy available.
        AdventureDeckException ex = assertThrows(AdventureDeckException.class,
                () -> builder.buildAdventureDeck(ENV, objectiveRoom, 8, 3));

        assertEquals("dialog.newDungeon.error.notEnoughFillerCopies", ex.i18nKey());
    }

    @Test
    void sameSeedProducesIdenticalDeck() {
        List<DungeonCard> pool = basePool(6, 6);
        DungeonCard objectiveRoom = card(CardType.OBJECTIVE_ROOM, 1, true);

        List<DungeonCard> deckA = new AdventureDeckBuilder(pool, new Random(99))
                .buildAdventureDeck(ENV, objectiveRoom, 12, 4);
        List<DungeonCard> deckB = new AdventureDeckBuilder(pool, new Random(99))
                .buildAdventureDeck(ENV, objectiveRoom, 12, 4);

        assertEquals(deckA, deckB);
    }

    @Test
    void pickAdditionalAdventureCardsExcludesExistingIdsAndObjectiveRooms() {
        List<DungeonCard> pool = basePool(3, 3);
        DungeonCard objectiveRoom = card(CardType.OBJECTIVE_ROOM, 2, true);
        pool.add(objectiveRoom);
        DungeonCard disabledCard = card(CardType.CORRIDOR, 3, false);
        pool.add(disabledCard);
        DungeonCard noCopiesCard = card(CardType.CORRIDOR, 0, true);
        pool.add(noCopiesCard);

        Set<Long> existingIds = Set.of(pool.get(0).getId());
        AdventureDeckBuilder builder = new AdventureDeckBuilder(pool, new Random(5));

        List<DungeonCard> additional = builder.pickAdditionalAdventureCards(ENV, existingIds);

        assertTrue(additional.stream().noneMatch(c -> c.getId() == pool.get(0).getId()));
        assertTrue(additional.stream().noneMatch(c -> c.getType() == CardType.OBJECTIVE_ROOM));
        assertTrue(additional.stream().noneMatch(c -> !c.isEnabled()));
        assertTrue(additional.stream().noneMatch(c -> c.getCopyCount() <= 0));
    }

    @Test
    void pickAdditionalAdventureCardsIgnoresOtherEnvironments() {
        List<DungeonCard> pool = basePool(2, 2);
        DungeonCard otherEnvironmentCard = new DungeonCard(
                nextId++, "otro", CardType.CORRIDOR, "otro-entorno", 3, true, "d", "r", "tile.png");
        pool.add(otherEnvironmentCard);
        AdventureDeckBuilder builder = new AdventureDeckBuilder(pool, new Random(5));

        List<DungeonCard> additional = builder.pickAdditionalAdventureCards(ENV, Set.of());

        assertTrue(additional.stream().noneMatch(c -> c == otherEnvironmentCard));
    }

    @Test
    void deckDoesNotContainDisabledOrExhaustedCards() {
        List<DungeonCard> pool = basePool(6, 6);
        pool.add(card(CardType.CORRIDOR, 0, true));
        pool.add(card(CardType.SPECIAL, 3, false));
        DungeonCard objectiveRoom = card(CardType.OBJECTIVE_ROOM, 1, true);
        AdventureDeckBuilder builder = new AdventureDeckBuilder(pool, new Random(11));

        List<DungeonCard> deck = builder.buildAdventureDeck(ENV, objectiveRoom, 12, 4);

        List<DungeonCard> problematic = deck.stream()
                .filter(c -> !c.isEnabled() || c.getCopyCount() <= 0)
                .collect(Collectors.toList());
        assertTrue(problematic.isEmpty());
    }
}
