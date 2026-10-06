package com.whq.app.i18n;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.whq.app.AppPaths;
import com.whq.app.io.SafeXml;

public final class ContentTranslations {

  private static final String RELATIVE_DIR = "data/i18n";

  private record CacheKey(Path projectRoot, Language language) {
  }

  // fileSizeBytes se comprueba junto con el lastModified: en sistemas de ficheros con
  // granularidad de segundo, dos escrituras dentro del mismo tick tendrian el mismo
  // lastModified pero (casi siempre) tamanyo distinto, y la cache debe invalidarse igualmente.
  private record CacheEntry(
      long fileLastModifiedMillis,
      long fileSizeBytes,
      long userFileLastModifiedMillis,
      long userFileSizeBytes,
      ContentTranslations translations) {
  }

  // Cache keyed por (projectRoot, idioma), invalidada cuando cambia el idioma (clave distinta)
  // o cuando cambia el lastModified/tamanyo del fichero de traducciones (entrada distinta en la cache).
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
    Path userFile = userTranslationsFile(normalizedRoot, language);
    long lastModifiedMillis = lastModifiedMillisOrZero(file);
    long sizeBytes = sizeOrZero(file);
    long userLastModifiedMillis = lastModifiedMillisOrZero(userFile);
    long userSizeBytes = sizeOrZero(userFile);

    CacheKey key = new CacheKey(normalizedRoot, language);
    CacheEntry cached = CACHE.get(key);
    if (cached != null
        && cached.fileLastModifiedMillis() == lastModifiedMillis
        && cached.fileSizeBytes() == sizeBytes
        && cached.userFileLastModifiedMillis() == userLastModifiedMillis
        && cached.userFileSizeBytes() == userSizeBytes) {
      return cached.translations();
    }

    Map<String, String> values = parse(file);
    values.putAll(parse(userFile));
    ContentTranslations loaded = new ContentTranslations(values);
    CACHE.put(key, new CacheEntry(lastModifiedMillis, sizeBytes, userLastModifiedMillis, userSizeBytes, loaded));
    return loaded;
  }

  // Traducciones del contenido base: shared home, solo lectura.
  static Path translationsFile(Path normalizedProjectRoot, Language language) {
    return AppPaths.contentPath(normalizedProjectRoot, RELATIVE_DIR).resolve("content-" + suffix(language) + ".xml");
  }

  // Traducciones del contenido del usuario: runtime home escribible, aplicadas sobre las base.
  static Path userTranslationsFile(Path normalizedProjectRoot, Language language) {
    return normalizedProjectRoot.resolve(RELATIVE_DIR).resolve("userdefined-content-" + suffix(language) + ".xml");
  }

  private static String suffix(Language language) {
    return language == Language.EN ? "en" : "es";
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

  static Map<String, String> parse(Path file) {
    Map<String, String> map = new HashMap<>();
    if (!Files.isRegularFile(file)) {
      return map;
    }

    try {
      var factory = SafeXml.newFactory();
      factory.setNamespaceAware(false);
      var document = factory.newDocumentBuilder().parse(file.toFile());
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
        if (!key.isEmpty() && !value.isEmpty()) {
          map.put(key, value);
        }
      }
    } catch (Exception ignored) {
      // If translation parsing fails, runtime content falls back to XML text.
    }

    return map;
  }

  public String t(String key, String fallback) {
    if (key == null || key.isBlank()) {
      return fallback == null ? "" : fallback;
    }
    String translated = values.get(key);
    return translated != null ? translated : (fallback == null ? "" : fallback);
  }
}
