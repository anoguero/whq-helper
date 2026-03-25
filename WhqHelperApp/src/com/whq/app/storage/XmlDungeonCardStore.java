package com.whq.app.storage;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
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

import com.whq.app.model.CardType;
import com.whq.app.model.DungeonCard;

public class XmlDungeonCardStore implements DungeonCardStore {
    private static final String DEFAULT_ENVIRONMENT = "The Old World";
    private static final String XML_DIR = "data/xml/dungeon";
    private static final String XML_PATH = "data/xml/dungeon/dungeon-cards.xml";
    private static final String USER_XML_PATH = "data/xml/dungeon/userdefined-dungeon-cards.xml";
    private static final String SCHEMA_PATH = "data/xml/dungeon/whq-dungeon-cards-schema.xsd";

    private final Path projectRoot;
    private final Path xmlDirectory;
    private final Path xmlPath;
    private final Path userXmlPath;
    private final Path schemaPath;
    private final DocumentBuilderFactory parserFactory;

    public XmlDungeonCardStore(Path projectRoot) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.xmlDirectory = this.projectRoot.resolve(XML_DIR);
        this.xmlPath = this.projectRoot.resolve(XML_PATH);
        this.userXmlPath = this.projectRoot.resolve(USER_XML_PATH);
        this.schemaPath = this.projectRoot.resolve(SCHEMA_PATH);
        this.parserFactory = DocumentBuilderFactory.newInstance();
        this.parserFactory.setNamespaceAware(true);
    }

    @Override
    public List<DungeonCard> loadCards() throws DungeonCardStorageException {
        return readCards();
    }

    @Override
    public List<String> loadEnvironments() throws DungeonCardStorageException {
        Set<String> environments = new LinkedHashSet<>();
        for (DungeonCard card : readCards()) {
            if (card.getType() != CardType.OBJECTIVE_ROOM || !card.isEnabled() || card.getCopyCount() <= 0) {
                continue;
            }
            environments.add(normalizeEnvironment(card.getEnvironment()));
        }
        return environments.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Override
    public List<DungeonCard> loadObjectiveRoomsByEnvironment(String environment) throws DungeonCardStorageException {
        String normalizedEnvironment = normalizeEnvironment(environment);
        return readCards().stream()
                .filter(card -> normalizedEnvironment.equalsIgnoreCase(card.getEnvironment()))
                .filter(card -> card.getType() == CardType.OBJECTIVE_ROOM)
                .filter(DungeonCard::isEnabled)
                .filter(card -> card.getCopyCount() > 0)
                .sorted(Comparator.comparing(DungeonCard::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public void updateCard(DungeonCard card) throws DungeonCardStorageException {
        if (card == null) {
            throw new DungeonCardStorageException("La carta a actualizar no puede ser nula.");
        }

        List<DungeonCard> cards = new ArrayList<>(readUserCards());
        List<DungeonCard> effectiveCards = readCards();
        boolean updated = false;
        for (int i = 0; i < effectiveCards.size(); i++) {
            DungeonCard current = effectiveCards.get(i);
            if (current.getId() != card.getId()) {
                continue;
            }
            DungeonCard updatedCard = new DungeonCard(
                    current.getId(),
                    require(card.getName(), "name"),
                    card.getType(),
                    normalizeEnvironment(card.getEnvironment()),
                    Math.max(0, card.getCopyCount()),
                    card.isEnabled(),
                    nullToEmpty(card.getDescriptionText()),
                    nullToEmpty(card.getRulesText()),
                    require(card.getTileImagePath(), "tileImagePath"));
            upsertById(cards, updatedCard);
            updated = true;
            break;
        }
        if (!updated) {
            throw new DungeonCardStorageException("No se ha encontrado la carta con id " + card.getId() + ".");
        }
        writeUserCards(cards);
    }

    @Override
    public void updateCardAvailability(long cardId, int copyCount, boolean enabled) throws DungeonCardStorageException {
        if (copyCount < 0) {
            throw new IllegalArgumentException("El numero de copias no puede ser negativo.");
        }

        List<DungeonCard> cards = new ArrayList<>(readUserCards());
        List<DungeonCard> effectiveCards = readCards();
        boolean updated = false;
        for (DungeonCard current : effectiveCards) {
            if (current.getId() != cardId) {
                continue;
            }
            upsertById(cards, new DungeonCard(
                    current.getId(),
                    current.getName(),
                    current.getType(),
                    current.getEnvironment(),
                    copyCount,
                    enabled,
                    current.getDescriptionText(),
                    current.getRulesText(),
                    current.getTileImagePath()));
            updated = true;
            break;
        }
        if (!updated) {
            throw new DungeonCardStorageException("No se ha encontrado la carta con id " + cardId + ".");
        }
        writeUserCards(cards);
    }

    @Override
    public void deleteCard(long cardId) throws DungeonCardStorageException {
        List<DungeonCard> cards = new ArrayList<>(readUserCards());
        boolean removed = cards.removeIf(card -> card.getId() == cardId);
        if (!removed) {
            throw new DungeonCardStorageException("Solo se pueden eliminar cartas definidas por el usuario. Id: " + cardId + ".");
        }
        writeUserCards(cards);
    }

    @Override
    public void insertCards(List<DungeonCard> cards) throws DungeonCardStorageException {
        if (cards == null || cards.isEmpty()) {
            return;
        }

        List<DungeonCard> existing = new ArrayList<>(readCards());
        List<DungeonCard> userCards = new ArrayList<>(readUserCards());
        long nextId = existing.stream().mapToLong(DungeonCard::getId).max().orElse(0L) + 1L;
        for (DungeonCard card : cards) {
            userCards.add(new DungeonCard(
                    nextId++,
                    require(card.getName(), "name"),
                    card.getType(),
                    normalizeEnvironment(card.getEnvironment()),
                    Math.max(0, card.getCopyCount()),
                    card.isEnabled(),
                    nullToEmpty(card.getDescriptionText()),
                    nullToEmpty(card.getRulesText()),
                    require(card.getTileImagePath(), "tileImagePath")));
        }
        writeUserCards(userCards);
    }

    private List<DungeonCard> readCards() throws DungeonCardStorageException {
        ensureBaseXmlExists();
        try {
            Map<Long, DungeonCard> merged = new LinkedHashMap<>();
            for (Path file : listCardFiles()) {
                for (DungeonCard card : readCardsFromFile(file)) {
                    merged.put(card.getId(), card);
                }
            }
            List<DungeonCard> cards = new ArrayList<>(merged.values());
            cards.sort(Comparator
                    .comparing(DungeonCard::getEnvironment, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(DungeonCard::getName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingLong(DungeonCard::getId));
            return cards;
        } catch (DungeonCardStorageException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DungeonCardStorageException("No se han podido leer las cartas XML.", ex);
        }
    }

    private List<DungeonCard> readUserCards() throws DungeonCardStorageException {
        ensureSchemaExists();
        if (!Files.exists(userXmlPath)) {
            return new ArrayList<>();
        }
        return readCardsFromFile(userXmlPath);
    }

    private List<Path> listCardFiles() throws DungeonCardStorageException {
        ensureSchemaExists();
        try {
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
        } catch (Exception ex) {
            throw new DungeonCardStorageException("No se ha podido listar el contenido XML de mazmorra.", ex);
        }
    }

    private List<DungeonCard> readCardsFromFile(Path file) throws DungeonCardStorageException {
        try {
            validateFile(file);
            Document document = parse(file);
            Element root = document.getDocumentElement();
            NodeList children = root.getChildNodes();
            List<DungeonCard> cards = new ArrayList<>();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE || !"card".equals(node.getNodeName())) {
                    continue;
                }
                Element element = (Element) node;
                cards.add(new DungeonCard(
                        parseId(element.getAttribute("id")),
                        require(element.getAttribute("name"), "name"),
                        CardType.valueOf(require(element.getAttribute("type"), "type").toUpperCase(Locale.ROOT)),
                        normalizeEnvironment(element.getAttribute("environment")),
                        parseNonNegativeInt(element.getAttribute("copyCount"), "copyCount"),
                        Boolean.parseBoolean(element.getAttribute("enabled")),
                        readChildText(element, "description"),
                        readChildText(element, "rules"),
                        require(readChildText(element, "tileImagePath"), "tileImagePath")));
            }
            return cards;
        } catch (DungeonCardStorageException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DungeonCardStorageException("No se han podido leer las cartas XML desde " + file + ".", ex);
        }
    }

    private void writeUserCards(List<DungeonCard> cards) throws DungeonCardStorageException {
        try {
            Files.createDirectories(userXmlPath.getParent());
            Document document = newDocument();
            Element root = document.createElement("dungeonCards");
            document.appendChild(root);

            List<DungeonCard> sorted = new ArrayList<>(cards);
            sorted.sort(Comparator
                    .comparing(DungeonCard::getEnvironment, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(DungeonCard::getName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingLong(DungeonCard::getId));

            for (DungeonCard card : sorted) {
                Element node = document.createElement("card");
                node.setAttribute("id", Long.toString(card.getId()));
                node.setAttribute("name", require(card.getName(), "name"));
                node.setAttribute("type", card.getType().name());
                node.setAttribute("environment", normalizeEnvironment(card.getEnvironment()));
                node.setAttribute("copyCount", Integer.toString(Math.max(0, card.getCopyCount())));
                node.setAttribute("enabled", Boolean.toString(card.isEnabled()));
                appendText(document, node, "description", nullToEmpty(card.getDescriptionText()));
                appendText(document, node, "rules", nullToEmpty(card.getRulesText()));
                appendText(document, node, "tileImagePath", require(card.getTileImagePath(), "tileImagePath"));
                root.appendChild(node);
            }

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            try (OutputStream output = Files.newOutputStream(userXmlPath)) {
                transformer.transform(new DOMSource(document), new StreamResult(output));
            }
            validateFile(userXmlPath);
        } catch (DungeonCardStorageException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DungeonCardStorageException("No se han podido guardar las cartas XML.", ex);
        }
    }

    private void ensureBaseXmlExists() throws DungeonCardStorageException {
        if (Files.exists(xmlPath)) {
            return;
        }

        ensureSchemaExists();
        writeBaseCards(defaultCards());
    }

    private void ensureSchemaExists() throws DungeonCardStorageException {
        try {
            Files.createDirectories(schemaPath.getParent());
            if (Files.exists(schemaPath)) {
                return;
            }
            Files.writeString(schemaPath, DungeonCardXmlValidator.defaultSchema());
        } catch (Exception ex) {
            throw new DungeonCardStorageException("No se ha podido crear el esquema XML de cartas.", ex);
        }
    }

    private void writeBaseCards(List<DungeonCard> cards) throws DungeonCardStorageException {
        try {
            Files.createDirectories(xmlPath.getParent());
            Document document = newDocument();
            Element root = document.createElement("dungeonCards");
            document.appendChild(root);
            for (DungeonCard card : cards) {
                Element node = document.createElement("card");
                node.setAttribute("id", Long.toString(card.getId()));
                node.setAttribute("name", require(card.getName(), "name"));
                node.setAttribute("type", card.getType().name());
                node.setAttribute("environment", normalizeEnvironment(card.getEnvironment()));
                node.setAttribute("copyCount", Integer.toString(Math.max(0, card.getCopyCount())));
                node.setAttribute("enabled", Boolean.toString(card.isEnabled()));
                appendText(document, node, "description", nullToEmpty(card.getDescriptionText()));
                appendText(document, node, "rules", nullToEmpty(card.getRulesText()));
                appendText(document, node, "tileImagePath", require(card.getTileImagePath(), "tileImagePath"));
                root.appendChild(node);
            }
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            try (OutputStream output = Files.newOutputStream(xmlPath)) {
                transformer.transform(new DOMSource(document), new StreamResult(output));
            }
            validateFile(xmlPath);
        } catch (Exception ex) {
            throw new DungeonCardStorageException("No se han podido guardar las cartas base XML.", ex);
        }
    }

    private void validateFile(Path file) throws DungeonCardStorageException {
        try {
            SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI)
                    .newSchema(schemaPath.toFile())
                    .newValidator()
                    .validate(new javax.xml.transform.stream.StreamSource(file.toFile()));
        } catch (Exception ex) {
            throw new DungeonCardStorageException("El XML de cartas no es valido: " + file + ".", ex);
        }
    }

    private void upsertById(List<DungeonCard> cards, DungeonCard updatedCard) {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).getId() == updatedCard.getId()) {
                cards.set(i, updatedCard);
                return;
            }
        }
        cards.add(updatedCard);
    }

    static List<DungeonCard> defaultCards() {
        return List.of(
                new DungeonCard(
                        1,
                        "SYLVAN RESPITE",
                        CardType.DUNGEON_ROOM,
                        DEFAULT_ENVIRONMENT,
                        1,
                        true,
                        "Autumn scents fill the air as leaves crackle underfoot, a long hidden Elven shrine appears ahead.",
                        "The Sylvan Respite will always trigger an event card. The Wood Elf player gains 1 extra attack should monsters appear.",
                        "resources/tiles/sylvan-respite.png"),
                new DungeonCard(
                        2,
                        "EERIE CHASM",
                        CardType.CORRIDOR,
                        DEFAULT_ENVIRONMENT,
                        1,
                        true,
                        "The mists coil about your feet and fill the chasm ahead, be sure your chances to cross.",
                        "The Eerie Chasm can be crossed through use of ropes taking D6 turns to prepare, or leap. Roll a D6 to leap, a 1 is a deadly fall.",
                        "resources/tiles/eerie-chasm.png"),
                new DungeonCard(
                        3,
                        "WICKED WELL",
                        CardType.OBJECTIVE_ROOM,
                        DEFAULT_ENVIRONMENT,
                        1,
                        true,
                        "Vile waters stir beneath broken boards, the last sacred water font mere steps away.",
                        "You arrive just in time to protect the sacred font. See the Adventure Book or ask the GM for what you encounter.",
                        "resources/tiles/wicked-well.png"),
                new DungeonCard(
                        4,
                        "RUNIC ANTECHAMBER",
                        CardType.SPECIAL,
                        DEFAULT_ENVIRONMENT,
                        1,
                        true,
                        "Ancient runes glow as soon as a warrior crosses the threshold. Cold whispers fill the chamber.",
                        "When revealed, draw one event card. Wizards gain +1 to all casting rolls until the start of the next Power Phase.",
                        "resources/tiles/sylvan-respite.png"));
    }

    private Document parse(Path file) throws Exception {
        DocumentBuilder builder = parserFactory.newDocumentBuilder();
        return builder.parse(file.toFile());
    }

    private Document newDocument() throws Exception {
        DocumentBuilder builder = parserFactory.newDocumentBuilder();
        return builder.newDocument();
    }

    private void appendText(Document document, Element parent, String tagName, String value) {
        Element child = document.createElement(tagName);
        child.setTextContent(value);
        parent.appendChild(child);
    }

    private String readChildText(Element parent, String tagName) {
        NodeList children = parent.getElementsByTagName(tagName);
        if (children.getLength() == 0) {
            return "";
        }
        Node node = children.item(0);
        return node == null || node.getTextContent() == null ? "" : node.getTextContent().trim();
    }

    private long parseId(String rawId) throws DungeonCardStorageException {
        try {
            long id = Long.parseLong(require(rawId, "id"));
            if (id <= 0) {
                throw new IllegalArgumentException();
            }
            return id;
        } catch (IllegalArgumentException ex) {
            throw new DungeonCardStorageException("El id de carta no es valido: " + rawId + ".");
        }
    }

    private int parseNonNegativeInt(String rawValue, String fieldName) throws DungeonCardStorageException {
        try {
            int value = Integer.parseInt(require(rawValue, fieldName));
            if (value < 0) {
                throw new IllegalArgumentException();
            }
            return value;
        } catch (IllegalArgumentException ex) {
            throw new DungeonCardStorageException("El campo " + fieldName + " no es valido: " + rawValue + ".");
        }
    }

    private String require(String value, String fieldName) throws DungeonCardStorageException {
        if (value == null || value.isBlank()) {
            throw new DungeonCardStorageException("El campo " + fieldName + " es obligatorio.");
        }
        return value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeEnvironment(String environment) {
        if (environment == null || environment.isBlank()) {
            return DEFAULT_ENVIRONMENT;
        }
        return environment.trim();
    }
}
