package com.whq.app.adventure;

public record ObjectiveRoomAdventure(
        String objectiveRoomName,
        String id,
        String name,
        String flavorText,
        String rulesText,
        boolean generic) {}
