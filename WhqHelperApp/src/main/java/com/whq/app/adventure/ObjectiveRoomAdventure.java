package com.whq.app.adventure;

/**
 * Aventura (misión) de una sala objetivo. {@code objectiveRoomCardId} identifica la sala por su id de
 * carta; 0 si se desconoce (fichero antiguo sin cardId cuyo nombre no casa con ninguna carta).
 */
public record ObjectiveRoomAdventure(
        String objectiveRoomName,
        String id,
        String name,
        String flavorText,
        String rulesText,
        boolean generic,
        long objectiveRoomCardId) {

    /** Sin id de carta: el repositorio lo resuelve por el nombre de la sala al guardar. */
    public ObjectiveRoomAdventure(
            String objectiveRoomName,
            String id,
            String name,
            String flavorText,
            String rulesText,
            boolean generic) {
        this(objectiveRoomName, id, name, flavorText, rulesText, generic, 0L);
    }
}
