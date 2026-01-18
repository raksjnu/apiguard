package com.raks.aegis;

import org.yaml.snakeyaml.Yaml;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RulesToJsConverter {
    private static final Logger logger = LoggerFactory.getLogger(RulesToJsConverter.class);
    public static void main(String[] args) {
        try {
            Path yamlPath = Paths.get("src/main/resources/rules/rules.yaml");
            Path jsPath = Paths.get("rules_data.js");

            logger.info("Reading YAML from: {}", yamlPath.toAbsolutePath());

            Yaml yaml = new Yaml();
            Map<String, Object> obj = yaml.load(new FileReader(yamlPath.toFile()));

            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);

            String jsContent = "const Aegis_RULES_DATA = " + json + ";";

            Files.write(jsPath, jsContent.getBytes());
            logger.info("Successfully created {}", jsPath.toAbsolutePath());

        } catch (Exception e) {
            logger.error("Error converting rules to JS", e);
            System.exit(1);
        }
    }
}
