package pms.whq.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import pms.whq.data.Table;

class RuntimeContentValidatorTest {

  @Test
  void keepsTableReferencesThatPointToAStableIdOrAVisibleName() throws Exception {
    // El fallo: solo valia el nombre visible, y una referencia por id se descartaba al cargar.
    ContentRepository repository = new ContentRepository();
    Table target = tableFromXml("<table id=\"minions\" name=\"Minions (renamed)\" kind=\"dungeon\"/>");
    Table source =
        tableFromXml(
            "<table id=\"dungeon\" name=\"Dungeon\" kind=\"dungeon\">"
                + "<tableRef name=\"minions\"/>"
                + "<tableRef name=\"Minions (renamed)\"/>"
                + "<tableRef name=\"missing\"/>"
                + "</table>");
    repository.tables().put(target.getName(), target);
    repository.tables().put(source.getName(), source);
    List<ContentIssue> issues = new ArrayList<>();

    new RuntimeContentValidator().pruneInvalidTableEntries(repository, issues::add);

    assertEquals(2, source.getMonsterEntries().size());
    assertEquals(1, issues.size());
    assertTrue(issues.get(0).message().contains("[missing]"));
  }

  private static Table tableFromXml(String xml) throws Exception {
    return new Table(
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new InputSource(new StringReader(xml)))
            .getDocumentElement());
  }
}
