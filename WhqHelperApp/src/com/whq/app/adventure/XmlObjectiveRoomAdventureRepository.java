package com.whq.app.adventure;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.whq.app.i18n.ContentTranslations;
import com.whq.app.i18n.I18n;
import com.whq.app.i18n.Language;

public class XmlObjectiveRoomAdventureRepository implements ObjectiveRoomAdventureRepository {
    private static final String XML_DIR = "data/xml/adventures";
    private static final String XML_PATH = "data/xml/adventures/original-objective-room-adventures.xml";
    private static final String USER_XML_PATH = "data/xml/adventures/userdefined-objective-room-adventures.xml";
    private static final String SCHEMA_PATH = "data/xml/adventures/whq-adventures-schema.xsd";

    private final Path xmlDirectory;
    private final Path xmlPath;
    private final Path userXmlPath;
    private final Path schemaPath;
    private final Path projectRoot;
    private final DocumentBuilderFactory parserFactory;

    public XmlObjectiveRoomAdventureRepository(Path projectRoot) {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        this.projectRoot = normalizedRoot;
        this.xmlDirectory = normalizedRoot.resolve(XML_DIR);
        this.xmlPath = normalizedRoot.resolve(XML_PATH);
        this.userXmlPath = normalizedRoot.resolve(USER_XML_PATH);
        this.schemaPath = normalizedRoot.resolve(SCHEMA_PATH);
        this.parserFactory = DocumentBuilderFactory.newInstance();
        this.parserFactory.setNamespaceAware(true);
    }

    @Override
    public List<ObjectiveRoomAdventure> loadAdventuresForObjectiveRoom(String objectiveRoomName)
            throws ObjectiveRoomAdventureRepositoryException {
        String normalizedObjectiveRoomName = normalize(objectiveRoomName);
        validateFiles();

        try {
            ContentTranslations translations = ContentTranslations.load(projectRoot, I18n.getLanguage());
            Map<String, ObjectiveRoomAdventure> merged = loadMergedAdventures(translations);
            List<ObjectiveRoomAdventure> adventures = new ArrayList<>();
            for (ObjectiveRoomAdventure adventure : merged.values()) {
                if (!normalizedObjectiveRoomName.equals(normalize(adventure.objectiveRoomName()))) {
                    continue;
                }
                adventures.add(adventure);
            }

            if (adventures.isEmpty()) {
                adventures.add(buildGenericAdventure(objectiveRoomName, translations));
            } else if (adventures.stream().noneMatch(ObjectiveRoomAdventure::generic)) {
                adventures.add(buildGenericAdventure(objectiveRoomName, translations));
            }

            adventures.sort(Comparator
                    .comparing(ObjectiveRoomAdventure::generic).reversed()
                    .thenComparing(ObjectiveRoomAdventure::id, String.CASE_INSENSITIVE_ORDER));
            return adventures;
        } catch (Exception ex) {
            throw new ObjectiveRoomAdventureRepositoryException("No se han podido cargar las aventuras de la sala objetivo.", ex);
        }
    }

    public List<ObjectiveRoomAdventure> loadAllAdventures() throws ObjectiveRoomAdventureRepositoryException {
        try {
            ContentTranslations translations = ContentTranslations.load(projectRoot, I18n.getLanguage());
            List<ObjectiveRoomAdventure> adventures = new ArrayList<>(loadMergedAdventures(translations).values());
            adventures.sort(Comparator
                    .comparing(ObjectiveRoomAdventure::objectiveRoomName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(ObjectiveRoomAdventure::generic).reversed()
                    .thenComparing(ObjectiveRoomAdventure::id, String.CASE_INSENSITIVE_ORDER));
            return adventures;
        } catch (Exception ex) {
            throw new ObjectiveRoomAdventureRepositoryException("No se han podido cargar las aventuras.", ex);
        }
    }

    public void saveUserAdventure(ObjectiveRoomAdventure adventure) throws ObjectiveRoomAdventureRepositoryException {
        if (adventure == null) {
            throw new ObjectiveRoomAdventureRepositoryException("La aventura no puede ser nula.");
        }
        try {
            List<ObjectiveRoomAdventure> userAdventures = loadUserAdventuresRaw();
            upsertAdventure(userAdventures, adventure);
            writeUserAdventures(userAdventures);
        } catch (Exception ex) {
            throw new ObjectiveRoomAdventureRepositoryException("No se ha podido guardar la aventura del usuario.", ex);
        }
    }

    public void deleteUserAdventure(String objectiveRoomName, String adventureId) throws ObjectiveRoomAdventureRepositoryException {
        try {
            List<ObjectiveRoomAdventure> userAdventures = loadUserAdventuresRaw();
            boolean removed = userAdventures.removeIf(adventure ->
                    normalize(adventure.objectiveRoomName()).equals(normalize(objectiveRoomName))
                            && normalize(adventure.id()).equals(normalize(adventureId)));
            if (!removed) {
                throw new ObjectiveRoomAdventureRepositoryException("Solo se pueden eliminar aventuras definidas por el usuario.");
            }
            writeUserAdventures(userAdventures);
        } catch (ObjectiveRoomAdventureRepositoryException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ObjectiveRoomAdventureRepositoryException("No se ha podido eliminar la aventura del usuario.", ex);
        }
    }

    private void validateFiles() throws ObjectiveRoomAdventureRepositoryException {
        if (!Files.exists(xmlPath)) {
            throw new ObjectiveRoomAdventureRepositoryException("No existe el fichero de aventuras: " + xmlPath + ".");
        }
        if (!Files.exists(schemaPath)) {
            throw new ObjectiveRoomAdventureRepositoryException("No existe el esquema de aventuras: " + schemaPath + ".");
        }
        try {
            SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                    .newSchema(schemaPath.toFile())
                    .newValidator()
                    .validate(new javax.xml.transform.stream.StreamSource(xmlPath.toFile()));
        } catch (Exception ex) {
            throw new ObjectiveRoomAdventureRepositoryException("El XML de aventuras no es valido.", ex);
        }
    }

    private Map<String, ObjectiveRoomAdventure> loadMergedAdventures(ContentTranslations translations) throws Exception {
        Map<String, ObjectiveRoomAdventure> merged = new LinkedHashMap<>();
        for (Path file : listAdventureFiles()) {
            Document document = parserFactory.newDocumentBuilder().parse(file.toFile());
            NodeList roomNodes = document.getDocumentElement().getChildNodes();
            for (int i = 0; i < roomNodes.getLength(); i++) {
                Node roomNode = roomNodes.item(i);
                if (roomNode.getNodeType() != Node.ELEMENT_NODE || !"objectiveRoom".equals(roomNode.getNodeName())) {
                    continue;
                }

                Element roomElement = (Element) roomNode;
                NodeList adventureNodes = roomElement.getChildNodes();
                for (int j = 0; j < adventureNodes.getLength(); j++) {
                    Node adventureNode = adventureNodes.item(j);
                    if (adventureNode.getNodeType() != Node.ELEMENT_NODE || !"adventure".equals(adventureNode.getNodeName())) {
                        continue;
                    }
                    Element adventureElement = (Element) adventureNode;
                    String adventureId = adventureElement.getAttribute("id").trim();
                    String roomName = roomElement.getAttribute("name").trim();
                    String roomKey = normalizeTranslationKey(roomName);
                    String baseKey = "adventure." + roomKey + "." + adventureId;
                    String fallbackKey = "adventure." + adventureId;
                    ObjectiveRoomAdventure adventure = new ObjectiveRoomAdventure(
                            roomName,
                            adventureId,
                            translations.t(
                                    baseKey + ".name",
                                    translations.t(fallbackKey + ".name", adventureElement.getAttribute("name").trim())),
                            translations.t(
                                    baseKey + ".flavor",
                                    translations.t(fallbackKey + ".flavor", childText(adventureElement, "flavor"))),
                            translations.t(
                                    baseKey + ".rules",
                                    translations.t(fallbackKey + ".rules", childText(adventureElement, "rules"))),
                            Boolean.parseBoolean(adventureElement.getAttribute("generic")));
                    merged.put(adventureKey(roomName, adventureId), adventure);
                }
            }
        }
        return merged;
    }

    private List<ObjectiveRoomAdventure> loadUserAdventuresRaw() throws Exception {
        validateFiles();
        if (!Files.exists(userXmlPath)) {
            return new ArrayList<>();
        }
        Document document = parserFactory.newDocumentBuilder().parse(userXmlPath.toFile());
        List<ObjectiveRoomAdventure> adventures = new ArrayList<>();
        NodeList roomNodes = document.getDocumentElement().getChildNodes();
        for (int i = 0; i < roomNodes.getLength(); i++) {
            Node roomNode = roomNodes.item(i);
            if (roomNode.getNodeType() != Node.ELEMENT_NODE || !"objectiveRoom".equals(roomNode.getNodeName())) {
                continue;
            }
            Element roomElement = (Element) roomNode;
            NodeList adventureNodes = roomElement.getChildNodes();
            for (int j = 0; j < adventureNodes.getLength(); j++) {
                Node adventureNode = adventureNodes.item(j);
                if (adventureNode.getNodeType() != Node.ELEMENT_NODE || !"adventure".equals(adventureNode.getNodeName())) {
                    continue;
                }
                Element adventureElement = (Element) adventureNode;
                adventures.add(new ObjectiveRoomAdventure(
                        roomElement.getAttribute("name").trim(),
                        adventureElement.getAttribute("id").trim(),
                        adventureElement.getAttribute("name").trim(),
                        childText(adventureElement, "flavor"),
                        childText(adventureElement, "rules"),
                        Boolean.parseBoolean(adventureElement.getAttribute("generic"))));
            }
        }
        return adventures;
    }

    private List<Path> listAdventureFiles() throws Exception {
        if (!Files.isDirectory(xmlDirectory)) {
            return List.of(xmlPath);
        }
        try (var stream = Files.list(xmlDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml"))
                    .sorted(Comparator
                            .comparing((Path path) -> !path.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("userdefined-"))
                            .thenComparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        }
    }

    private void writeUserAdventures(List<ObjectiveRoomAdventure> adventures) throws Exception {
        Files.createDirectories(userXmlPath.getParent());
        Document document = parserFactory.newDocumentBuilder().newDocument();
        Element root = document.createElement("objectiveRoomAdventures");
        root.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        root.setAttribute("xsi:noNamespaceSchemaLocation", schemaPath.getFileName().toString());
        document.appendChild(root);

        Map<String, List<ObjectiveRoomAdventure>> byRoom = new LinkedHashMap<>();
        for (ObjectiveRoomAdventure adventure : adventures) {
            byRoom.computeIfAbsent(adventure.objectiveRoomName(), ignored -> new ArrayList<>()).add(adventure);
        }

        for (Map.Entry<String, List<ObjectiveRoomAdventure>> entry : byRoom.entrySet()) {
            Element roomElement = document.createElement("objectiveRoom");
            roomElement.setAttribute("name", entry.getKey());
            root.appendChild(roomElement);
            entry.getValue().sort(Comparator.comparing(ObjectiveRoomAdventure::id, String.CASE_INSENSITIVE_ORDER));
            for (ObjectiveRoomAdventure adventure : entry.getValue()) {
                Element adventureElement = document.createElement("adventure");
                adventureElement.setAttribute("id", adventure.id());
                adventureElement.setAttribute("name", adventure.name());
                adventureElement.setAttribute("generic", Boolean.toString(adventure.generic()));
                appendText(document, adventureElement, "flavor", adventure.flavorText());
                appendText(document, adventureElement, "rules", adventure.rulesText());
                roomElement.appendChild(adventureElement);
            }
        }

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        try (OutputStream output = Files.newOutputStream(userXmlPath)) {
            transformer.transform(new DOMSource(document), new StreamResult(output));
        }
        validateAdventureFile(userXmlPath);
    }

    private void validateAdventureFile(Path file) throws Exception {
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(schemaPath.toFile())
                .newValidator()
                .validate(new javax.xml.transform.stream.StreamSource(file.toFile()));
    }

    private void appendText(Document document, Element parent, String tagName, String value) {
        Element child = document.createElement(tagName);
        child.setTextContent(value == null ? "" : value.trim());
        parent.appendChild(child);
    }

    private void upsertAdventure(List<ObjectiveRoomAdventure> adventures, ObjectiveRoomAdventure updated) {
        String key = adventureKey(updated.objectiveRoomName(), updated.id());
        for (int i = 0; i < adventures.size(); i++) {
            if (adventureKey(adventures.get(i).objectiveRoomName(), adventures.get(i).id()).equals(key)) {
                adventures.set(i, updated);
                return;
            }
        }
        adventures.add(updated);
    }

    private String adventureKey(String objectiveRoomName, String adventureId) {
        return normalize(objectiveRoomName) + "::" + normalize(adventureId);
    }

    private String childText(Element parent, String tagName) {
        NodeList children = parent.getElementsByTagName(tagName);
        if (children.getLength() == 0) {
            return "";
        }
        Node child = children.item(0);
        return child == null || child.getTextContent() == null ? "" : child.getTextContent().trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String normalizeTranslationKey(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase().replace("&", "and");
        normalized = normalized.replaceAll("[^a-z0-9]+", "-");
        normalized = normalized.replaceAll("^-+|-+$", "");
        return normalized;
    }

    private ObjectiveRoomAdventure buildGenericAdventure(String objectiveRoomName, ContentTranslations translations) {
        String resolvedObjectiveRoomName = objectiveRoomName == null || objectiveRoomName.isBlank()
                ? "OBJECTIVE ROOM"
                : objectiveRoomName.trim();
        boolean english = I18n.getLanguage() == Language.EN;
        return new ObjectiveRoomAdventure(
                resolvedObjectiveRoomName,
                "generic",
                translations.t("adventure.generic.name", english ? "Generic" : "Generica"),
                translations.t(
                        "adventure.generic.flavor",
                        english
                                ? "Use the default ambience of " + resolvedObjectiveRoomName + " without an associated mission."
                                : "Usa la ambientacion normal de " + resolvedObjectiveRoomName + " sin una aventura concreta asociada."),
                translations.t(
                        "adventure.generic.rules",
                        english
                                ? "Resolve the objective room with the default app behaviour. There are no additional special rules for this mission."
                                : "Resuelve la habitacion objetivo con el comportamiento habitual de la aplicacion. No hay reglas especiales adicionales para esta mision."),
                true);
    }
}
