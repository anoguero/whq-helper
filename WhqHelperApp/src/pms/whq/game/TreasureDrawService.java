package pms.whq.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import pms.whq.Settings;
import pms.whq.data.EventEntry;

public class TreasureDrawService {

  public static final String DUNGEON_GOLD_TREASURE_ID = "rpb-treasure-dungeon-gold";

  public Object drawTreasureEntry(List<Object> eventEntries) {
    List<Object> dungeonGoldEntries = new ArrayList<>();
    List<Object> regularEntries = new ArrayList<>();

    for (Object entry : eventEntries) {
      if (isDungeonGoldTreasureEntry(entry)) {
        dungeonGoldEntries.add(entry);
      } else {
        regularEntries.add(entry);
      }
    }

    if (dungeonGoldEntries.isEmpty() || regularEntries.isEmpty()) {
      return randomEntry(eventEntries);
    }

    int goldProbability = Math.max(0, Math.min(100, Settings.getSettingAsInt(Settings.TREASURE_GOLD_PROBABILITY)));
    boolean drawGold = ThreadLocalRandom.current().nextInt(100) < goldProbability;
    return drawGold ? randomEntry(dungeonGoldEntries) : randomEntry(regularEntries);
  }

  public boolean isDungeonGoldTreasureEntry(Object entry) {
    if (!(entry instanceof EventEntry eventEntry)) {
      return false;
    }

    String normalized = eventEntry.id == null ? "" : eventEntry.id.trim().toLowerCase(Locale.ROOT);
    return DUNGEON_GOLD_TREASURE_ID.equals(normalized);
  }

  public static Object randomEntry(List<Object> source) {
    if (source.isEmpty()) {
      return null;
    }
    int index = ThreadLocalRandom.current().nextInt(source.size());
    return source.get(index);
  }
}
