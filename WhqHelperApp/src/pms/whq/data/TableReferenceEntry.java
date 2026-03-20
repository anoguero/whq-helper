package pms.whq.data;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Node;

import pms.whq.util.XMLUtil;

public class TableReferenceEntry {

  public String tableName;
  public int level;
  public int targetLevel;
  public int times;
  public List<String> ambiences;

  public TableReferenceEntry() {
    tableName = "";
    level = 1;
    targetLevel = 1;
    times = 1;
    ambiences = new ArrayList<>();
  }

  public TableReferenceEntry(Node node) {
    this();
    tableName = XMLUtil.getAttribute(node, "name");
    level = parsePositiveInt(XMLUtil.getAttribute(node, "level"), 1);
    targetLevel = parsePositiveInt(XMLUtil.getAttribute(node, "targetLevel"), level);
    times = parsePositiveInt(XMLUtil.getAttribute(node, "times"), 1);

    String ambienceList = XMLUtil.getAttribute(node, "ambiences");
    if (ambienceList == null || ambienceList.isBlank()) {
      return;
    }

    for (String ambience : ambienceList.trim().split("\\s+")) {
      if (!ambience.isBlank()) {
        ambiences.add(ambience);
      }
    }
  }

  private int parsePositiveInt(String rawValue, int fallback) {
    try {
      int parsed = Integer.parseInt(rawValue);
      return parsed > 0 ? parsed : fallback;
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }
}
