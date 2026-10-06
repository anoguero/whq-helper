package pms.whq.swt.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import pms.whq.xml.XmlContentService.EventEntry;
import pms.whq.xml.XmlContentService.LocationEntry;
import pms.whq.xml.XmlContentService.MonsterEntry;
import pms.whq.xml.XmlContentService.RuleEntry;
import pms.whq.xml.XmlContentService.WarriorEntry;

class EditorSupportTest {

  @Test
  void extractIdReadsTheIdOfEveryEditableEntryType() {
    RuleEntry rule = new RuleEntry();
    rule.id = "rule-1";
    EventEntry event = new EventEntry();
    event.id = "event-1";
    MonsterEntry monster = new MonsterEntry();
    monster.id = "monster-1";
    WarriorEntry warrior = new WarriorEntry();
    warrior.id = "warrior-1";
    LocationEntry location = new LocationEntry();
    location.id = "location-1";

    assertEquals("rule-1", EditorSupport.extractId(rule));
    assertEquals("event-1", EditorSupport.extractId(event));
    assertEquals("monster-1", EditorSupport.extractId(monster));
    assertEquals("warrior-1", EditorSupport.extractId(warrior));
    assertEquals("location-1", EditorSupport.extractId(location));
  }

  @Test
  void extractIdIsEmptyForOtherObjectsNullAndMissingIds() {
    MonsterEntry withoutId = new MonsterEntry();
    withoutId.id = null;

    assertEquals("", EditorSupport.extractId("not an entry"));
    assertEquals("", EditorSupport.extractId(null));
    assertEquals("", EditorSupport.extractId(withoutId));
  }
}
