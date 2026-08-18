package com.whq.app.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pms.whq.Settings;
import pms.whq.content.ContentRepository;
import pms.whq.data.MonsterEntry;
import pms.whq.data.Table;
import pms.whq.data.TableKind;

class ObjectiveRoomGeneratorTest {

    private String previousEasy;
    private String previousNormal;
    private String previousHard;
    private String previousVeryHard;
    private String previousExtreme;
    private String previousAmbience;

    @BeforeEach
    void snapshotSettings() {
        previousEasy = Settings.getSetting(Settings.OBJECTIVE_MONSTER_EASY_WEIGHT);
        previousNormal = Settings.getSetting(Settings.OBJECTIVE_MONSTER_NORMAL_WEIGHT);
        previousHard = Settings.getSetting(Settings.OBJECTIVE_MONSTER_HARD_WEIGHT);
        previousVeryHard = Settings.getSetting(Settings.OBJECTIVE_MONSTER_VERY_HARD_WEIGHT);
        previousExtreme = Settings.getSetting(Settings.OBJECTIVE_MONSTER_EXTREME_WEIGHT);
        previousAmbience = Settings.getSetting(Settings.ADVENTURE_AMBIENCE);
        Settings.setSetting(Settings.ADVENTURE_AMBIENCE, "generic");
    }

    @AfterEach
    void restoreSettings() {
        Settings.setSetting(Settings.OBJECTIVE_MONSTER_EASY_WEIGHT, previousEasy);
        Settings.setSetting(Settings.OBJECTIVE_MONSTER_NORMAL_WEIGHT, previousNormal);
        Settings.setSetting(Settings.OBJECTIVE_MONSTER_HARD_WEIGHT, previousHard);
        Settings.setSetting(Settings.OBJECTIVE_MONSTER_VERY_HARD_WEIGHT, previousVeryHard);
        Settings.setSetting(Settings.OBJECTIVE_MONSTER_EXTREME_WEIGHT, previousExtreme);
        Settings.setSetting(Settings.ADVENTURE_AMBIENCE, previousAmbience);
    }

    private void setWeights(int easy, int normal, int hard, int veryHard, int extreme) {
        Settings.setSetting(Settings.OBJECTIVE_MONSTER_EASY_WEIGHT, Integer.toString(easy));
        Settings.setSetting(Settings.OBJECTIVE_MONSTER_NORMAL_WEIGHT, Integer.toString(normal));
        Settings.setSetting(Settings.OBJECTIVE_MONSTER_HARD_WEIGHT, Integer.toString(hard));
        Settings.setSetting(Settings.OBJECTIVE_MONSTER_VERY_HARD_WEIGHT, Integer.toString(veryHard));
        Settings.setSetting(Settings.OBJECTIVE_MONSTER_EXTREME_WEIGHT, Integer.toString(extreme));
    }

    private ContentRepository repositoryWithMonsterAtLevel(int level, String id) {
        ContentRepository repository = new ContentRepository();
        Table table = new Table();
        table.setTableKind(TableKind.DUNGEON);
        table.setActive(true);
        MonsterEntry entry = new MonsterEntry(id, 1, 1);
        entry.level = level;
        table.addEntry(entry);
        repository.tables().put("dungeon-" + level, table);
        return repository;
    }

    @Test
    void weightedDifficultyRollOnlyPicksDifficultiesWithPositiveWeight() {
        setWeights(0, 5, 0, 0, 0);
        ObjectiveRoomGenerator generator = new ObjectiveRoomGenerator(new Random(1));

        for (int i = 0; i < 20; i++) {
            ObjectiveRoomGenerator.ObjectiveMonsterDifficulty difficulty = generator.rollObjectiveMonsterDifficulty();
            assertEquals("difficulty.normal", difficulty.labelKey());
        }
    }

    @Test
    void zeroTotalWeightMeansNoDifficultyCanBeRolled() {
        setWeights(0, 0, 0, 0, 0);
        ObjectiveRoomGenerator generator = new ObjectiveRoomGenerator(new Random(1));

        assertNull(generator.rollObjectiveMonsterDifficulty());
    }

    @Test
    void zeroTotalWeightMakesGenerateReturnNull() {
        setWeights(0, 0, 0, 0, 0);
        ObjectiveRoomGenerator generator = new ObjectiveRoomGenerator(new Random(1));
        ContentRepository repository = repositoryWithMonsterAtLevel(4, "goblin");

        assertNull(generator.generateObjectiveRoomMonsterEntries(repository, 4));
    }

    @Test
    void sameSeedProducesReproducibleEncounter() {
        setWeights(1, 0, 0, 0, 0);
        ContentRepository repositoryA = repositoryWithMonsterAtLevel(4, "goblin");
        ContentRepository repositoryB = repositoryWithMonsterAtLevel(4, "goblin");

        ObjectiveRoomGenerator generatorA = new ObjectiveRoomGenerator(new Random(42));
        ObjectiveRoomGenerator generatorB = new ObjectiveRoomGenerator(new Random(42));

        ObjectiveRoomGenerator.ObjectiveRoomEncounter encounterA =
                generatorA.generateObjectiveRoomMonsterEntries(repositoryA, 4);
        ObjectiveRoomGenerator.ObjectiveRoomEncounter encounterB =
                generatorB.generateObjectiveRoomMonsterEntries(repositoryB, 4);

        assertEquals(encounterA.difficulty(), encounterB.difficulty());
        assertEquals(encounterA.entries().size(), encounterB.entries().size());
        for (int i = 0; i < encounterA.entries().size(); i++) {
            MonsterEntry entryA = (MonsterEntry) encounterA.entries().get(i);
            MonsterEntry entryB = (MonsterEntry) encounterB.entries().get(i);
            assertEquals(entryA.id, entryB.id);
        }
    }

    @Test
    void easyDifficultyGeneratesTwoEntriesAtTheSameLevel() {
        setWeights(1, 0, 0, 0, 0);
        ContentRepository repository = repositoryWithMonsterAtLevel(4, "goblin");
        ObjectiveRoomGenerator generator = new ObjectiveRoomGenerator(new Random(1));

        ObjectiveRoomGenerator.ObjectiveRoomEncounter encounter =
                generator.generateObjectiveRoomMonsterEntries(repository, 4);

        assertEquals("difficulty.easy", encounter.difficulty().labelKey());
        assertEquals(2, encounter.entries().size());
        for (Object entry : encounter.entries()) {
            assertEquals("goblin", ((MonsterEntry) entry).id);
        }
    }

    @Test
    void resolveObjectiveEncounterLevelsReturnsRequestedLevelWhenAvailable() {
        ContentRepository repository = repositoryWithMonsterAtLevel(4, "goblin");
        ObjectiveRoomGenerator generator = new ObjectiveRoomGenerator(new Random(1));

        assertEquals(List.of(4), generator.resolveObjectiveEncounterLevels(repository, 4));
    }

    @Test
    void resolveObjectiveEncounterLevelsFallsBackToClosestLowerLevel() {
        ContentRepository repository = new ContentRepository();
        Table table = new Table();
        table.setTableKind(TableKind.DUNGEON);
        table.setActive(true);
        MonsterEntry low = new MonsterEntry("low", 1, 1);
        low.level = 2;
        MonsterEntry high = new MonsterEntry("high", 1, 1);
        high.level = 4;
        table.addEntry(low);
        table.addEntry(high);
        repository.tables().put("dungeon", table);
        ObjectiveRoomGenerator generator = new ObjectiveRoomGenerator(new Random(1));

        assertEquals(List.of(2, 2), generator.resolveObjectiveEncounterLevels(repository, 3));
    }

    @Test
    void resolveObjectiveEncounterLevelsFallsBackToLowestWhenNoneBelowRequested() {
        ContentRepository repository = new ContentRepository();
        Table table = new Table();
        table.setTableKind(TableKind.DUNGEON);
        table.setActive(true);
        MonsterEntry entryA = new MonsterEntry("a", 1, 1);
        entryA.level = 5;
        MonsterEntry entryB = new MonsterEntry("b", 1, 1);
        entryB.level = 7;
        table.addEntry(entryA);
        table.addEntry(entryB);
        repository.tables().put("dungeon", table);
        ObjectiveRoomGenerator generator = new ObjectiveRoomGenerator(new Random(1));

        assertEquals(List.of(5, 5), generator.resolveObjectiveEncounterLevels(repository, 2));
    }

    @Test
    void resolveObjectiveEncounterLevelsReturnsEmptyWhenNoLevelsAvailable() {
        ContentRepository repository = new ContentRepository();
        ObjectiveRoomGenerator generator = new ObjectiveRoomGenerator(new Random(1));

        assertTrue(generator.resolveObjectiveEncounterLevels(repository, 4).isEmpty());
    }

    @Test
    void activeDungeonMonsterEntriesIgnoresInactiveTables() {
        ContentRepository repository = repositoryWithMonsterAtLevel(4, "goblin");
        repository.tables().get("dungeon-4").setActive(false);
        ObjectiveRoomGenerator generator = new ObjectiveRoomGenerator(new Random(1));

        assertTrue(generator.activeDungeonMonsterEntries(repository).isEmpty());
    }

    @Test
    void activeDungeonMonsterEntriesIgnoresNonDungeonTables() {
        ContentRepository repository = new ContentRepository();
        Table treasureTable = new Table();
        treasureTable.setTableKind(TableKind.TREASURE);
        treasureTable.setActive(true);
        treasureTable.addEntry(new MonsterEntry("goblin", 1, 1));
        repository.tables().put("treasure", treasureTable);
        ObjectiveRoomGenerator generator = new ObjectiveRoomGenerator(new Random(1));

        assertTrue(generator.activeDungeonMonsterEntries(repository).isEmpty());
    }

    @Test
    void entryLevelReadsMonsterEntryLevel() {
        MonsterEntry entry = new MonsterEntry("goblin", 1, 1);
        entry.level = 6;
        ObjectiveRoomGenerator generator = new ObjectiveRoomGenerator(new Random(1));

        assertEquals(6, generator.entryLevel(entry));
    }

    @Test
    void objectiveMonsterWeightReadsConfiguredSettingPerDifficulty() {
        setWeights(3, 4, 5, 6, 7);
        ObjectiveRoomGenerator generator = new ObjectiveRoomGenerator(new Random(1));

        assertEquals(3, generator.objectiveMonsterWeight(
                new ObjectiveRoomGenerator.ObjectiveMonsterDifficulty("difficulty.easy", new int[] {0, 0})));
        assertEquals(4, generator.objectiveMonsterWeight(
                new ObjectiveRoomGenerator.ObjectiveMonsterDifficulty("difficulty.normal", new int[] {0, 0, 0})));
        assertEquals(5, generator.objectiveMonsterWeight(
                new ObjectiveRoomGenerator.ObjectiveMonsterDifficulty("difficulty.hard", new int[] {1, 0, 0})));
        assertEquals(6, generator.objectiveMonsterWeight(
                new ObjectiveRoomGenerator.ObjectiveMonsterDifficulty("difficulty.veryHard", new int[] {1, 1, 0})));
        assertEquals(7, generator.objectiveMonsterWeight(
                new ObjectiveRoomGenerator.ObjectiveMonsterDifficulty("difficulty.extreme", new int[] {2, 1, 0})));
    }
}
