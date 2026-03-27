package pms.whq.xml;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XmlContentService {

  private static final String MONSTER_SCHEMA = "whq-monster-schema.xsd";
  private static final String EVENT_SCHEMA = "whq-events-schema.xsd";
  private static final String RULE_SCHEMA = "whq-rules-schema.xsd";
  private static final String TABLE_SCHEMA = "whq-tables-schema.xsd";
  private static final String MONSTERS_DIR = "data/xml/monsters";
  private static final String EVENTS_DIR = "data/xml/events";
  private static final String RULES_DIR = "data/xml/rules";
  private static final String TABLES_DIR = "data/xml/tables";
  private static final String TRAVEL_DIR = "data/xml/travel";
  private static final String SETTLEMENT_DIR = "data/xml/settlement";

  private final Path projectRoot;
  private final DocumentBuilderFactory dbf;

  private enum EventScope {
    DUNGEON,
    TRAVEL,
    SETTLEMENT
  }

  public XmlContentService(Path projectRoot) {
    this.projectRoot = projectRoot.toAbsolutePath().normalize();
    this.dbf = DocumentBuilderFactory.newInstance();
    this.dbf.setNamespaceAware(true);
  }

  public List<Path> listMonsterFiles() throws IOException {
    return listXmlFiles(projectRoot.resolve(MONSTERS_DIR));
  }

  public List<Path> listEventFiles() throws IOException {
    return listXmlFiles(projectRoot.resolve(EVENTS_DIR));
  }

  public List<Path> listRuleFiles() throws IOException {
    return listXmlFiles(projectRoot.resolve(RULES_DIR));
  }

  public List<Path> listTableFiles() throws IOException {
    return listXmlFiles(projectRoot.resolve(TABLES_DIR));
  }

  public List<Path> listTravelFiles() throws IOException {
    return listXmlFiles(projectRoot.resolve(TRAVEL_DIR));
  }

  public List<Path> listSettlementFiles() throws IOException {
    return listXmlFiles(projectRoot.resolve(SETTLEMENT_DIR));
  }

  public Path getRulesDirectory() {
    return projectRoot.resolve(RULES_DIR);
  }

  public Path getEventsDirectory() {
    return projectRoot.resolve(EVENTS_DIR);
  }

  public Path getTravelDirectory() {
    return projectRoot.resolve(TRAVEL_DIR);
  }

  public Path getSettlementDirectory() {
    return projectRoot.resolve(SETTLEMENT_DIR);
  }

  public Path getMonstersDirectory() {
    return projectRoot.resolve(MONSTERS_DIR);
  }

  public Path getTablesDirectory() {
    return projectRoot.resolve(TABLES_DIR);
  }

  private List<Path> listXmlFiles(Path directory) throws IOException {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    List<Path> files = new ArrayList<>();
    try (var stream = Files.list(directory)) {
      stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".xml"))
          .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
          .forEach(files::add);
    }
    return files;
  }

  public List<RuleEntry> loadRules(Path file) throws Exception {
    Document doc = parse(file);
    Element root = doc.getDocumentElement();
    List<RuleEntry> entries = new ArrayList<>();
    NodeList children = root.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node node = children.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      Element element = (Element) node;
      if (!"rule".equals(element.getTagName()) && !"magic".equals(element.getTagName())) {
        continue;
      }
      RuleEntry entry = new RuleEntry();
      entry.type = element.getTagName();
      entry.id = element.getAttribute("id");
      entry.name = element.getAttribute("name");
      entry.parameterName = element.getAttribute("parameterName");
      entry.parameterNames = element.getAttribute("parameterNames");
      entry.parameterFormat = element.getAttribute("parameterFormat");
      entry.text = element.getTextContent() == null ? "" : element.getTextContent().trim();
      entries.add(entry);
    }
    return entries;
  }

  public void saveRules(Path file, List<RuleEntry> entries) throws Exception {
    validateRuleEntries(entries);

    Document doc = newDocument("rules", RULE_SCHEMA);
    Element root = doc.getDocumentElement();
    for (RuleEntry entry : entries) {
      validateRequired(entry.id, "id");
      validateRequired(entry.name, "name");

      String tag = "magic".equals(entry.type) ? "magic" : "rule";
      Element node = doc.createElement(tag);
      node.setAttribute("id", entry.id.trim());
      node.setAttribute("name", entry.name.trim());
      if (entry.parameterName != null && !entry.parameterName.trim().isEmpty()) {
        node.setAttribute("parameterName", entry.parameterName.trim());
      }
      if (entry.parameterNames != null && !entry.parameterNames.trim().isEmpty()) {
        node.setAttribute("parameterNames", entry.parameterNames.trim());
      }
      if (entry.parameterFormat != null && !entry.parameterFormat.trim().isEmpty()) {
        node.setAttribute("parameterFormat", entry.parameterFormat.trim());
      }
      node.setTextContent(entry.text == null ? "" : entry.text.trim());
      root.appendChild(node);
    }

    writeDocument(file, doc, file.getParent().resolve(RULE_SCHEMA));
  }

  public Path createEmptyRulesFile(Path file) throws Exception {
    return createEmptyXmlFile(file, getRulesDirectory(), "rules", getRulesDirectory().resolve(RULE_SCHEMA));
  }

  public List<EventEntry> loadEvents(Path file) throws Exception {
    Document doc = parse(file);
    Element root = doc.getDocumentElement();
    List<EventEntry> entries = new ArrayList<>();
    NodeList children = root.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node node = children.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE || !"event".equals(node.getNodeName())) {
        continue;
      }

      Element eventElement = (Element) node;
      EventEntry entry = new EventEntry();
      entry.id = eventElement.getAttribute("id");
      entry.name = eventElement.getAttribute("name");
      entry.flavor = getChildText(eventElement, "flavor");
      entry.rules = getChildText(eventElement, "rules");
      entry.special = getChildText(eventElement, "special");
      entry.goldValue = getChildText(eventElement, "goldValue");
      entry.users = getChildText(eventElement, "users");
      String treasure = getChildText(eventElement, "treasure");
      entry.treasure = Boolean.parseBoolean(treasure);
      entries.add(entry);
    }
    return entries;
  }

  public void saveEvents(Path file, List<EventEntry> entries) throws Exception {
    validateEventEntries(entries);

    Document doc = newDocument("events", EVENT_SCHEMA);
    Element root = doc.getDocumentElement();
    for (EventEntry entry : entries) {
      validateRequired(entry.id, "id");
      validateRequired(entry.name, "name");

      Element node = doc.createElement("event");
      node.setAttribute("id", entry.id.trim());
      node.setAttribute("name", entry.name.trim());
      appendOptionalText(doc, node, "flavor", entry.flavor);
      appendRequiredText(doc, node, "rules", entry.rules);
      appendOptionalText(doc, node, "special", entry.special);
      appendOptionalText(doc, node, "goldValue", entry.goldValue);
      appendOptionalText(doc, node, "users", entry.users);
      appendRequiredText(doc, node, "treasure", Boolean.toString(entry.treasure));
      root.appendChild(node);
    }

    writeDocument(file, doc, eventSchemaPath());
  }

  public Path createEmptyEventsFile(Path file) throws Exception {
    return createEmptyXmlFile(file, getEventsDirectory(), "events", eventSchemaPath());
  }

  public Path createEmptyTravelFile(Path file) throws Exception {
    return createEmptyXmlFile(file, getTravelDirectory(), "events", eventSchemaPath());
  }

  public Path createEmptySettlementFile(Path file) throws Exception {
    return createEmptyXmlFile(file, getSettlementDirectory(), "events", eventSchemaPath());
  }

  public List<MonsterEntry> loadMonsters(Path file) throws Exception {
    Document doc = parse(file);
    Element root = doc.getDocumentElement();
    List<MonsterEntry> entries = new ArrayList<>();
    NodeList children = root.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node node = children.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE || !"monster".equals(node.getNodeName())) {
        continue;
      }

      Element monsterElement = (Element) node;
      MonsterEntry entry = new MonsterEntry();
      entry.id = monsterElement.getAttribute("id");
      entry.name = monsterElement.getAttribute("name");
      entry.plural = monsterElement.getAttribute("plural");
      entry.factions = monsterElement.getAttribute("factions");
      entry.wounds = getChildText(monsterElement, "wounds");
      entry.move = getChildText(monsterElement, "move");
      entry.weaponSkill = getChildText(monsterElement, "weaponskill");
      entry.ballisticSkill = getChildText(monsterElement, "ballisticskill");
      entry.strength = getChildText(monsterElement, "strength");
      entry.toughness = getChildText(monsterElement, "toughness");
      entry.initiative = getChildText(monsterElement, "initiative");
      entry.attacks = getChildText(monsterElement, "attacks");
      entry.gold = getChildText(monsterElement, "gold");
      entry.armor = getChildText(monsterElement, "armor");
      entry.damage = getChildText(monsterElement, "damage");
      entry.specialEntriesRaw = specialNodeToRaw(monsterElement);
      entries.add(entry);
    }
    return entries;
  }

  public void saveMonsters(Path file, List<MonsterEntry> entries) throws Exception {
    validateMonsterEntries(entries);

    Document doc = newDocument("monsters", MONSTER_SCHEMA);
    Element root = doc.getDocumentElement();
    for (MonsterEntry entry : entries) {
      validateRequired(entry.id, "id");
      validateRequired(entry.name, "name");
      validateRequired(entry.plural, "plural");

      Element node = doc.createElement("monster");
      node.setAttribute("id", entry.id.trim());
      node.setAttribute("name", entry.name.trim());
      node.setAttribute("plural", entry.plural.trim());
      node.setAttribute("factions", entry.factions.trim());

      appendRequiredText(doc, node, "wounds", entry.wounds);
      appendRequiredText(doc, node, "move", entry.move);
      appendRequiredText(doc, node, "weaponskill", entry.weaponSkill);
      appendRequiredText(doc, node, "ballisticskill", entry.ballisticSkill);
      appendRequiredText(doc, node, "strength", entry.strength);
      appendRequiredText(doc, node, "toughness", entry.toughness);
      appendRequiredText(doc, node, "initiative", entry.initiative);
      appendRequiredText(doc, node, "attacks", entry.attacks);
      appendRequiredText(doc, node, "gold", entry.gold);
      appendRequiredText(doc, node, "armor", entry.armor);
      appendRequiredText(doc, node, "damage", entry.damage);

      Element special = doc.createElement("special");
      applySpecialRaw(doc, special, entry.specialEntriesRaw);
      node.appendChild(special);

      root.appendChild(node);
    }

    writeDocument(file, doc, file.getParent().resolve(MONSTER_SCHEMA));
  }

  public Path createEmptyMonstersFile(Path file) throws Exception {
    return createEmptyXmlFile(
        file, getMonstersDirectory(), "monsters", getMonstersDirectory().resolve(MONSTER_SCHEMA));
  }

  public void validateRulesFile(Path file) throws Exception {
    validateFile(file, file.getParent().resolve(RULE_SCHEMA));
    List<RuleEntry> entries = loadRules(file);
    validateRuleEntries(entries);
    ensureRuleIdsUniqueAcrossFiles(file, entries);

    RuleReferences references = buildRuleReferences(file, entries);
    validateSpecialReferencesAgainstRules(loadAllMonsterSpecialContexts(), references);
    validateSpecialReferencesAgainstRules(loadAllTableSpecialContexts(), references);
  }

  public void validateEventsFile(Path file) throws Exception {
    validateFile(file, eventSchemaPath());
    EventScope scope = resolveEventScope(file);
    List<EventEntry> entries = loadEvents(file);
    validateEventEntries(entries);
    ensureEventIdsUniqueAcrossFiles(file, entries, scope);
    ensureTableEventReferencesRemainValid(file, entries, scope);
  }

  public void validateMonstersFile(Path file) throws Exception {
    validateFile(file, file.getParent().resolve(MONSTER_SCHEMA));
    List<MonsterEntry> entries = loadMonsters(file);
    validateMonsterEntries(entries);
    ensureMonsterIdsUniqueAcrossFiles(file, entries);
    RuleReferences references = buildRuleReferences(null, null);
    validateMonsterSpecialReferences(entries, references);
    ensureTableMonsterReferencesRemainValid(file, entries);
  }

  public void validateTravelFile(Path file) throws Exception {
    validateEventsFile(file);
  }

  public void validateSettlementFile(Path file) throws Exception {
    validateEventsFile(file);
  }

  public TableFileModel loadTables(Path file) throws Exception {
    Document doc = parse(file);
    Element root = doc.getDocumentElement();
    TableFileModel model = new TableFileModel();
    NodeList tableNodes = root.getChildNodes();
    for (int i = 0; i < tableNodes.getLength(); i++) {
      Node tableNode = tableNodes.item(i);
      if (tableNode.getNodeType() != Node.ELEMENT_NODE || !"table".equals(tableNode.getNodeName())) {
        continue;
      }
      Element tableElement = (Element) tableNode;
      TableDefinition table = new TableDefinition();
      table.name = tableElement.getAttribute("name");
      table.kind = tableElement.getAttribute("kind");

      NodeList entries = tableElement.getChildNodes();
      for (int j = 0; j < entries.getLength(); j++) {
        Node entryNode = entries.item(j);
        if (entryNode.getNodeType() != Node.ELEMENT_NODE) {
          continue;
        }
        Element entryElement = (Element) entryNode;
        String tag = entryElement.getTagName();
        if ("monster".equals(tag)) {
          TableEntry entry = new TableEntry();
          entry.type = "monster";
          entry.id = entryElement.getAttribute("id");
          entry.number = entryElement.getAttribute("number");
          entry.level = entryElement.getAttribute("level");
          entry.ambiences = entryElement.getAttribute("ambiences");
          entry.specialRaw = specialNodeToRaw(entryElement);
          table.entries.add(entry);
        } else if ("event".equals(tag)) {
          TableEntry entry = new TableEntry();
          entry.type = "event";
          entry.id = entryElement.getAttribute("id");
          entry.ambiences = entryElement.getAttribute("ambiences");
          table.entries.add(entry);
        } else if ("group".equals(tag)) {
          TableEntry groupEntry = new TableEntry();
          groupEntry.type = "group";
          groupEntry.level = entryElement.getAttribute("level");
          NodeList monsters = entryElement.getChildNodes();
          for (int k = 0; k < monsters.getLength(); k++) {
            Node monsterNode = monsters.item(k);
            if (monsterNode.getNodeType() != Node.ELEMENT_NODE || !"monster".equals(monsterNode.getNodeName())) {
              continue;
            }
            Element monsterElement = (Element) monsterNode;
            TableGroupMember member = new TableGroupMember();
            member.id = monsterElement.getAttribute("id");
            member.number = monsterElement.getAttribute("number");
            member.ambiences = monsterElement.getAttribute("ambiences");
            member.specialRaw = specialNodeToRaw(monsterElement);
            groupEntry.groupMembers.add(member);
          }
          table.entries.add(groupEntry);
        }
      }
      model.tables.add(table);
    }
    return model;
  }

  public void saveTables(Path file, TableFileModel model) throws Exception {
    validateTableModel(model);

    Document doc = newDocument("tables", TABLE_SCHEMA);
    Element root = doc.getDocumentElement();
    for (TableDefinition table : model.tables) {
      validateRequired(table.name, "table.name");
      Element tableElement = doc.createElement("table");
      tableElement.setAttribute("name", table.name.trim());
      if (table.kind != null && !table.kind.trim().isEmpty()) {
        tableElement.setAttribute("kind", table.kind.trim());
      }

      if (table.entries.isEmpty()) {
        throw new IllegalArgumentException("Table '" + table.name + "' must contain at least one entry.");
      }

      for (TableEntry entry : table.entries) {
        String type = safe(entry.type);
        if ("monster".equals(type)) {
          validateRequired(entry.id, "monster.id");
          validateRequired(entry.number, "monster.number");
          Element monster = doc.createElement("monster");
          monster.setAttribute("id", entry.id.trim());
          monster.setAttribute("number", entry.number.trim());
          if (entry.level != null && !entry.level.trim().isEmpty()) {
            monster.setAttribute("level", entry.level.trim());
          }
          if (entry.ambiences != null && !entry.ambiences.trim().isEmpty()) {
            monster.setAttribute("ambiences", entry.ambiences.trim());
          }
          if (entry.specialRaw != null && !entry.specialRaw.isBlank()) {
            Element special = doc.createElement("special");
            applySpecialRaw(doc, special, entry.specialRaw);
            monster.appendChild(special);
          }
          tableElement.appendChild(monster);
        } else if ("event".equals(type)) {
          validateRequired(entry.id, "event.id");
          Element event = doc.createElement("event");
          event.setAttribute("id", entry.id.trim());
          if (entry.ambiences != null && !entry.ambiences.trim().isEmpty()) {
            event.setAttribute("ambiences", entry.ambiences.trim());
          }
          tableElement.appendChild(event);
        } else if ("group".equals(type)) {
          if (entry.groupMembers == null || entry.groupMembers.isEmpty()) {
            throw new IllegalArgumentException("Group entries require at least one member.");
          }
          Element group = doc.createElement("group");
          if (entry.level != null && !entry.level.trim().isEmpty()) {
            group.setAttribute("level", entry.level.trim());
          }
          for (TableGroupMember member : entry.groupMembers) {
            validateRequired(member.id, "group.member.id");
            validateRequired(member.number, "group.member.number");
            Element monster = doc.createElement("monster");
            monster.setAttribute("id", member.id.trim());
            monster.setAttribute("number", member.number.trim());
            if (member.ambiences != null && !member.ambiences.trim().isEmpty()) {
              monster.setAttribute("ambiences", member.ambiences.trim());
            }
            if (member.specialRaw != null && !member.specialRaw.isBlank()) {
              Element special = doc.createElement("special");
              applySpecialRaw(doc, special, member.specialRaw);
              monster.appendChild(special);
            }
            group.appendChild(monster);
          }
          tableElement.appendChild(group);
        } else {
          throw new IllegalArgumentException("Unknown table entry type: " + type);
        }
      }

      root.appendChild(tableElement);
    }

    writeDocument(file, doc, file.getParent().resolve(TABLE_SCHEMA));
  }

  public Path createEmptyTablesFile(Path file) throws Exception {
    return createEmptyXmlFile(file, getTablesDirectory(), "tables", getTablesDirectory().resolve(TABLE_SCHEMA));
  }

  public void validateTablesFile(Path file) throws Exception {
    validateFile(file, file.getParent().resolve(TABLE_SCHEMA));
    TableFileModel model = loadTables(file);
    validateTableModel(model);
    ensureTableNamesUniqueAcrossFiles(file, model);

    RuleReferences references = buildRuleReferences(null, null);
    validateTablesAgainstKnownReferences(model, buildMonsterIdSet(null, null), buildEventIdSets(null, null), references);
  }

  private static void validateRuleEntries(List<RuleEntry> entries) {
    if (entries == null || entries.isEmpty()) {
      throw new IllegalArgumentException("Rules XML must contain at least one entry.");
    }

    Set<String> ids = new HashSet<>();
    for (RuleEntry entry : entries) {
      validateRequired(entry.id, "id");
      validateRequired(entry.name, "name");
      String id = safe(entry.id);
      if (!ids.add(id)) {
        throw new IllegalArgumentException("Duplicate rule ID in file: " + id);
      }

      String type = safe(entry.type);
      if (!"rule".equals(type) && !"magic".equals(type)) {
        throw new IllegalArgumentException("Unknown rule type: " + type);
      }
    }
  }

  private static void validateEventEntries(List<EventEntry> entries) {
    if (entries == null || entries.isEmpty()) {
      throw new IllegalArgumentException("Events XML must contain at least one entry.");
    }

    Set<String> ids = new HashSet<>();
    for (EventEntry entry : entries) {
      validateRequired(entry.id, "id");
      validateRequired(entry.name, "name");
      validateRequired(entry.rules, "rules");
      String id = safe(entry.id);
      if (!ids.add(id)) {
        throw new IllegalArgumentException("Duplicate event ID in file: " + id);
      }
    }
  }

  private void validateMonsterEntries(List<MonsterEntry> entries) throws Exception {
    if (entries == null || entries.isEmpty()) {
      throw new IllegalArgumentException("Monsters XML must contain at least one entry.");
    }

    Set<String> ids = new HashSet<>();
    for (MonsterEntry entry : entries) {
      validateRequired(entry.id, "id");
      validateRequired(entry.name, "name");
      validateRequired(entry.plural, "plural");
      validateRequired(entry.wounds, "wounds");
      validateRequired(entry.move, "move");
      validateRequired(entry.weaponSkill, "weaponskill");
      validateRequired(entry.ballisticSkill, "ballisticskill");
      validateRequired(entry.strength, "strength");
      validateRequired(entry.toughness, "toughness");
      validateRequired(entry.initiative, "initiative");
      validateRequired(entry.attacks, "attacks");
      validateRequired(entry.gold, "gold");
      validateRequired(entry.armor, "armor");
      validateRequired(entry.damage, "damage");

      String id = safe(entry.id);
      if (!ids.add(id)) {
        throw new IllegalArgumentException("Duplicate monster ID in file: " + id);
      }
      parseSpecialDirectives(entry.specialEntriesRaw, "monster '" + id + "'");
    }
  }

  private void validateTableModel(TableFileModel model) throws Exception {
    if (model == null || model.tables == null || model.tables.isEmpty()) {
      throw new IllegalArgumentException("Tables XML must contain at least one table.");
    }

    Set<String> tableNames = new HashSet<>();
    for (TableDefinition table : model.tables) {
      validateRequired(table.name, "table.name");
      String tableName = safe(table.name);
      if (!tableNames.add(tableName)) {
        throw new IllegalArgumentException("Duplicate table name in file: " + tableName);
      }
      if (table.entries == null || table.entries.isEmpty()) {
        throw new IllegalArgumentException("Table '" + tableName + "' must contain at least one entry.");
      }

      for (TableEntry entry : table.entries) {
        String type = safe(entry.type);
        if ("monster".equals(type)) {
          validateRequired(entry.id, "monster.id");
          validateRequired(entry.number, "monster.number");
          parseSpecialDirectives(entry.specialRaw, "table '" + tableName + "' monster '" + safe(entry.id) + "'");
        } else if ("event".equals(type)) {
          validateRequired(entry.id, "event.id");
        } else if ("group".equals(type)) {
          if (entry.groupMembers == null || entry.groupMembers.isEmpty()) {
            throw new IllegalArgumentException(
                "Table '" + tableName + "' has a group entry without members.");
          }
          for (TableGroupMember member : entry.groupMembers) {
            validateRequired(member.id, "group.member.id");
            validateRequired(member.number, "group.member.number");
            parseSpecialDirectives(
                member.specialRaw,
                "table '" + tableName + "' group member '" + safe(member.id) + "'");
          }
        } else {
          throw new IllegalArgumentException(
              "Unknown table entry type in table '" + tableName + "': " + type);
        }
      }
    }
  }

  private void ensureRuleIdsUniqueAcrossFiles(Path targetFile, List<RuleEntry> entries) throws Exception {
    Set<String> ids = new HashSet<>();
    for (RuleEntry entry : entries) {
      ids.add(safe(entry.id));
    }

    Path normalizedTarget = normalize(targetFile);
    for (Path file : listRuleFiles()) {
      if (samePath(file, normalizedTarget)) {
        continue;
      }
      for (RuleEntry existing : loadRules(file)) {
        String id = safe(existing.id);
        if (ids.contains(id)) {
          throw new IllegalArgumentException(
              "Rule ID '" + id + "' already exists in file '" + file.getFileName() + "'.");
        }
      }
    }
  }

  private void ensureEventIdsUniqueAcrossFiles(Path targetFile, List<EventEntry> entries, EventScope scope)
      throws Exception {
    Set<String> ids = new HashSet<>();
    for (EventEntry entry : entries) {
      ids.add(safe(entry.id));
    }

    Path normalizedTarget = normalize(targetFile);
    for (Path file : listEventFiles(scope)) {
      if (samePath(file, normalizedTarget)) {
        continue;
      }
      for (EventEntry existing : loadEvents(file)) {
        String id = safe(existing.id);
        if (ids.contains(id)) {
          throw new IllegalArgumentException(
              "Event ID '" + id + "' already exists in file '" + file.getFileName() + "'.");
        }
      }
    }
  }

  private void ensureMonsterIdsUniqueAcrossFiles(Path targetFile, List<MonsterEntry> entries) throws Exception {
    Set<String> ids = new HashSet<>();
    for (MonsterEntry entry : entries) {
      ids.add(safe(entry.id));
    }

    Path normalizedTarget = normalize(targetFile);
    for (Path file : listMonsterFiles()) {
      if (samePath(file, normalizedTarget)) {
        continue;
      }
      for (MonsterEntry existing : loadMonsters(file)) {
        String id = safe(existing.id);
        if (ids.contains(id)) {
          throw new IllegalArgumentException(
              "Monster ID '" + id + "' already exists in file '" + file.getFileName() + "'.");
        }
      }
    }
  }

  private void ensureTableNamesUniqueAcrossFiles(Path targetFile, TableFileModel model) throws Exception {
    Set<String> names = new HashSet<>();
    for (TableDefinition table : model.tables) {
      names.add(safe(table.name));
    }

    Path normalizedTarget = normalize(targetFile);
    for (Path file : listTableFiles()) {
      if (samePath(file, normalizedTarget)) {
        continue;
      }
      TableFileModel existingModel = loadTables(file);
      for (TableDefinition table : existingModel.tables) {
        String tableName = safe(table.name);
        if (names.contains(tableName)) {
          throw new IllegalArgumentException(
              "Table name '" + tableName + "' already exists in file '" + file.getFileName() + "'.");
        }
      }
    }
  }

  private void ensureTableMonsterReferencesRemainValid(Path targetFile, List<MonsterEntry> replacementEntries)
      throws Exception {
    Set<String> monsterIds = buildMonsterIdSet(targetFile, replacementEntries);
    for (Path file : listTableFiles()) {
      TableFileModel model = loadTables(file);
      for (TableDefinition table : model.tables) {
        for (TableEntry entry : table.entries) {
          if ("monster".equals(safe(entry.type))) {
            String id = safe(entry.id);
            if (!monsterIds.contains(id)) {
              throw new IllegalArgumentException(
                  "Table '" + safe(table.name) + "' references missing monster ID '" + id + "'.");
            }
          } else if ("group".equals(safe(entry.type))) {
            for (TableGroupMember member : entry.groupMembers) {
              String id = safe(member.id);
              if (!monsterIds.contains(id)) {
                throw new IllegalArgumentException(
                    "Table '"
                        + safe(table.name)
                        + "' references missing monster ID '"
                        + id
                        + "' in a group entry.");
              }
            }
          }
        }
      }
    }
  }

  private void ensureTableEventReferencesRemainValid(
      Path targetFile, List<EventEntry> replacementEntries, EventScope scope) throws Exception {
    EventIds eventIds = buildEventIdSets(targetFile, replacementEntries);
    Set<String> scopedIds = eventIds.get(scope);

    for (Path file : listTableFiles()) {
      TableFileModel model = loadTables(file);
      for (TableDefinition table : model.tables) {
        if (scopeFromTableKind(table.kind) != scope) {
          continue;
        }
        for (TableEntry entry : table.entries) {
          if (!"event".equals(safe(entry.type))) {
            continue;
          }
          String id = safe(entry.id);
          if (!scopedIds.contains(id)) {
            throw new IllegalArgumentException(
                "Table '" + safe(table.name) + "' references missing event ID '" + id + "'.");
          }
        }
      }
    }
  }

  private void validateTablesAgainstKnownReferences(
      TableFileModel model, Set<String> monsterIds, EventIds eventIds, RuleReferences ruleReferences)
      throws Exception {
    for (TableDefinition table : model.tables) {
      EventScope scope = scopeFromTableKind(table.kind);
      Set<String> availableEvents = eventIds.get(scope);
      String tableName = safe(table.name);
      for (TableEntry entry : table.entries) {
        String type = safe(entry.type);
        if ("monster".equals(type)) {
          String id = safe(entry.id);
          if (!monsterIds.contains(id)) {
            throw new IllegalArgumentException(
                "Table '" + tableName + "' references unknown monster ID '" + id + "'.");
          }
          validateSpecialRawReferences(
              entry.specialRaw,
              ruleReferences,
              "table '" + tableName + "' monster '" + id + "'");
        } else if ("event".equals(type)) {
          String id = safe(entry.id);
          if (!availableEvents.contains(id)) {
            throw new IllegalArgumentException(
                "Table '" + tableName + "' references unknown event ID '" + id + "'.");
          }
        } else if ("group".equals(type)) {
          for (TableGroupMember member : entry.groupMembers) {
            String id = safe(member.id);
            if (!monsterIds.contains(id)) {
              throw new IllegalArgumentException(
                  "Table '" + tableName + "' references unknown monster ID '" + id + "' in a group entry.");
            }
            validateSpecialRawReferences(
                member.specialRaw,
                ruleReferences,
                "table '" + tableName + "' group member '" + id + "'");
          }
        }
      }
    }
  }

  private void validateMonsterSpecialReferences(List<MonsterEntry> entries, RuleReferences references)
      throws Exception {
    for (MonsterEntry entry : entries) {
      validateSpecialRawReferences(
          entry.specialEntriesRaw, references, "monster '" + safe(entry.id) + "'");
    }
  }

  private List<SpecialContext> loadAllMonsterSpecialContexts() throws Exception {
    List<SpecialContext> contexts = new ArrayList<>();
    for (Path file : listMonsterFiles()) {
      for (MonsterEntry entry : loadMonsters(file)) {
        contexts.add(
            new SpecialContext(
                "monster '" + safe(entry.id) + "' in file '" + file.getFileName() + "'",
                entry.specialEntriesRaw));
      }
    }
    return contexts;
  }

  private List<SpecialContext> loadAllTableSpecialContexts() throws Exception {
    List<SpecialContext> contexts = new ArrayList<>();
    for (Path file : listTableFiles()) {
      TableFileModel model = loadTables(file);
      for (TableDefinition table : model.tables) {
        String tableName = safe(table.name);
        for (TableEntry entry : table.entries) {
          if ("monster".equals(safe(entry.type))) {
            contexts.add(
                new SpecialContext(
                    "table '" + tableName + "' monster '" + safe(entry.id) + "' in file '" + file.getFileName() + "'",
                    entry.specialRaw));
          } else if ("group".equals(safe(entry.type))) {
            for (TableGroupMember member : entry.groupMembers) {
              contexts.add(
                  new SpecialContext(
                      "table '"
                          + tableName
                          + "' group member '"
                          + safe(member.id)
                          + "' in file '"
                          + file.getFileName()
                          + "'",
                      member.specialRaw));
            }
          }
        }
      }
    }
    return contexts;
  }

  private void validateSpecialReferencesAgainstRules(List<SpecialContext> contexts, RuleReferences references)
      throws Exception {
    for (SpecialContext context : contexts) {
      try {
        validateSpecialRawReferences(context.raw, references, context.context);
      } catch (IllegalArgumentException ex) {
        String message = safe(ex.getMessage()).toLowerCase(Locale.ROOT);
        if (!message.contains("invalid special line")
            && !message.contains("invalid rule special line")
            && !message.contains("invalid magic special line")) {
          throw ex;
        }
      }
    }
  }

  private void validateSpecialRawReferences(String raw, RuleReferences references, String context)
      throws Exception {
    for (SpecialDirective directive : parseSpecialDirectives(raw, context)) {
      if ("rule".equals(directive.type) && !references.ruleIds.contains(directive.id)) {
        throw new IllegalArgumentException(
            "Unknown rule ID '" + directive.id + "' in " + context + ".");
      }
      if ("magic".equals(directive.type) && !references.magicIds.contains(directive.id)) {
        throw new IllegalArgumentException(
            "Unknown magic ID '" + directive.id + "' in " + context + ".");
      }
    }
  }

  private RuleReferences buildRuleReferences(Path targetFile, List<RuleEntry> replacementEntries)
      throws Exception {
    RuleReferences references = new RuleReferences();
    Path normalizedTarget = normalize(targetFile);
    for (Path file : listRuleFiles()) {
      List<RuleEntry> source;
      if (replacementEntries != null && samePath(file, normalizedTarget)) {
        source = replacementEntries;
      } else {
        source = loadRules(file);
      }
      for (RuleEntry entry : source) {
        String id = safe(entry.id);
        references.ruleIds.add(id);
        if ("magic".equals(safe(entry.type))) {
          references.magicIds.add(id);
        }
      }
    }
    return references;
  }

  private Set<String> buildMonsterIdSet(Path targetFile, List<MonsterEntry> replacementEntries)
      throws Exception {
    Set<String> ids = new HashSet<>();
    Path normalizedTarget = normalize(targetFile);
    for (Path file : listMonsterFiles()) {
      List<MonsterEntry> source;
      if (replacementEntries != null && samePath(file, normalizedTarget)) {
        source = replacementEntries;
      } else {
        source = loadMonsters(file);
      }
      for (MonsterEntry entry : source) {
        ids.add(safe(entry.id));
      }
    }
    return ids;
  }

  private EventIds buildEventIdSets(Path targetFile, List<EventEntry> replacementEntries) throws Exception {
    EventIds ids = new EventIds();
    Path normalizedTarget = normalize(targetFile);
    EventScope targetScope =
        replacementEntries == null || targetFile == null ? null : resolveEventScope(targetFile);

    for (EventScope scope : EventScope.values()) {
      for (Path file : listEventFiles(scope)) {
        List<EventEntry> source;
        if (targetScope == scope && replacementEntries != null && samePath(file, normalizedTarget)) {
          source = replacementEntries;
        } else {
          source = loadEvents(file);
        }
        for (EventEntry entry : source) {
          ids.get(scope).add(safe(entry.id));
        }
      }
    }
    return ids;
  }

  private EventScope resolveEventScope(Path file) {
    Path normalized = normalize(file);
    if (normalized == null) {
      return EventScope.DUNGEON;
    }
    if (normalized.startsWith(normalize(projectRoot.resolve(TRAVEL_DIR)))) {
      return EventScope.TRAVEL;
    }
    if (normalized.startsWith(normalize(projectRoot.resolve(SETTLEMENT_DIR)))) {
      return EventScope.SETTLEMENT;
    }
    return EventScope.DUNGEON;
  }

  private static EventScope scopeFromTableKind(String kind) {
    String normalized = safe(kind).toLowerCase(Locale.ROOT);
    if ("travel".equals(normalized)) {
      return EventScope.TRAVEL;
    }
    if ("settlement".equals(normalized)) {
      return EventScope.SETTLEMENT;
    }
    return EventScope.DUNGEON;
  }

  private List<Path> listEventFiles(EventScope scope) throws IOException {
    if (scope == EventScope.TRAVEL) {
      return listTravelFiles();
    }
    if (scope == EventScope.SETTLEMENT) {
      return listSettlementFiles();
    }
    return listEventFiles();
  }

  private static Path normalize(Path path) {
    return path == null ? null : path.toAbsolutePath().normalize();
  }

  private Path createEmptyXmlFile(Path file, Path expectedDirectory, String rootName, Path schemaPath)
      throws Exception {
    Path normalizedDirectory = normalize(expectedDirectory);
    if (normalizedDirectory == null) {
      throw new IOException("Target directory not configured.");
    }
    Files.createDirectories(normalizedDirectory);

    Path normalizedFile = normalize(file);
    if (normalizedFile == null) {
      throw new IOException("Target file not provided.");
    }
    if (!normalizedFile.startsWith(normalizedDirectory)) {
      throw new IOException("The XML file must be created inside " + normalizedDirectory + ".");
    }

    String fileName = normalizedFile.getFileName() == null ? "" : normalizedFile.getFileName().toString().trim();
    if (fileName.isEmpty()) {
      throw new IOException("The XML file name is required.");
    }
    if (!fileName.toLowerCase(Locale.ROOT).endsWith(".xml")) {
      normalizedFile = normalizedFile.resolveSibling(fileName + ".xml");
      fileName = normalizedFile.getFileName().toString();
    }
    if (!fileName.toLowerCase(Locale.ROOT).startsWith("userdefined-")) {
      normalizedFile = normalizedFile.resolveSibling("userdefined-" + fileName);
    }
    if (Files.exists(normalizedFile)) {
      throw new IOException("The XML file already exists: " + normalizedFile.getFileName());
    }

    String schemaFileName = schemaPath == null ? "" : safe(schemaPath.getFileName().toString());
    Document doc = newDocument(rootName, schemaFileName);
    writeDocumentWithoutValidation(normalizedFile, doc);
    return normalizedFile;
  }

  private Path eventSchemaPath() {
    return getEventsDirectory().resolve(EVENT_SCHEMA);
  }

  private static boolean samePath(Path a, Path b) {
    if (a == null || b == null) {
      return false;
    }
    return normalize(a).equals(normalize(b));
  }

  private Document parse(Path file) throws Exception {
    DocumentBuilder builder = dbf.newDocumentBuilder();
    return builder.parse(file.toFile());
  }

  private Document newDocument(String rootName, String schemaFileName) throws Exception {
    DocumentBuilder builder = dbf.newDocumentBuilder();
    Document doc = builder.newDocument();
    Element root = doc.createElement(rootName);
    root.setAttributeNS(
        XMLConstants.XMLNS_ATTRIBUTE_NS_URI,
        "xmlns:xsi",
        XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI);
    root.setAttributeNS(
        XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI,
        "xsi:noNamespaceSchemaLocation",
        schemaFileName);
    doc.appendChild(root);
    return doc;
  }

  private void writeDocument(Path file, Document doc, Path schemaPath) throws Exception {
    validateDocument(doc, schemaPath);
    writeDocumentWithoutValidation(file, doc);
  }

  private void writeDocumentWithoutValidation(Path file, Document doc) throws Exception {
    Files.createDirectories(file.toAbsolutePath().normalize().getParent());

    if (Files.exists(file)) {
      Path backup = Path.of(file.toString() + ".bak");
      Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
    }

    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Transformer transformer = transformerFactory.newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

    try (FileOutputStream output = new FileOutputStream(file.toFile())) {
      transformer.transform(new DOMSource(doc), new StreamResult(output));
    }
  }

  private void validateFile(Path xmlFile, Path schemaFile) throws Exception {
    validateSchemaPath(schemaFile);
    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    Schema schema = factory.newSchema(schemaFile.toFile());
    Validator validator = schema.newValidator();
    validator.validate(new javax.xml.transform.stream.StreamSource(xmlFile.toFile()));
  }

  private void validateDocument(Document doc, Path schemaFile) throws Exception {
    validateSchemaPath(schemaFile);
    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    Schema schema = factory.newSchema(schemaFile.toFile());
    Validator validator = schema.newValidator();
    validator.validate(new DOMSource(doc));
  }

  private static void validateSchemaPath(Path schemaFile) throws IOException {
    if (schemaFile == null || !Files.isRegularFile(schemaFile)) {
      throw new IOException("Schema not found: " + schemaFile);
    }
  }

  private static void validateRequired(String value, String fieldName) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("Field '" + fieldName + "' is required.");
    }
  }

  private static void appendRequiredText(Document doc, Element parent, String tagName, String value) {
    validateRequired(value, tagName);
    appendText(doc, parent, tagName, value.trim());
  }

  private static void appendOptionalText(Document doc, Element parent, String tagName, String value) {
    if (value == null || value.trim().isEmpty()) {
      return;
    }
    appendText(doc, parent, tagName, value.trim());
  }

  private static void appendText(Document doc, Element parent, String tagName, String value) {
    Element element = doc.createElement(tagName);
    element.setTextContent(value == null ? "" : value);
    parent.appendChild(element);
  }

  private static String getChildText(Element parent, String tagName) {
    NodeList nodes = parent.getElementsByTagName(tagName);
    if (nodes == null || nodes.getLength() < 1) {
      return "";
    }
    Node node = nodes.item(0);
    String text = node.getTextContent();
    return text == null ? "" : text.trim();
  }

  private static String specialNodeToRaw(Element parentElement) {
    NodeList children = parentElement.getChildNodes();
    Element specialElement = null;
    for (int i = 0; i < children.getLength(); i++) {
      Node node = children.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE && "special".equals(node.getNodeName())) {
        specialElement = (Element) node;
        break;
      }
    }
    if (specialElement == null) {
      return "";
    }
    NodeList specialChildren = specialElement.getChildNodes();
    List<String> lines = new ArrayList<>();
    for (int i = 0; i < specialChildren.getLength(); i++) {
      Node child = specialChildren.item(i);
      if (child.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      Element element = (Element) child;
      String tag = element.getTagName();
      if ("text".equals(tag)) {
        lines.add("text|" + safe(element.getTextContent()));
      } else if ("rule".equals(tag)) {
        String parameter = safe(element.getAttribute("param"));
        String line = "rule|" + safe(element.getAttribute("id")) + "|" + safe(element.getTextContent());
        if (!parameter.isBlank()) {
          line += "|" + parameter;
        }
        lines.add(line);
      } else if ("magic".equals(tag)) {
        lines.add("magic|" + safe(element.getAttribute("id")) + "|" + safe(element.getAttribute("level")));
      }
    }
    return String.join(System.lineSeparator(), lines);
  }

  private static void applySpecialRaw(Document doc, Element specialElement, String raw)
      throws IllegalArgumentException {
    try {
      for (SpecialDirective directive : parseSpecialDirectives(raw, "special")) {
        if ("text".equals(directive.type)) {
          Element textNode = doc.createElement("text");
          textNode.setTextContent(directive.payload);
          specialElement.appendChild(textNode);
        } else if ("rule".equals(directive.type)) {
          Element ruleNode = doc.createElement("rule");
          ruleNode.setAttribute("id", directive.id);
          if (!safe(directive.parameter).isBlank()) {
            ruleNode.setAttribute("param", directive.parameter);
          }
          ruleNode.setTextContent(directive.payload);
          specialElement.appendChild(ruleNode);
        } else if ("magic".equals(directive.type)) {
          Element magicNode = doc.createElement("magic");
          magicNode.setAttribute("id", directive.id);
          magicNode.setAttribute("level", directive.payload);
          specialElement.appendChild(magicNode);
        }
      }
    } catch (Exception ex) {
      if (ex instanceof IllegalArgumentException illegalArgumentException) {
        throw illegalArgumentException;
      }
      throw new IllegalArgumentException(ex.getMessage(), ex);
    }
  }

  private static List<SpecialDirective> parseSpecialDirectives(String raw, String context)
      throws IllegalArgumentException {
    List<SpecialDirective> directives = new ArrayList<>();
    if (raw == null || raw.isBlank()) {
      return directives;
    }

    String[] lines = raw.split("\\R");
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i] == null ? "" : lines[i].trim();
      if (line.isEmpty()) {
        continue;
      }

      String[] parts = line.split("\\|", -1);
      if (parts.length < 2) {
        throw new IllegalArgumentException("Invalid special line at " + context + " (line " + (i + 1) + ").");
      }

      String type = parts[0].trim();
      if ("text".equals(type)) {
        directives.add(new SpecialDirective("text", "", parts[1].trim(), ""));
      } else if ("rule".equals(type)) {
        if (parts.length < 3) {
          throw new IllegalArgumentException(
              "Invalid rule special line at " + context + " (line " + (i + 1) + ").");
        }
        String id = parts[1].trim();
        if (id.isEmpty()) {
          throw new IllegalArgumentException("Rule ID is required at " + context + " (line " + (i + 1) + ").");
        }
        directives.add(
            new SpecialDirective("rule", id, parts[2].trim(), parts.length >= 4 ? parts[3].trim() : ""));
      } else if ("magic".equals(type)) {
        if (parts.length < 3) {
          throw new IllegalArgumentException(
              "Invalid magic special line at " + context + " (line " + (i + 1) + ").");
        }
        String id = parts[1].trim();
        String level = parts[2].trim();
        if (id.isEmpty()) {
          throw new IllegalArgumentException("Magic ID is required at " + context + " (line " + (i + 1) + ").");
        }
        if (level.isEmpty()) {
          throw new IllegalArgumentException("Magic level is required at " + context + " (line " + (i + 1) + ").");
        }
        try {
          int parsedLevel = Integer.parseInt(level);
          if (parsedLevel < 0) {
            throw new IllegalArgumentException(
                "Magic level must be >= 0 at " + context + " (line " + (i + 1) + ").");
          }
        } catch (NumberFormatException ex) {
          throw new IllegalArgumentException(
              "Magic level must be numeric at " + context + " (line " + (i + 1) + ").");
        }
        directives.add(new SpecialDirective("magic", id, level, ""));
      } else {
        throw new IllegalArgumentException(
            "Unknown special type '" + type + "' at " + context + " (line " + (i + 1) + ").");
      }
    }
    return directives;
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }

  private static final class RuleReferences {
    private final Set<String> ruleIds = new HashSet<>();
    private final Set<String> magicIds = new HashSet<>();
  }

  private static final class EventIds {
    private final Map<EventScope, Set<String>> ids = new LinkedHashMap<>();

    private EventIds() {
      ids.put(EventScope.DUNGEON, new HashSet<>());
      ids.put(EventScope.TRAVEL, new HashSet<>());
      ids.put(EventScope.SETTLEMENT, new HashSet<>());
    }

    private Set<String> get(EventScope scope) {
      return ids.get(scope);
    }
  }

  private static final class SpecialContext {
    private final String context;
    private final String raw;

    private SpecialContext(String context, String raw) {
      this.context = context;
      this.raw = raw;
    }
  }

  private static final class SpecialDirective {
    private final String type;
    private final String id;
    private final String payload;
    private final String parameter;

    private SpecialDirective(String type, String id, String payload, String parameter) {
      this.type = type;
      this.id = id;
      this.payload = payload;
      this.parameter = parameter;
    }
  }

  public static final class RuleEntry {
    public String type = "rule";
    public String id = "";
    public String name = "";
    public String text = "";
    public String parameterName = "";
    public String parameterNames = "";
    public String parameterFormat = "";
  }

  public static final class EventEntry {
    public String id = "";
    public String name = "";
    public String flavor = "";
    public String rules = "";
    public String special = "";
    public String goldValue = "";
    public String users = "";
    public boolean treasure;
  }

  public static final class MonsterEntry {
    public String id = "";
    public String name = "";
    public String plural = "";
    public String factions = "";
    public String wounds = "";
    public String move = "";
    public String weaponSkill = "";
    public String ballisticSkill = "";
    public String strength = "";
    public String toughness = "";
    public String initiative = "";
    public String attacks = "";
    public String gold = "";
    public String armor = "";
    public String damage = "";
    public String specialEntriesRaw = "";
  }

  public static final class TableFileModel {
    public List<TableDefinition> tables = new ArrayList<>();
  }

  public static final class TableDefinition {
    public String name = "";
    public String kind = "";
    public List<TableEntry> entries = new ArrayList<>();
  }

  public static final class TableEntry {
    public String type = "monster";
    public String id = "";
    public String number = "";
    public String level = "";
    public String ambiences = "";
    public String specialRaw = "";
    public List<TableGroupMember> groupMembers = new ArrayList<>();
  }

  public static final class TableGroupMember {
    public String id = "";
    public String number = "";
    public String ambiences = "";
    public String specialRaw = "";
  }
}
