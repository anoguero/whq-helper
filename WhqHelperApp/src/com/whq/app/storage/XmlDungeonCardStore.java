package com.whq.app.storage;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
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

import com.whq.app.AppPaths;
import com.whq.app.i18n.ContentTranslations;
import com.whq.app.i18n.I18n;
import com.whq.app.io.SafeXml;
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
        // Catalogo base y esquema en el shared home; las cartas del usuario en el runtime home.
        this.xmlDirectory = AppPaths.sharedPath(this.projectRoot, XML_DIR);
        this.xmlPath = AppPaths.sharedPath(this.projectRoot, XML_PATH);
        this.userXmlPath = this.projectRoot.resolve(USER_XML_PATH);
        this.schemaPath = AppPaths.sharedPath(this.projectRoot, SCHEMA_PATH);
        this.parserFactory = SafeXml.newFactory();
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

        // Un unico recorrido de los ficheros XML (readRawCardsByFile) sirve tanto para el listado
        // crudo del fichero de usuario como para la version efectiva (base+usuario fusionados); la
        // traduccion, ya cacheada por (projectRoot, idioma), se aplica solo a la carta afectada.
        Map<Path, List<DungeonCard>> byFile = readRawCardsByFile();
        List<DungeonCard> cards = new ArrayList<>(byFile.getOrDefault(userXmlPath, List.of()));
        List<DungeonCard> effectiveCards = mergeCards(byFile.values());
        DungeonCard current = findById(effectiveCards, card.getId());
        if (current == null) {
            throw new DungeonCardStorageException("No se ha encontrado la carta con id " + card.getId() + ".");
        }
        // El editor rellena el formulario con el texto traducido; si un campo no se ha modificado
        // (sigue igual a la traduccion mostrada) persistimos el valor CRUDO original para no perder
        // el texto base como fallback en otros idiomas. Si el usuario lo cambio, guardamos lo nuevo.
        DungeonCard translatedCurrent = translateCard(current, ContentTranslations.load(projectRoot, I18n.getLanguage()));
        DungeonCard updatedCard = new DungeonCard(
                current.getId(),
                require(preserveRawIfUnchanged(card.getName(), translatedCurrent.getName(), current.getName()), "name"),
                card.getType(),
                normalizeEnvironment(card.getEnvironment()),
                Math.max(0, card.getCopyCount()),
                card.isEnabled(),
                nullToEmpty(preserveRawIfUnchanged(card.getDescriptionText(), translatedCurrent.getDescriptionText(), current.getDescriptionText())),
                nullToEmpty(preserveRawIfUnchanged(card.getRulesText(), translatedCurrent.getRulesText(), current.getRulesText())),
                require(card.getTileImagePath(), "tileImagePath"));
        upsertById(cards, updatedCard);
        writeUserCards(cards);
    }

    @Override
    public void updateCardAvailability(long cardId, int copyCount, boolean enabled) throws DungeonCardStorageException {
        if (copyCount < 0) {
            throw new IllegalArgumentException("El numero de copias no puede ser negativo.");
        }

        Map<Path, List<DungeonCard>> byFile = readRawCardsByFile();
        List<DungeonCard> cards = new ArrayList<>(byFile.getOrDefault(userXmlPath, List.of()));
        List<DungeonCard> effectiveCards = mergeCards(byFile.values());
        DungeonCard current = findById(effectiveCards, cardId);
        if (current == null) {
            throw new DungeonCardStorageException("No se ha encontrado la carta con id " + cardId + ".");
        }
        // Cambiar disponibilidad no debe tocar el texto: persistimos el texto CRUDO (sin traducir).
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
        writeUserCards(cards);
    }

    @Override
    public void deleteCard(long cardId) throws DungeonCardStorageException {
        List<DungeonCard> cards = new ArrayList<>(readUserCards(false));
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

        Map<Path, List<DungeonCard>> byFile = readRawCardsByFile();
        List<DungeonCard> existing = mergeCards(byFile.values());
        List<DungeonCard> userCards = new ArrayList<>(byFile.getOrDefault(userXmlPath, List.of()));
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
        return readCards(true);
    }

    private List<DungeonCard> readCards(boolean translate) throws DungeonCardStorageException {
        List<DungeonCard> merged = mergeCards(readRawCardsByFile().values());
        return translate ? translateAll(merged) : merged;
    }

    private List<DungeonCard> readUserCards(boolean translate) throws DungeonCardStorageException {
        ensureSchemaExists();
        if (!Files.exists(userXmlPath)) {
            return new ArrayList<>();
        }
        List<DungeonCard> raw = readCardsFromFile(userXmlPath);
        return translate ? translateAll(raw) : raw;
    }

    /**
     * Lee el texto CRUDO (sin traducir) de todos los ficheros de cartas, uno por uno, en un unico
     * recorrido. Base para {@link #readCards(boolean)}, {@link #readUserCards(boolean)} y para las
     * operaciones de escritura, que necesitan tanto el fichero de usuario en crudo como la vista
     * fusionada y no deben parsear el mismo fichero mas de una vez.
     */
    private Map<Path, List<DungeonCard>> readRawCardsByFile() throws DungeonCardStorageException {
        ensureBaseXmlExists();
        Map<Path, List<DungeonCard>> byFile = new LinkedHashMap<>();
        for (Path file : listCardFiles()) {
            byFile.put(file, readCardsFromFile(file));
        }
        return byFile;
    }

    // listCardFiles() devuelve los ficheros userdefined-* primero: al fusionar por id usamos
    // putIfAbsent para que la version del usuario prevalezca sobre la del fichero base y las
    // ediciones guardadas no se pierdan al recargar.
    private List<DungeonCard> mergeCards(Collection<List<DungeonCard>> rawCardsByFileInPriorityOrder) {
        Map<Long, DungeonCard> merged = new LinkedHashMap<>();
        for (List<DungeonCard> cards : rawCardsByFileInPriorityOrder) {
            for (DungeonCard card : cards) {
                merged.putIfAbsent(card.getId(), card);
            }
        }
        List<DungeonCard> cards = new ArrayList<>(merged.values());
        cards.sort(Comparator
                .comparing(DungeonCard::getEnvironment, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(DungeonCard::getName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(DungeonCard::getId));
        return cards;
    }

    private List<DungeonCard> translateAll(List<DungeonCard> rawCards) throws DungeonCardStorageException {
        if (rawCards.isEmpty()) {
            return rawCards;
        }
        ContentTranslations translations = ContentTranslations.load(projectRoot, I18n.getLanguage());
        return rawCards.stream().map(card -> translateCard(card, translations)).toList();
    }

    private DungeonCard translateCard(DungeonCard raw, ContentTranslations translations) {
        String baseKey = "dungeonCard." + raw.getId();
        return new DungeonCard(
                raw.getId(),
                translations.t(baseKey + ".name", raw.getName()),
                raw.getType(),
                raw.getEnvironment(),
                raw.getCopyCount(),
                raw.isEnabled(),
                translations.t(baseKey + ".description", raw.getDescriptionText()),
                translations.t(baseKey + ".rules", raw.getRulesText()),
                raw.getTileImagePath());
    }

    private List<Path> listCardFiles() throws DungeonCardStorageException {
        ensureSchemaExists();
        try {
            if (!Files.isDirectory(xmlDirectory)) {
                return List.of(xmlPath);
            }
            try (var stream = AppPaths.listContentFiles(projectRoot, XML_DIR).stream()) {
                return stream
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

    // Siempre devuelve el texto CRUDO del XML (sin traducir); la traduccion se aplica aparte
    // (ver translateAll/translateCard) para no tener que reparsear el fichero por cada variante.
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
                long id = parseId(element.getAttribute("id"));
                cards.add(new DungeonCard(
                        id,
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

            writeXmlAtomically(userXmlPath, document);
            validateFile(userXmlPath);
        } catch (DungeonCardStorageException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DungeonCardStorageException("No se han podido guardar las cartas XML.", ex);
        }
    }

    /**
     * Escribe el documento en un temporal dentro del mismo directorio que {@code target} y lo
     * publica con un Files.move atomico, para no dejar el XML truncado si el transform falla a medias.
     */
    private void writeXmlAtomically(Path target, Document document) throws Exception {
        Path directory = target.toAbsolutePath().normalize().getParent();
        Files.createDirectories(directory);

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        Path tmpFile = Files.createTempFile(directory, target.getFileName().toString(), ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(tmpFile)) {
                transformer.transform(new DOMSource(document), new StreamResult(output));
            }
            Files.move(tmpFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmpFile);
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
            writeXmlAtomically(xmlPath, document);
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

    private static DungeonCard findById(List<DungeonCard> cards, long id) {
        for (DungeonCard card : cards) {
            if (card.getId() == id) {
                return card;
            }
        }
        return null;
    }

    // Si el valor entrante coincide con la traduccion mostrada (campo no editado), devuelve el texto
    // crudo original para preservarlo como fallback; en caso contrario, devuelve el valor entrante.
    private static String preserveRawIfUnchanged(String incoming, String translatedValue, String rawValue) {
        if (translatedValue != null && translatedValue.equals(incoming)) {
            return rawValue;
        }
        return incoming;
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
