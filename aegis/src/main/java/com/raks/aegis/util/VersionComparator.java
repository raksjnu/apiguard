package com.raks.aegis.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for comparing semantic versions.
 * Supports standard version formats like 4.9.0, 4.9.LTS, 3.0.0, etc.
 */
public class VersionComparator {
    
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:[.-](.+))?");
    
    /**
     * Compare two version strings.
     * @param version1 First version
     * @param version2 Second version
     * @return -1 if version1 < version2, 0 if equal, 1 if version1 > version2
     */
    public static int compare(String version1, String version2) {
        if (version1 == null || version2 == null) {
            throw new IllegalArgumentException("Version strings cannot be null");
        }
        
        VersionParts v1 = parseVersion(version1);
        VersionParts v2 = parseVersion(version2);
        
        // Compare major version
        if (v1.major != v2.major) {
            return Integer.compare(v1.major, v2.major);
        }
        
        // Compare minor version
        if (v1.minor != v2.minor) {
            return Integer.compare(v1.minor, v2.minor);
        }
        
        // Compare patch version
        if (v1.patch != v2.patch) {
            return Integer.compare(v1.patch, v2.patch);
        }
        
        // Compare qualifier (e.g., LTS, SNAPSHOT, etc.)
        return compareQualifiers(v1.qualifier, v2.qualifier);
    }
    
    /**
     * Check if version is greater than or equal to minVersion.
     */
    public static boolean isGreaterThanOrEqual(String version, String minVersion) {
        return compare(version, minVersion) >= 0;
    }
    
    /**
     * Check if version is less than or equal to maxVersion.
     */
    public static boolean isLessThanOrEqual(String version, String maxVersion) {
        return compare(version, maxVersion) <= 0;
    }
    
    /**
     * Check if version is strictly greater than compareVersion.
     */
    public static boolean isGreaterThan(String version, String compareVersion) {
        return compare(version, compareVersion) > 0;
    }
    
    /**
     * Check if version is strictly less than compareVersion.
     */
    public static boolean isLessThan(String version, String compareVersion) {
        return compare(version, compareVersion) < 0;
    }
    
    /**
     * Parse version string into components.
     */
    private static VersionParts parseVersion(String version) {
        Matcher matcher = VERSION_PATTERN.matcher(version.trim());
        
        if (!matcher.matches()) {
            // Fallback: try to extract just the numeric parts
            String numericOnly = version.replaceAll("[^0-9.]", "");
            matcher = VERSION_PATTERN.matcher(numericOnly);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid version format: " + version);
            }
        }
        
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
        String qualifier = matcher.group(4);
        
        return new VersionParts(major, minor, patch, qualifier);
    }
    
    /**
     * Compare version qualifiers (LTS, SNAPSHOT, etc.)
     * LTS is considered higher than numeric-only versions.
     */
    private static int compareQualifiers(String q1, String q2) {
        if (q1 == null && q2 == null) return 0;
        if (q1 == null) return -1;  // No qualifier < with qualifier
        if (q2 == null) return 1;
        
        // Special handling for LTS
        boolean isLTS1 = q1.equalsIgnoreCase("LTS");
        boolean isLTS2 = q2.equalsIgnoreCase("LTS");
        
        if (isLTS1 && isLTS2) return 0;
        if (isLTS1) return 1;  // LTS > non-LTS
        if (isLTS2) return -1;
        
        // Lexicographic comparison for other qualifiers
        return q1.compareToIgnoreCase(q2);
    }
    
    /**
     * Internal class to hold version components.
     */
    private static class VersionParts {
        final int major;
        final int minor;
        final int patch;
        final String qualifier;
        
        VersionParts(int major, int minor, int patch, String qualifier) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.qualifier = qualifier;
        }
    }
}
