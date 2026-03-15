package pms.whq.data;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pms.whq.Settings;

class TableTest {

  @BeforeEach
  void setUp() {
    Settings.setSetting(Settings.EVENT_PROBABILITY, "37");
    Settings.setSetting(Settings.TREASURE_GOLD_PROBABILITY, "19");
    Settings.setSetting(Settings.ADVENTURE_AMBIENCE, "generic");
  }

  @Test
  void returnsGoldTreasureWhenGoldProbabilityIsHundred() {
    Table table = new Table();
    table.setTableKind(TableKind.TREASURE);
    table.addEntry(new EventEntry("rpb-treasure-dungeon-gold"));
    table.addEntry(new EventEntry("regular-treasure"));
    Settings.setSetting(Settings.TREASURE_GOLD_PROBABILITY, "100");

    EventEntry entry = assertInstanceOf(EventEntry.class, table.getEntry());

    org.junit.jupiter.api.Assertions.assertEquals("rpb-treasure-dungeon-gold", entry.id);
  }

  @Test
  void returnsRegularTreasureWhenGoldProbabilityIsZero() {
    Table table = new Table();
    table.setTableKind(TableKind.TREASURE);
    table.addEntry(new EventEntry("rpb-treasure-dungeon-gold"));
    table.addEntry(new EventEntry("regular-treasure"));
    Settings.setSetting(Settings.TREASURE_GOLD_PROBABILITY, "0");

    EventEntry entry = assertInstanceOf(EventEntry.class, table.getEntry());

    org.junit.jupiter.api.Assertions.assertEquals("regular-treasure", entry.id);
  }

  @Test
  void returnsEventWhenEventProbabilityIsHundred() {
    Table table = new Table();
    table.addEntry(new MonsterEntry("goblin", 1, 1));
    table.addEntry(new EventEntry("dungeon-event"));
    Settings.setSetting(Settings.EVENT_PROBABILITY, "100");

    Object entry = table.getEntry();

    assertInstanceOf(EventEntry.class, entry);
  }

  @Test
  void returnsMonsterWhenEventProbabilityIsZero() {
    Table table = new Table();
    table.addEntry(new MonsterEntry("goblin", 1, 1));
    table.addEntry(new EventEntry("dungeon-event"));
    Settings.setSetting(Settings.EVENT_PROBABILITY, "0");

    Object entry = table.getEntry();

    assertInstanceOf(MonsterEntry.class, entry);
  }

  @Test
  void filtersMonsterEntriesBySelectedAdventureAmbience() {
    Table table = new Table();
    MonsterEntry chaosMonster = new MonsterEntry("chaos-warrior", 1, 1);
    chaosMonster.ambiences.add("chaos");
    MonsterEntry undeadMonster = new MonsterEntry("skeleton", 1, 1);
    undeadMonster.ambiences.add("undead");
    table.addEntry(chaosMonster);
    table.addEntry(undeadMonster);
    Settings.setSetting(Settings.ADVENTURE_AMBIENCE, "undead");

    MonsterEntry entry = assertInstanceOf(MonsterEntry.class, table.getEntry());

    org.junit.jupiter.api.Assertions.assertEquals("skeleton", entry.id);
  }
}
