package com.whq.app.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.BeforeEach;

import com.whq.app.AppPaths;
import com.whq.app.i18n.I18n;
import com.whq.app.i18n.Language;
import com.whq.app.model.CardType;
import com.whq.app.model.DungeonCard;
import com.whq.app.storage.XmlDungeonCardStore;
import com.whq.app.RealContent;

class XmlObjectiveRoomAdventureRepositoryTest {

    private static final long FIGHTING_PIT = 20L;

    @TempDir
    Path tempDir;

    private final Language previousLanguage = I18n.getLanguage();

    @AfterEach
    void restoreLanguage() {
        I18n.setLanguage(previousLanguage);
    }

    @BeforeEach
    void requireRealContent() {
        RealContent.assumeAvailable();
    }

    @Test
    void loadsObjectiveRoomAdventuresIncludingGenericMission() throws Exception {
        // Los nombres de aventura se traducen con el idioma activo de I18n; fijamos EN para que la
        // asercion sea determinista independientemente del idioma por defecto de la aplicacion.
        I18n.setLanguage(Language.EN);
        XmlObjectiveRoomAdventureRepository repository = new XmlObjectiveRoomAdventureRepository(
                Path.of(System.getProperty("user.dir")));

        List<ObjectiveRoomAdventure> adventures = repository.loadAdventuresForObjectiveRoom(sharedCard(FIGHTING_PIT));

        assertEquals(7, adventures.size());
        assertTrue(adventures.stream().anyMatch(ObjectiveRoomAdventure::generic));
        assertTrue(adventures.stream().anyMatch(adventure -> "Free the Prisoners".equals(adventure.name())));
    }

    @Test
    void returnsGenericMissionWhenObjectiveRoomHasNoConfiguredAdventures() throws Exception {
        XmlObjectiveRoomAdventureRepository repository = new XmlObjectiveRoomAdventureRepository(
                Path.of(System.getProperty("user.dir")));

        List<ObjectiveRoomAdventure> adventures = repository.loadAdventuresForObjectiveRoom(
                new DungeonCard(9_999L, "WICKED WELL", CardType.OBJECTIVE_ROOM, "The Old World", 1, true, "", "", ""));

        assertEquals(1, adventures.size());
        assertTrue(adventures.get(0).generic());
        assertEquals("WICKED WELL", adventures.get(0).objectiveRoomName());
        assertFalse(adventures.get(0).rulesText().isBlank());
    }

    @Test
    void findsTheMissionsOfATranslatedObjectiveRoomInSpanish() throws Exception {
        // El fallo original: en ES la carta se llama "FOSO DE COMBATE" y la busqueda por nombre no
        // encontraba las aventuras de "FIGHTING PIT"; solo quedaba la mision generica.
        I18n.setLanguage(Language.ES);
        DungeonCard fightingPit = sharedCard(FIGHTING_PIT);
        assertEquals("FOSO DE COMBATE", fightingPit.getName());

        List<ObjectiveRoomAdventure> adventures = new XmlObjectiveRoomAdventureRepository(Path.of(""))
                .loadAdventuresForObjectiveRoom(fightingPit);

        assertEquals(7, adventures.size());
        assertEquals(6, adventures.stream().filter(adventure -> !adventure.generic()).count());
        assertTrue(adventures.stream().allMatch(adventure -> adventure.objectiveRoomCardId() == FIGHTING_PIT));
    }

    @Test
    void resolvesAnObjectiveRoomWithoutCardIdByName() throws Exception {
        Path runtimeHome = copySharedContent();
        Files.writeString(runtimeHome.resolve("data/xml/adventures/userdefined-objective-room-adventures.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <objectiveRoomAdventures>
                  <objectiveRoom name="FOSO DE COMBATE">
                    <adventure id="legacy-es" name="Legacy ES" generic="false"><flavor>F</flavor><rules>R</rules></adventure>
                  </objectiveRoom>
                  <objectiveRoom name="FIGHTING PIT">
                    <adventure id="legacy-en" name="Legacy EN" generic="false"><flavor>F</flavor><rules>R</rules></adventure>
                  </objectiveRoom>
                </objectiveRoomAdventures>
                """);
        XmlObjectiveRoomAdventureRepository repository = new XmlObjectiveRoomAdventureRepository(runtimeHome);

        for (Language language : Language.values()) {
            I18n.setLanguage(language);
            List<String> ids = repository.loadAdventuresForObjectiveRoom(sharedCard(FIGHTING_PIT)).stream()
                    .map(ObjectiveRoomAdventure::id)
                    .toList();
            assertTrue(ids.contains("legacy-es"), language + ": " + ids);
            assertTrue(ids.contains("legacy-en"), language + ": " + ids);
            assertTrue(ids.contains("1"), language + ": " + ids);
        }
    }

    @Test
    void addsUpdatesAndDeletesUserAdventures() throws Exception {
        Path runtimeHome = copySharedContent();
        XmlObjectiveRoomAdventureRepository repository = new XmlObjectiveRoomAdventureRepository(runtimeHome);
        I18n.setLanguage(Language.ES);
        DungeonCard fightingPit = sharedCard(FIGHTING_PIT);

        // Como el editor de contenido: sala por nombre traducido y sin cardId.
        repository.saveUserAdventure(new ObjectiveRoomAdventure("FOSO DE COMBATE", "mi-mision", "Mi mision", "Sabor", "Reglas", false));
        Path userFile = runtimeHome.resolve("data/xml/adventures/userdefined-objective-room-adventures.xml");
        String saved = Files.readString(userFile);
        assertTrue(saved.contains("cardId=\"20\""), saved);
        assertTrue(saved.contains("name=\"FIGHTING PIT\""), saved);
        assertEquals("Mi mision", find(repository, fightingPit, "mi-mision").name());

        repository.saveUserAdventure(new ObjectiveRoomAdventure("FOSO DE COMBATE", "mi-mision", "Mi mision 2", "Sabor", "Reglas", false));
        assertEquals("Mi mision 2", find(repository, fightingPit, "mi-mision").name());
        assertEquals(1, repository.loadAdventuresForObjectiveRoom(fightingPit).stream()
                .filter(adventure -> "mi-mision".equals(adventure.id()))
                .count());

        // Se conserva otra aventura de usuario (borrar la ultima se prueba aparte).
        repository.saveUserAdventure(new ObjectiveRoomAdventure("FIGHTING PIT", "otra", "Otra", "Sabor", "Reglas", false));
        repository.deleteUserAdventure("FOSO DE COMBATE", "mi-mision");
        List<String> ids = repository.loadAdventuresForObjectiveRoom(fightingPit).stream()
                .map(ObjectiveRoomAdventure::id)
                .toList();
        assertFalse(ids.contains("mi-mision"), ids.toString());
        assertTrue(ids.contains("otra"), ids.toString());
    }

    @Test
    void deletingTheLastUserAdventureLeavesNoInvalidFile() throws Exception {
        Path runtimeHome = copySharedContent();
        XmlObjectiveRoomAdventureRepository repository = new XmlObjectiveRoomAdventureRepository(runtimeHome);
        I18n.setLanguage(Language.ES);
        DungeonCard fightingPit = sharedCard(FIGHTING_PIT);
        Path userFile = runtimeHome.resolve("data/xml/adventures/userdefined-objective-room-adventures.xml");
        Path userTranslations = runtimeHome.resolve("data/i18n/userdefined-content-es.xml");

        repository.saveUserAdventure(new ObjectiveRoomAdventure("FOSO DE COMBATE", "unica", "Unica", "Sabor", "Reglas", false));
        assertTrue(Files.exists(userFile));

        // El fallo: el esquema exige al menos una sala, y borrar la ultima escribia un fichero vacio invalido.
        repository.deleteUserAdventure("FOSO DE COMBATE", "unica");

        assertFalse(Files.exists(userFile));
        assertTrue(repository.loadAdventuresForObjectiveRoom(fightingPit).stream().noneMatch(a -> "unica".equals(a.id())));
        if (Files.exists(userTranslations)) {
            assertFalse(Files.readString(userTranslations).contains(".unica."));
        }
        repository.saveUserAdventure(new ObjectiveRoomAdventure("FOSO DE COMBATE", "otra", "Otra", "Sabor", "Reglas", false));
        assertEquals("Otra", find(repository, fightingPit, "otra").name());
    }

    @Test
    void aUserAdventureOverridesTheBaseAdventureWithTheSameId() throws Exception {
        Path runtimeHome = copySharedContent();
        XmlObjectiveRoomAdventureRepository repository = new XmlObjectiveRoomAdventureRepository(runtimeHome);
        I18n.setLanguage(Language.EN);
        DungeonCard fightingPit = sharedCard(FIGHTING_PIT);
        ObjectiveRoomAdventure base = repository.loadAdventuresForObjectiveRoom(fightingPit).stream()
                .filter(adventure -> !adventure.generic())
                .findFirst()
                .orElseThrow();

        // La version del usuario cambia un campo que no pasa por traducciones: generic.
        repository.saveUserAdventure(new ObjectiveRoomAdventure(
                "FIGHTING PIT", base.id(), base.name(), "Sabor", "Reglas", true));

        // El fallo: el fichero base se procesaba despues y tapaba a la aventura del usuario.
        List<ObjectiveRoomAdventure> withSameId = repository.loadAdventuresForObjectiveRoom(fightingPit).stream()
                .filter(adventure -> base.id().equals(adventure.id()))
                .toList();
        assertEquals(1, withSameId.size());
        assertTrue(withSameId.get(0).generic());
    }

    private static ObjectiveRoomAdventure find(XmlObjectiveRoomAdventureRepository repository, DungeonCard room, String id)
            throws Exception {
        return repository.loadAdventuresForObjectiveRoom(room).stream()
                .filter(adventure -> id.equals(adventure.id()))
                .findFirst()
                .orElseThrow();
    }

    private static DungeonCard sharedCard(long id) throws Exception {
        return new XmlDungeonCardStore(Path.of("")).loadCards().stream()
                .filter(card -> card.getId() == id)
                .findFirst()
                .orElseThrow();
    }

    // Copia del contenido base necesario: guardar una aventura escribe traducciones en content-*.xml
    // y los tests no deben tocar el contenido real. Los esquemas van a shared/ y el contenido a
    // content/, junto a el, como en una instalacion.
    private Path copySharedContent() throws Exception {
        Path sharedSource = AppPaths.sharedHome(Path.of(""));
        Path contentSource = AppPaths.contentHome(Path.of(""));
        Path sharedCopy = tempDir.resolve("shared");
        Path contentCopy = tempDir.resolve("content");
        for (String relative : List.of(
                "data/xml/adventures/whq-adventures-schema.xsd",
                "data/xml/dungeon/whq-dungeon-cards-schema.xsd")) {
            Files.createDirectories(sharedCopy.resolve(relative).getParent());
            Files.copy(sharedSource.resolve(relative), sharedCopy.resolve(relative));
        }
        for (String relative : List.of(
                "data/xml/adventures/original-objective-room-adventures.xml",
                "data/xml/dungeon/dungeon-cards.xml",
                "data/i18n/content-es.xml",
                "data/i18n/content-en.xml")) {
            Files.createDirectories(contentCopy.resolve(relative).getParent());
            Files.copy(contentSource.resolve(relative), contentCopy.resolve(relative));
        }
        Path runtimeHome = tempDir.resolve("user-home");
        Files.createDirectories(runtimeHome.resolve("data/xml/adventures"));
        AppPaths.bindSharedHome(runtimeHome, sharedCopy);
        return runtimeHome;
    }
}
