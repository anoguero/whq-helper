package pms.whq.swt.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.whq.app.adventure.ObjectiveRoomAdventure;
import com.whq.app.model.CardType;
import com.whq.app.model.DungeonCard;

class ObjectiveRoomAdventuresTabTest {

  // Como las carga el editor en espanol: nombres traducidos.
  private static final List<DungeonCard> ROOMS = List.of(
      room(16, "CAMARA DE LA TUMBA"),
      room(20, "FOSO DE COMBATE"));

  private static DungeonCard room(long id, String name) {
    return new DungeonCard(id, name, CardType.OBJECTIVE_ROOM, "The Old World", 1, true, "", "", "");
  }

  @Test
  void findsTheRoomOfAnAdventureByCardIdEvenWithTheEnglishXmlName() {
    // El fallo: la aventura trae el nombre ingles del XML, que no esta en el desplegable traducido.
    ObjectiveRoomAdventure adventure = new ObjectiveRoomAdventure("FIGHTING PIT", "a", "A", "", "", false, 20);

    assertEquals(1, ObjectiveRoomAdventuresTab.objectiveRoomIndex(ROOMS, adventure));
    assertEquals("FOSO DE COMBATE", ObjectiveRoomAdventuresTab.objectiveRoomLabel(ROOMS, adventure));
  }

  @Test
  void fallsBackToTheNameWithoutCardIdAndToNothingWhenItDoesNotMatch() {
    ObjectiveRoomAdventure byName = new ObjectiveRoomAdventure("camara de la tumba", "a", "A", "", "", false);
    ObjectiveRoomAdventure unknown = new ObjectiveRoomAdventure("WICKED WELL", "b", "B", "", "", false);

    assertEquals(0, ObjectiveRoomAdventuresTab.objectiveRoomIndex(ROOMS, byName));
    assertEquals(-1, ObjectiveRoomAdventuresTab.objectiveRoomIndex(ROOMS, unknown));
    assertEquals("WICKED WELL", ObjectiveRoomAdventuresTab.objectiveRoomLabel(ROOMS, unknown));
  }
}
