/*
 * EventEntry.java
 *
 * Created on September 28, 2005, 9:32 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package pms.whq.data;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.*;

import pms.whq.util.XMLUtil;

/**
 *
 * @author psiegel
 */
public class EventEntry {
  
  public String       id;
  public List<String> ambiences;
  
  /** Creates a new instance of EventEntry */
  public EventEntry(String id) {
    this.id = id;
    this.ambiences = new ArrayList<>();
  }
  
  public EventEntry(Node node) {
    this(XMLUtil.getAttribute(node, "id"));
    String ambienceList = XMLUtil.getAttribute(node, "ambiences");
    if (ambienceList != null && !ambienceList.isBlank()) {
      for (String ambience : ambienceList.trim().split("\\s+")) {
        ambiences.add(ambience);
      }
    }
  }
}
