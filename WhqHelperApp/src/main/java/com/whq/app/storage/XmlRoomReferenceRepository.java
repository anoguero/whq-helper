package com.whq.app.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.XMLConstants;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.whq.app.AppPaths;
import com.whq.app.i18n.ContentTranslations;
import com.whq.app.i18n.Language;
import com.whq.app.io.SafeXml;
import com.whq.app.model.WhiteDwarfRoomReferences.Reference;

/**
 * Referencias de reglas de sala (shared/data/xml/dungeon/room-references.xml), indexadas por id de carta.
 * El XML guarda el texto base en ingles; titulo y texto de cada idioma salen de content-*.xml
 * (claves roomReference.&lt;id&gt;.title|text), con el texto del XML como respaldo.
 */
public final class XmlRoomReferenceRepository {

    private static final String XML_PATH = "data/xml/dungeon/room-references.xml";
    private static final String SCHEMA_PATH = "data/xml/dungeon/whq-room-references-schema.xsd";

    private record RawReference(long cardId, String source, String title, String text) {
    }

    // Igual que ContentTranslations: se invalida cuando cambia el lastModified o el tamanyo del XML.
    private record CacheEntry(long fileLastModifiedMillis, long fileSizeBytes, Map<Long, RawReference> references) {
    }

    private static final Map<Path, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private final Path projectRoot;
    private final Path xmlPath;
    private final Path schemaPath;

    public XmlRoomReferenceRepository(Path projectRoot) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.xmlPath = AppPaths.contentPath(this.projectRoot, XML_PATH);
        this.schemaPath = AppPaths.sharedPath(this.projectRoot, SCHEMA_PATH);
    }

    public Optional<Reference> find(long cardId) {
        RawReference raw = loadRaw().get(cardId);
        return raw == null ? Optional.empty() : Optional.of(translate(raw));
    }

    public Map<Long, Reference> findAll() {
        Map<Long, Reference> references = new LinkedHashMap<>();
        for (RawReference raw : loadRaw().values()) {
            references.put(raw.cardId(), translate(raw));
        }
        return references;
    }

    private Reference translate(RawReference raw) {
        ContentTranslations en = ContentTranslations.load(projectRoot, Language.EN);
        ContentTranslations es = ContentTranslations.load(projectRoot, Language.ES);
        String baseKey = "roomReference." + raw.cardId();
        return new Reference(
                raw.source(),
                en.t(baseKey + ".title", raw.title()),
                es.t(baseKey + ".title", raw.title()),
                en.t(baseKey + ".text", raw.text()),
                es.t(baseKey + ".text", raw.text()));
    }

    private Map<Long, RawReference> loadRaw() {
        long lastModifiedMillis = lastModifiedMillisOrZero(xmlPath);
        long sizeBytes = sizeOrZero(xmlPath);
        CacheEntry cached = CACHE.get(xmlPath);
        if (cached != null && cached.fileLastModifiedMillis() == lastModifiedMillis && cached.fileSizeBytes() == sizeBytes) {
            return cached.references();
        }

        Map<Long, RawReference> references = parse();
        CACHE.put(xmlPath, new CacheEntry(lastModifiedMillis, sizeBytes, references));
        return references;
    }

    // Las referencias son informacion de apoyo: si el XML falta o no es valido, no hay referencias.
    private Map<Long, RawReference> parse() {
        if (!Files.isRegularFile(xmlPath)) {
            return Map.of();
        }
        try {
            if (Files.isRegularFile(schemaPath)) {
                SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                        .newSchema(schemaPath.toFile())
                        .newValidator()
                        .validate(new javax.xml.transform.stream.StreamSource(xmlPath.toFile()));
            }

            var factory = SafeXml.newFactory();
            factory.setNamespaceAware(false);
            Document document = factory.newDocumentBuilder().parse(xmlPath.toFile());
            Element root = document.getDocumentElement();
            if (root == null || !"roomReferences".equals(root.getTagName())) {
                return Map.of();
            }

            Map<Long, RawReference> references = new LinkedHashMap<>();
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE || !"reference".equals(node.getNodeName())) {
                    continue;
                }
                Element element = (Element) node;
                long cardId = Long.parseLong(element.getAttribute("cardId").trim());
                references.put(cardId, new RawReference(
                        cardId,
                        element.getAttribute("source").trim(),
                        childText(element, "title"),
                        childText(element, "text")));
            }
            return Collections.unmodifiableMap(references);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    // trim() solo recorta los extremos: la sangria de las lineas interiores forma parte del texto.
    private static String childText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0).getTextContent() == null) {
            return "";
        }
        return nodes.item(0).getTextContent().trim();
    }

    private static long lastModifiedMillisOrZero(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static long sizeOrZero(Path file) {
        try {
            return Files.size(file);
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
