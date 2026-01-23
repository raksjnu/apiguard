package com.raks.aegis.checks;

import com.raks.aegis.model.Check;
import java.util.HashMap;
import java.util.Map;

public class CheckFactory {
    private static Map<String, Class<? extends AbstractCheck>> registry = new HashMap<>();

    static {

        registry.put("TOKEN_SEARCH", TokenSearchCheck.class);
        registry.put("XML_GENERIC", XmlGenericCheck.class);
        registry.put("JSON_GENERIC", JsonGenericCheck.class);
        registry.put("PROPERTY_GENERIC", PropertyGenericCheck.class);

        registry.put("POM_VALIDATION_REQUIRED", PomValidationRequiredCheck.class);
        registry.put("POM_VALIDATION_FORBIDDEN", PomValidationForbiddenCheck.class);
        registry.put("CONDITIONAL_CHECK", ConditionalCheck.class);
        registry.put("PROJECT_CONTEXT", ProjectContextCheck.class);
    }

    public static AbstractCheck create(Check check) {
        String type = check.getType();

        Map<String, Object> effectiveParams = check.getParams() != null ? new HashMap<>(check.getParams()) : new HashMap<>();

        if (effectiveParams.containsKey("filePattern")) {
            effectiveParams.put("filePatterns", java.util.Collections.singletonList(effectiveParams.get("filePattern")));
        }
        if (effectiveParams.containsKey("targetFiles")) {
            effectiveParams.put("filePatterns", effectiveParams.get("targetFiles"));
        }

        if (effectiveParams.containsKey("fileExtensions")) {
             Object extObj = effectiveParams.get("fileExtensions");
             if (extObj instanceof java.util.List) {
                 java.util.List<?> exts = (java.util.List<?>) extObj;
                 java.util.List<String> patterns = new java.util.ArrayList<>();
                 for (Object o : exts) {
                     String ext = o.toString();
                     if (ext.startsWith(".")) {
                         patterns.add("**/*" + ext);
                     } else {
                         patterns.add("**/*." + ext);
                     }
                 }
                 effectiveParams.put("filePatterns", patterns);
             }
        }

        AbstractCheck instance = null;

        if (type.equals("GENERIC_TOKEN_SEARCH") || type.equals("GENERIC_TOKEN_SEARCH_FORBIDDEN") || type.equals("GENERIC_TOKEN_SEARCH_REQUIRED") || type.equals("MANDATORY_SUBSTRING_CHECK") || type.equals("GENERIC_CODE_CHECK") || type.equals("GENERIC_CODE_TOKEN_CHECK") || type.equals("SUBSTRING_TOKEN_CHECK") || type.equals("DLP_REFERENCE_CHECK")  || type.equals("FORBIDDEN_TOKEN_IN_ELEMENT") || type.equals("GENERIC_CONFIG_TOKEN_CHECK")) {

            if (!effectiveParams.containsKey("mode")) {
                if (type.contains("REQUIRED") || type.contains("MANDATORY")) {
                    effectiveParams.put("mode", "REQUIRED");
                } else {
                    effectiveParams.put("mode", "FORBIDDEN");
                }
            }
            instance = new TokenSearchCheck();
        }

        else if (type.equals("XML_XPATH_EXISTS") || type.equals("XML_ATTRIBUTE_EXISTS") || type.equals("XML_XPATH_NOT_EXISTS") || type.equals("XML_ATTRIBUTE_NOT_EXISTS") || type.equals("XML_ELEMENT_CONTENT_REQUIRED") || type.equals("XML_ELEMENT_CONTENT_FORBIDDEN") || type.equals("XML_XPATH_OPTIONAL")) {
            if (!effectiveParams.containsKey("mode")) {
                if (type.contains("NOT_EXISTS") || type.contains("FORBIDDEN")) effectiveParams.put("mode", "NOT_EXISTS");
                else if (type.contains("OPTIONAL")) effectiveParams.put("mode", "OPTIONAL_MATCH");
                else effectiveParams.put("mode", "EXISTS");
            }

            if (!effectiveParams.containsKey("xpath") && effectiveParams.containsKey("xpathExpressions")) {
                Object exprsObj = effectiveParams.get("xpathExpressions");
                if (exprsObj instanceof java.util.List) {
                    java.util.List<?> list = (java.util.List<?>) exprsObj;
                    if (!list.isEmpty()) {
                        Object first = list.get(0);
                        if (first instanceof Map) {
                            String validXpath = (String) ((Map<?, ?>) first).get("xpath");
                            effectiveParams.put("xpath", validXpath);
                        }
                    }
                }
            }

            if (!effectiveParams.containsKey("xpath") && effectiveParams.containsKey("elements") && effectiveParams.containsKey("forbiddenAttributes")) {
                @SuppressWarnings("unchecked")
                java.util.List<String> elements = (java.util.List<String>) effectiveParams.get("elements");
                @SuppressWarnings("unchecked")
                java.util.List<String> attrs = (java.util.List<String>) effectiveParams.get("forbiddenAttributes");
                if (elements != null && !elements.isEmpty() && attrs != null && !attrs.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (String el : elements) {
                        for (String at : attrs) {
                            if (sb.length() > 0) sb.append(" | ");
                            sb.append("//*[local-name()='").append(el).append("' and @").append(at).append("]");
                        }
                    }
                     effectiveParams.put("xpath", sb.toString());
                 }
            }

            if (!effectiveParams.containsKey("xpath") && effectiveParams.containsKey("elementAttributeSets")) {
                 @SuppressWarnings("unchecked")
                 java.util.List<Map<String, Object>> sets = (java.util.List<Map<String, Object>>) effectiveParams.get("elementAttributeSets");
                 if (sets != null && !sets.isEmpty()) {
                     StringBuilder sb = new StringBuilder();
                     for (Map<String, Object> set : sets) {
                         String el = (String) set.get("element");

                         String localName = (el != null && el.contains(":")) ? el.substring(el.lastIndexOf(":") + 1) : el;

                         @SuppressWarnings("unchecked")
                         Map<String, String> attrs = (Map<String, String>) set.get("attributes");
                         if (el != null && attrs != null && !attrs.isEmpty()) {
                             for (Map.Entry<String, String> entry : attrs.entrySet()) {
                                 if (sb.length() > 0) sb.append(" | ");
                                 sb.append("//*[local-name()='").append(localName).append("' and @").append(entry.getKey()).append("='").append(entry.getValue()).append("']");
                             }
                         }
                     }
                     effectiveParams.put("xpath", sb.toString());
                 }
            }
             if (!effectiveParams.containsKey("xpath") && effectiveParams.containsKey("element")) {
                  String el = (String) effectiveParams.get("element");
                  String localName = (el != null && el.contains(":")) ? el.substring(el.lastIndexOf(":") + 1) : el;
                  effectiveParams.put("xpath", "//*[local-name()='" + localName + "']");
             }

            instance = new XmlGenericCheck();
        }
        else if (type.equals("GENERIC_XML_VALIDATION")) {
             instance = new XmlGenericCheck();
        }

        else if (type.equals("JSON_VALIDATION_REQUIRED") || type.equals("JSON_VALIDATION_OPTIONAL")) {
            if (!effectiveParams.containsKey("jsonPath")) {
                effectiveParams.put("jsonPath", "$");
            }
            if (!effectiveParams.containsKey("mode")) {
                if (type.contains("OPTIONAL")) effectiveParams.put("mode", "OPTIONAL_MATCH");
                else if (effectiveParams.containsKey("expectedValue") || effectiveParams.containsKey("requiredFields") || effectiveParams.containsKey("requiredElements")) {
                    effectiveParams.put("mode", "VALUE_MATCH");
                } else {
                    effectiveParams.put("mode", "EXISTS");
                }
            }
            instance = new JsonGenericCheck();
        }
        else if (type.equals("JSON_VALIDATION_FORBIDDEN")) {
            effectiveParams.put("mode", "NOT_EXISTS");

            if (!effectiveParams.containsKey("jsonPath") && effectiveParams.containsKey("forbiddenElements")) {
                @SuppressWarnings("unchecked")
                java.util.List<String> forbidden = (java.util.List<String>) effectiveParams.get("forbiddenElements");
                if (forbidden != null && !forbidden.isEmpty()) {
                     StringBuilder sb = new StringBuilder("$['");
                     sb.append(String.join("','", forbidden));
                     sb.append("']");
                     effectiveParams.put("jsonPath", sb.toString());
                }
            }
            instance = new JsonGenericCheck();
        }

        else if (type.equals("GENERIC_PROPERTY_FILE") || type.equals("CONFIG_PROPERTY_EXISTS") || type.equals("CONFIG_POLICY_EXISTS")) {
            effectiveParams.put("mode", "EXISTS");
            instance = new PropertyGenericCheck();
        }
        else if (type.equals("MANDATORY_PROPERTY_VALUE_CHECK")) {
            effectiveParams.put("mode", "VALUE_MATCH");
            instance = new PropertyGenericCheck();
        }
        else if (type.equals("OPTIONAL_PROPERTY_VALUE_CHECK")) {
            effectiveParams.put("mode", "OPTIONAL_MATCH");
            instance = new PropertyGenericCheck();
        }
        else {

             try {
                Class<? extends AbstractCheck> clazz = registry.get(type);
                if (clazz == null)
                    throw new IllegalArgumentException("Unknown check type: " + type);
                instance = clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create check: " + check.getType(), e);
            }
        }

        if (instance != null) {
            instance.init(effectiveParams);
        }

        return instance;
    }
}
