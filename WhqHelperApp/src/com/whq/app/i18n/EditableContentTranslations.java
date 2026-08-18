package com.whq.app.i18n;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.whq.app.io.SafeXml;

public final class EditableContentTranslations {

  private static final String RELATIVE_DIR = "data/i18n";

  private record CacheKey(Path projectRoot, Language language) {
  }

  // fileSizeBytes se comprueba junto con el lastModified: en sistemas de ficheros con
  // granularidad de segundo, dos escrituras dentro del mismo tick tendrian el mismo
  // lastModified pero (casi siempre) tamanyo distinto, y la cache debe invalidarse igualmente.
  private record CacheEntry(long fileLastModifiedMillis, long fileSizeBytes, Map<String, String> values) {
  }

  // Cache del mapa base parseado del XML, con la misma invalidacion que ContentTranslations
  // (idioma + lastModified + tamanyo). load() siempre devuelve una copia mutable propia del mapa
  // cacheado: como esta clase se usa para editar (put/remove/save), compartir la misma instancia
  // mutable entre llamadas a load() podria filtrar cambios sin guardar de una sesion de edicion a otra.
  private static final Map<CacheKey, CacheEntry> CACHE = new ConcurrentHashMap<>();

  private final Path file;
  private final Map<String, String> values;

  private EditableContentTranslations(Path file, Map<String, String> values) {
    this.file = file;
    this.values = values;
  }

  public static EditableContentTranslations load(Path projectRoot, Language language) {
    if (projectRoot == null || language == null) {
      return new EditableContentTranslations(null, new LinkedHashMap<>());
    }

    Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
    Path file = translationsFile(normalizedRoot, language);
    long lastModifiedMillis = lastModifiedMillisOrZero(file);
    long sizeBytes = sizeOrZero(file);

    CacheKey key = new CacheKey(normalizedRoot, language);
    CacheEntry cached = CACHE.get(key);
    if (cached == null || cached.fileLastModifiedMillis() != lastModifiedMillis || cached.fileSizeBytes() != sizeBytes) {
      cached = new CacheEntry(lastModifiedMillis, sizeBytes, parse(file));
      CACHE.put(key, cached);
    }

    return new EditableContentTranslations(file, new LinkedHashMap<>(cached.values()));
  }

  private static Path translationsFile(Path normalizedProjectRoot, Language language) {
    String suffix = language == Language.EN ? "en" : "es";
    return normalizedProjectRoot.resolve(RELATIVE_DIR).resolve("content-" + suffix + ".xml");
  }

  private static long sizeOrZero(Path file) {
    try {
      return Files.size(file);
    } catch (Exception ignored) {
      return 0L;
    }
  }

  private static long lastModifiedMillisOrZero(Path file) {
    try {
      return Files.getLastModifiedTime(file).toMillis();
    } catch (Exception ignored) {
      return 0L;
    }
  }

  private static Map<String, String> parse(Path file) {
    Map<String, String> map = new LinkedHashMap<>();
    if (!Files.isRegularFile(file)) {
      return map;
    }

    try {
      var factory = SafeXml.newFactory();
      factory.setNamespaceAware(false);
      Document document = factory.newDocumentBuilder().parse(file.toFile());
      Element root = document.getDocumentElement();
      if (root == null || !"translations".equals(root.getTagName())) {
        return map;
      }

      NodeList children = root.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        Node node = children.item(i);
        if (node.getNodeType() != Node.ELEMENT_NODE || !"entry".equals(node.getNodeName())) {
          continue;
        }
        Element element = (Element) node;
        String key = element.getAttribute("key") == null ? "" : element.getAttribute("key").trim();
        String value = element.getTextContent() == null ? "" : element.getTextContent().trim();
        if (!key.isEmpty()) {
          map.put(key, value);
        }
      }
    } catch (Exception ignored) {
      // Falls back to an empty editable map if translation loading fails.
    }

    return map;
  }

  public String t(String key, String fallback) {
    if (key == null || key.isBlank()) {
      return fallback == null ? "" : fallback;
    }
    String value = values.get(key);
    return value != null ? value : (fallback == null ? "" : fallback);
  }

  public void put(String key, String value) {
    if (key == null || key.isBlank()) {
      return;
    }
    values.put(key.trim(), value == null ? "" : value.trim());
  }

  public void remove(String key) {
    if (key == null || key.isBlank()) {
      return;
    }
    values.remove(key.trim());
  }

  public void save() throws Exception {
    if (file == null) {
      return;
    }
    Path directory = file.toAbsolutePath().normalize().getParent();
    Files.createDirectories(directory);

    Document document = SafeXml.newDocumentBuilder().newDocument();
    Element root = document.createElement("translations");
    document.appendChild(root);

    Map<String, String> sorted = new TreeMap<>(values);
    for (Map.Entry<String, String> entry : sorted.entrySet()) {
      Element element = document.createElement("entry");
      element.setAttribute("key", entry.getKey());
      element.setTextContent(entry.getValue() == null ? "" : entry.getValue());
      root.appendChild(element);
    }

    var transformer = TransformerFactory.newInstance().newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

    // Escritura atomica: se escribe primero en un temporal en el mismo directorio y se publica
    // con Files.move, para no dejar el XML de traducciones truncado si el transform falla a medias.
    Path tmpFile = Files.createTempFile(directory, file.getFileName().toString(), ".tmp");
    try {
      try (OutputStream output = Files.newOutputStream(tmpFile)) {
        transformer.transform(new DOMSource(document), new StreamResult(output));
      }
      Files.move(tmpFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(tmpFile);
    }
  }
}
