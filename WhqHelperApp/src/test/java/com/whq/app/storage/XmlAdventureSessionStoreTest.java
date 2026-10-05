package com.whq.app.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.whq.app.AppPaths;
import com.whq.app.game.AdventureSession;
import com.whq.app.game.SavedAdventure;
import com.whq.app.model.CardType;
import com.whq.app.model.DungeonCard;

import pms.whq.content.ContentIssue;

class XmlAdventureSessionStoreTest {

    @TempDir
    Path tempDir;

    private final Map<Long, DungeonCard> catalog = new LinkedHashMap<>();
    private XmlAdventureSessionStore store;

    @BeforeEach
    void setUp() {
        // El esquema es contenido base: se usa el de shared/ del repositorio.
        AppPaths.bindSharedHome(tempDir, AppPaths.sharedHome(Path.of("")));
        store = new XmlAdventureSessionStore(tempDir, Clock.fixed(Instant.parse("2026-10-05T14:33:00Z"), ZoneOffset.UTC));
        for (long id = 1; id <= 9; id++) {
            catalog.put(id, card(id));
        }
    }

    @Test
    void roundTripsSeveralPilesHistoriesAndContext() throws Exception {
        AdventureSession session = new AdventureSession(
                List.of(cards(1, 2, 3), cards(4), cards()),
                List.of(cards(5, 6), cards(), cards(7, 8, 9)),
                2,
                catalog.get(8L));
        store.save(new SavedAdventure("The Old World", 3, "undead", 4, 9, null, "fighting-pit-1", session));

        List<ContentIssue> issues = new ArrayList<>();
        SavedAdventure loaded = new XmlAdventureSessionStore(tempDir).loadPending(catalog, issues::add).orElseThrow();

        assertTrue(issues.isEmpty(), issues.toString());
        assertEquals("The Old World", loaded.environment());
        assertEquals(3, loaded.adventureLevel());
        assertEquals("undead", loaded.ambience());
        assertEquals(4, loaded.partySize());
        assertEquals(9L, loaded.objectiveRoomCardId());
        assertSame(catalog.get(9L), loaded.objectiveRoom());
        assertEquals("fighting-pit-1", loaded.missionId());
        assertEquals(3, loaded.session().pileCount());
        assertEquals(2, loaded.session().selectedPile());
        assertSame(catalog.get(8L), loaded.session().selectedCard());
        assertTrue(Files.exists(tempDir.resolve("data/sessions/20261005-143300.xml")));
    }

    @Test
    void preservesTheOrderOfPilesAndHistories() throws Exception {
        AdventureSession session = new AdventureSession(cards(3, 1, 2, 5, 4));
        session.drawFrom(0);
        session.drawFrom(0);
        session.splitPile(0, 2);
        store.save(new SavedAdventure("The Old World", 1, "generic", 4, 9, null, null, session));

        AdventureSession loaded = store.loadPending(catalog, issue -> { }).orElseThrow().session();

        for (int i = 0; i < session.pileCount(); i++) {
            assertEquals(ids(session.pile(i)), ids(loaded.pile(i)), "pile " + i);
            assertEquals(ids(session.history(i)), ids(loaded.history(i)), "history " + i);
        }
        // drawFrom inserta en la posicion 0: el historial va de la mas reciente a la mas antigua.
        assertEquals(List.of(1L, 3L), ids(loaded.history(0)));
    }

    @Test
    void sessionWithoutSelectionOmitsTheSelectionElement() throws Exception {
        AdventureSession session = new AdventureSession(cards(1, 2, 3, 4));
        session.splitPile(0, 2);
        store.save(new SavedAdventure("The Old World", 1, "generic", 4, 9, null, null, session));

        assertFalse(Files.readString(tempDir.resolve("data/sessions/20261005-143300.xml")).contains("<selection"));
        AdventureSession loaded = store.loadPending(catalog, issue -> { }).orElseThrow().session();
        assertEquals(-1, loaded.selectedPile());
        assertNull(loaded.selectedCard());
    }

    @Test
    void unknownIdsAreReportedAndSkippedInsteadOfFailing() throws Exception {
        AdventureSession session = new AdventureSession(
                List.of(cards(1, 2, 3)), List.of(cards(4, 5)), 0, catalog.get(4L));
        store.save(new SavedAdventure("The Old World", 1, "generic", 4, 9, null, null, session));
        catalog.remove(2L);
        catalog.remove(4L);
        catalog.remove(9L);

        List<ContentIssue> issues = new ArrayList<>();
        SavedAdventure loaded = store.loadPending(catalog, issues::add).orElseThrow();

        assertEquals(List.of(1L, 3L), ids(loaded.session().pile(0)));
        assertEquals(List.of(5L), ids(loaded.session().history(0)));
        assertEquals(-1, loaded.session().selectedPile());
        assertNull(loaded.objectiveRoom());
        // Pila (2), historial (4), seleccion (4) y sala objetivo (9).
        assertEquals(4, issues.size(), issues.toString());
        assertTrue(issues.stream().anyMatch(issue -> issue.message().contains("9")
                && (issue.message().contains("sala objetivo") || issue.message().contains("objective room"))));
    }

    @Test
    void rejectsPilesAndHistoriesOfDifferentLengthOnLoad() throws Exception {
        Path file = tempDir.resolve("data/sessions/20261005-143300.xml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                <?xml version="1.0" encoding="UTF-8"?>
                <adventure-session version="1" savedAt="2026-10-05T14:33:00Z">
                  <context environment="The Old World" adventureLevel="1" ambience="generic" partySize="4"/>
                  <objective-room cardId="9"/>
                  <piles>
                    <pile index="0"><card id="1"/></pile>
                    <pile index="1"/>
                  </piles>
                  <histories>
                    <history pileIndex="0"/>
                  </histories>
                </adventure-session>
                """);

        List<ContentIssue> issues = new ArrayList<>();
        Optional<SavedAdventure> loaded = store.loadPending(catalog, issues::add);

        assertTrue(loaded.isEmpty());
        assertEquals(1, issues.size());
        assertFalse(Files.exists(file));
        assertTrue(Files.exists(file.resolveSibling(file.getFileName() + ".invalid")));
    }

    @Test
    void constructorRejectsPilesAndHistoriesOfDifferentLength() {
        try {
            new AdventureSession(List.of(cards(1), cards(2)), List.of(cards()), -1, null);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Se esperaba IllegalArgumentException");
    }

    @Test
    void everyMutationNotifiesTheChangeListenerSoTheSessionIsAutoSaved() throws Exception {
        AdventureSession session = new AdventureSession(cards(1, 2, 3, 4, 5, 6));
        session.setChangeListener(() -> {
            try {
                store.save(new SavedAdventure("The Old World", 1, "generic", 4, 9, null, null, session));
            } catch (AdventureSessionStorageException ex) {
                throw new IllegalStateException(ex);
            }
        });

        DungeonCard drawn = session.drawFrom(0);
        assertEquals(List.of(drawn.getId()), ids(store.loadPending(catalog, issue -> { }).orElseThrow().session().history(0)));
        session.splitPile(0, 2);
        assertEquals(2, store.loadPending(catalog, issue -> { }).orElseThrow().session().pileCount());
        session.addCardsToPile(1, cards(7), new java.util.Random(1));
        assertTrue(ids(store.loadPending(catalog, issue -> { }).orElseThrow().session().pile(1)).contains(7L));
        session.selectFromHistory(0, drawn);
        assertEquals(0, store.loadPending(catalog, issue -> { }).orElseThrow().session().selectedPile());

        try (var files = Files.list(tempDir.resolve("data/sessions"))) {
            assertEquals(1, files.count(), "Una unica sesion en curso que se sobrescribe");
        }
    }

    @Test
    void discardRemovesThePendingSession() throws Exception {
        store.save(new SavedAdventure("The Old World", 1, "generic", 4, 9, null, null, new AdventureSession(cards(1))));
        assertTrue(store.hasPendingSession());

        store.discard();

        assertFalse(store.hasPendingSession());
        assertTrue(store.loadPending(catalog, issue -> { }).isEmpty());
    }

    private List<DungeonCard> cards(long... ids) {
        List<DungeonCard> result = new ArrayList<>();
        for (long id : ids) {
            result.add(catalog.get(id));
        }
        return result;
    }

    private static List<Long> ids(List<DungeonCard> cards) {
        return cards.stream().map(DungeonCard::getId).toList();
    }

    private static DungeonCard card(long id) {
        return new DungeonCard(id, "CARD " + id, CardType.DUNGEON_ROOM, "The Old World", 1, true, "", "", "resources/tiles/x.png");
    }
}
