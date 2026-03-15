package com.whq.app.adventure;

import java.util.List;

public interface ObjectiveRoomAdventureRepository {
    List<ObjectiveRoomAdventure> loadAdventuresForObjectiveRoom(String objectiveRoomName) throws ObjectiveRoomAdventureRepositoryException;
}
