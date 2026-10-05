package com.whq.app.storage;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.whq.app.AppPaths;
import com.whq.app.game.AdventureSession;
import com.whq.app.game.SavedAdventure;
import com.whq.app.i18n.I18n;
import com.whq.app.io.SafeXml;
import com.whq.app.model.DungeonCard;

import pms.whq.content.ContentIssue;

/**
 * Persiste la aventura en curso en data/sessions/&lt;timestamp&gt;.xml del directorio escribible.
 * Hay una única sesión en curso: cada guardado sobrescribe su fichero. Solo se guardan ids de carta;
 * al cargar se resuelven contra el catálogo y los que ya no existen se reportan como ContentIssue.
 */
public final class XmlAdventureSessionStore {

    static final int VERSION = 1;
    private static final String SESSIONS_DIR = "data/sessions";
    private static final String SCHEMA_PATH = "data/xml/sessions/whq-session-schema.xsd";
    private static final DateTimeFormatter FILE_NAME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final Path sessionsDirectory;
    private final Path schemaPath;
    private final Clock clock;
    private Path currentFile;

    public XmlAdventureSessionStore(Path projectRoot) {
        this(projectRoot, Clock.systemUTC());
    }

    XmlAdventureSessionStore(Path projectRoot, Clock clock) {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        this.sessionsDirectory = normalizedRoot.resolve(SESSIONS_DIR);
        this.schemaPath = AppPaths.sharedPath(normalizedRoot, SCHEMA_PATH);
        this.clock = clock;
    }

    /** Guarda la aventura sobrescribiendo la sesión en curso (la crea en el primer guardado). */
    public synchronized void save(SavedAdventure adventure) throws AdventureSessionStorageException {
        try {
            Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
            if (currentFile == null) {
                currentFile = sessionsDirectory.resolve(FILE_NAME_FORMAT.format(now) + ".xml");
            }
            Document document = toDocument(adventure, now);
            schema().newValidator().validate(new DOMSource(document));
            writeXmlAtomically(currentFile, document);
        } catch (Exception ex) {
            throw new AdventureSessionStorageException("No se ha podido guardar la sesión de aventura.", ex);
        }
    }

    /** Borra todas las sesiones guardadas: la aventura ha terminado o se descarta. */
    public synchronized void discard() throws AdventureSessionStorageException {
        try {
            for (Path file : sessionFiles()) {
                Files.deleteIfExists(file);
            }
            currentFile = null;
        } catch (Exception ex) {
            throw new AdventureSessionStorageException("No se han podido borrar las sesiones de aventura.", ex);
        }
    }

    public synchronized boolean hasPendingSession() {
        try {
            return !sessionFiles().isEmpty();
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Carga la sesión pendiente más reciente. Los ids que ya no resuelven se omiten y se reportan; un
     * fichero inválido (esquema, versión o montones sin historial) se renombra a .invalid y no se carga.
     */
    public synchronized Optional<SavedAdventure> loadPending(
            Map<Long, DungeonCard> catalog,
            Consumer<ContentIssue> issues) {
        List<Path> files;
        try {
            files = sessionFiles();
        } catch (Exception ex) {
            return Optional.empty();
        }
        if (files.isEmpty()) {
            return Optional.empty();
        }

        Path file = files.get(files.size() - 1);
        try {
            SavedAdventure adventure = read(file, catalog, issues);
            currentFile = file;
            return Optional.of(adventure);
        } catch (Exception ex) {
            issues.accept(new ContentIssue(
                    I18n.t("session.issue.title"),
                    I18n.t("session.issue.invalidFile", Map.of(
                            "file", file.getFileName().toString(),
                            "error", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()))));
            setAside(file);
            return Optional.empty();
        }
    }

    private SavedAdventure read(Path file, Map<Long, DungeonCard> catalog, Consumer<ContentIssue> issues)
            throws Exception {
        schema().newValidator().validate(new javax.xml.transform.stream.StreamSource(file.toFile()));
        var factory = SafeXml.newFactory();
        factory.setNamespaceAware(false);
        Element root = factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();

        int version = Integer.parseInt(root.getAttribute("version"));
        if (version != VERSION) {
            throw new IllegalArgumentException("Versión de sesión no soportada: " + version + ".");
        }

        Element context = child(root, "context");
        long objectiveRoomCardId = Long.parseLong(child(root, "objective-room").getAttribute("cardId"));
        DungeonCard objectiveRoom = catalog.get(objectiveRoomCardId);
        if (objectiveRoom == null) {
            issues.accept(new ContentIssue(
                    I18n.t("session.issue.title"),
                    I18n.t("session.issue.objectiveRoomLost", Map.of("id", objectiveRoomCardId))));
        }
        Element mission = optionalChild(root, "mission");

        List<List<DungeonCard>> piles = new ArrayList<>();
        List<Element> pileElements = children(child(root, "piles"), "pile");
        for (int i = 0; i < pileElements.size(); i++) {
            Element pile = pileElements.get(i);
            requireIndex(pile.getAttribute("index"), i, "pile");
            piles.add(resolveCards(pile, catalog, issues, "session.issue.missingPileCard", i));
        }

        List<List<DungeonCard>> histories = new ArrayList<>();
        List<Element> historyElements = children(child(root, "histories"), "history");
        for (int i = 0; i < historyElements.size(); i++) {
            Element history = historyElements.get(i);
            requireIndex(history.getAttribute("pileIndex"), i, "history");
            histories.add(resolveCards(history, catalog, issues, "session.issue.missingHistoryCard", i));
        }

        if (piles.size() != histories.size()) {
            throw new IllegalArgumentException(
                    "Hay " + piles.size() + " montones y " + histories.size() + " historiales; deben coincidir.");
        }

        int selectedPile = -1;
        DungeonCard selectedCard = null;
        Element selection = optionalChild(root, "selection");
        if (selection != null) {
            int pileIndex = Integer.parseInt(selection.getAttribute("pileIndex"));
            long cardId = Long.parseLong(selection.getAttribute("cardId"));
            if (pileIndex >= piles.size()) {
                throw new IllegalArgumentException("La selección apunta a un montón inexistente: " + pileIndex + ".");
            }
            selectedCard = catalog.get(cardId);
            if (selectedCard == null) {
                issues.accept(new ContentIssue(
                        I18n.t("session.issue.title"),
                        I18n.t("session.issue.missingSelection", Map.of("id", cardId))));
            } else {
                selectedPile = pileIndex;
            }
        }

        return new SavedAdventure(
                context.getAttribute("environment"),
                Integer.parseInt(context.getAttribute("adventureLevel")),
                context.getAttribute("ambience"),
                Integer.parseInt(context.getAttribute("partySize")),
                objectiveRoomCardId,
                objectiveRoom,
                mission == null ? null : mission.getAttribute("id"),
                new AdventureSession(piles, histories, selectedPile, selectedCard));
    }

    private static List<DungeonCard> resolveCards(
            Element parent,
            Map<Long, DungeonCard> catalog,
            Consumer<ContentIssue> issues,
            String missingKey,
            int pileIndex) {
        List<DungeonCard> cards = new ArrayList<>();
        for (Element card : children(parent, "card")) {
            long id = Long.parseLong(card.getAttribute("id"));
            DungeonCard resolved = catalog.get(id);
            if (resolved == null) {
                issues.accept(new ContentIssue(
                        I18n.t("session.issue.title"),
                        I18n.t(missingKey, Map.of("id", id, "pile", pileIndex + 1))));
            } else {
                cards.add(resolved);
            }
        }
        return cards;
    }

    private static void requireIndex(String rawIndex, int expected, String elementName) {
        if (Integer.parseInt(rawIndex) != expected) {
            throw new IllegalArgumentException(
                    "Índice de " + elementName + " fuera de orden: " + rawIndex + " (se esperaba " + expected + ").");
        }
    }

    private Document toDocument(SavedAdventure adventure, Instant savedAt) throws Exception {
        AdventureSession session = adventure.session();
        Document document = SafeXml.newDocumentBuilder().newDocument();
        Element root = document.createElement("adventure-session");
        root.setAttribute("version", Integer.toString(VERSION));
        root.setAttribute("savedAt", DateTimeFormatter.ISO_INSTANT.format(savedAt));
        document.appendChild(root);

        Element context = append(document, root, "context");
        context.setAttribute("environment", nullToEmpty(adventure.environment()));
        context.setAttribute("adventureLevel", Integer.toString(adventure.adventureLevel()));
        context.setAttribute("ambience", nullToEmpty(adventure.ambience()));
        context.setAttribute("partySize", Integer.toString(adventure.partySize()));

        append(document, root, "objective-room").setAttribute("cardId", Long.toString(adventure.objectiveRoomCardId()));
        if (adventure.missionId() != null && !adventure.missionId().isBlank()) {
            append(document, root, "mission").setAttribute("id", adventure.missionId());
        }
        if (session.selectedPile() != -1 && session.selectedCard() != null) {
            Element selection = append(document, root, "selection");
            selection.setAttribute("pileIndex", Integer.toString(session.selectedPile()));
            selection.setAttribute("cardId", Long.toString(session.selectedCard().getId()));
        }

        Element piles = append(document, root, "piles");
        Element histories = append(document, root, "histories");
        for (int i = 0; i < session.pileCount(); i++) {
            Element pile = append(document, piles, "pile");
            pile.setAttribute("index", Integer.toString(i));
            appendCards(document, pile, session.pile(i));

            Element history = append(document, histories, "history");
            history.setAttribute("pileIndex", Integer.toString(i));
            appendCards(document, history, session.history(i));
        }
        return document;
    }

    private static void appendCards(Document document, Element parent, List<DungeonCard> cards) {
        for (DungeonCard card : cards) {
            append(document, parent, "card").setAttribute("id", Long.toString(card.getId()));
        }
    }

    private static Element append(Document document, Element parent, String name) {
        Element element = document.createElement(name);
        parent.appendChild(element);
        return element;
    }

    private static Element child(Element parent, String name) {
        Element element = optionalChild(parent, name);
        if (element == null) {
            throw new IllegalArgumentException("Falta el elemento <" + name + ">.");
        }
        return element;
    }

    private static Element optionalChild(Element parent, String name) {
        List<Element> elements = children(parent, name);
        return elements.isEmpty() ? null : elements.get(0);
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && name.equals(node.getNodeName())) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private Schema schema() throws Exception {
        return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(schemaPath.toFile());
    }

    // Nombres yyyyMMdd-HHmmss.xml: el orden alfabético es el cronológico.
    private List<Path> sessionFiles() throws Exception {
        if (!Files.isDirectory(sessionsDirectory)) {
            return List.of();
        }
        try (var stream = Files.list(sessionsDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".xml"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private void setAside(Path file) {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".invalid"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
            // Si no se puede apartar, se volverá a avisar en el próximo arranque.
        }
    }

    /**
     * Escribe el documento en un temporal dentro del mismo directorio que {@code target} y lo
     * publica con un Files.move atomico, para no dejar el XML truncado si el transform falla a medias.
     */
    private static void writeXmlAtomically(Path target, Document document) throws Exception {
        Path directory = target.toAbsolutePath().normalize().getParent();
        Files.createDirectories(directory);

        var transformer = TransformerFactory.newInstance().newTransformer();
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
}
