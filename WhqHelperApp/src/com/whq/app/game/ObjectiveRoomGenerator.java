package com.whq.app.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import pms.whq.Settings;
import pms.whq.content.ContentRepository;
import pms.whq.data.MonsterEntry;
import pms.whq.data.MonsterGroup;
import pms.whq.data.Table;
import pms.whq.data.TableKind;
import pms.whq.data.TableReferenceEntry;
import pms.whq.game.TableDrawService;
import pms.whq.state.AdventureAmbience;

/**
 * Genera los monstruos de la sala objetivo a partir de las tablas de mazmorra activas.
 * Sin dependencias de SWT ni de I18n: las claves i18n (dificultad, etc.) se exponen
 * como datos y se traducen en la capa de UI.
 */
public final class ObjectiveRoomGenerator {

    public record ObjectiveMonsterDifficulty(String labelKey, int[] offsets) {
    }

    public static final List<ObjectiveMonsterDifficulty> OBJECTIVE_MONSTER_DIFFICULTIES = List.of(
            new ObjectiveMonsterDifficulty("difficulty.easy", new int[] {0, 0}),
            new ObjectiveMonsterDifficulty("difficulty.normal", new int[] {0, 0, 0}),
            new ObjectiveMonsterDifficulty("difficulty.hard", new int[] {1, 0, 0}),
            new ObjectiveMonsterDifficulty("difficulty.veryHard", new int[] {1, 1, 0}),
            new ObjectiveMonsterDifficulty("difficulty.extreme", new int[] {2, 1, 0}));

    /** Resultado de generar los monstruos de la sala objetivo: la dificultad sorteada y las entradas resueltas. */
    public record ObjectiveRoomEncounter(ObjectiveMonsterDifficulty difficulty, List<Object> entries) {
    }

    private final Random random;
    private final TableDrawService tableDrawService;

    public ObjectiveRoomGenerator(Random random) {
        this(random, new TableDrawService());
    }

    public ObjectiveRoomGenerator(Random random, TableDrawService tableDrawService) {
        this.random = random;
        this.tableDrawService = tableDrawService;
    }

    public int objectiveMonsterWeight(ObjectiveMonsterDifficulty difficulty) {
        if (difficulty == null) {
            return 0;
        }
        return switch (difficulty.labelKey()) {
            case "difficulty.easy" -> Math.max(0, Settings.getSettingAsInt(Settings.OBJECTIVE_MONSTER_EASY_WEIGHT));
            case "difficulty.normal" -> Math.max(0, Settings.getSettingAsInt(Settings.OBJECTIVE_MONSTER_NORMAL_WEIGHT));
            case "difficulty.hard" -> Math.max(0, Settings.getSettingAsInt(Settings.OBJECTIVE_MONSTER_HARD_WEIGHT));
            case "difficulty.veryHard" -> Math.max(0, Settings.getSettingAsInt(Settings.OBJECTIVE_MONSTER_VERY_HARD_WEIGHT));
            default -> Math.max(0, Settings.getSettingAsInt(Settings.OBJECTIVE_MONSTER_EXTREME_WEIGHT));
        };
    }

    public ObjectiveMonsterDifficulty rollObjectiveMonsterDifficulty() {
        int totalWeight = OBJECTIVE_MONSTER_DIFFICULTIES.stream()
                .mapToInt(this::objectiveMonsterWeight)
                .sum();
        if (totalWeight <= 0) {
            return null;
        }

        int roll = random.nextInt(totalWeight);
        for (ObjectiveMonsterDifficulty difficulty : OBJECTIVE_MONSTER_DIFFICULTIES) {
            roll -= objectiveMonsterWeight(difficulty);
            if (roll < 0) {
                return difficulty;
            }
        }
        return OBJECTIVE_MONSTER_DIFFICULTIES.get(OBJECTIVE_MONSTER_DIFFICULTIES.size() - 1);
    }

    public List<Object> activeDungeonMonsterEntries(ContentRepository repository) {
        AdventureAmbience selectedAmbience =
                AdventureAmbience.fromStorageValue(Settings.getSetting(Settings.ADVENTURE_AMBIENCE));

        List<Object> activeEntries = repository.tables().values().stream()
                .filter(Table::isActive)
                .filter(table -> table.getTableKind() == TableKind.DUNGEON)
                .flatMap(table -> table.getMonsterEntries().stream())
                .collect(Collectors.toCollection(ArrayList::new));

        if (selectedAmbience.isGeneric()) {
            return activeEntries;
        }

        List<Object> ambienceFiltered = activeEntries.stream()
                .filter(entry -> matchesObjectiveMonsterAmbience(entry, selectedAmbience))
                .collect(Collectors.toCollection(ArrayList::new));
        return ambienceFiltered.isEmpty() ? activeEntries : ambienceFiltered;
    }

    public boolean matchesObjectiveMonsterAmbience(Object entry, AdventureAmbience selectedAmbience) {
        if (selectedAmbience == null || selectedAmbience.isGeneric()) {
            return true;
        }
        if (entry instanceof MonsterEntry monsterEntry) {
            return selectedAmbience.matches(monsterEntry.ambiences);
        }
        if (entry instanceof TableReferenceEntry tableReferenceEntry) {
            return tableReferenceEntry.ambiences.isEmpty()
                    || selectedAmbience.matches(tableReferenceEntry.ambiences);
        }
        if (entry instanceof MonsterGroup group) {
            if (group.isEmpty()) {
                return false;
            }
            for (Object nested : group) {
                if (!matchesObjectiveMonsterAmbience(nested, selectedAmbience)) {
                    return false;
                }
            }
            return true;
        }
        return true;
    }

    public int entryLevel(Object entry) {
        if (entry instanceof MonsterEntry monsterEntry) {
            return monsterEntry.level;
        }
        if (entry instanceof TableReferenceEntry tableReferenceEntry) {
            return tableReferenceEntry.level;
        }
        if (entry instanceof MonsterGroup group) {
            return group.level;
        }
        return 1;
    }

    public List<Integer> availableObjectiveMonsterLevels(ContentRepository repository) {
        return activeDungeonMonsterEntries(repository).stream()
                .map(this::entryLevel)
                .distinct()
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public List<Integer> resolveObjectiveEncounterLevels(ContentRepository repository, int requestedLevel) {
        List<Integer> availableLevels = availableObjectiveMonsterLevels(repository);
        if (availableLevels.isEmpty()) {
            return List.of();
        }
        if (availableLevels.contains(requestedLevel)) {
            return List.of(requestedLevel);
        }

        for (int i = availableLevels.size() - 1; i >= 0; i--) {
            int availableLevel = availableLevels.get(i);
            if (availableLevel <= requestedLevel) {
                return List.of(availableLevel, availableLevel);
            }
        }

        int fallbackLevel = availableLevels.get(0);
        return List.of(fallbackLevel, fallbackLevel);
    }

    public Object pickRandomObjectiveMonsterEntry(ContentRepository repository, int level) {
        List<Object> entries = activeDungeonMonsterEntries(repository).stream()
                .filter(entry -> entryLevel(entry) == level)
                .collect(Collectors.toCollection(ArrayList::new));
        if (entries.isEmpty()) {
            return null;
        }
        Object selected = entries.get(random.nextInt(entries.size()));
        if (selected instanceof TableReferenceEntry tableReferenceEntry) {
            return tableDrawService.resolveEntry(tableReferenceEntry);
        }
        return selected;
    }

    /**
     * Sortea la dificultad y genera las entradas de monstruo de la sala objetivo.
     * Devuelve {@code null} si los pesos de dificultad configurados suman 0 (sin dificultad posible).
     */
    public ObjectiveRoomEncounter generateObjectiveRoomMonsterEntries(ContentRepository repository, int dungeonLevel) {
        ObjectiveMonsterDifficulty difficulty = rollObjectiveMonsterDifficulty();
        if (difficulty == null) {
            return null;
        }

        List<Object> entries = new ArrayList<>();
        for (int offset : difficulty.offsets()) {
            List<Integer> resolvedLevels = resolveObjectiveEncounterLevels(repository, dungeonLevel + offset);
            for (int resolvedLevel : resolvedLevels) {
                Object entry = pickRandomObjectiveMonsterEntry(repository, resolvedLevel);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }
        return new ObjectiveRoomEncounter(difficulty, entries);
    }
}
