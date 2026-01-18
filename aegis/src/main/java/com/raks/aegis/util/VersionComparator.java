package com.raks.aegis.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionComparator {

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:[.-](.+))?");

    public static int compare(String version1, String version2) {
        if (version1 == null || version2 == null) {
            throw new IllegalArgumentException("Version strings cannot be null");
        }

        VersionParts v1 = parseVersion(version1);
        VersionParts v2 = parseVersion(version2);

        if (v1.major != v2.major) {
            return Integer.compare(v1.major, v2.major);
        }

        if (v1.minor != v2.minor) {
            return Integer.compare(v1.minor, v2.minor);
        }

        if (v1.patch != v2.patch) {
            return Integer.compare(v1.patch, v2.patch);
        }

        return compareQualifiers(v1.qualifier, v2.qualifier);
    }

    public static boolean isGreaterThanOrEqual(String version, String minVersion) {
        return compare(version, minVersion) >= 0;
    }

    public static boolean isLessThanOrEqual(String version, String maxVersion) {
        return compare(version, maxVersion) <= 0;
    }

    public static boolean isGreaterThan(String version, String compareVersion) {
        return compare(version, compareVersion) > 0;
    }

    public static boolean isLessThan(String version, String compareVersion) {
        return compare(version, compareVersion) < 0;
    }

    private static VersionParts parseVersion(String version) {
        Matcher matcher = VERSION_PATTERN.matcher(version.trim());

        if (!matcher.matches()) {

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

    private static int compareQualifiers(String q1, String q2) {
        if (q1 == null && q2 == null) return 0;
        if (q1 == null) return -1;  
        if (q2 == null) return 1;

        boolean isLTS1 = q1.equalsIgnoreCase("LTS");
        boolean isLTS2 = q2.equalsIgnoreCase("LTS");

        if (isLTS1 && isLTS2) return 0;
        if (isLTS1) return 1;  
        if (isLTS2) return -1;

        return q1.compareToIgnoreCase(q2);
    }

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
