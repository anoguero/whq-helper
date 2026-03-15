package pms.whq.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import pms.whq.content.ContentRepository;
import pms.whq.data.EventEntry;
import pms.whq.data.Table;
import pms.whq.data.TableKind;

class DeckBuilderServiceTest {

  @Test
  void addsAlwaysAvailableDungeonGoldWhenNoActiveTreasureTableContainsIt() {
    ContentRepository repository = new ContentRepository();
    repository.events().put(TreasureDrawService.DUNGEON_GOLD_TREASURE_ID, new pms.whq.data.Event());

    Table treasureTable = new Table();
    treasureTable.setTableKind(TableKind.TREASURE);
    treasureTable.setActive(true);
    treasureTable.addEntry(new EventEntry("regular-treasure"));
    repository.tables().put("treasure", treasureTable);

    DeckBuildResult result = new DeckBuilderService().build(repository, false);

    Table builtTreasureTable = assertInstanceOf(Table.class, result.treasureEventList());
    assertEquals(2, builtTreasureTable.getEventEntries().size());
  }

  @Test
  void separatesObjectiveTreasureFromDungeonTreasure() {
    ContentRepository repository = new ContentRepository();

    Table treasureTable = new Table();
    treasureTable.setTableKind(TableKind.TREASURE);
    treasureTable.setActive(true);
    treasureTable.addEntry(new EventEntry("dungeon-treasure"));
    treasureTable.addEntry(new EventEntry("rpb-treasure-objective-sword"));
    repository.tables().put("treasure", treasureTable);

    DeckBuildResult result = new DeckBuilderService().build(repository, false);

    Table dungeonTreasure = assertInstanceOf(Table.class, result.treasureEventList());
    Table objectiveTreasure = assertInstanceOf(Table.class, result.objectiveTreasureEventList());
    assertEquals(1, dungeonTreasure.getEventEntries().size());
    assertEquals("dungeon-treasure", ((EventEntry) dungeonTreasure.getEventEntries().get(0)).id);
    assertEquals(1, objectiveTreasure.getEventEntries().size());
    assertEquals("rpb-treasure-objective-sword", ((EventEntry) objectiveTreasure.getEventEntries().get(0)).id);
  }
}
