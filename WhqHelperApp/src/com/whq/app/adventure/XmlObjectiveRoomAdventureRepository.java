package com.whq.app.adventure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.whq.app.i18n.ContentTranslations;
import com.whq.app.i18n.I18n;
import com.whq.app.i18n.Language;

public class XmlObjectiveRoomAdventureRepository implements ObjectiveRoomAdventureRepository {
    private static final String XML_PATH = "data/xml/adventures/original-objective-room-adventures.xml";
    private static final String SCHEMA_PATH = "data/xml/adventures/whq-adventures-schema.xsd";

    private final Path xmlPath;
    private final Path schemaPath;
    private final Path projectRoot;
    private final DocumentBuilderFactory parserFactory;

    public XmlObjectiveRoomAdventureRepository(Path projectRoot) {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        this.projectRoot = normalizedRoot;
        this.xmlPath = normalizedRoot.resolve(XML_PATH);
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
            Document document = parserFactory.newDocumentBuilder().parse(xmlPath.toFile());
            List<ObjectiveRoomAdventure> adventures = new ArrayList<>();
            NodeList roomNodes = document.getDocumentElement().getChildNodes();
            for (int i = 0; i < roomNodes.getLength(); i++) {
                Node roomNode = roomNodes.item(i);
                if (roomNode.getNodeType() != Node.ELEMENT_NODE || !"objectiveRoom".equals(roomNode.getNodeName())) {
                    continue;
                }

                Element roomElement = (Element) roomNode;
                if (!normalizedObjectiveRoomName.equals(normalize(roomElement.getAttribute("name")))) {
                    continue;
                }

                NodeList adventureNodes = roomElement.getChildNodes();
                for (int j = 0; j < adventureNodes.getLength(); j++) {
                    Node adventureNode = adventureNodes.item(j);
                    if (adventureNode.getNodeType() != Node.ELEMENT_NODE || !"adventure".equals(adventureNode.getNodeName())) {
                        continue;
                    }
                    Element adventureElement = (Element) adventureNode;
                    String adventureId = adventureElement.getAttribute("id").trim();
                    String roomKey = normalizeTranslationKey(roomElement.getAttribute("name"));
                    String baseKey = "adventure." + roomKey + "." + adventureId;
                    String fallbackKey = "adventure." + adventureId;
                    adventures.add(new ObjectiveRoomAdventure(
                            roomElement.getAttribute("name").trim(),
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
                            Boolean.parseBoolean(adventureElement.getAttribute("generic"))));
                }
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
