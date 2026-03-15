package pms.whq.game;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import pms.whq.Settings;
import pms.whq.data.MonsterEntry;
import pms.whq.data.Table;
import pms.whq.data.TableKind;
import pms.whq.state.AdventureAmbience;

public class TableDrawService {

  private final TreasureDrawService treasureDrawService;

  public TableDrawService() {
    this(new TreasureDrawService());
  }

  public TableDrawService(TreasureDrawService treasureDrawService) {
    this.treasureDrawService = treasureDrawService;
  }

  public Object drawEntry(Table table) {
    List<Object> monsters = filterMonsterEntries(table.getMonsterEntries());
    List<Object> events = table.getEventEntries();
    boolean hasMonsters = !monsters.isEmpty();
    boolean hasEvents = !events.isEmpty();
    if (!hasMonsters && !hasEvents) {
      return null;
    }

    if (table.getTableKind() == TableKind.TREASURE && hasEvents && !hasMonsters) {
      return treasureDrawService.drawTreasureEntry(events);
    }

    if (hasMonsters && hasEvents) {
      int eventProbability = Settings.getSettingAsInt(Settings.EVENT_PROBABILITY);
      boolean drawEvent = ThreadLocalRandom.current().nextInt(100) < eventProbability;
      return drawEvent ? TreasureDrawService.randomEntry(events) : TreasureDrawService.randomEntry(monsters);
    }

    return hasMonsters ? TreasureDrawService.randomEntry(monsters) : TreasureDrawService.randomEntry(events);
  }

  private List<Object> filterMonsterEntries(List<Object> monsters) {
    boolean adventureActive = Settings.getSettingAsBool(Settings.ADVENTURE_ACTIVE);
    int adventureLevel = normalizeAdventureLevel(Settings.getSettingAsInt(Settings.ADVENTURE_LEVEL));

    AdventureAmbience selectedAmbience =
        AdventureAmbience.fromStorageValue(Settings.getSetting(Settings.ADVENTURE_AMBIENCE));

    List<Object> ambienceFiltered = monsters;
    if (!selectedAmbience.isGeneric()) {
      ambienceFiltered =
          monsters.stream()
              .filter(entry -> matchesSelectedAmbience(entry, selectedAmbience))
              .collect(Collectors.toList());
      if (ambienceFiltered.isEmpty()) {
        ambienceFiltered = monsters;
      }
    }

    if (!adventureActive) {
      return ambienceFiltered;
    }

    return ambienceFiltered.stream()
        .filter(entry -> matchesAdventureLevel(entry, adventureLevel))
        .collect(Collectors.toList());
  }

  private boolean matchesSelectedAmbience(Object entry, AdventureAmbience selectedAmbience) {
    if (entry instanceof MonsterEntry monsterEntry) {
      return selectedAmbience.matches(monsterEntry.ambiences);
    }
    if (entry instanceof List<?> group) {
      if (group.isEmpty()) {
        return false;
      }
      for (Object groupEntry : group) {
        if (!matchesSelectedAmbience(groupEntry, selectedAmbience)) {
          return false;
        }
      }
      return true;
    }
    return true;
  }

  private boolean matchesAdventureLevel(Object entry, int adventureLevel) {
    if (entry instanceof MonsterEntry monsterEntry) {
      return monsterEntry.level == adventureLevel;
    }
    if (entry instanceof List<?> group) {
      if (group.isEmpty()) {
        return false;
      }
      Object first = group.get(0);
      if (first instanceof MonsterEntry groupEntry) {
        return groupEntry.level == adventureLevel;
      }
      return false;
    }
    return true;
  }

  private int normalizeAdventureLevel(int level) {
    return Math.max(1, Math.min(10, level));
  }
}
