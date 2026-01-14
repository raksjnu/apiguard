package com.raks.muleguard;

import org.yaml.snakeyaml.Yaml;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class RulesToJsConverter {
    public static void main(String[] args) {
        try {
            Path yamlPath = Paths.get("src/main/resources/rules/rules.yaml");
            Path jsPath = Paths.get("rules_data.js");

            System.out.println("Reading YAML from: " + yamlPath.toAbsolutePath());
            
            Yaml yaml = new Yaml();
            Map<String, Object> obj = yaml.load(new FileReader(yamlPath.toFile()));
            
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
            
            String jsContent = "const MULEGUARD_RULES_DATA = " + json + ";";
            
            Files.write(jsPath, jsContent.getBytes());
            System.out.println("Successfully created " + jsPath.toAbsolutePath());
            
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
