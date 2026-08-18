package com.whq.app.i18n;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.whq.app.io.SafeXml;

public final class ContentTranslations {

  private static final String RELATIVE_DIR = "data/i18n";

  private record CacheKey(Path projectRoot, Language language) {
  }

  private record CacheEntry(long fileLastModifiedMillis, ContentTranslations translations) {
  }

  // Cache keyed por (projectRoot, idioma), invalidada cuando cambia el idioma (clave distinta)
  // o cuando cambia el lastModified del fichero de traducciones (entrada distinta en la cache).
  private static final Map<CacheKey, CacheEntry> CACHE = new ConcurrentHashMap<>();

  private final Map<String, String> values;

  private ContentTranslations(Map<String, String> values) {
    this.values = values;
  }

  public static ContentTranslations load(Path projectRoot, Language language) {
    if (projectRoot == null || language == null) {
      return new ContentTranslations(new HashMap<>());
    }

    Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
    Path file = translationsFile(normalizedRoot, language);
    long lastModifiedMillis = lastModifiedMillisOrZero(file);

    CacheKey key = new CacheKey(normalizedRoot, language);
    CacheEntry cached = CACHE.get(key);
    if (cached != null && cached.fileLastModifiedMillis() == lastModifiedMillis) {
      return cached.translations();
    }

    ContentTranslations loaded = parse(file);
    CACHE.put(key, new CacheEntry(lastModifiedMillis, loaded));
    return loaded;
  }

  private static Path translationsFile(Path normalizedProjectRoot, Language language) {
    String suffix = language == Language.EN ? "en" : "es";
    return normalizedProjectRoot.resolve(RELATIVE_DIR).resolve("content-" + suffix + ".xml");
  }

  private static long lastModifiedMillisOrZero(Path file) {
    try {
      return Files.getLastModifiedTime(file).toMillis();
    } catch (Exception ignored) {
      return 0L;
    }
  }

  private static ContentTranslations parse(Path file) {
    Map<String, String> map = new HashMap<>();
    if (!Files.isRegularFile(file)) {
      return new ContentTranslations(map);
    }

    try {
      var factory = SafeXml.newFactory();
      factory.setNamespaceAware(false);
      var document = factory.newDocumentBuilder().parse(file.toFile());
      Element root = document.getDocumentElement();
      if (root == null || !"translations".equals(root.getTagName())) {
        return new ContentTranslations(map);
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
        if (!key.isEmpty() && !value.isEmpty()) {
          map.put(key, value);
        }
      }
    } catch (Exception ignored) {
      // If translation parsing fails, runtime content falls back to XML text.
    }

    return new ContentTranslations(map);
  }

  public String t(String key, String fallback) {
    if (key == null || key.isBlank()) {
      return fallback == null ? "" : fallback;
    }
    String translated = values.get(key);
    return translated != null ? translated : (fallback == null ? "" : fallback);
  }
}
