package com.raks.gitanalyzer.util;

import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.TreeMap;

public class XmlCanonicalizer {

    public static String canonicalize(String xmlFragment) {
        if (xmlFragment == null || xmlFragment.isBlank()) return xmlFragment;

        try {
            // 1. Wrap in dummy root to ensure well-formedness (e.g. for single tags or attributes)
            // Note: If fragment is already a full document with decl, wrapping might fail.
            // Check for XML declaration
            boolean hasDecl = xmlFragment.trim().startsWith("<?xml");
            String toParse = hasDecl ? xmlFragment : "<dummyroot>" + xmlFragment + "</dummyroot>";

            // 2. Parse
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setNamespaceAware(true); // Preserve namespaces
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new InputSource(new StringReader(toParse)));

            // 3. Normalize (Sort Attributes)
            normalizeNode(doc.getDocumentElement());

            // 4. Serialize
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "no"); // Keep compact to avoid whitespace noise

            StringWriter writer = new StringWriter();
            
            // If we wrapped it, we only want children of dummyroot
            if (!hasDecl) {
                NodeList children = doc.getDocumentElement().getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    transformer.transform(new DOMSource(child), new StreamResult(writer));
                }
            } else {
                // Full doc comparison
                transformer.transform(new DOMSource(doc), new StreamResult(writer));
            }

            return writer.toString().trim();

        } catch (Exception e) {
            // If parsing fails, return original (fallback to strict text compare)
            // System.err.println("Canonicalization failed for: " + xmlFragment + " -> " + e.getMessage());
            return xmlFragment.trim();
        }
    }

    private static void normalizeNode(Node node) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            NamedNodeMap attributes = element.getAttributes();
            
            if (attributes != null && attributes.getLength() > 1) {
                TreeMap<String, String> sortedAttrs = new TreeMap<>();
                for (int i = 0; i < attributes.getLength(); i++) {
                    Node attr = attributes.item(i);
                    sortedAttrs.put(attr.getNodeName(), attr.getNodeValue());
                }
                
                // Remove all
                while (attributes.getLength() > 0) {
                    element.removeAttributeNode((Attr) attributes.item(0));
                }
                
                // Add back sorted
                for (Map.Entry<String, String> entry : sortedAttrs.entrySet()) {
                    element.setAttribute(entry.getKey(), entry.getValue());
                }
            }
        }
        
        // Recurse children
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            normalizeNode(children.item(i));
        }
    }
}
