package pms.whq.game;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import pms.whq.Settings;
import pms.whq.data.MonsterGroup;
import pms.whq.data.MonsterEntry;
import pms.whq.data.Table;
import pms.whq.data.TableReferenceEntry;
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
    return drawEntry(table, null, new HashSet<>());
  }

  public Object resolveEntry(Object entry) {
    return resolveEntry(entry, new HashSet<>());
  }

  private Object drawEntry(Table table, Integer forcedLevel, Set<String> visitedTables) {
    List<Object> monsters = filterMonsterEntries(table.getMonsterEntries());
    if (forcedLevel != null) {
      monsters = filterMonsterEntries(table.getMonsterEntries(), forcedLevel);
    }
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
      Object drawn = drawEvent ? TreasureDrawService.randomEntry(events) : TreasureDrawService.randomEntry(monsters);
      return resolveEntry(drawn, visitedTables);
    }

    Object drawn = hasMonsters ? TreasureDrawService.randomEntry(monsters) : TreasureDrawService.randomEntry(events);
    return resolveEntry(drawn, visitedTables);
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

    return filterMonsterEntries(ambienceFiltered, adventureLevel);
  }

  private List<Object> filterMonsterEntries(List<Object> monsters, int adventureLevel) {
    return monsters.stream()
        .filter(entry -> matchesAdventureLevel(entry, adventureLevel))
        .collect(Collectors.toList());
  }

  private boolean matchesSelectedAmbience(Object entry, AdventureAmbience selectedAmbience) {
    if (entry instanceof MonsterEntry monsterEntry) {
      return selectedAmbience.matches(monsterEntry.ambiences);
    }
    if (entry instanceof TableReferenceEntry tableReferenceEntry) {
      return tableReferenceEntry.ambiences.isEmpty() || selectedAmbience.matches(tableReferenceEntry.ambiences);
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
    if (entry instanceof TableReferenceEntry tableReferenceEntry) {
      return tableReferenceEntry.level == adventureLevel;
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

  private Object resolveEntry(Object entry, Set<String> visitedTables) {
    if (!(entry instanceof TableReferenceEntry tableReferenceEntry)) {
      return entry;
    }

    Table referencedTable = Table.findRegistered(tableReferenceEntry.tableName);
    if (referencedTable == null || !visitedTables.add(tableReferenceEntry.tableName)) {
      return null;
    }

    try {
      MonsterGroup combined = new MonsterGroup();
      combined.level = Math.max(1, Math.min(10, tableReferenceEntry.level));

      int targetLevel = Math.max(1, Math.min(10, tableReferenceEntry.targetLevel));
      for (int i = 0; i < Math.max(1, tableReferenceEntry.times); i++) {
        Object resolved = drawReferencedEntry(referencedTable, targetLevel, visitedTables);
        appendResolvedEntry(combined, resolved);
      }

      if (combined.isEmpty()) {
        return null;
      }
      if (combined.size() == 1) {
        return combined.get(0);
      }
      return combined;
    } finally {
      visitedTables.remove(tableReferenceEntry.tableName);
    }
  }

  private Object drawReferencedEntry(Table referencedTable, int forcedLevel, Set<String> visitedTables) {
    for (int attempt = 0; attempt < 32; attempt++) {
      Object resolved = drawEntry(referencedTable, forcedLevel, visitedTables);
      if (resolved != null) {
        return resolved;
      }
    }
    return null;
  }

  private void appendResolvedEntry(MonsterGroup combined, Object entry) {
    if (entry == null) {
      return;
    }
    if (entry instanceof MonsterGroup group) {
      combined.addAll(group);
      return;
    }
    if (entry instanceof List<?> list) {
      combined.addAll(list);
      return;
    }
    combined.add(entry);
  }
}
