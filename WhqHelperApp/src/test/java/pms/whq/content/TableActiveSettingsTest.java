package pms.whq.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.whq.app.AppPaths;

import pms.whq.data.Table;
import pms.whq.data.TableIds;
import pms.whq.xml.XmlContentService;
import pms.whq.xml.XmlContentService.TableFileModel;

class TableActiveSettingsTest {

  @TempDir
  Path root;

  @Test
  void legacyKeysAreMigratedToIdKeysKeepingTheirValue() throws Exception {
    writeBaseTables(
        table("hag-queen-1", "Hall of the Hag Queen Monsters - Level 1"),
        table("cot-3", "Catacombs of Terror Monsters - Level 3"));
    writeSettings(
        "Hall of the Hag Queen Monsters - Level 1.active", "false",
        "Catacombs of Terror Monsters - Level 3.active", "true");

    ContentRepository repository = load(new ArrayList<>());

    assertFalse(repository.tables().get("Hall of the Hag Queen Monsters - Level 1").isActive());
    assertTrue(repository.tables().get("Catacombs of Terror Monsters - Level 3").isActive());
    Properties saved = readSettings();
    assertEquals("false", saved.getProperty("table.hag-queen-1.active"));
    assertEquals("true", saved.getProperty("table.cot-3.active"));
    assertNull(saved.getProperty("Hall of the Hag Queen Monsters - Level 1.active"));
    assertNull(saved.getProperty("Catacombs of Terror Monsters - Level 3.active"));
  }

  @Test
  void orphanLegacyKeyIsKeptAndReported() throws Exception {
    writeBaseTables(table("hag-queen-1", "Hall of the Hag Queen Monsters - Level 1"));
    writeSettings(
        "Hall of the Hag Queen Monsters - Level 1.active", "false",
        "gpt-events.active", "true");
    List<ContentIssue> issues = new ArrayList<>();

    load(issues);

    Properties saved = readSettings();
    assertEquals("true", saved.getProperty("gpt-events.active"));
    assertEquals("false", saved.getProperty("table.hag-queen-1.active"));
    assertEquals(1, issues.size());
    assertTrue(issues.get(0).message().contains("gpt-events.active"));
  }

  @Test
  void idKeyWinsOverALeftoverLegacyKey() throws Exception {
    writeBaseTables(table("hag-queen-1", "Hall of the Hag Queen Monsters - Level 1"));
    writeSettings(
        "Hall of the Hag Queen Monsters - Level 1.active", "true",
        "table.hag-queen-1.active", "false");

    ContentRepository repository = load(new ArrayList<>());

    assertFalse(repository.tables().get("Hall of the Hag Queen Monsters - Level 1").isActive());
    Properties saved = readSettings();
    assertEquals("false", saved.getProperty("table.hag-queen-1.active"));
    assertNull(saved.getProperty("Hall of the Hag Queen Monsters - Level 1.active"));
  }

  @Test
  void userTableWithoutIdResolvesByNameSlug() throws Exception {
    writeBaseTables(table("hag-queen-1", "Hall of the Hag Queen Monsters - Level 1"));
    Path userTables = Files.createDirectories(root.resolve("data/xml/tables"));
    Files.writeString(
        userTables.resolve("userdefined-tables.xml"),
        "<tables><table name=\"My Monsters\"><event id=\"e\"/></table></tables>",
        StandardCharsets.UTF_8);
    writeSettings("My Monsters.active", "false");

    ContentRepository repository = load(new ArrayList<>());

    Table userTable = repository.tables().get("My Monsters");
    assertEquals("my-monsters", userTable.getId());
    assertFalse(userTable.isActive());
    assertEquals("false", readSettings().getProperty("table.my-monsters.active"));
  }

  @Test
  void renamingATableKeepsItsActiveState() throws Exception {
    writeBaseTables(table("hag-queen-1", "Hall of the Hag Queen Monsters - Level 1"));
    writeSettings("Hall of the Hag Queen Monsters - Level 1.active", "false");
    assertFalse(load(new ArrayList<>()).tables().get("Hall of the Hag Queen Monsters - Level 1").isActive());

    writeBaseTables(table("hag-queen-1", "Hag Queen Monsters (Level 1)"));

    assertFalse(load(new ArrayList<>()).tables().get("Hag Queen Monsters (Level 1)").isActive());
  }

  @Test
  void editorKeepsTheIdOfAUserTableWhenItIsRenamed() throws Exception {
    XmlContentService service = new XmlContentService(Path.of(""));
    Path file = root.resolve("userdefined-tables.xml");
    Files.writeString(
        file,
        "<tables><table name=\"Old Name\"><monster id=\"rpb-black-orc\" number=\"1\"/></table></tables>",
        StandardCharsets.UTF_8);

    TableFileModel model = service.loadTables(file);
    assertEquals("old-name", model.tables.get(0).id);
    model.tables.get(0).name = "Fase 16 Renamed Table";
    service.saveTables(file, model);

    TableFileModel reloaded = service.loadTables(file);
    assertEquals("old-name", reloaded.tables.get(0).id);
    assertEquals("Fase 16 Renamed Table", reloaded.tables.get(0).name);
  }

  @Test
  void baseContentTableIdsArePresentAndUnique() throws Exception {
    Path tablesDir = AppPaths.sharedPath(Path.of(""), "data/xml/tables");
    Set<String> ids = new HashSet<>();
    int tables = 0;
    try (Stream<Path> files = Files.list(tablesDir)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".xml")).sorted().toList()) {
        NodeList nodes =
            DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(file.toFile())
                .getElementsByTagName("table");
        for (int i = 0; i < nodes.getLength(); i++) {
          Element element = (Element) nodes.item(i);
          String id = element.getAttribute("id");
          assertFalse(id.isBlank(), "Table without id: " + element.getAttribute("name"));
          assertTrue(ids.add(id), "Duplicate table id: " + id);
          tables++;
        }
      }
    }
    assertEquals(54, tables);
  }

  @Test
  void slugIsLowercaseHyphenatedAndWithoutAccents() {
    assertEquals("hall-of-the-hag-queen-monsters-level-1", TableIds.fromName("Hall of the Hag Queen Monsters - Level 1"));
    assertEquals("lm-town-events", TableIds.fromName("lm - Town Events"));
    assertEquals("tesoros-de-sala-n", TableIds.fromName("  Tesoros de Salá Ñ! "));
    assertEquals("table", TableIds.fromName(" -- "));
    assertEquals("table", TableIds.fromName(null));
  }

  private ContentRepository load(List<ContentIssue> issues) {
    return new RuntimeContentLoader(root).load(issues::add);
  }

  private static String table(String id, String name) {
    return "<table id=\"" + id + "\" name=\"" + name + "\"><event id=\"e\"/></table>";
  }

  private void writeBaseTables(String... tables) throws Exception {
    Path dir = Files.createDirectories(root.resolve("shared/data/xml/tables"));
    Files.writeString(
        dir.resolve("base-tables.xml"), "<tables>" + String.join("", tables) + "</tables>", StandardCharsets.UTF_8);
  }

  private void writeSettings(String... keysAndValues) throws Exception {
    Properties properties = new Properties();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      properties.setProperty(keysAndValues[i], keysAndValues[i + 1]);
    }
    try (OutputStream output = Files.newOutputStream(root.resolve("settings.cfg"))) {
      properties.store(output, null);
    }
  }

  private Properties readSettings() throws Exception {
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(root.resolve("settings.cfg"))) {
      properties.load(input);
    }
    return properties;
  }
}
