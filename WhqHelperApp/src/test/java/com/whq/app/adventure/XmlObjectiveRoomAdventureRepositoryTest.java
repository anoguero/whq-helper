package com.whq.app.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class XmlObjectiveRoomAdventureRepositoryTest {

    @Test
    void loadsObjectiveRoomAdventuresIncludingGenericMission() throws Exception {
        XmlObjectiveRoomAdventureRepository repository = new XmlObjectiveRoomAdventureRepository(
                Path.of(System.getProperty("user.dir")));

        List<ObjectiveRoomAdventure> adventures = repository.loadAdventuresForObjectiveRoom("FIGHTING PIT");

        assertEquals(7, adventures.size());
        assertTrue(adventures.stream().anyMatch(ObjectiveRoomAdventure::generic));
        assertTrue(adventures.stream().anyMatch(adventure -> "Free the Prisoners".equals(adventure.name())));
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
