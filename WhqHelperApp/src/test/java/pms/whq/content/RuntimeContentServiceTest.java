package pms.whq.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import pms.whq.data.Table;
import pms.whq.data.TableReferenceEntry;
import pms.whq.game.TableDrawService;

class RuntimeContentServiceTest {

  @Test
  void catacombsTableFileParsesIntoLegacyTableModel() throws Exception {
    Path file = Path.of("data/xml/tables/cot-monster-tables.xml").toAbsolutePath().normalize();
    var builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    var document = builder.parse(file.toFile());
    NodeList nodes = document.getDocumentElement().getChildNodes();
    List<String> tableNames = new ArrayList<>();

    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (!"table".equals(node.getNodeName())) {
        continue;
      }
      tableNames.add(new Table(node).getName());
    }

    assertTrue(tableNames.contains("Catacombs of Terror Monsters - Level 1"));
    assertTrue(tableNames.contains("Catacombs of Terror Monsters - Level 10"));
  }

  @Test
  void catacombsTablesLoadAndStayActive() {
    RuntimeContentService service = new RuntimeContentService(Path.of("").toAbsolutePath().normalize());
    List<ContentIssue> issues = new ArrayList<>();

    ContentRepository repository = service.load(issues::add);

    Table level1 = repository.tables().get("Catacombs of Terror Monsters - Level 1");
    Table level6 = repository.tables().get("Catacombs of Terror Monsters - Level 6");

    assertNotNull(level1);
    assertNotNull(level6);
    assertTrue(level1.isActive());
    assertTrue(level6.isActive());
    assertFalse(level1.getMonsterEntries().isEmpty());
    assertFalse(level6.getMonsterEntries().isEmpty());
  }

  @Test
  void catacombsTableReferencesSurviveValidationAndResolve() {
    RuntimeContentService service = new RuntimeContentService(Path.of("").toAbsolutePath().normalize());
    List<ContentIssue> issues = new ArrayList<>();

    ContentRepository repository = service.load(issues::add);
    Table level1 = repository.tables().get("Catacombs of Terror Monsters - Level 1");

    assertNotNull(level1);
    assertTrue(
        level1.getMonsterEntries().stream().anyMatch(TableReferenceEntry.class::isInstance));
    assertTrue(
        issues.stream().noneMatch(issue -> "Unknown Entry Type".equals(issue.title())));

    TableReferenceEntry reference =
        (TableReferenceEntry)
            level1.getMonsterEntries().stream()
                .filter(TableReferenceEntry.class::isInstance)
                .findFirst()
                .orElseThrow();

    Object resolved = new TableDrawService().resolveEntry(reference);
    assertNotNull(resolved);
  }
}
