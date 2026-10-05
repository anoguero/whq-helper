package com.whq.app.model;

public class DungeonCard {
    private final long id;
    private final String name;
    private final CardType type;
    private final String environment;
    private final int copyCount;
    private final boolean enabled;
    private final String descriptionText;
    private final String rulesText;
    private final String tileImagePath;

    public DungeonCard(
            long id,
            String name,
            CardType type,
            String environment,
            int copyCount,
            boolean enabled,
            String descriptionText,
            String rulesText,
            String tileImagePath) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.environment = environment;
        this.copyCount = copyCount;
        this.enabled = enabled;
        this.descriptionText = descriptionText;
        this.rulesText = rulesText;
        this.tileImagePath = tileImagePath;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public CardType getType() {
        return type;
    }

    public String getEnvironment() {
        return environment;
    }

    public int getCopyCount() {
        return copyCount;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDescriptionText() {
        return descriptionText;
    }

    public String getRulesText() {
        return rulesText;
    }

    public String getTileImagePath() {
        return tileImagePath;
    }

    @Override
    public String toString() {
        return name + " (" + type.getLabel() + " - " + environment + ", copias: " + copyCount
                + (enabled ? ", habilitada" : ", deshabilitada") + ")";
    }
}
