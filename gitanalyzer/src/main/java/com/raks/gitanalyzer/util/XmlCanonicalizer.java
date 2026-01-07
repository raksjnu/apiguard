package com.raks.gitanalyzer.util;

import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XmlCanonicalizer {

    private static final Pattern TAG_PATTERN = Pattern.compile("^<([\\w:-]+)(\\s+[^>]*?)\\s*(/?)>$");
    private static final Pattern ATTR_PATTERN = Pattern.compile("([\\w:-]+)\\s*=\\s*(['\"])((?:\\\\\\2|.)*?)\\2");

    public static String canonicalize(String xmlFragment) {
        if (xmlFragment == null || xmlFragment.isBlank()) return xmlFragment;

        String input = xmlFragment.trim();

        // 1. Try to match a single XML tag (e.g. <flow name="a" ... >)
        Matcher tagMatcher = TAG_PATTERN.matcher(input);
        if (tagMatcher.find()) {
            String tagName = tagMatcher.group(1);
            String attributes = tagMatcher.group(2);
            String selfClose = tagMatcher.group(3); // "/" or ""

            // 2. Extract and Sort Attributes
            Map<String, String> sortedAttrs = new TreeMap<>();
            Matcher attrMatcher = ATTR_PATTERN.matcher(attributes);
            
            while (attrMatcher.find()) {
                String key = attrMatcher.group(1);
                String quote = attrMatcher.group(2);
                String value = attrMatcher.group(3);
                // Reconstruct exact value including quotes
                sortedAttrs.put(key, quote + value + quote);
            }

            // 3. Reconstruct
            return reconstruct(tagName, sortedAttrs, selfClose);
        }

        // Fallback: If not a simple tag (e.g. text node or complex), return original
        return input;
    }

    private static String reconstruct(String tagName, Map<String, String> sortedAttrs, String selfClose) {
        StringBuilder sb = new StringBuilder();
        sb.append("<").append(tagName);

        // TreeMap iterates identifiers in alphabetical order
        for (Map.Entry<String, String> entry : sortedAttrs.entrySet()) {
            sb.append(" ").append(entry.getKey()).append("=").append(entry.getValue());
        }

        if (selfClose != null && !selfClose.isEmpty()) {
             sb.append(" /");
        }
        sb.append(">");
        return sb.toString();
    }
}
