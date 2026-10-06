package pms.whq.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import pms.whq.Settings;
import pms.whq.data.EventEntry;
import pms.whq.data.MonsterEntry;
import pms.whq.data.MonsterGroup;
import pms.whq.data.Table;
import pms.whq.data.TableKind;
import pms.whq.data.TableReferenceEntry;

class TableDrawServiceTest {

    private final TableDrawService service = new TableDrawService();

    private String previousAdventureActive;
    private String previousAdventureLevel;
    private String previousAmbience;
    private String previousEventProbability;
    private String previousTreasureGoldProbability;

    @BeforeEach
    void snapshotSettingsAndRegistry() {
        previousAdventureActive = Settings.getSetting(Settings.ADVENTURE_ACTIVE);
        previousAdventureLevel = Settings.getSetting(Settings.ADVENTURE_LEVEL);
        previousAmbience = Settings.getSetting(Settings.ADVENTURE_AMBIENCE);
        previousEventProbability = Settings.getSetting(Settings.EVENT_PROBABILITY);
        previousTreasureGoldProbability = Settings.getSetting(Settings.TREASURE_GOLD_PROBABILITY);
        Settings.setSetting(Settings.ADVENTURE_AMBIENCE, "generic");
        Table.registerAll(Map.of());
    }

    @AfterEach
    void restoreSettingsAndRegistry() {
        Settings.setSetting(Settings.ADVENTURE_ACTIVE, previousAdventureActive);
        Settings.setSetting(Settings.ADVENTURE_LEVEL, previousAdventureLevel);
        Settings.setSetting(Settings.ADVENTURE_AMBIENCE, previousAmbience);
        Settings.setSetting(Settings.EVENT_PROBABILITY, previousEventProbability);
        Settings.setSetting(Settings.TREASURE_GOLD_PROBABILITY, previousTreasureGoldProbability);
        Table.registerAll(Map.of());
    }

    private MonsterEntry monster(String id, int level) {
        MonsterEntry entry = new MonsterEntry(id, 1, 1);
        entry.level = level;
        return entry;
    }

    @Test
    void emptyTableReturnsNull() {
        Table table = new Table();

        assertNull(service.drawEntry(table));
    }

    @Test
    void onlyMonstersAlwaysReturnsMonsterRegardlessOfEventProbability() {
        Table table = new Table();
        MonsterEntry goblin = monster("goblin", 1);
        table.addEntry(goblin);
        Settings.setSetting(Settings.EVENT_PROBABILITY, "100");
        Settings.setSetting(Settings.ADVENTURE_ACTIVE, "false");

        assertSame(goblin, service.drawEntry(table));
    }

    @Test
    void onlyEventsInNonTreasureTableReturnsEventDirectly() {
        Table table = new Table();
        EventEntry event = new EventEntry("dungeon-event");
        table.addEntry(event);
        Settings.setSetting(Settings.EVENT_PROBABILITY, "0");

        assertSame(event, service.drawEntry(table));
    }

    @Test
    void treasureTableWithOnlyEventsRoutesThroughTreasureDrawService() {
        Table table = new Table();
        table.setTableKind(TableKind.TREASURE);
        EventEntry gold = new EventEntry(TreasureDrawService.DUNGEON_GOLD_TREASURE_ID);
        EventEntry regular = new EventEntry("regular-treasure");
        table.addEntry(gold);
        table.addEntry(regular);
        Settings.setSetting(Settings.TREASURE_GOLD_PROBABILITY, "100");

        Object drawn = service.drawEntry(table);

        assertSame(gold, drawn);
    }

    @Test
    void eventProbabilityHundredPicksEventWhenBothPresent() {
        Table table = new Table();
        MonsterEntry goblin = monster("goblin", 1);
        EventEntry event = new EventEntry("dungeon-event");
        table.addEntry(goblin);
        table.addEntry(event);
        Settings.setSetting(Settings.EVENT_PROBABILITY, "100");
        Settings.setSetting(Settings.ADVENTURE_ACTIVE, "false");

        assertSame(event, service.drawEntry(table));
    }

    @Test
    void eventProbabilityZeroPicksMonsterWhenBothPresent() {
        Table table = new Table();
        MonsterEntry goblin = monster("goblin", 1);
        EventEntry event = new EventEntry("dungeon-event");
        table.addEntry(goblin);
        table.addEntry(event);
        Settings.setSetting(Settings.EVENT_PROBABILITY, "0");
        Settings.setSetting(Settings.ADVENTURE_ACTIVE, "false");

        assertSame(goblin, service.drawEntry(table));
    }

    @Test
    void filtersByLevelWhenAdventureIsActive() {
        Table table = new Table();
        MonsterEntry low = monster("low", 1);
        MonsterEntry high = monster("high", 5);
        table.addEntry(low);
        table.addEntry(high);
        Settings.setSetting(Settings.ADVENTURE_ACTIVE, "true");
        Settings.setSetting(Settings.ADVENTURE_LEVEL, "5");

        assertSame(high, service.drawEntry(table));
    }

    @Test
    void doesNotFilterByLevelWhenAdventureIsInactive() {
        Table table = new Table();
        MonsterEntry lonelyEntry = monster("lonely", 3);
        table.addEntry(lonelyEntry);
        Settings.setSetting(Settings.ADVENTURE_ACTIVE, "false");
        Settings.setSetting(Settings.ADVENTURE_LEVEL, "7");

        // If level filtering wrongly applied while inactive, this entry (level 3) would be
        // filtered out against adventure level 7, leaving no monsters and no events -> null.
        assertSame(lonelyEntry, service.drawEntry(table));
    }

    @Test
    void filtersByAmbienceWhenNotGeneric() {
        Table table = new Table();
        MonsterEntry chaosMonster = monster("chaos-warrior", 1);
        chaosMonster.ambiences.add("chaos");
        MonsterEntry undeadMonster = monster("skeleton", 1);
        undeadMonster.ambiences.add("undead");
        table.addEntry(chaosMonster);
        table.addEntry(undeadMonster);
        Settings.setSetting(Settings.ADVENTURE_AMBIENCE, "undead");
        Settings.setSetting(Settings.ADVENTURE_ACTIVE, "false");

        assertSame(undeadMonster, service.drawEntry(table));
    }

    @Test
    void fallsBackToFullListWhenAmbienceFilterWouldEmptyIt() {
        Table table = new Table();
        MonsterEntry chaosMonster = monster("chaos-warrior", 1);
        chaosMonster.ambiences.add("chaos");
        table.addEntry(chaosMonster);
        Settings.setSetting(Settings.ADVENTURE_AMBIENCE, "undead");
        Settings.setSetting(Settings.ADVENTURE_ACTIVE, "false");

        // No entry matches "undead", so the ambience filter would empty the list -> fallback
        // to the unfiltered list, and the chaos monster is still drawn.
        assertSame(chaosMonster, service.drawEntry(table));
    }

    @Test
    void resolveEntryReturnsNonReferenceEntryUnchanged() {
        MonsterEntry goblin = monster("goblin", 1);

        assertSame(goblin, service.resolveEntry(goblin));
    }

    @Test
    void resolveEntryReturnsNullWhenReferencedTableIsNotRegistered() {
        TableReferenceEntry reference = new TableReferenceEntry();
        reference.tableName = "unregistered";

        assertNull(service.resolveEntry(reference));
    }

    @Test
    void resolveEntryExpandsTimesIntoAMonsterGroup() {
        Table referenced = new Table();
        referenced.setTableKind(TableKind.DUNGEON);
        MonsterEntry orc = monster("orc", 5);
        referenced.addEntry(orc);
        Table.registerAll(Map.of("referenced", referenced));

        TableReferenceEntry reference = new TableReferenceEntry();
        reference.tableName = "referenced";
        reference.level = 5;
        reference.targetLevel = 5;
        reference.times = 3;

        Object resolved = service.resolveEntry(reference);

        MonsterGroup group = assertInstanceOf(MonsterGroup.class, resolved);
        assertEquals(3, group.size());
        assertTrue(group.stream().allMatch(entry -> entry == orc));
    }

    @Test
    void resolveEntryWithSingleTimeReturnsTheRawEntryUnwrapped() {
        Table referenced = new Table();
        referenced.setTableKind(TableKind.DUNGEON);
        MonsterEntry orc = monster("orc", 5);
        referenced.addEntry(orc);
        Table.registerAll(Map.of("referenced", referenced));

        TableReferenceEntry reference = new TableReferenceEntry();
        reference.tableName = "referenced";
        reference.level = 5;
        reference.targetLevel = 5;
        reference.times = 1;

        Object resolved = service.resolveEntry(reference);

        assertSame(orc, resolved);
    }

    @Test
    void resolveEntryAppliesTargetLevelRatherThanLevel() {
        Table referenced = new Table();
        referenced.setTableKind(TableKind.DUNGEON);
        MonsterEntry wrongLevel = monster("wrong", 3);
        MonsterEntry rightLevel = monster("right", 7);
        referenced.addEntry(wrongLevel);
        referenced.addEntry(rightLevel);
        Table.registerAll(Map.of("referenced", referenced));

        TableReferenceEntry reference = new TableReferenceEntry();
        reference.tableName = "referenced";
        reference.level = 3;
        reference.targetLevel = 7;
        reference.times = 1;

        Object resolved = service.resolveEntry(reference);

        assertSame(rightLevel, resolved);
    }

    @Test
    void resolveEntryFindsTheReferencedTableByStableIdAfterARename() throws Exception {
        // El fallo: se buscaba solo por nombre visible y, al renombrar la tabla, la referencia no la encontraba.
        Table renamed = tableFromXml("<table id=\"minions\" name=\"Minions (renamed)\" kind=\"dungeon\"/>");
        MonsterEntry rat = monster("rat", 1);
        renamed.addEntry(rat);
        Table.registerAll(Map.of(renamed.getName(), renamed));

        TableReferenceEntry reference = new TableReferenceEntry();
        reference.tableName = "minions";
        reference.targetLevel = 1;

        assertSame(rat, service.resolveEntry(reference));
    }

    @Test
    void resolveEntryStillFindsTheReferencedTableByVisibleName() throws Exception {
        Table legacy = tableFromXml("<table id=\"minions\" name=\"Minions\" kind=\"dungeon\"/>");
        MonsterEntry rat = monster("rat", 1);
        legacy.addEntry(rat);
        Table.registerAll(Map.of(legacy.getName(), legacy));

        TableReferenceEntry reference = new TableReferenceEntry();
        reference.tableName = "Minions";
        reference.targetLevel = 1;

        assertSame(rat, service.resolveEntry(reference));
    }

    private static Table tableFromXml(String xml) throws Exception {
        return new Table(
            DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)))
                .getDocumentElement());
    }

    @Test
    void resolveEntryOfSelfReferencingTableReturnsNullInsteadOfHanging() {
        Table selfReferencing = new Table();
        selfReferencing.setTableKind(TableKind.DUNGEON);
        TableReferenceEntry selfReference = new TableReferenceEntry();
        selfReference.tableName = "self";
        selfReference.times = 1;
        selfReferencing.addEntry(selfReference);
        Table.registerAll(Map.of("self", selfReferencing));

        TableReferenceEntry entryPoint = new TableReferenceEntry();
        entryPoint.tableName = "self";
        entryPoint.times = 1;

        assertNull(service.resolveEntry(entryPoint));
    }

    @Test
    void resolveEntryOfIndirectCycleReturnsNullInsteadOfHanging() {
        Table tableA = new Table();
        tableA.setTableKind(TableKind.DUNGEON);
        TableReferenceEntry refToB = new TableReferenceEntry();
        refToB.tableName = "B";
        refToB.times = 1;
        tableA.addEntry(refToB);

        Table tableB = new Table();
        tableB.setTableKind(TableKind.DUNGEON);
        TableReferenceEntry refToA = new TableReferenceEntry();
        refToA.tableName = "A";
        refToA.times = 1;
        tableB.addEntry(refToA);

        Table.registerAll(Map.of("A", tableA, "B", tableB));

        TableReferenceEntry entryPoint = new TableReferenceEntry();
        entryPoint.tableName = "A";
        entryPoint.times = 1;

        assertNull(service.resolveEntry(entryPoint));
    }
}
