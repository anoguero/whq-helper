package com.whq.app.io;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Punto único para crear {@link DocumentBuilderFactory}/{@link DocumentBuilder} con las
 * protecciones estándar contra XXE y expansión de entidades: sin DOCTYPE, sin entidades
 * externas (generales ni de parámetro), sin XInclude y sin expansión de referencias a entidad.
 */
public final class SafeXml {

    private SafeXml() {
    }

    /** Factory endurecida. namespaceAware queda en false (el valor por defecto de la JDK); el llamante lo ajusta si lo necesita. */
    public static DocumentBuilderFactory newFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        harden(factory);
        return factory;
    }

    /** Atajo para el caso mas comun: un DocumentBuilder de usar y tirar. */
    public static DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
        return newFactory().newDocumentBuilder();
    }

    private static void harden(DocumentBuilderFactory factory) {
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (ParserConfigurationException ignored) {
            // Si el parser subyacente no soporta alguna de estas features, seguimos igualmente
            // con XInclude y la expansion de entidades desactivados como defensa adicional.
        }
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
    }
}
