package com.whq.app.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.whq.app.i18n.I18n;
import com.whq.app.i18n.Language;
import com.whq.app.model.CardType;
import com.whq.app.model.DungeonCard;

class XmlDungeonCardStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void createsDefaultXmlStoreWhenNoSourceDataExists() throws Exception {
        createDefaultTiles();
        XmlDungeonCardStore store = new XmlDungeonCardStore(tempDir);

        List<DungeonCard> cards = store.loadCards();

        assertEquals(4, cards.size());
        assertTrue(Files.exists(tempDir.resolve("data/xml/dungeon/dungeon-cards.xml")));
    }

    @Test
    void persistsInsertedCardsAndAvailabilityChanges() throws Exception {
        createDefaultTiles();
        XmlDungeonCardStore store = new XmlDungeonCardStore(tempDir);
        store.loadCards();
        createTile("resources/tiles/crypt-stairs.png");

        store.insertCards(List.of(new DungeonCard(
                0,
                "CRYPT STAIRS",
                CardType.CORRIDOR,
                "Morr's Reach",
                2,
                true,
                "A narrow stair descends.",
                "Draw one event card.",
                "resources/tiles/crypt-stairs.png")));

        DungeonCard inserted = store.loadCards().stream()
                .filter(card -> "CRYPT STAIRS".equals(card.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(inserted.getId() > 0);

        store.updateCardAvailability(inserted.getId(), 5, false);

        DungeonCard updated = store.loadCards().stream()
                .filter(card -> card.getId() == inserted.getId())
                .findFirst()
                .orElseThrow();
        assertEquals(5, updated.getCopyCount());
        assertFalse(updated.isEnabled());

        store.deleteCard(inserted.getId());

        assertFalse(store.loadCards().stream().anyMatch(card -> card.getId() == inserted.getId()));
    }

    @Test
    void appliesDungeonCardTranslationsForCurrentLanguage() throws Exception {
        Language previousLanguage = I18n.getLanguage();
        try {
            I18n.setLanguage(Language.ES);
            createDefaultTiles();
            Files.createDirectories(tempDir.resolve("data/i18n"));
            Files.writeString(tempDir.resolve("data/i18n/content-es.xml"), """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <translations>
                      <entry key="dungeonCard.1.name">SALA TRADUCIDA</entry>
                      <entry key="dungeonCard.1.description">Descripción traducida.</entry>
                      <entry key="dungeonCard.1.rules">Reglas traducidas.</entry>
                    </translations>
                    """);
            XmlDungeonCardStore store = new XmlDungeonCardStore(tempDir);

            DungeonCard card = store.loadCards().stream()
                    .filter(candidate -> candidate.getId() == 1)
                    .findFirst()
                    .orElseThrow();

            assertEquals("SALA TRADUCIDA", card.getName());
            assertEquals("Descripción traducida.", card.getDescriptionText());
            assertEquals("Reglas traducidas.", card.getRulesText());
        } finally {
            I18n.setLanguage(previousLanguage);
        }
    }

    private void createDefaultTiles() throws Exception {
        for (DungeonCard card : XmlDungeonCardStore.defaultCards()) {
            createTile(card.getTileImagePath());
        }
    }

    private void createTile(String relativePath) throws Exception {
        Path tilePath = tempDir.resolve(relativePath);
        Files.createDirectories(tilePath.getParent());
        if (!Files.exists(tilePath)) {
            Files.writeString(tilePath, "tile");
        }
    }
}
