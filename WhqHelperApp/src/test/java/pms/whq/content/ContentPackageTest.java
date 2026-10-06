package pms.whq.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.whq.app.AppPaths;
import com.whq.app.model.DungeonCard;
import com.whq.app.storage.XmlDungeonCardStore;

import pms.whq.data.Table;

/** La aplicacion se distribuye sin contenido: con el de ejemplo, sin ninguno o con uno incompleto. */
class ContentPackageTest {

  private static final Path SHARED_HOME = AppPaths.sharedHome(Path.of("").toAbsolutePath().normalize());
  private static final Path SAMPLE = SHARED_HOME.resolve("sample");

  @TempDir
  Path tempDir;

  private Path runtimeHome;
  private String previousContentHome;

  @BeforeEach
  void bindRealSharedHome() {
    previousContentHome = System.getProperty("whq.content.home");
    runtimeHome = tempDir.resolve("user-home");
    AppPaths.bindSharedHome(runtimeHome, SHARED_HOME);
  }

  @AfterEach
  void restoreContentHome() {
    if (previousContentHome == null) {
      System.clearProperty("whq.content.home");
    } else {
      System.setProperty("whq.content.home", previousContentHome);
    }
  }

  @Test
  void sampleContentLoadsThroughTheContentHomeProperty() throws Exception {
    System.setProperty("whq.content.home", SAMPLE.toString());
    List<ContentIssue> issues = new ArrayList<>();

    ContentRepository repository = new RuntimeContentService(runtimeHome).load(issues::add);
    List<DungeonCard> cards = new XmlDungeonCardStore(runtimeHome).loadCards();

    assertTrue(AppPaths.hasContent(runtimeHome));
    assertEquals(List.of(), issues);
    assertEquals(3, repository.monsters().size());
    Table table = repository.tables().get("Sample Monsters");
    assertNotNull(table);
    assertEquals("sample-monsters", table.getId());
    assertEquals(4, table.getMonsterEntries().size());
    assertEquals(1, cards.size());
    assertEquals("LANTERN HALL", cards.get(0).getName());
  }

  @Test
  void withoutContentPackageTheAppLoadsNothingAndCreatesNothing() throws Exception {
    Path missing = tempDir.resolve("no-content-here");
    System.setProperty("whq.content.home", missing.toString());
    List<ContentIssue> issues = new ArrayList<>();

    ContentRepository repository = new RuntimeContentService(runtimeHome).load(issues::add);
    List<DungeonCard> cards = new XmlDungeonCardStore(runtimeHome).loadCards();

    assertFalse(AppPaths.hasContent(runtimeHome));
    assertTrue(repository.monsters().isEmpty());
    assertTrue(repository.tables().isEmpty());
    assertTrue(cards.isEmpty());
    // El aviso lo da la ventana principal; aqui no se duplica.
    assertEquals(List.of(), issues);
    // Crear algo en la ubicacion esperada haria que el siguiente arranque lo tomase por un paquete.
    assertFalse(Files.exists(missing));
  }

  @Test
  void incompletePackageLoadsWhatItHasAndReportsTheMissingDirectories() throws Exception {
    Path content = tempDir.resolve("partial-content");
    Path tables = Files.createDirectories(content.resolve("data/xml/tables"));
    Files.copy(SAMPLE.resolve("data/xml/tables/sample-tables.xml"), tables.resolve("sample-tables.xml"));
    System.setProperty("whq.content.home", content.toString());
    List<ContentIssue> issues = new ArrayList<>();

    ContentRepository repository = new RuntimeContentService(runtimeHome).load(issues::add);

    assertNotNull(repository.tables().get("Sample Monsters"));
    List<ContentIssue> incomplete =
        issues.stream().filter(issue -> "Incomplete Content Package".equals(issue.title())).toList();
    assertEquals(1, incomplete.size());
    String message = incomplete.get(0).message();
    for (String directory : List.of("rules", "monsters", "events", "travel", "settlement")) {
      assertTrue(message.contains(Path.of("data/xml", directory).toString()), directory);
    }
    assertFalse(message.contains(Path.of("data/xml/tables").toString()));
  }

  @Test
  void sampleContentValidatesAgainstTheSchemas() throws Exception {
    Map<String, String> schemaByDirectory =
        Map.of(
            "monsters", "whq-monster-schema.xsd",
            "tables", "whq-tables-schema.xsd",
            "dungeon", "whq-dungeon-cards-schema.xsd");
    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    int validated = 0;
    try (Stream<Path> files = Files.walk(SAMPLE.resolve("data/xml"))) {
      for (Path file : files.filter(path -> path.toString().endsWith(".xml")).sorted().toList()) {
        String directory = file.getParent().getFileName().toString();
        Path schema = SHARED_HOME.resolve("data/xml").resolve(directory).resolve(schemaByDirectory.get(directory));
        factory.newSchema(schema.toFile()).newValidator().validate(new StreamSource(file.toFile()));
        validated++;
      }
    }
    assertEquals(3, validated);
  }
}
