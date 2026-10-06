package pms.whq.swt.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pms.whq.xml.XmlContentService.TableDefinition;
import pms.whq.xml.XmlContentService.TableFileModel;

class TableIdConflictsTest {

  private static final Path USER_FILE = Path.of("data/xml/tables/userdefined-tables.xml");
  private static final Path BASE_FILE = Path.of("content/data/xml/tables/base-tables.xml");

  private static TableDefinition table(String id, String name) {
    TableDefinition table = new TableDefinition();
    table.id = id;
    table.name = name;
    return table;
  }

  private static TableFileModel file(TableDefinition... tables) {
    TableFileModel model = new TableFileModel();
    model.tables.addAll(java.util.List.of(tables));
    return model;
  }

  @Test
  void aNewTableWhoseNameGivesTheIdOfARenamedTableIsRejected() {
    // El fallo: "Bar" renombrada a "Foo" conserva el id bar, y una tabla nueva "Bar" tambien lo recibia.
    TableFileModel userFile = file(table("bar", "Foo"));

    TableIdConflicts.Conflict conflict = TableIdConflicts.find(table("", "Bar"), -1, USER_FILE, userFile, Map.of());

    assertEquals(new TableIdConflicts.Conflict("bar", "Foo", USER_FILE), conflict);
  }

  @Test
  void aTableWhoseIdIsUsedInAnotherFileIsRejected() {
    Map<Path, TableFileModel> others = Map.of(BASE_FILE, file(table("catacombs-monsters", "Catacombs Monsters")));

    TableIdConflicts.Conflict conflict =
        TableIdConflicts.find(table("", "Catacombs  Monsters!"), -1, USER_FILE, file(), others);

    assertEquals(new TableIdConflicts.Conflict("catacombs-monsters", "Catacombs Monsters", BASE_FILE), conflict);
  }

  @Test
  void theTableBeingEditedIsNotAConflictWithItself() {
    TableFileModel userFile = file(table("bar", "Foo"), table("other", "Other"));

    assertNull(TableIdConflicts.find(table("bar", "Foo renamed"), 0, USER_FILE, userFile, Map.of()));
  }

  @Test
  void theFileBeingSavedIsCheckedFromTheEditorNotFromDisk() {
    // La copia en disco del fichero que se guarda puede estar desfasada: cuenta la del editor.
    Map<Path, TableFileModel> others = Map.of(USER_FILE, file(table("stale", "Stale")));

    assertNull(TableIdConflicts.find(table("stale", "New"), -1, USER_FILE, file(), others));
  }

  @Test
  void theEffectiveIdIsTheIdAttributeOrTheSlugOfTheName() {
    assertEquals("kept", TableIdConflicts.effectiveId(table(" kept ", "Renamed")));
    assertEquals("sala-n", TableIdConflicts.effectiveId(table("", "Salá Ñ")));
  }
}
