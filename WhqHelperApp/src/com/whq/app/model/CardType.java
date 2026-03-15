package com.whq.app.model;

public enum CardType {
    DUNGEON_ROOM("DUNGEON ROOM", 30, 102, 182),
    OBJECTIVE_ROOM("OBJECTIVE ROOM", 239, 68, 30),
    CORRIDOR("CORRIDOR", 71, 190, 122),
    SPECIAL("SPECIAL", 187, 127, 255);

    private final String label;
    private final int accentRed;
    private final int accentGreen;
    private final int accentBlue;

    CardType(String label, int accentRed, int accentGreen, int accentBlue) {
        this.label = label;
        this.accentRed = accentRed;
        this.accentGreen = accentGreen;
        this.accentBlue = accentBlue;
    }

    public String getLabel() {
        return label;
    }

    public int getAccentRed() {
        return accentRed;
    }

    public int getAccentGreen() {
        return accentGreen;
    }

    public int getAccentBlue() {
        return accentBlue;
    }
}
