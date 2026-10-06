package com.whq.app.model;

public enum CardType {
    DUNGEON_ROOM("DUNGEON ROOM"),
    OBJECTIVE_ROOM("OBJECTIVE ROOM"),
    CORRIDOR("CORRIDOR"),
    SPECIAL("SPECIAL");

    private final String label;

    CardType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
