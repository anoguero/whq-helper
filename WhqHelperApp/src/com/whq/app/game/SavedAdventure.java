package com.whq.app.game;

import com.whq.app.model.DungeonCard;

/**
 * Partida de aventura persistible: contexto con el que se generó, sala objetivo, misión y estado de los
 * montones. Al cargar, {@code objectiveRoom} es null si la carta ya no existe en el catálogo.
 */
public record SavedAdventure(
        String environment,
        int adventureLevel,
        String ambience,
        int partySize,
        long objectiveRoomCardId,
        DungeonCard objectiveRoom,
        String missionId,
        AdventureSession session) {
}
