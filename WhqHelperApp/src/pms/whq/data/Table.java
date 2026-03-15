package pms.whq.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import pms.whq.game.TableDrawService;
import pms.whq.util.XMLUtil;

public class Table implements EventList {

  private static final TableDrawService DRAW_SERVICE = new TableDrawService();

  private String name;
  private String kind;
  private final List<Object> monsters;
  private final List<Object> events;
  private boolean active;

  public Table() {
    name = "Table";
    kind = "";
    monsters = new ArrayList<>();
    events = new ArrayList<>();
    active = true;
  }

  public Table(Node node) {
    this();
    name = XMLUtil.getAttribute(node, "name");
    kind = XMLUtil.getAttribute(node, "kind");

    NodeList children = node.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      addNodeEntry(children.item(i));
    }
  }

  @Override
  public void addEntry(Object entry) {
    if (entry instanceof MonsterEntry || entry instanceof List<?>) {
      monsters.add(entry);
    } else if (entry instanceof EventEntry) {
      events.add(entry);
    }
  }

  @Override
  public void addEntries(Collection<Object> entries) {
    for (Object entry : entries) {
      addEntry(entry);
    }
  }

  public List<Object> getEntries() {
    List<Object> list = new ArrayList<>(monsters.size() + events.size());
    list.addAll(monsters);
    list.addAll(events);
    return list;
  }

  public List<Object> getMonsterEntries() {
    return monsters;
  }

  public List<Object> getEventEntries() {
    return events;
  }

  private void addNodeEntry(Node node) {
    String type = node.getNodeName();
    if ("group".equals(type)) {
      MonsterGroup entryList = new MonsterGroup();
      entryList.level = parseLevel(XMLUtil.getAttribute(node, "level"));

      NodeList entryNodes = node.getChildNodes();
      for (int i = 0; i < entryNodes.getLength(); i++) {
        Object entry = nodeToEntry(entryNodes.item(i));
        if (entry != null) {
          entryList.add(entry);
        }
      }
      monsters.add(entryList);
    } else if ("monster".equals(type)) {
      monsters.add(nodeToEntry(node));
    } else if ("event".equals(type)) {
      events.add(nodeToEntry(node));
    }
  }

  private Object nodeToEntry(Node node) {
    String type = node.getNodeName();
    if ("monster".equals(type)) {
      return new MonsterEntry(node);
    }
    if ("event".equals(type)) {
      return new EventEntry(node);
    }
    return null;
  }

  private int parseLevel(String rawValue) {
    try {
      int parsed = Integer.parseInt(rawValue);
      return Math.max(1, Math.min(10, parsed));
    } catch (NumberFormatException ignored) {
      return 1;
    }
  }

  @Override
  public int size() {
    return monsters.size() + events.size();
  }

  public String getName() {
    return name;
  }

  public String getKind() {
    return kind;
  }

  public TableKind getTableKind() {
    return TableKind.fromValue(kind);
  }

  public void setKind(String kind) {
    this.kind = kind == null ? "" : kind;
  }

  public void setTableKind(TableKind kind) {
    this.kind = kind == null ? "" : kind.storageValue();
  }

  @Override
  public Object getEntry() {
    return DRAW_SERVICE.drawEntry(this);
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
