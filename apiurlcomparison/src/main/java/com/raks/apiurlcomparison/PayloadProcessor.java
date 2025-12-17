package com.raks.apiurlcomparison;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;

public class PayloadProcessor {

    private final String templateContent;
    private final String apiType;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * Constructor for PayloadProcessor.
     * 
     * @param payloadTemplatePath The path to the payload template file OR raw
     *                            content.
     * @param apiType             The type of API ("REST" for JSON, "SOAP" for XML).
     * @throws IOException If the input is a file path and cannot be read.
     */
    public PayloadProcessor(String payloadTemplatePath, String apiType) throws IOException {
        this.apiType = apiType;
        if (payloadTemplatePath == null) {
            this.templateContent = "";
        } else {
            this.templateContent = loadTemplate(payloadTemplatePath);
        }
    }

    private String loadTemplate(String templatePath) throws IOException {
        try {
            Path path = Paths.get(templatePath);
            if (Files.exists(path) && !Files.isDirectory(path)) {
                return new String(Files.readAllBytes(path));
            } else {
                // Assume raw content if file does not exist
                return templatePath;
            }
        } catch (Exception e) {
            // If Paths.get fails (invalid chars for path), treat as raw content
            return templatePath;
        }
    }

    /**
     * Processes the template with the given token values for an iteration.
     * 
     * @param iterationTokens A map containing token names and their values for the
     *                        current iteration.
     * @return The processed payload as a String, or the original template on
     *         failure.
     */
    public String process(Map<String, Object> iterationTokens) {
        try {
            if ("SOAP".equalsIgnoreCase(apiType)) {
                return processXml(iterationTokens);
            } else { // Default to JSON
                return processJson(iterationTokens);
            }
        } catch (Exception e) {
            // System.err.println("Error processing payload, returning original template.
            // Error: " + e.getMessage());
            // Fail safe: return raw template
            return templateContent;
        }
    }

    private String processJson(Map<String, Object> tokens) throws IOException {
        if (templateContent == null || templateContent.trim().isEmpty())
            return "";
        JsonNode rootNode = jsonMapper.readTree(templateContent);
        traverseAndReplaceJson(rootNode, tokens);
        return jsonMapper.writeValueAsString(rootNode);
    }

    private void traverseAndReplaceJson(JsonNode node, Map<String, Object> tokens) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldName = field.getKey();
                JsonNode childNode = field.getValue();

                // Check if the field name contains a token key (case-insensitive)
                for (Map.Entry<String, Object> token : tokens.entrySet()) {
                    if (fieldName.toLowerCase().contains(token.getKey().toLowerCase())) {
                        Object value = token.getValue();
                        if (value instanceof Number) {
                            // If the original node was a number, keep it as a number
                            if (childNode.isNumber()) {
                                objectNode.put(fieldName, ((Number) value).doubleValue());
                            } else {
                                objectNode.put(fieldName, String.valueOf(value));
                            }
                        } else {
                            objectNode.put(fieldName, String.valueOf(value));
                        }
                        break; // Token found and replaced, move to the next field
                    }
                }
                traverseAndReplaceJson(childNode, tokens);
            }
        } else if (node.isArray()) {
            for (JsonNode arrayElement : node) {
                traverseAndReplaceJson(arrayElement, tokens);
            }
        }
    }

    private String processXml(Map<String, Object> tokens) throws Exception {
        if (templateContent == null || templateContent.trim().isEmpty())
            return "";
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(templateContent.getBytes()));

        traverseAndReplaceXml(doc.getDocumentElement(), tokens);

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    private void traverseAndReplaceXml(Node node, Map<String, Object> tokens) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String nodeName = child.getNodeName();
                // Check if the XML element name contains a token key (case-insensitive)
                for (Map.Entry<String, Object> token : tokens.entrySet()) {
                    if (nodeName.toLowerCase().contains(token.getKey().toLowerCase())) {
                        child.setTextContent(String.valueOf(token.getValue()));
                        break; // Token found and replaced, move to the next node
                    }
                }
            }
            traverseAndReplaceXml(child, tokens);
        }
    }
}