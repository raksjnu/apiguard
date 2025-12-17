package com.raks.muleguard.checks;

import com.raks.muleguard.model.Check;

import java.util.HashMap;
import java.util.Map;

public class CheckFactory {
    private static Map<String, Class<? extends AbstractCheck>> registry = new HashMap<>();

    static {
        // === GENERIC CONSOLIDATED CHECKS (New) ===
        // GenericTokenSearchCheck handles all token-based searches
        registry.put("GENERIC_TOKEN_SEARCH", GenericTokenSearchCheck.class);

        // GenericXmlValidationCheck handles all XML validation
        registry.put("GENERIC_XML_VALIDATION", GenericXmlValidationCheck.class);

        // GenericPropertyFileCheck handles all property file validation
        registry.put("GENERIC_PROPERTY_FILE", GenericPropertyFileCheck.class);

        // GenericPomValidationCheck handles all POM validation (dependencies, plugins,
        // properties)
        registry.put("GENERIC_POM_VALIDATION", GenericPomValidationCheck.class);

        // === NEW CONFIGURATION CHECKS ===
        // Mandatory substring check for config files
        registry.put("MANDATORY_SUBSTRING_CHECK", MandatorySubstringCheck.class);

        // Mandatory property name-value check
        registry.put("MANDATORY_PROPERTY_VALUE_CHECK", MandatoryPropertyValueCheck.class);

        // Optional property name-value check
        registry.put("OPTIONAL_PROPERTY_VALUE_CHECK", OptionalPropertyValueCheck.class);

        // === NEW STANDARD CODE RULES ===
        // XML XPath validation (positive and negative)
        registry.put("XML_XPATH_EXISTS", XmlXPathExistsCheck.class);
        registry.put("XML_XPATH_NOT_EXISTS", XmlXPathNotExistsCheck.class);

        // XML Attribute validation (positive and negative)
        registry.put("XML_ATTRIBUTE_EXISTS", XmlAttributeExistsCheck.class);
        registry.put("XML_ATTRIBUTE_NOT_EXISTS", XmlAttributeNotExistsCheck.class);

        // XML Element Content validation (positive and negative)
        registry.put("XML_ELEMENT_CONTENT_REQUIRED", XmlElementContentRequiredCheck.class);
        registry.put("XML_ELEMENT_CONTENT_FORBIDDEN", XmlElementContentForbiddenCheck.class);

        // Generic Token Search (positive and negative)
        registry.put("GENERIC_TOKEN_SEARCH_REQUIRED", GenericTokenSearchRequiredCheck.class);
        registry.put("GENERIC_TOKEN_SEARCH_FORBIDDEN", GenericTokenSearchForbiddenCheck.class);

        // POM Validation (positive and negative)
        registry.put("POM_VALIDATION_REQUIRED", PomValidationRequiredCheck.class);
        registry.put("POM_VALIDATION_FORBIDDEN", PomValidationForbiddenCheck.class);

        // JSON Validation (positive and negative)
        registry.put("JSON_VALIDATION_REQUIRED", JsonValidationRequiredCheck.class);
        registry.put("JSON_VALIDATION_FORBIDDEN", JsonValidationForbiddenCheck.class);

        // === LEGACY MAPPINGS (For backward compatibility) ===
        // Token search mappings
        registry.put("GENERIC_CODE_TOKEN_CHECK", GenericTokenSearchCheck.class);
        registry.put("DLP_REFERENCE_CHECK", GenericTokenSearchCheck.class);
        registry.put("FORBIDDEN_TOKEN_IN_ELEMENT", GenericTokenSearchCheck.class);
        registry.put("GENERIC_CONFIG_TOKEN_CHECK", GenericTokenSearchCheck.class);
        registry.put("SUBSTRING_TOKEN_CHECK", GenericTokenSearchCheck.class);

        // XML validation mappings (legacy - now using dedicated classes)
        registry.put("IBM_MQ_CIPHER_CHECK", GenericXmlValidationCheck.class);
        registry.put("UNSUPPORTED_XML_ATTRIBUTE", GenericXmlValidationCheck.class);
        registry.put("CRYPTO_JCE_ENCRYPT_PBE_CHECK", GenericXmlValidationCheck.class);
        registry.put("CRYPTO_JCE_CONFIG_TYPE_CHECK", GenericXmlValidationCheck.class);

        // Property file mappings
        registry.put("CONFIG_PROPERTY_EXISTS", GenericPropertyFileCheck.class);
        registry.put("CONFIG_POLICY_EXISTS", GenericPropertyFileCheck.class);

        // POM validation mappings (NEW - using GenericPomValidationCheck)
        registry.put("POM_PLUGIN_REMOVED", GenericPomValidationCheck.class);
        registry.put("POM_DEPENDENCY_REMOVED", GenericPomValidationCheck.class);
        registry.put("POM_DEPENDENCY_ADDED", GenericPomValidationCheck.class);

        // Custom validators with hardcoded patterns (to avoid YAML escaping issues)
        registry.put("CLIENTIDMAP_VALIDATOR", ClientIDMapCheck.class);
    }

    public static AbstractCheck create(Check check) {
        try {
            Class<? extends AbstractCheck> clazz = registry.get(check.getType());
            if (clazz == null)
                throw new IllegalArgumentException("Unknown check type: " + check.getType());
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create check: " + check.getType(), e);
        }
    }
}