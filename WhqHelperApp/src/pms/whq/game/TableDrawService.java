package pms.whq.game;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import pms.whq.Settings;
import pms.whq.data.DrawableEntry;
import pms.whq.data.EventEntry;
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

  public DrawableEntry drawEntry(Table table) {
    return drawEntry(table, null, new HashSet<>());
  }

  public DrawableEntry resolveEntry(DrawableEntry entry) {
    return resolveEntry(entry, new HashSet<>());
  }

  private DrawableEntry drawEntry(Table table, Integer forcedLevel, Set<String> visitedTables) {
    List<DrawableEntry> monsters = filterMonsterEntries(table.getMonsterEntries(), forcedLevel);
    List<DrawableEntry> events = table.getEventEntries();
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
      DrawableEntry drawn = drawEvent ? TreasureDrawService.randomEntry(events) : TreasureDrawService.randomEntry(monsters);
      return resolveEntry(drawn, visitedTables);
    }

    DrawableEntry drawn = hasMonsters ? TreasureDrawService.randomEntry(monsters) : TreasureDrawService.randomEntry(events);
    return resolveEntry(drawn, visitedTables);
  }

  // Filtra por ambientacion (siempre) y por nivel. Con forcedLevel != null (resolucion de tableRef)
  // se filtra al nivel indicado; si no, solo se filtra por nivel cuando hay una aventura activa.
  private List<DrawableEntry> filterMonsterEntries(List<DrawableEntry> monsters, Integer forcedLevel) {
    AdventureAmbience selectedAmbience =
        AdventureAmbience.fromStorageValue(Settings.getSetting(Settings.ADVENTURE_AMBIENCE));

    List<DrawableEntry> ambienceFiltered = monsters;
    if (!selectedAmbience.isGeneric()) {
      ambienceFiltered =
          monsters.stream()
              .filter(entry -> matchesSelectedAmbience(entry, selectedAmbience))
              .collect(Collectors.toList());
      if (ambienceFiltered.isEmpty()) {
        ambienceFiltered = monsters;
      }
    }

    if (forcedLevel != null) {
      return filterByLevel(ambienceFiltered, forcedLevel);
    }

    boolean adventureActive = Settings.getSettingAsBool(Settings.ADVENTURE_ACTIVE);
    if (!adventureActive) {
      return ambienceFiltered;
    }
    int adventureLevel = normalizeAdventureLevel(Settings.getSettingAsInt(Settings.ADVENTURE_LEVEL));
    return filterByLevel(ambienceFiltered, adventureLevel);
  }

  private List<DrawableEntry> filterByLevel(List<DrawableEntry> monsters, int adventureLevel) {
    return monsters.stream()
        .filter(entry -> matchesAdventureLevel(entry, adventureLevel))
        .collect(Collectors.toList());
  }

  private boolean matchesSelectedAmbience(DrawableEntry entry, AdventureAmbience selectedAmbience) {
    return switch (entry) {
      case MonsterEntry monsterEntry -> selectedAmbience.matches(monsterEntry.ambiences);
      case TableReferenceEntry tableReferenceEntry ->
          tableReferenceEntry.ambiences.isEmpty() || selectedAmbience.matches(tableReferenceEntry.ambiences);
      case MonsterGroup group -> !group.isEmpty()
          && group.stream().allMatch(groupEntry -> matchesSelectedAmbience(groupEntry, selectedAmbience));
      case EventEntry eventEntry -> true;
      case null -> true;
    };
  }

  private boolean matchesAdventureLevel(DrawableEntry entry, int adventureLevel) {
    return switch (entry) {
      case MonsterEntry monsterEntry -> monsterEntry.level == adventureLevel;
      case TableReferenceEntry tableReferenceEntry -> tableReferenceEntry.level == adventureLevel;
      case MonsterGroup group -> !group.isEmpty()
          && group.get(0) instanceof MonsterEntry groupEntry
          && groupEntry.level == adventureLevel;
      case EventEntry eventEntry -> true;
      case null -> true;
    };
  }

  private int normalizeAdventureLevel(int level) {
    return Math.max(1, Math.min(10, level));
  }

  private DrawableEntry resolveEntry(DrawableEntry entry, Set<String> visitedTables) {
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
        DrawableEntry resolved = drawReferencedEntry(referencedTable, targetLevel, visitedTables);
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

  private DrawableEntry drawReferencedEntry(Table referencedTable, int forcedLevel, Set<String> visitedTables) {
    for (int attempt = 0; attempt < 32; attempt++) {
      DrawableEntry resolved = drawEntry(referencedTable, forcedLevel, visitedTables);
      if (resolved != null) {
        return resolved;
      }
    }
    return null;
  }

  private void appendResolvedEntry(MonsterGroup combined, DrawableEntry entry) {
    switch (entry) {
      case null -> {
        // nada que anyadir
      }
      case MonsterGroup group -> combined.addAll(group);
      default -> combined.add(entry);
    }
  }
}
