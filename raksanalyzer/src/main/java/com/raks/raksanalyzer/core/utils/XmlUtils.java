package com.raks.raksanalyzer.core.utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
public class XmlUtils {
    private static final Logger logger = LoggerFactory.getLogger(XmlUtils.class);
    public static Document parseXmlFile(Path xmlFile) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(xmlFile.toFile());
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse XML file: " + xmlFile, e);
        }
    }
    public static String getElementText(Element element) {
        if (element == null) {
            return null;
        }
        return element.getTextContent().trim();
    }
    public static String getAttributeValue(Element element, String attributeName) {
        if (element == null || !element.hasAttribute(attributeName)) {
            return null;
        }
        return element.getAttribute(attributeName);
    }
    public static List<Element> getChildElements(Element parent, String tagName) {
        List<Element> elements = new ArrayList<>();
        NodeList nodeList = parent.getElementsByTagName(tagName);
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                elements.add((Element) node);
            }
        }
        return elements;
    }
    public static Element getFirstChildElement(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return (Element) nodeList.item(0);
        }
        return null;
    }
    public static List<Element> getElementsByTagName(Document document, String tagName) {
        List<Element> elements = new ArrayList<>();
        NodeList nodeList = document.getElementsByTagName(tagName);
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                elements.add((Element) node);
            }
        }
        return elements;
    }
    public static boolean hasAttribute(Element element, String attributeName) {
        return element != null && element.hasAttribute(attributeName);
    }
    public static String getLocalName(Element element) {
        String localName = element.getLocalName();
        return localName != null ? localName : element.getTagName();
    }
    public static String elementToString(Element element) {
        try {
            javax.xml.transform.TransformerFactory tf = javax.xml.transform.TransformerFactory.newInstance();
            javax.xml.transform.Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            java.io.StringWriter writer = new java.io.StringWriter();
            transformer.transform(new javax.xml.transform.dom.DOMSource(element), new javax.xml.transform.stream.StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            logger.warn("Failed to convert element to string", e);
            return element.getTextContent();
        }
    }
    public static String documentToString(Document document) {
        try {
            javax.xml.transform.TransformerFactory tf = javax.xml.transform.TransformerFactory.newInstance();
            javax.xml.transform.Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            java.io.StringWriter writer = new java.io.StringWriter();
            transformer.transform(new javax.xml.transform.dom.DOMSource(document), new javax.xml.transform.stream.StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            logger.warn("Failed to convert document to string", e);
            return "";
        }
    }
}
