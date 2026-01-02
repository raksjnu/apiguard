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
            // Priority 1: Local Filesystem
            Path path = Paths.get(templatePath);
            if (Files.exists(path) && !Files.isDirectory(path)) {
                return new String(Files.readAllBytes(path));
            }
            
            // Priority 2: Classpath Resource (Packaged in JAR)
            String resourcePath = templatePath.replace('\\', '/');
            if (!resourcePath.startsWith("/")) {
                resourcePath = "/" + resourcePath;
            }
            
            try (java.io.InputStream is = getClass().getResourceAsStream(resourcePath)) {
                if (is != null) {
                    return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }

            // Fallback: Return raw string if it's not a file path
            return templatePath;
        } catch (Exception e) {
            return templatePath;
        }
    }
    public String process(Map<String, Object> iterationTokens) {
        try {
            if ("SOAP".equalsIgnoreCase(apiType)) {
                return processXml(iterationTokens);
            } else { 
                return processJson(iterationTokens);
            }
        } catch (Exception e) {
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
                for (Map.Entry<String, Object> token : tokens.entrySet()) {
                    if (fieldName.toLowerCase().contains(token.getKey().toLowerCase())) {
                        Object value = token.getValue();
                        if (value instanceof Number) {
                            if (childNode.isNumber()) {
                                objectNode.put(fieldName, ((Number) value).doubleValue());
                            } else {
                                objectNode.put(fieldName, String.valueOf(value));
                            }
                        } else {
                            objectNode.put(fieldName, String.valueOf(value));
                        }
                        break; 
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
                for (Map.Entry<String, Object> token : tokens.entrySet()) {
                    if (nodeName.toLowerCase().contains(token.getKey().toLowerCase())) {
                        child.setTextContent(String.valueOf(token.getValue()));
                        break; 
                    }
                }
            }
            traverseAndReplaceXml(child, tokens);
        }
    }
}