package pms.whq.data;

import java.util.Locale;

public enum TableKind {
  DUNGEON(""),
  TRAVEL("travel"),
  SETTLEMENT("settlement"),
  TREASURE("treasure");

  private final String storageValue;

  TableKind(String storageValue) {
    this.storageValue = storageValue;
  }

  public String storageValue() {
    return storageValue;
  }

  public static TableKind fromValue(String value) {
    String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "travel" -> TRAVEL;
      case "settlement" -> SETTLEMENT;
      case "treasure" -> TREASURE;
      default -> DUNGEON;
    };
  }
}
