package com.whq.app.i18n;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class EditableContentTranslations {

  private static final String RELATIVE_DIR = "data/i18n";

  private final Path file;
  private final Map<String, String> values;

  private EditableContentTranslations(Path file, Map<String, String> values) {
    this.file = file;
    this.values = values;
  }

  public static EditableContentTranslations load(Path projectRoot, Language language) {
    Map<String, String> map = new LinkedHashMap<>();
    if (projectRoot == null || language == null) {
      return new EditableContentTranslations(null, map);
    }

    String suffix = language == Language.EN ? "en" : "es";
    Path file =
        projectRoot
            .toAbsolutePath()
            .normalize()
            .resolve(RELATIVE_DIR)
            .resolve("content-" + suffix + ".xml");

    if (!Files.isRegularFile(file)) {
      return new EditableContentTranslations(file, map);
    }

    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(false);
      Document document = factory.newDocumentBuilder().parse(file.toFile());
      Element root = document.getDocumentElement();
      if (root == null || !"translations".equals(root.getTagName())) {
        return new EditableContentTranslations(file, map);
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

    return new EditableContentTranslations(file, map);
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
    Files.createDirectories(file.getParent());

    Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
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
    try (OutputStream output = Files.newOutputStream(file)) {
      transformer.transform(new DOMSource(document), new StreamResult(output));
    }
  }
}
