package com.raks.aegis.checks;

import com.raks.aegis.model.Check;
import java.util.HashMap;
import java.util.Map;

public class CheckFactory {
    private static Map<String, Class<? extends AbstractCheck>> registry = new HashMap<>();
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CheckFactory.class);

    static {
        // Universal Checks
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
        Map<String, Object> params = check.getParams();
        if (params == null) params = new HashMap<>(); // Safety
        

        if (params.containsKey("filePattern")) {
            params.put("filePatterns", java.util.Collections.singletonList(params.get("filePattern")));
        }
        if (params.containsKey("targetFiles")) {
            params.put("filePatterns", params.get("targetFiles"));
        }
        
        if (params.containsKey("fileExtensions")) {
             Object extObj = params.get("fileExtensions");
             if (extObj instanceof java.util.List) {
                 java.util.List<?> exts = (java.util.List<?>) extObj;
                 java.util.List<String> patterns = new java.util.ArrayList<>();
                 for (Object o : exts) {
                     patterns.add("**/*." + o.toString());
                 }
                 params.put("filePatterns", patterns);
             }
        }

        if (!params.containsKey("filePatterns") && params.containsKey("directory")) {

        }

        if (type.equals("GENERIC_TOKEN_SEARCH") || type.equals("GENERIC_TOKEN_SEARCH_FORBIDDEN") || type.equals("GENERIC_TOKEN_SEARCH_REQUIRED") || type.equals("MANDATORY_SUBSTRING_CHECK") || type.equals("GENERIC_CODE_CHECK") || type.equals("GENERIC_CODE_TOKEN_CHECK") || type.equals("SUBSTRING_TOKEN_CHECK") || type.equals("DLP_REFERENCE_CHECK")  || type.equals("FORBIDDEN_TOKEN_IN_ELEMENT") || type.equals("GENERIC_CONFIG_TOKEN_CHECK")) {
            
            if (type.contains("REQUIRED") || type.contains("MANDATORY")) {
                params.put("mode", "REQUIRED");
            } else {
                params.put("mode", "FORBIDDEN");
            }
            check.setParams(params);
            return new TokenSearchCheck();
        }


        if (type.equals("XML_XPATH_EXISTS") || type.equals("XML_ATTRIBUTE_EXISTS") || type.equals("XML_XPATH_NOT_EXISTS") || type.equals("XML_ATTRIBUTE_NOT_EXISTS")) {
            if (type.contains("NOT_EXISTS")) params.put("mode", "NOT_EXISTS");
            else params.put("mode", "EXISTS");
            
            
            if (!params.containsKey("xpath") && params.containsKey("xpathExpressions")) {
                Object exprsObj = params.get("xpathExpressions");
                if (exprsObj instanceof java.util.List) {
                    java.util.List<?> list = (java.util.List<?>) exprsObj;
                    if (!list.isEmpty()) {
                        Object first = list.get(0);
                        if (first instanceof Map) {
                            String validXpath = (String) ((Map<?, ?>) first).get("xpath");
                            params.put("xpath", validXpath);
                        }
                    }
                }
            }
            
            if (!params.containsKey("xpath") && params.containsKey("elements") && params.containsKey("forbiddenAttributes")) {
                @SuppressWarnings("unchecked")
                java.util.List<String> elements = (java.util.List<String>) params.get("elements");
                @SuppressWarnings("unchecked")
                java.util.List<String> attrs = (java.util.List<String>) params.get("forbiddenAttributes");
                if (elements != null && !elements.isEmpty() && attrs != null && !attrs.isEmpty()) {
                    // Construct: //element[@attr1] | //element[@attr2]
                    StringBuilder sb = new StringBuilder();
                    for (String el : elements) {
                        for (String at : attrs) {
                            if (sb.length() > 0) sb.append(" | ");
                            sb.append("//").append(el).append("[@").append(at).append("]");
                        }
                    }
                    params.put("xpath", sb.toString());
                }
            }
            
            
            if (!params.containsKey("xpath") && params.containsKey("elementAttributeSets")) {
                 @SuppressWarnings("unchecked")
                 java.util.List<Map<String, Object>> sets = (java.util.List<Map<String, Object>>) params.get("elementAttributeSets");
                 if (sets != null && !sets.isEmpty()) {
                     StringBuilder sb = new StringBuilder();
                     for (Map<String, Object> set : sets) {
                         String el = (String) set.get("element");
                         @SuppressWarnings("unchecked")
                         Map<String, String> attrs = (Map<String, String>) set.get("attributes");
                         if (el != null && attrs != null && !attrs.isEmpty()) {
                             for (Map.Entry<String, String> entry : attrs.entrySet()) {
                                 if (sb.length() > 0) sb.append(" | ");
                                 // //element[@attr='val']
                                 sb.append("//").append(el).append("[@").append(entry.getKey()).append("='").append(entry.getValue()).append("']");
                             }
                         }
                     }
                     params.put("xpath", sb.toString());
                 }
            }
            
            check.setParams(params);
            return new XmlGenericCheck();
        }
        if (type.equals("GENERIC_XML_VALIDATION")) {
             return new XmlGenericCheck();
        }


        if (type.equals("JSON_VALIDATION_REQUIRED")) {

            if (!params.containsKey("jsonPath")) {
                params.put("jsonPath", "$");
            }
            params.put("mode", "EXISTS"); 
            if (params.containsKey("expectedValue")) params.put("mode", "VALUE_MATCH");
            check.setParams(params);
            return new JsonGenericCheck();
        }
        if (type.equals("JSON_VALIDATION_FORBIDDEN")) {
            params.put("mode", "NOT_EXISTS");

            if (!params.containsKey("jsonPath") && params.containsKey("forbiddenElements")) {
                @SuppressWarnings("unchecked")
                java.util.List<String> forbidden = (java.util.List<String>) params.get("forbiddenElements");
                if (forbidden != null && !forbidden.isEmpty()) {
                     StringBuilder sb = new StringBuilder("$['");
                     sb.append(String.join("','", forbidden));
                     sb.append("']");
                     params.put("jsonPath", sb.toString());
                }
            }
            check.setParams(params);
            return new JsonGenericCheck();
        }


        if (type.equals("GENERIC_PROPERTY_FILE") || type.equals("CONFIG_PROPERTY_EXISTS") || type.equals("CONFIG_POLICY_EXISTS")) {
            params.put("mode", "EXISTS");
            check.setParams(params);
            return new PropertyGenericCheck();
        }
        if (type.equals("MANDATORY_PROPERTY_VALUE_CHECK")) {
            params.put("mode", "VALUE_MATCH");
            check.setParams(params);
            return new PropertyGenericCheck();
        }
        if (type.equals("OPTIONAL_PROPERTY_VALUE_CHECK")) {
            params.put("mode", "OPTIONAL_MATCH");
            check.setParams(params);
            return new PropertyGenericCheck();
        }

        try {
            Class<? extends AbstractCheck> clazz = registry.get(type);
            if (clazz == null)
                throw new IllegalArgumentException("Unknown check type: " + type);
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create check: " + check.getType(), e);
        }
    }
}
