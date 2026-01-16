package com.raks.muleguard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropertyResolver {
    private static final Logger logger = LoggerFactory.getLogger(PropertyResolver.class);
    private final Properties properties = new Properties();
    private static final ObjectMapper mapper = new ObjectMapper();

    public PropertyResolver(Path projectDir) {
        // 1. Load System Env Variables
        System.getenv().forEach(properties::setProperty);

        // 2. Load from pom.xml
        loadFromPom(projectDir.resolve("pom.xml"));

        // 3. Load from mule-artifact.json
        loadFromMuleArtifact(projectDir.resolve("mule-artifact.json"));

        // 4. Load from all .properties files in src/main/resources
        Path resourcesDir = projectDir.resolve("src/main/resources");
        if (Files.isDirectory(resourcesDir)) {
            try (Stream<Path> files = Files.walk(resourcesDir)) {
                files.filter(file -> file.toString().toLowerCase().endsWith(".properties"))
                        .forEach(this::loadPropertiesFromFile);
            } catch (IOException e) {
                logger.warn("Could not scan for property files: {}", e.getMessage());
            }
        }
    }

    private void loadFromPom(Path pomPath) {
        if (!Files.exists(pomPath)) return;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pomPath.toFile());
            
            // Basic properties
            NodeList props = doc.getElementsByTagName("properties");
            if (props.getLength() > 0) {
                NodeList children = props.item(0).getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    if (children.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                        properties.setProperty(children.item(i).getNodeName(), children.item(i).getTextContent().trim());
                    }
                }
            }
            // Project metrics
            NodeList groupId = doc.getElementsByTagName("groupId");
            if (groupId.getLength() > 0) properties.setProperty("project.groupId", groupId.item(0).getTextContent().trim());
            NodeList artifactId = doc.getElementsByTagName("artifactId");
            if (artifactId.getLength() > 0) properties.setProperty("project.artifactId", artifactId.item(0).getTextContent().trim());
            NodeList version = doc.getElementsByTagName("version");
            if (version.getLength() > 0) properties.setProperty("project.version", version.item(0).getTextContent().trim());
            
        } catch (Exception e) {
            logger.warn("Could not load properties from pom.xml: {}", e.getMessage());
        }
    }

    private void loadFromMuleArtifact(Path path) {
        if (!Files.exists(path)) return;
        try {
            JsonNode root = mapper.readTree(path.toFile());
            // Flatten some basic info if needed, or structured keys
            if (root.has("name")) properties.setProperty("mule.artifact.name", root.get("name").asText());
            if (root.has("minMuleVersion")) properties.setProperty("mule.version", root.get("minMuleVersion").asText());
        } catch (Exception e) {
            logger.warn("Could not load mule-artifact.json: {}", e.getMessage());
        }
    }

    private void loadPropertiesFromFile(Path propertyFile) {
        try (InputStream input = new FileInputStream(propertyFile.toFile())) {
            properties.load(input);
        } catch (IOException e) {
            logger.warn("Could not load property file: {}. Error: {}", propertyFile, e.getMessage());
        }
    }

    /**
     * Resolves placeholders in a string. Supports ${key} and #[p('key')]
     */
    public String resolve(String value) {
        if (value == null || value.isEmpty()) return value;

        String resolved = value;
        
        // Handle ${key} - multiple occurrences
        Pattern p1 = Pattern.compile("\\$\\{([^}]+)\\}");
        Matcher m1 = p1.matcher(resolved);
        StringBuilder sb1 = new StringBuilder();
        int lastPos1 = 0;
        while (m1.find()) {
            sb1.append(resolved, lastPos1, m1.start());
            String key = m1.group(1);
            String val = properties.getProperty(key);
            sb1.append(val != null ? val : m1.group(0));
            lastPos1 = m1.end();
        }
        sb1.append(resolved.substring(lastPos1));
        resolved = sb1.toString();

        // Handle #[p('key')] - multiple occurrences
        Pattern p2 = Pattern.compile("#\\[\\s*p\\s*\\(\\s*['\\\"]([^'\\\"]+)['\\\"]\\s*\\)\\s*\\]");
        Matcher m2 = p2.matcher(resolved);
        StringBuilder sb2 = new StringBuilder();
        int lastPos2 = 0;
        while (m2.find()) {
            sb2.append(resolved, lastPos2, m2.start());
            String key = m2.group(1);
            String val = properties.getProperty(key);
            sb2.append(val != null ? val : m2.group(0));
            lastPos2 = m2.end();
        }
        sb2.append(resolved.substring(lastPos2));
        resolved = sb2.toString();

        return resolved;
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }
}
