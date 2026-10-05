package com.whq.app.adventure;

import java.util.List;

import com.whq.app.model.DungeonCard;

public interface ObjectiveRoomAdventureRepository {
    List<ObjectiveRoomAdventure> loadAdventuresForObjectiveRoom(DungeonCard objectiveRoom) throws ObjectiveRoomAdventureRepositoryException;
}
