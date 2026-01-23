package com.raks.aegis.util;

import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Collections;

public class CheckHelper {

    /**
     * Centralized value comparison logic.
     */
    public static boolean compareValues(String actual, String expected, String operator, String type) {
        if (actual == null || expected == null) return false;
        if (operator == null) operator = "EQ";
        if (type == null) type = "STRING";
        
        try {
            if ("INTEGER".equalsIgnoreCase(type)) {
                long act = Long.parseLong(actual.trim());
                long exp = Long.parseLong(expected.trim());
                return compareNumeric(act, exp, operator);
            } else if ("SEMVER".equalsIgnoreCase(type)) {
                return compareSemVer(actual.trim(), expected.trim(), operator);
            } else {
                return compareString(actual, expected, operator);
            }
        } catch (NumberFormatException e) {
            return "NEQ".equalsIgnoreCase(operator);
        }
    }

    private static boolean compareNumeric(long a, long b, String op) {
        switch (op.toUpperCase()) {
            case "EQ": return a == b;
            case "NEQ": return a != b;
            case "GT": return a > b;
            case "GTE": return a >= b;
            case "LT": return a < b;
            case "LTE": return a <= b;
            default: return false;
        }
    }

    private static boolean compareString(String a, String b, String op) {
        switch (op.toUpperCase()) {
            case "EQ": return a.equals(b);
            case "NEQ": return !a.equals(b);
            case "CONTAINS": return a.contains(b);
            case "NOT_CONTAINS": return !a.contains(b);
            case "MATCHES": return a.matches(b);
            case "NOT_MATCHES": return !a.matches(b);
            case "GT": return a.compareTo(b) > 0;
            case "GTE": return a.compareTo(b) >= 0;
            case "LT": return a.compareTo(b) < 0;
            case "LTE": return a.compareTo(b) <= 0;
            default: return a.equals(b);
        }
    }

    private static boolean compareSemVer(String v1, String v2, String op) {
        try {
            int result = VersionComparator.compare(v1, v2);
            switch (op.toUpperCase()) {
                case "EQ": return result == 0;
                case "NEQ": return result != 0;
                case "GT": return result > 0;
                case "GTE": return result >= 0;
                case "LT": return result < 0;
                case "LTE": return result <= 0;
                default: return result == 0;
            }
        } catch (Exception e) {
            // Fallback to simple comparison if parsing fails
            return compareString(v1, v2, op);
        }
    }

    /**
     * Evaluates if the count of matching files satisfies the match mode.
     */
    public static boolean evaluateMatchMode(String matchMode, int totalFiles, int matchingFiles) {
        if (matchMode == null || matchMode.isEmpty()) return matchingFiles == totalFiles; 
        switch (matchMode.toUpperCase()) {
            case "ANY_FILE": return matchingFiles > 0;
            case "NONE_OF_FILES": return matchingFiles == 0;
            case "EXACTLY_ONE": return matchingFiles == 1;
            case "ALL_FILES":
            default: return matchingFiles == totalFiles;
        }
    }

    /**
     * Resolves properties and records details into the provided collector.
     */
    public static Set<String> resolveAndRecord(String value, Path projectRoot, Path linkedConfigPath, List<String> searchTokens, boolean alwaysRecord, List<String> resolutionCollector, boolean includeLinked) {
        if (value == null) return Collections.emptySet();
        List<String> tempCollector = new ArrayList<>();
        Set<String> allValues = PropertyResolver.resolveProjectPropertyAll(value, projectRoot, linkedConfigPath, includeLinked, tempCollector);
        
        List<String> relevant = new ArrayList<>();
        for (String resLine : tempCollector) {
            boolean isRelevant = alwaysRecord;
            if (!isRelevant && searchTokens != null) {
                for (String token : searchTokens) {
                    if (resLine.contains(token)) {
                        isRelevant = true;
                        break;
                    }
                }
            }
            if (isRelevant) {
                relevant.add(resLine);
            }
        }
        
        if (resolutionCollector != null) {
            for (String res : relevant) {
                if (!resolutionCollector.contains(res)) {
                    resolutionCollector.add(res);
                }
            }
        }
        
        return allValues;
    }
    /**
     * Checks if a token is present in the content, supporting whole word and case sensitivity.
     */
    public static boolean isTokenPresent(String content, String token, boolean wholeWord, boolean isRegex, boolean caseSensitive) {
        if (content == null || token == null) return false;
        
        if (isRegex) {
            int flags = caseSensitive ? 0 : java.util.regex.Pattern.CASE_INSENSITIVE;
            return java.util.regex.Pattern.compile(token, flags).matcher(content).find();
        } else if (wholeWord) {
            int flags = caseSensitive ? 0 : java.util.regex.Pattern.CASE_INSENSITIVE;
            String regex = "\\b" + java.util.regex.Pattern.quote(token) + "\\b";
            return java.util.regex.Pattern.compile(regex, flags).matcher(content).find();
        } else {
            if (caseSensitive) {
                return content.contains(token);
            } else {
                return content.toLowerCase().contains(token.toLowerCase());
            }
        }
    }
}
