package com.whq.app.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.whq.app.i18n.I18n;
import com.whq.app.i18n.Language;

class XmlObjectiveRoomAdventureRepositoryTest {

    @Test
    void loadsObjectiveRoomAdventuresIncludingGenericMission() throws Exception {
        // Los nombres de aventura se traducen con el idioma activo de I18n; fijamos EN para que la
        // asercion sea determinista independientemente del idioma por defecto de la aplicacion.
        Language previousLanguage = I18n.getLanguage();
        try {
            I18n.setLanguage(Language.EN);
            XmlObjectiveRoomAdventureRepository repository = new XmlObjectiveRoomAdventureRepository(
                    Path.of(System.getProperty("user.dir")));

            List<ObjectiveRoomAdventure> adventures = repository.loadAdventuresForObjectiveRoom("FIGHTING PIT");

            assertEquals(7, adventures.size());
            assertTrue(adventures.stream().anyMatch(ObjectiveRoomAdventure::generic));
            assertTrue(adventures.stream().anyMatch(adventure -> "Free the Prisoners".equals(adventure.name())));
        } finally {
            I18n.setLanguage(previousLanguage);
        }
    }

    @Test
    void returnsGenericMissionWhenObjectiveRoomHasNoConfiguredAdventures() throws Exception {
        XmlObjectiveRoomAdventureRepository repository = new XmlObjectiveRoomAdventureRepository(
                Path.of(System.getProperty("user.dir")));

        List<ObjectiveRoomAdventure> adventures = repository.loadAdventuresForObjectiveRoom("WICKED WELL");

        assertEquals(1, adventures.size());
        assertTrue(adventures.get(0).generic());
        assertEquals("WICKED WELL", adventures.get(0).objectiveRoomName());
        assertFalse(adventures.get(0).rulesText().isBlank());
    }
}
