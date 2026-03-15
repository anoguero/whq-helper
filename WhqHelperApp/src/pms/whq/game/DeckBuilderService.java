package pms.whq.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import pms.whq.content.ContentRepository;
import pms.whq.data.Deck;
import pms.whq.data.EventEntry;
import pms.whq.data.EventList;
import pms.whq.data.Table;
import pms.whq.data.TableKind;

public class DeckBuilderService {

  public DeckBuildResult build(ContentRepository repository, boolean asDeck) {
    EventList eventList = asDeck ? new Deck() : createTable(TableKind.DUNGEON);
    EventList travelEventList = asDeck ? new Deck() : createTable(TableKind.TRAVEL);
    EventList settlementEventList = asDeck ? new Deck() : createTable(TableKind.SETTLEMENT);
    EventList treasureEventList = asDeck ? new Deck() : createTable(TableKind.TREASURE);
    EventList objectiveTreasureEventList = asDeck ? new Deck() : createTable(TableKind.TREASURE);

    for (Table table : repository.tables().values()) {
      if (!table.isActive()) {
        continue;
      }

      if (table.getTableKind() == TableKind.TRAVEL) {
        travelEventList.addEntries(table.getEntries());
      } else if (table.getTableKind() == TableKind.SETTLEMENT) {
        settlementEventList.addEntries(table.getEntries());
      } else if (table.getTableKind() == TableKind.TREASURE) {
        treasureEventList.addEntries(filterTreasureEntries(table.getEntries(), true));
        objectiveTreasureEventList.addEntries(filterTreasureEntries(table.getEntries(), false));
      } else {
        eventList.addEntries(table.getEntries());
      }
    }

    addAlwaysAvailableTreasureEntries(repository, treasureEventList);

    if (asDeck) {
      ((Deck) eventList).shuffle();
      ((Deck) travelEventList).shuffle();
      ((Deck) settlementEventList).shuffle();
      ((Deck) treasureEventList).shuffle();
      ((Deck) objectiveTreasureEventList).shuffle();
    }

    return new DeckBuildResult(
        eventList,
        travelEventList,
        settlementEventList,
        treasureEventList,
        objectiveTreasureEventList);
  }

  private static Table createTable(TableKind kind) {
    Table table = new Table();
    table.setTableKind(kind);
    return table;
  }

  private static List<Object> filterTreasureEntries(List<Object> entries, boolean dungeonTreasure) {
    List<Object> filtered = new ArrayList<>();
    for (Object entry : entries) {
      if (!(entry instanceof EventEntry eventEntry)) {
        continue;
      }

      boolean isObjective = normalizeEntryId(eventEntry).contains("-objective-");
      if (dungeonTreasure && !isObjective) {
        filtered.add(eventEntry);
      } else if (!dungeonTreasure && isObjective) {
        filtered.add(eventEntry);
      }
    }
    return filtered;
  }

  private static String normalizeEntryId(EventEntry entry) {
    return entry == null || entry.id == null ? "" : entry.id.trim().toLowerCase(Locale.ROOT);
  }

  private static void addAlwaysAvailableTreasureEntries(ContentRepository repository, EventList treasureEventList) {
    if (repository.containsDungeonEvent(TreasureDrawService.DUNGEON_GOLD_TREASURE_ID)
        && !hasActiveDungeonGoldTreasureEntry(repository)) {
      treasureEventList.addEntry(new EventEntry(TreasureDrawService.DUNGEON_GOLD_TREASURE_ID));
    }
  }

  private static boolean hasActiveDungeonGoldTreasureEntry(ContentRepository repository) {
    for (Table table : repository.tables().values()) {
      if (!table.isActive() || table.getTableKind() != TableKind.TREASURE) {
        continue;
      }

      for (Object entry : table.getEntries()) {
        if (entry instanceof EventEntry eventEntry
            && TreasureDrawService.DUNGEON_GOLD_TREASURE_ID.equalsIgnoreCase(eventEntry.id)) {
          return true;
        }
      }
    }
    return false;
  }
}
