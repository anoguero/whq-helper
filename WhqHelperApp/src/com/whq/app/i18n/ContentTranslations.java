package com.whq.app.i18n;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class ContentTranslations {

  private static final String RELATIVE_DIR = "data/i18n";

  private final Map<String, String> values;

  private ContentTranslations(Map<String, String> values) {
    this.values = values;
  }

  public static ContentTranslations load(Path projectRoot, Language language) {
    Map<String, String> map = new HashMap<>();
    if (projectRoot == null || language == null) {
      return new ContentTranslations(map);
    }

    String suffix = language == Language.EN ? "en" : "es";
    Path file = projectRoot.toAbsolutePath().normalize().resolve(RELATIVE_DIR).resolve("content-" + suffix + ".xml");
    if (!Files.isRegularFile(file)) {
      return new ContentTranslations(map);
    }

    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
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
