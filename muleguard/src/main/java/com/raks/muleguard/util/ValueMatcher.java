package com.raks.muleguard.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValueMatcher {

    public enum MatchMode {
        EXACT,
        SUBSTRING,
        REGEX,
        SEMANTIC_VERSION
    }

    private static final Pattern VERSION_OP_PATTERN = Pattern.compile("^([<>]=?|=)(.*)$");

    public static boolean matches(String actual, String expected, MatchMode mode, boolean caseSensitive) {
        if (actual == null || expected == null) {
            return false;
        }

        switch (mode) {
            case SUBSTRING:
                if (caseSensitive) {
                    return actual.contains(expected);
                } else {
                    return actual.toLowerCase().contains(expected.toLowerCase());
                }
            case REGEX:
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                Pattern p = Pattern.compile(expected, flags);
                return p.matcher(actual).matches();
            case SEMANTIC_VERSION:
                return satisfiesVersion(actual, expected);
            case EXACT:
            default:
                if (caseSensitive) {
                    return actual.equals(expected);
                } else {
                    return actual.equalsIgnoreCase(expected);
                }
        }
    }

    private static boolean satisfiesVersion(String actual, String criteria) {
        String operator = "=";
        String versionCriteria = criteria.trim();

        Matcher m = VERSION_OP_PATTERN.matcher(versionCriteria);
        if (m.find()) {
            operator = m.group(1);
            versionCriteria = m.group(2).trim();
        }

        int comparison = compareVersions(actual, versionCriteria);

        switch (operator) {
            case ">":
                return comparison > 0;
            case ">=":
                return comparison >= 0;
            case "<":
                return comparison < 0;
            case "<=":
                return comparison <= 0;
            case "=":
            default:
                // For direct equality in semver, 4.9 == 4.9.0
                return comparison == 0;
        }
    }

    private static int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int num1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int num2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            if (num1 < num2)
                return -1;
            if (num1 > num2)
                return 1;
        }
        return 0;
    }

    private static int parseVersionPart(String part) {
         // Remove any non-numeric suffixes (e.g., -SNAPSHOT, -beta) for basic integer comparison
         // This is a simplified semver approach where 1.0.0-SNAPSHOT is treated effectively as 1.0.0 for the numeric part
         // For stricter semver, a more complex parser would be needed.
         String numericPart = part.replaceAll("[^0-9].*$", "");
         if (numericPart.isEmpty()) return 0;
         try {
             return Integer.parseInt(numericPart);
         } catch (NumberFormatException e) {
             return 0;
         }
    }
}
