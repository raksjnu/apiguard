package com.raks.raksanalyzer.core.analyzer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.Map;
public class JsonAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(JsonAnalyzer.class);
    private final ObjectMapper mapper = new ObjectMapper();
    public Map<String, Object> analyze(File file) {
        try {
            return mapper.readValue(file, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            logger.error("Failed to parse JSON file: {}", file.getName(), e);
            return null;
        }
    }
}
