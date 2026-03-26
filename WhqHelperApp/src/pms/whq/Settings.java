package pms.whq;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

import com.whq.app.i18n.Language;

public final class Settings {

  public static final String MONSTER_DIR = "MonsterDir";
  public static final String EVENT_DIR = "EventDir";
  public static final String TRAVEL_DIR = "TravelDir";
  public static final String SETTLEMENT_DIR = "SettlementDir";
  public static final String TABLE_DIR = "TableDir";
  public static final String RULES_DIR = "RulesDir";

  public static final String IMG_DIR = "ImageDir";
  public static final String MONSTER_IMG_DIR = "MonsterImageDir";
  public static final String EVENT_IMG_DIR = "EventImageDir";

  public static final String CARD_WIDTH = "CardWidth";
  public static final String CARD_HEIGHT = "CardHeight";
  public static final String FONT_DIR = "FontDir";
  public static final String SIMULATE_DECK = "SimulateDeck";
  public static final String PARTY_SIZE = "PartySize";
  public static final String EVENT_PROBABILITY = "EventPropability";
  public static final String TREASURE_GOLD_PROBABILITY = "TreasureGoldProbability";
  public static final String LANGUAGE = "Language";
  public static final String SHOW_EVENT_DECK = "ShowEventDeck";
  public static final String SHOW_SETTLEMENT_DECK = "ShowSettlementDeck";
  public static final String SHOW_TRAVEL_DECK = "ShowTravelDeck";
  public static final String SHOW_TREASURE_DECK = "ShowTreasureDeck";
  public static final String SHOW_OBJECTIVE_TREASURE_DECK = "ShowObjectiveTreasureDeck";
  public static final String ADVENTURE_DEFAULT_DECK_SIZE = "AdventureDefaultDeckSize";
  public static final String ADVENTURE_DEFAULT_ROOM_COUNT = "AdventureDefaultRoomCount";
  public static final String ADVENTURE_AMBIENCE = "AdventureAmbience";
  public static final String ADVENTURE_ACTIVE = "AdventureActive";
  public static final String ADVENTURE_LEVEL = "AdventureLevel";
  public static final String OBJECTIVE_MONSTER_EASY_WEIGHT = "ObjectiveMonsterEasyWeight";
  public static final String OBJECTIVE_MONSTER_NORMAL_WEIGHT = "ObjectiveMonsterNormalWeight";
  public static final String OBJECTIVE_MONSTER_HARD_WEIGHT = "ObjectiveMonsterHardWeight";
  public static final String OBJECTIVE_MONSTER_VERY_HARD_WEIGHT = "ObjectiveMonsterVeryHardWeight";
  public static final String OBJECTIVE_MONSTER_EXTREME_WEIGHT = "ObjectiveMonsterExtremeWeight";

  private static final Properties settings = new Properties();

  private static Path baseDir = Path.of("").toAbsolutePath().normalize();
  private static Path settingsFile = baseDir.resolve("settings.cfg");

  static {
    applyDefaultSettings();
  }

  private Settings() {
  }

  public static void load() {
    load(baseDir);
  }

  public static void load(Path projectRoot) {
    baseDir = projectRoot.toAbsolutePath().normalize();
    settingsFile = baseDir.resolve("settings.cfg");

    applyDefaultSettings();

    File file = settingsFile.toFile();
    if (!file.exists()) {
      return;
    }

    try (FileInputStream input = new FileInputStream(file)) {
      settings.load(input);
      normalizeDirectorySettings();
    } catch (IOException ignored) {
      // Keep default values if persisted settings cannot be read.
    }
  }

  public static void save() {
    File file = settingsFile.toFile();
    try (FileOutputStream output = new FileOutputStream(file)) {
      settings.store(output, "EventDeck Settings");
    } catch (IOException ignored) {
      // Ignore save failures.
    }
  }

  public static String getSetting(String setting) {
    return settings.getProperty(setting);
  }

  public static int getSettingAsInt(String setting) {
    String value = getSetting(setting);
    if (value == null) {
      return 0;
    }

    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  public static boolean getSettingAsBool(String setting) {
    return Boolean.parseBoolean(getSetting(setting));
  }

  public static void setSetting(String setting, String value) {
    settings.setProperty(setting, value);
  }

  public static void setSettingAndSave(String setting, String value) {
    setSetting(setting, value);
    save();
  }

  public static Path getBaseDir() {
    return baseDir;
  }

  public static Language getLanguage() {
    String value = settings.getProperty(LANGUAGE);
    if (value == null || value.isBlank()) {
      return Language.ES;
    }

    try {
      return Language.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return Language.ES;
    }
  }

  public static void setLanguage(Language language) {
    if (language == null) {
      return;
    }
    settings.setProperty(LANGUAGE, language.name());
  }

  public static int countActiveTableSettings() {
    int active = 0;

    for (String name : settings.stringPropertyNames()) {
      if (name.endsWith(".active") && Boolean.parseBoolean(settings.getProperty(name))) {
        active++;
      }
    }

    return active;
  }

  private static void applyDefaultSettings() {
    settings.clear();

    settings.setProperty(MONSTER_DIR, directoryPath("data/xml/monsters"));
    settings.setProperty(EVENT_DIR, directoryPath("data/xml/events"));
    settings.setProperty(TRAVEL_DIR, directoryPath("data/xml/travel"));
    settings.setProperty(SETTLEMENT_DIR, directoryPath("data/xml/settlement"));
    settings.setProperty(TABLE_DIR, directoryPath("data/xml/tables"));
    settings.setProperty(RULES_DIR, directoryPath("data/xml/rules"));

    settings.setProperty(IMG_DIR, directoryPath("data/graphics"));
    settings.setProperty(MONSTER_IMG_DIR, directoryPath("data/graphics/monsters"));
    settings.setProperty(EVENT_IMG_DIR, directoryPath("data/graphics/events"));
    settings.setProperty(FONT_DIR, directoryPath("data/fonts"));

    settings.setProperty(CARD_WIDTH, "240");
    settings.setProperty(CARD_HEIGHT, "370");
    settings.setProperty(PARTY_SIZE, "4");
    settings.setProperty(EVENT_PROBABILITY, "37");
    settings.setProperty(TREASURE_GOLD_PROBABILITY, "19");
    settings.setProperty(SIMULATE_DECK, "false");
    settings.setProperty(LANGUAGE, Language.ES.name());
    settings.setProperty(SHOW_EVENT_DECK, "true");
    settings.setProperty(SHOW_SETTLEMENT_DECK, "true");
    settings.setProperty(SHOW_TRAVEL_DECK, "true");
    settings.setProperty(SHOW_TREASURE_DECK, "true");
    settings.setProperty(SHOW_OBJECTIVE_TREASURE_DECK, "true");
    settings.setProperty(ADVENTURE_DEFAULT_DECK_SIZE, "10");
    settings.setProperty(ADVENTURE_DEFAULT_ROOM_COUNT, "6");
    settings.setProperty(ADVENTURE_AMBIENCE, "generic");
    settings.setProperty(ADVENTURE_ACTIVE, "false");
    settings.setProperty(ADVENTURE_LEVEL, "1");
    settings.setProperty(OBJECTIVE_MONSTER_EASY_WEIGHT, "1");
    settings.setProperty(OBJECTIVE_MONSTER_NORMAL_WEIGHT, "2");
    settings.setProperty(OBJECTIVE_MONSTER_HARD_WEIGHT, "1");
    settings.setProperty(OBJECTIVE_MONSTER_VERY_HARD_WEIGHT, "1");
    settings.setProperty(OBJECTIVE_MONSTER_EXTREME_WEIGHT, "1");
  }

  private static String directoryPath(String relativePath) {
    return appendSeparator(baseDir.resolve(relativePath).normalize().toString());
  }

  private static void normalizeDirectorySettings() {
    normalizeDirectorySetting(MONSTER_DIR);
    normalizeDirectorySetting(EVENT_DIR);
    normalizeDirectorySetting(TRAVEL_DIR);
    normalizeDirectorySetting(SETTLEMENT_DIR);
    normalizeDirectorySetting(TABLE_DIR);
    normalizeDirectorySetting(RULES_DIR);
    normalizeDirectorySetting(IMG_DIR);
    normalizeDirectorySetting(MONSTER_IMG_DIR);
    normalizeDirectorySetting(EVENT_IMG_DIR);
    normalizeDirectorySetting(FONT_DIR);
  }

  private static void normalizeDirectorySetting(String key) {
    String value = settings.getProperty(key);
    if (value == null || value.isBlank()) {
      return;
    }

    Path path = resolveDirectoryPath(value);
    if (path == null || !Files.isDirectory(path)) {
      path = baseDir.resolve(defaultRelativeDirectory(key)).normalize();
    }

    settings.setProperty(key, appendSeparator(path.toString()));
  }

  private static Path resolveDirectoryPath(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    Path path = Path.of(value);
    if (!path.isAbsolute()) {
      return baseDir.resolve(path).normalize();
    }
    return path.normalize();
  }

  private static String defaultRelativeDirectory(String key) {
    return switch (key) {
      case MONSTER_DIR -> "data/xml/monsters";
      case EVENT_DIR -> "data/xml/events";
      case TRAVEL_DIR -> "data/xml/travel";
      case SETTLEMENT_DIR -> "data/xml/settlement";
      case TABLE_DIR -> "data/xml/tables";
      case RULES_DIR -> "data/xml/rules";
      case IMG_DIR -> "data/graphics";
      case MONSTER_IMG_DIR -> "data/graphics/monsters";
      case EVENT_IMG_DIR -> "data/graphics/events";
      case FONT_DIR -> "data/fonts";
      default -> "";
    };
  }

  private static String appendSeparator(String path) {
    if (path.endsWith(File.separator)) {
      return path;
    }
    return path + File.separator;
  }
}
