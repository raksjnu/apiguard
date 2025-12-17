package com.raks.apiurlcomparison;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ComparisonEngine {

    private static final Logger logger = LoggerFactory.getLogger(ComparisonEngine.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void compare(ComparisonResult result, String apiType) {
        ApiCallResult api1Result = result.getApi1();
        ApiCallResult api2Result = result.getApi2();

        if (api1Result == null || api2Result == null) {
            result.setStatus(ComparisonResult.Status.ERROR);
            result.setErrorMessage("One or both API calls failed, cannot compare.");
            return;
        }

        String response1 = api1Result.getResponsePayload();
        String response2 = api2Result.getResponsePayload();

        if (response1 == null || response2 == null) {
            result.setStatus(ComparisonResult.Status.ERROR);
            result.setErrorMessage("One or both API responses are null.");
            return;
        }

        try {
            boolean isMatch = false;
            List<String> differences = new ArrayList<>();

            if ("SOAP".equalsIgnoreCase(apiType)) {
                try {
                    Diff xmlDiff = DiffBuilder.compare(response1).withTest(response2).ignoreComments().build();
                    isMatch = !xmlDiff.hasDifferences();
                    if (!isMatch) {
                        for (org.xmlunit.diff.Difference diff : xmlDiff.getDifferences()) {
                            differences.add(diff.toString());
                        }
                    }
                } catch (Exception e) {
                    // Fallback if XML parsing fails
                    isMatch = safeStringEquals(response1, response2);
                    if (!isMatch)
                        differences.add("XML Parsing failed, and strings differ.");
                }
            } else { // Default to JSON/REST
                try {
                    JsonNode json1 = objectMapper.readTree(response1);
                    JsonNode json2 = objectMapper.readTree(response2);
                    isMatch = json1.equals(json2);
                    if (!isMatch) {
                        differences = detailedJsonDiff(json1, json2, "$");
                    }
                } catch (Exception e) {
                    // Fallback if JSON parsing fails (e.g. HTML 404)
                    isMatch = safeStringEquals(response1, response2);
                    if (!isMatch)
                        differences.add("JSON Parsing failed (possible HTML response?), and strings differ.");
                }
            }

            result.setStatus(isMatch ? ComparisonResult.Status.MATCH : ComparisonResult.Status.MISMATCH);
            result.setDifferences(differences);

        } catch (Exception e) {
            logger.error("Failed to parse or compare responses", e);
            result.setStatus(ComparisonResult.Status.ERROR);
            result.setErrorMessage("Error during response comparison: " + e.getMessage());
        }
    }

    private static boolean safeStringEquals(String s1, String s2) {
        if (s1 == null && s2 == null)
            return true;
        if (s1 == null || s2 == null)
            return false;
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