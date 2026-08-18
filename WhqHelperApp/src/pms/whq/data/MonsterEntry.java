/*
 * MonsterEntry.java
 *
 * Created on September 25, 2005, 6:28 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package pms.whq.data;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.*;
import java.util.concurrent.ThreadLocalRandom;

import pms.whq.util.XMLUtil;

/**
 *
 * @author psiegel
 */
public final class MonsterEntry extends SpecialContainer implements DrawableEntry {
  
  public String       id;
  public int          min;
  public int          max;
  public int          level;
  public List<String> ambiences;
  
  public boolean      appendSpecials = true;
  
  /** Creates a new instance of MonsterEntry */
  public MonsterEntry(String id, int min, int max) {
    super();
    this.id = id;
    this.min = min;
    this.max = max;
    this.level = 1;
    this.ambiences = new ArrayList<>();
  }
  
  public MonsterEntry(Node node) {
    super(node);
    ambiences = new ArrayList<>();
    
    id = XMLUtil.getAttribute(node, "id");    
    String number = XMLUtil.getAttribute(node, "number");
    String levelValue = XMLUtil.getAttribute(node, "level");
    String ambienceList = XMLUtil.getAttribute(node, "ambiences");
    if (ambienceList != null && !ambienceList.isBlank()) {
      for (String ambience : ambienceList.trim().split("\\s+")) {
        ambiences.add(ambience);
      }
    }
    
    Node special = XMLUtil.getNamedChild(node, "special");
    if (special != null) {
      // Coherente con la ausencia total de <special> (que mantiene appendSpecials=true):
      // un <special> sin atributo "append" conserva el valor por defecto y solo se desactiva
      // cuando el atributo indica explicitamente "false".
      String append = XMLUtil.getAttribute(special, "append");
      appendSpecials = append == null || append.isBlank() || Boolean.parseBoolean(append);
    }
    
    min = max = 0;
    level = 1;

    try {
      int parsedLevel = Integer.parseInt(levelValue);
      level = Math.max(1, Math.min(10, parsedLevel));
    } catch (NumberFormatException nfe) {
      level = 1;
    }
    
    try {
      if (number.indexOf('-') > -1) {
        String[] values = number.split("-");
        min = Integer.parseInt(values[0]);
        max = Integer.parseInt(values[1]);
      } else {
        min = max = Integer.parseInt(number);
      }
    } catch (NumberFormatException nfe) {
      //  Just make sure the range remains at 0
      min = max = 0;
    }
  }

  public int getNumber() {
    if (max <= min) {
      return min;
    }
    return ThreadLocalRandom.current().nextInt(min, max + 1);
  }
  
}
