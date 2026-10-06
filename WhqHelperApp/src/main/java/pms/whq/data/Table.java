package pms.whq.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import pms.whq.game.TableDrawService;
import pms.whq.util.XMLUtil;

public class Table implements EventList {

  private static final TableDrawService DRAW_SERVICE = new TableDrawService();
  private static final Map<String, Table> TABLE_REGISTRY = new ConcurrentHashMap<>();

  private String id;
  private String name;
  private String kind;
  private final List<DrawableEntry> monsters;
  private final List<DrawableEntry> events;
  private boolean active;

  public Table() {
    id = "";
    name = "Table";
    kind = "";
    monsters = new ArrayList<>();
    events = new ArrayList<>();
    active = true;
  }

  public Table(Node node) {
    this();
    id = XMLUtil.getAttribute(node, "id").trim();
    name = XMLUtil.getAttribute(node, "name");
    kind = XMLUtil.getAttribute(node, "kind");

    NodeList children = node.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      addNodeEntry(children.item(i));
    }
  }

  @Override
  public void addEntry(DrawableEntry entry) {
    switch (entry) {
      case MonsterEntry monsterEntry -> monsters.add(monsterEntry);
      case MonsterGroup group -> monsters.add(group);
      case TableReferenceEntry tableReferenceEntry -> monsters.add(tableReferenceEntry);
      case EventEntry eventEntry -> events.add(eventEntry);
      case null -> {
        // Se ignoran las entradas nulas, igual que hacia el instanceof original.
      }
    }
  }

  @Override
  public void addEntries(Collection<DrawableEntry> entries) {
    for (DrawableEntry entry : entries) {
      addEntry(entry);
    }
  }

  public List<DrawableEntry> getEntries() {
    List<DrawableEntry> list = new ArrayList<>(monsters.size() + events.size());
    list.addAll(monsters);
    list.addAll(events);
    return list;
  }

  public List<DrawableEntry> getMonsterEntries() {
    return monsters;
  }

  public List<DrawableEntry> getEventEntries() {
    return events;
  }

  private void addNodeEntry(Node node) {
    String type = node.getNodeName();
    if ("group".equals(type)) {
      MonsterGroup entryList = new MonsterGroup();
      entryList.level = parseLevel(XMLUtil.getAttribute(node, "level"));

      NodeList entryNodes = node.getChildNodes();
      for (int i = 0; i < entryNodes.getLength(); i++) {
        DrawableEntry entry = nodeToEntry(entryNodes.item(i));
        if (entry != null) {
          entryList.add(entry);
        }
      }
      monsters.add(entryList);
    } else if ("monster".equals(type)) {
      monsters.add(nodeToEntry(node));
    } else if ("tableRef".equals(type)) {
      monsters.add(new TableReferenceEntry(node));
    } else if ("event".equals(type)) {
      events.add(nodeToEntry(node));
    }
  }

  private DrawableEntry nodeToEntry(Node node) {
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

  /** Id estable de la tabla; si el XML no lo trae, el slug de su nombre (ver {@link TableIds}). */
  public String getId() {
    return id.isEmpty() ? TableIds.fromName(name) : id;
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
  public DrawableEntry getEntry() {
    return DRAW_SERVICE.drawEntry(this);
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public static void registerAll(Map<String, Table> tables) {
    TABLE_REGISTRY.clear();
    if (tables != null) {
      TABLE_REGISTRY.putAll(tables);
    }
  }

  public static Table findRegistered(String name) {
    if (name == null || name.isBlank()) {
      return null;
    }
    return TABLE_REGISTRY.get(name);
  }
}
