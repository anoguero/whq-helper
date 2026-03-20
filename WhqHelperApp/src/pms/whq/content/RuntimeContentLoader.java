package pms.whq.content;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.whq.app.i18n.ContentTranslations;

import pms.whq.Settings;
import pms.whq.data.Event;
import pms.whq.data.Monster;
import pms.whq.data.Rule;
import pms.whq.data.SettlementEvent;
import pms.whq.data.Table;
import pms.whq.data.TravelEvent;

public class RuntimeContentLoader {

  private static final String[] ALWAYS_LOADED_EVENT_FILES = {"main-gold.xml"};

  private final Path projectRoot;
  private final DocumentBuilderFactory parserFactory;

  public RuntimeContentLoader(Path projectRoot) {
    this.projectRoot = projectRoot.toAbsolutePath().normalize();
    this.parserFactory = DocumentBuilderFactory.newInstance();
  }

  public ContentRepository load() {
    Settings.load(projectRoot);
    ContentTranslations translations = ContentTranslations.load(projectRoot, Settings.getLanguage());

    ContentRepository repository = new ContentRepository();
    loadRules(repository, translations);
    loadMonsters(repository, translations);
    loadEvents(repository, translations);
    loadTravelEvents(repository, translations);
    loadSettlementEvents(repository, translations);
    loadTables(repository);
    return repository;
  }

  private void loadRules(ContentRepository repository, ContentTranslations translations) {
    loadNodes(
        Settings.getSetting(Settings.RULES_DIR),
        "rules",
        node -> {
          String nodeName = node.getNodeName();
          if ("rule".equals(nodeName) || "magic".equals(nodeName)) {
            Rule rule = new Rule(node);
            rule.name = translations.t("rule." + rule.id + ".name", rule.name);
            rule.text = translations.t("rule." + rule.id + ".text", rule.text);
            repository.rules().put(rule.id, rule);
          }
        });
  }

  private void loadTables(ContentRepository repository) {
    loadNodes(
        Settings.getSetting(Settings.TABLE_DIR),
        "tables",
        node -> {
          if ("table".equals(node.getNodeName())) {
            Table table = new Table(node);
            String settingName = table.getName() + ".active";
            table.setActive(Settings.getSettingAsBool(settingName));
            repository.tables().put(table.getName(), table);
          }
        });
    Table.registerAll(repository.tables());
  }

  private void loadMonsters(ContentRepository repository, ContentTranslations translations) {
    loadNodes(
        Settings.getSetting(Settings.MONSTER_DIR),
        "monsters",
        node -> {
          if ("monster".equals(node.getNodeName())) {
            Monster monster = new Monster(node);
            monster.name = translations.t("monster." + monster.id + ".name", monster.name);
            monster.plural = translations.t("monster." + monster.id + ".plural", monster.plural);
            monster.special = translations.t("monster." + monster.id + ".special", monster.special);
            repository.monsters().put(monster.id, monster);
          }
        });
  }

  private void loadEvents(ContentRepository repository, ContentTranslations translations) {
    loadNodes(
        Settings.getSetting(Settings.EVENT_DIR),
        "events",
        node -> {
          if ("event".equals(node.getNodeName())) {
            Event event = new Event(node);
            applyEventTranslations(event, translations);
            repository.events().put(event.id, event);
          }
        });

    String eventDirectory = Settings.getSetting(Settings.EVENT_DIR);
    if (eventDirectory == null || eventDirectory.isBlank()) {
      return;
    }

    File dir = new File(eventDirectory);
    for (String fileName : ALWAYS_LOADED_EVENT_FILES) {
      File file = new File(dir, fileName);
      if (file.isFile()) {
        loadNodesFromFile(
            file,
            "events",
            node -> {
              if ("event".equals(node.getNodeName())) {
                Event event = new Event(node);
                applyEventTranslations(event, translations);
                repository.events().put(event.id, event);
              }
            });
      }
    }
  }

  private void loadTravelEvents(ContentRepository repository, ContentTranslations translations) {
    loadNodes(
        Settings.getSetting(Settings.TRAVEL_DIR),
        "events",
        node -> {
          if ("event".equals(node.getNodeName())) {
            TravelEvent event = new TravelEvent(node);
            event.name = translations.t("event." + event.id + ".name", event.name);
            event.rules = translations.t("event." + event.id + ".rules", event.rules);
            repository.travelEvents().put(event.id, event);
          }
        });
  }

  private void loadSettlementEvents(ContentRepository repository, ContentTranslations translations) {
    loadNodes(
        Settings.getSetting(Settings.SETTLEMENT_DIR),
        "events",
        node -> {
          if ("event".equals(node.getNodeName())) {
            SettlementEvent event = new SettlementEvent(node);
            event.name = translations.t("event." + event.id + ".name", event.name);
            event.rules = translations.t("event." + event.id + ".rules", event.rules);
            repository.settlementEvents().put(event.id, event);
          }
        });
  }

  private static void applyEventTranslations(Event event, ContentTranslations translations) {
    if (event == null) {
      return;
    }
    event.name = translations.t("event." + event.id + ".name", event.name);
    event.flavor = translations.t("event." + event.id + ".flavor", event.flavor);
    event.rules = translations.t("event." + event.id + ".rules", event.rules);
    event.special = translations.t("event." + event.id + ".special", event.special);
    event.goldValue = translations.t("event." + event.id + ".goldValue", event.goldValue);
    event.users = translations.t("event." + event.id + ".users", event.users);
  }

  private void loadNodes(String directory, String rootName, NodeConsumer nodeConsumer) {
    if (directory == null || directory.isBlank()) {
      return;
    }

    File dir = new File(directory);
    File[] files = dir.listFiles(file -> file.getName().endsWith(".xml"));
    if (files == null) {
      return;
    }

    Arrays.sort(
        files,
        Comparator.comparing(
                (File file) -> !file.getName().toLowerCase().startsWith("userdefined-"))
            .thenComparing(file -> file.getName().toLowerCase()));

    for (File file : files) {
      loadNodesFromFile(file, rootName, nodeConsumer);
    }
  }

  private void loadNodesFromFile(File file, String rootName, NodeConsumer nodeConsumer) {
    if (file == null || !file.isFile()) {
      return;
    }

    try {
      Document doc = parse(file);
      Node root = doc.getDocumentElement();
      if (root != null && rootName.equals(root.getNodeName())) {
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
          nodeConsumer.accept(children.item(i));
        }
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private Document parse(File file) throws Exception {
    DocumentBuilder builder = parserFactory.newDocumentBuilder();
    return builder.parse(file);
  }

  @FunctionalInterface
  private interface NodeConsumer {
    void accept(Node node);
  }
}
