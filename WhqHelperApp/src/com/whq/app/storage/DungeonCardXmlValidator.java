package com.whq.app.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class DungeonCardXmlValidator {
    private final Path projectRoot;
    private final Path schemaPath;
    private final Path xmlPath;

    public DungeonCardXmlValidator(Path projectRoot, Path schemaPath, Path xmlPath) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.schemaPath = schemaPath.toAbsolutePath().normalize();
        this.xmlPath = xmlPath.toAbsolutePath().normalize();
    }

    public void validate() throws DungeonCardStorageException {
        if (!Files.exists(xmlPath)) {
            throw new DungeonCardStorageException("No existe el fichero XML de cartas: " + xmlPath + ".");
        }
        if (!Files.exists(schemaPath)) {
            throw new DungeonCardStorageException("No existe el esquema XML de cartas: " + schemaPath + ".");
        }

        try {
            Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(schemaPath.toFile());
            Validator schemaValidator = schema.newValidator();
            schemaValidator.validate(new javax.xml.transform.stream.StreamSource(xmlPath.toFile()));

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().parse(xmlPath.toFile());
            validateSemantics(document);
        } catch (DungeonCardStorageException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DungeonCardStorageException("El XML de cartas de mazmorra no es valido.", ex);
        }
    }

    private void validateSemantics(Document document) throws DungeonCardStorageException {
        NodeList nodes = document.getDocumentElement().getChildNodes();
        Set<Long> ids = new LinkedHashSet<>();

        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE || !"card".equals(node.getNodeName())) {
                continue;
            }

            Element element = (Element) node;
            long id = parseId(element.getAttribute("id"));
            if (!ids.add(id)) {
                throw new DungeonCardStorageException("Hay ids de carta duplicados en " + xmlPath + ".");
            }

            String tileImagePath = childText(element, "tileImagePath");
            if (tileImagePath.isBlank()) {
                throw new DungeonCardStorageException("Una carta no tiene tileImagePath en " + xmlPath + ".");
            }

            Path resolvedTile = projectRoot.resolve(tileImagePath).normalize();
            if (!Files.exists(resolvedTile)) {
                throw new DungeonCardStorageException("No existe la tile referenciada: " + tileImagePath + ".");
            }
        }
    }

    private long parseId(String rawId) throws DungeonCardStorageException {
        try {
            long id = Long.parseLong(rawId);
            if (id <= 0) {
                throw new IllegalArgumentException();
            }
            return id;
        } catch (Exception ex) {
            throw new DungeonCardStorageException("El id de carta no es valido: " + rawId + ".");
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

    public static String defaultSchema() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="dungeonCards">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="card" maxOccurs="unbounded">
                          <xs:complexType>
                            <xs:sequence>
                              <xs:element name="description" type="xs:string"/>
                              <xs:element name="rules" type="xs:string"/>
                              <xs:element name="tileImagePath" type="xs:string"/>
                            </xs:sequence>
                            <xs:attribute name="id" type="xs:positiveInteger" use="required"/>
                            <xs:attribute name="name" type="xs:string" use="required"/>
                            <xs:attribute name="type" use="required">
                              <xs:simpleType>
                                <xs:restriction base="xs:string">
                                  <xs:enumeration value="DUNGEON_ROOM"/>
                                  <xs:enumeration value="OBJECTIVE_ROOM"/>
                                  <xs:enumeration value="CORRIDOR"/>
                                  <xs:enumeration value="SPECIAL"/>
                                </xs:restriction>
                              </xs:simpleType>
                            </xs:attribute>
                            <xs:attribute name="environment" type="xs:string" use="required"/>
                            <xs:attribute name="copyCount" type="xs:nonNegativeInteger" use="required"/>
                            <xs:attribute name="enabled" type="xs:boolean" use="required"/>
                          </xs:complexType>
                        </xs:element>
                      </xs:sequence>
                    </xs:complexType>
                  </xs:element>
                </xs:schema>
                """;
    }
}
