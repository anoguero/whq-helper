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
import com.whq.app.AppPaths;
import com.whq.app.RealContent;

class XmlDungeonCardStoreTest {

    private static final Path SAMPLE_CATALOG = AppPaths.sharedHome(Path.of("").toAbsolutePath().normalize())
            .resolve("sample/data/xml/dungeon/dungeon-cards.xml");

    @TempDir
    Path tempDir;

    @Test
    void loadsTheSharedCatalogIgnoringOtherXmlInTheDungeonDirectory() throws Exception {
        RealContent.assumeAvailable();
        // shared/data/xml/dungeon tambien contiene room-references.xml, que no es un catalogo de cartas.
        List<DungeonCard> cards = new XmlDungeonCardStore(Path.of("")).loadCards();

        assertTrue(cards.stream().anyMatch(card -> card.getId() == 56L));
    }

    @Test
    void writesNothingIntoAContentPackageWithoutACardCatalog() throws Exception {
        // El fallo: con un paquete incompleto se escribia en el un catalogo de cartas por defecto.
        Path runtimeHome = tempDir.resolve("user-home");
        Path content = tempDir.resolve("partial-content");
        Files.createDirectories(content.resolve("data/xml/tables"));
        AppPaths.bindSharedHome(runtimeHome, AppPaths.sharedHome(Path.of("").toAbsolutePath().normalize()));
        String previousContentHome = System.getProperty("whq.content.home");
        System.setProperty("whq.content.home", content.toString());
        try {
            List<DungeonCard> cards = new XmlDungeonCardStore(runtimeHome).loadCards();

            assertEquals(List.of(), cards);
            assertFalse(Files.exists(content.resolve("data/xml/dungeon")));
        } finally {
            if (previousContentHome == null) {
                System.clearProperty("whq.content.home");
            } else {
                System.setProperty("whq.content.home", previousContentHome);
            }
        }
    }

    @Test
    void loadsTheOtherCatalogsOfAPackageWithoutTheMainOne() throws Exception {
        Path dungeon = Files.createDirectories(tempDir.resolve("data/xml/dungeon"));
        Files.copy(SAMPLE_CATALOG, dungeon.resolve("extra-cards.xml"));

        List<DungeonCard> cards = new XmlDungeonCardStore(tempDir).loadCards();

        assertEquals(List.of("LANTERN HALL"), cards.stream().map(DungeonCard::getName).toList());
        assertFalse(Files.exists(dungeon.resolve("dungeon-cards.xml")));
    }

    @Test
    void persistsInsertedCardsAndAvailabilityChanges() throws Exception {
        copySampleCatalog();
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
            copySampleCatalog();
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

    // El catalogo base lo trae el paquete de contenido: el de ejemplo, con la carta 1.
    private void copySampleCatalog() throws Exception {
        Path dungeon = Files.createDirectories(tempDir.resolve("data/xml/dungeon"));
        Files.copy(SAMPLE_CATALOG, dungeon.resolve("dungeon-cards.xml"));
        createTile("resources/tiles/sample/lantern-hall.png");
    }

    private void createTile(String relativePath) throws Exception {
        Path tilePath = tempDir.resolve(relativePath);
        Files.createDirectories(tilePath.getParent());
        if (!Files.exists(tilePath)) {
            Files.writeString(tilePath, "tile");
        }
    }
}
