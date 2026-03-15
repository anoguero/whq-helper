package pms.whq.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public enum AdventureAmbience {
  GENERIC("generic", "Generica"),
  CHAOS("chaos", "Chaos"),
  UNDEAD("undead", "Undead"),
  SKAVEN("skaven", "Skaven"),
  ORCS_GOBLINS("orcs-goblins", "Orcs & Goblins"),
  CHAOS_DWARVES("chaos-dwarves", "Chaos Dwarves"),
  DARK_ELVES("dark-elves", "Dark Elves");

  private final String storageValue;
  private final String displayName;

  AdventureAmbience(String storageValue, String displayName) {
    this.storageValue = storageValue;
    this.displayName = displayName;
  }

  public String storageValue() {
    return storageValue;
  }

  public String displayName() {
    return displayName;
  }

  public boolean isGeneric() {
    return this == GENERIC;
  }

  public boolean matches(List<String> entryAmbiences) {
    if (isGeneric()) {
      return true;
    }
    if (entryAmbiences == null || entryAmbiences.isEmpty()) {
      return true;
    }
    for (String ambience : entryAmbiences) {
      if (storageValue.equalsIgnoreCase(ambience)) {
        return true;
      }
    }
    return false;
  }

  public static AdventureAmbience fromStorageValue(String value) {
    if (value == null || value.isBlank()) {
      return GENERIC;
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    for (AdventureAmbience ambience : values()) {
      if (ambience.storageValue.equals(normalized)) {
        return ambience;
      }
    }
    return GENERIC;
  }

  public static AdventureAmbience fromDisplayName(String value) {
    if (value == null || value.isBlank()) {
      return GENERIC;
    }
    String normalized = value.trim();
    for (AdventureAmbience ambience : values()) {
      if (ambience.displayName.equalsIgnoreCase(normalized)) {
        return ambience;
      }
    }
    return GENERIC;
  }

  public static String[] displayNames() {
    List<String> names = new ArrayList<>();
    for (AdventureAmbience ambience : values()) {
      names.add(ambience.displayName);
    }
    return names.toArray(String[]::new);
  }
}
