package com.raks.apiurlcomparison;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

import java.io.IOException;
import java.util.*;

public class ComparisonEngine {
    private static final Logger logger = LoggerFactory.getLogger(ComparisonEngine.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Headers to always ignore during comparison (dynamic/transport headers)
    private static final Set<String> IGNORED_HEADERS = new HashSet<>(Arrays.asList(
            "Date", "Server", "Transfer-Encoding", "Keep-Alive", "Connection", "ETag", "Last-Modified", 
            "X-Request-ID", "Strict-Transport-Security", "Content-Length", "Vary"
    ));

    public static void compare(ComparisonResult result, String apiType, List<String> ignoredFields, boolean ignoreHeaders) {
        ApiCallResult api1Result = result.getApi1();
        ApiCallResult api2Result = result.getApi2();
        
        logger.info("=== COMPARISON ENGINE START ===");
        logger.info("API Type: {}", apiType);
        
        if (api1Result == null || api2Result == null) {
            logger.warn("One or both API results are null");
            result.setStatus(ComparisonResult.Status.ERROR);
            result.setErrorMessage("One or both API calls failed, cannot compare.");
            return;
        }
        
        // 1. Check HTTP Errors
        int status1 = api1Result.getStatusCode();
        int status2 = api2Result.getStatusCode();
        if (status1 >= 400 || status2 >= 400) {
            result.setStatus(ComparisonResult.Status.ERROR);
            StringBuilder errorMsg = new StringBuilder("HTTP ERROR DETECTED: ");
            if (status1 >= 400 && status2 >= 400) {
                errorMsg.append("Both endpoints failed - API1: ").append(status1).append(", API2: ").append(status2);
            } else if (status1 >= 400) {
                errorMsg.append("API1 failed with HTTP ").append(status1);
            } else {
                errorMsg.append("API2 failed with HTTP ").append(status2);
            }
            result.setErrorMessage(errorMsg.toString());
            result.setDifferences(Collections.singletonList(errorMsg.toString()));
            return;
        }

        List<String> differences = new ArrayList<>();

        // 2. Compare Headers
        if (!ignoreHeaders) {
            compareHeaders(result.getApi1().getResponseHeaders(), result.getApi2().getResponseHeaders(), differences, ignoredFields);
        }

        // 3. Compare Payloads (Body)
        String response1 = api1Result.getResponsePayload();
        String response2 = api2Result.getResponsePayload();
        
        if (response1 == null && response2 == null) {
            // Both empty/null - match (assuming headers matched, otherwise differences has entries)
            if (differences.isEmpty()) {
                 result.setStatus(ComparisonResult.Status.MATCH);
            } else {
                 result.setStatus(ComparisonResult.Status.MISMATCH);
                 result.setDifferences(differences);
            }
            return;
        }
        
        if (response1 == null || response2 == null) {
            differences.add("One response body is null while other is present.");
            result.setStatus(ComparisonResult.Status.MISMATCH);
            result.setDifferences(differences);
            return;
        }

        try {
            boolean isBodyMatch = false;
            if ("SOAP".equalsIgnoreCase(apiType)) {
                // For SOAP/XML
                // Note: Implementing recursive ignore for XML is complex with string manipulation. 
                // Using generic DiffBuilder. If ignoredFields provided, simple text removal fallback or specific node filter could work.
                // For now, standard XML comparison. If user needs extensive XML ignore, might need XSLT or similar.
                try {
                    Diff xmlDiff = DiffBuilder.compare(response1).withTest(response2)
                            .ignoreComments()
                            .ignoreWhitespace()
                            .withNodeFilter(node -> {
                                if (ignoredFields == null || ignoredFields.isEmpty()) return true;
                                return !ignoredFields.contains(node.getNodeName());
                            })
                            .build();
                    isBodyMatch = !xmlDiff.hasDifferences();
                    if (!isBodyMatch) {
                        xmlDiff.getDifferences().forEach(d -> differences.add(d.toString()));
                    }
                } catch (Exception e) {
                   // Fallback
                   isBodyMatch = safeStringEquals(response1, response2);
                   if (!isBodyMatch) differences.add("XML Parsing failed and strings differ.");
                }
            } else {
                // For REST/JSON
                try {
                    JsonNode json1 = objectMapper.readTree(response1);
                    JsonNode json2 = objectMapper.readTree(response2);
                    
                    if (ignoredFields != null && !ignoredFields.isEmpty()) {
                        removeIgnoredFields(json1, ignoredFields);
                        removeIgnoredFields(json2, ignoredFields);
                    }
                    
                    isBodyMatch = json1.equals(json2);
                    if (!isBodyMatch) {
                        differences.addAll(detailedJsonDiff(json1, json2, "$"));
                    }
                } catch (Exception e) {
                    isBodyMatch = safeStringEquals(response1, response2);
                     if (!isBodyMatch) differences.add("JSON Parsing failed and strings differ.");
                }
            }
            
            if (differences.isEmpty()) {
                result.setStatus(ComparisonResult.Status.MATCH);
            } else {
                result.setStatus(ComparisonResult.Status.MISMATCH);
                result.setDifferences(differences);
            }
            
        } catch (Exception e) {
             logger.error("Comparison error", e);
             result.setStatus(ComparisonResult.Status.ERROR);
             result.setErrorMessage("Comparison logic error: " + e.getMessage());
        }
    }

    private static void compareHeaders(Map<String, String> h1, Map<String, String> h2, List<String> diffs, List<String> ignoredFields) {
        if (h1 == null) h1 = Collections.emptyMap();
        if (h2 == null) h2 = Collections.emptyMap();
        
        Set<String> allKeys = new HashSet<>(h1.keySet());
        allKeys.addAll(h2.keySet());
        
        for (String key : allKeys) {
            if (isIgnoredHeader(key)) continue;
            // Also ignore if user requested it specifically
            if (ignoredFields != null && ignoredFields.contains(key)) continue;
            
            String v1 = h1.get(key);
            String v2 = h2.get(key);
            
            if (!Objects.equals(v1, v2)) {
                diffs.add("Header mismatch [" + key + "]: API1='" + v1 + "' vs API2='" + v2 + "'");
            }
        }
    }
    
    private static boolean isIgnoredHeader(String key) {
        for (String ignored : IGNORED_HEADERS) {
            if (ignored.equalsIgnoreCase(key)) return true;
        }
        return false;
    }

    private static void removeIgnoredFields(JsonNode node, List<String> ignoredFields) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            // Create a list of fields to remove to avoid concurrent modification during iteration
            List<String> toRemove = new ArrayList<>();
            Iterator<String> fieldNames = obj.fieldNames();
            while (fieldNames.hasNext()) {
                String name = fieldNames.next();
                if (ignoredFields.contains(name)) { // Name match
                    toRemove.add(name);
                } else {
                    removeIgnoredFields(obj.get(name), ignoredFields);
                }
            }
            toRemove.forEach(obj::remove);
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (JsonNode child : arr) {
                removeIgnoredFields(child, ignoredFields);
            }
        }
    }

    private static boolean safeStringEquals(String s1, String s2) {
        if (s1 == null && s2 == null) return true;
        if (s1 == null || s2 == null) return false;
        return s1.trim().equals(s2.trim());
    }

    private static List<String> detailedJsonDiff(JsonNode node1, JsonNode node2, String path) {
        List<String> differences = new ArrayList<>();
        if (node1.isObject() && node2.isObject()) {
            com.fasterxml.jackson.databind.node.ObjectNode obj1 = (com.fasterxml.jackson.databind.node.ObjectNode) node1;
            com.fasterxml.jackson.databind.node.ObjectNode obj2 = (com.fasterxml.jackson.databind.node.ObjectNode) node2;
            java.util.Iterator<String> fieldNames = obj1.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                String currentPath = path + "." + fieldName;
                if (obj2.has(fieldName)) {
                    differences.addAll(detailedJsonDiff(obj1.get(fieldName), obj2.get(fieldName), currentPath));
                } else {
                    differences.add("Missing field in API 2: " + currentPath);
                }
            }
            obj2.fieldNames().forEachRemaining(fieldName -> {
                if (!obj1.has(fieldName)) {
                    String currentPath = path + "." + fieldName;
                    differences.add("Missing field in API 1: " + currentPath);
                }
            });
        } else if (node1.isArray() && node2.isArray()) {
            com.fasterxml.jackson.databind.node.ArrayNode array1 = (com.fasterxml.jackson.databind.node.ArrayNode) node1;
            com.fasterxml.jackson.databind.node.ArrayNode array2 = (com.fasterxml.jackson.databind.node.ArrayNode) node2;
            int len1 = array1.size();
            int len2 = array2.size();
            int maxLength = Math.max(len1, len2);
            for (int i = 0; i < maxLength; i++) {
                String currentPath = path + "[" + i + "]";
                if (i < len1 && i < len2) {
                    differences.addAll(detailedJsonDiff(array1.get(i), array2.get(i), currentPath));
                } else if (i < len1) {
                    differences.add("Missing element in API 2: " + currentPath);
                } else {
                    differences.add("Missing element in API 1: " + currentPath);
                }
            }
        } else if (!node1.equals(node2)) {
            differences.add(
                    "Values differ at " + path + ". API 1: " + node1.textValue() + ", API 2: " + node2.textValue());
        }
        return differences;
    }
}