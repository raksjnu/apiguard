package com.raks.muleguard.checks;
import com.raks.muleguard.model.Check;
import java.util.HashMap;
import java.util.Map;
public class CheckFactory {
    private static Map<String, Class<? extends AbstractCheck>> registry = new HashMap<>();
    static {
        registry.put("GENERIC_TOKEN_SEARCH", GenericTokenSearchCheck.class);
        registry.put("GENERIC_XML_VALIDATION", GenericXmlValidationCheck.class);
        registry.put("GENERIC_PROPERTY_FILE", GenericPropertyFileCheck.class);
        registry.put("GENERIC_POM_VALIDATION", GenericPomValidationCheck.class);
        registry.put("MANDATORY_SUBSTRING_CHECK", MandatorySubstringCheck.class);
        registry.put("MANDATORY_PROPERTY_VALUE_CHECK", MandatoryPropertyValueCheck.class);
        registry.put("OPTIONAL_PROPERTY_VALUE_CHECK", OptionalPropertyValueCheck.class);
        registry.put("XML_XPATH_EXISTS", XmlXPathExistsCheck.class);
        registry.put("XML_XPATH_NOT_EXISTS", XmlXPathNotExistsCheck.class);
        registry.put("XML_ATTRIBUTE_EXISTS", XmlAttributeExistsCheck.class);
        registry.put("XML_ATTRIBUTE_NOT_EXISTS", XmlAttributeNotExistsCheck.class);
        registry.put("XML_ELEMENT_CONTENT_REQUIRED", XmlElementContentRequiredCheck.class);
        registry.put("XML_ELEMENT_CONTENT_FORBIDDEN", XmlElementContentForbiddenCheck.class);
        registry.put("GENERIC_TOKEN_SEARCH_REQUIRED", GenericTokenSearchRequiredCheck.class);
        registry.put("GENERIC_TOKEN_SEARCH_FORBIDDEN", GenericTokenSearchForbiddenCheck.class);
        registry.put("POM_VALIDATION_REQUIRED", PomValidationRequiredCheck.class);
        registry.put("POM_VALIDATION_FORBIDDEN", PomValidationForbiddenCheck.class);
        registry.put("JSON_VALIDATION_REQUIRED", JsonValidationRequiredCheck.class);
        registry.put("JSON_VALIDATION_FORBIDDEN", JsonValidationForbiddenCheck.class);
        registry.put("CONDITIONAL_CHECK", ConditionalCheck.class);
        registry.put("PROJECT_CONTEXT", ProjectContextCheck.class);
        registry.put("FILE_EXISTS", FileExistsCheck.class);
        registry.put("GENERIC_CODE_TOKEN_CHECK", GenericTokenSearchCheck.class);
        registry.put("DLP_REFERENCE_CHECK", GenericTokenSearchCheck.class);
        registry.put("FORBIDDEN_TOKEN_IN_ELEMENT", GenericTokenSearchCheck.class);
        registry.put("GENERIC_CONFIG_TOKEN_CHECK", GenericTokenSearchCheck.class);
        registry.put("SUBSTRING_TOKEN_CHECK", GenericTokenSearchCheck.class);
        registry.put("IBM_MQ_CIPHER_CHECK", GenericXmlValidationCheck.class);
        registry.put("UNSUPPORTED_XML_ATTRIBUTE", GenericXmlValidationCheck.class);
        registry.put("CRYPTO_JCE_ENCRYPT_PBE_CHECK", GenericXmlValidationCheck.class);
        registry.put("CRYPTO_JCE_CONFIG_TYPE_CHECK", GenericXmlValidationCheck.class);
        registry.put("CONFIG_PROPERTY_EXISTS", GenericPropertyFileCheck.class);
        registry.put("CONFIG_POLICY_EXISTS", GenericPropertyFileCheck.class);
        registry.put("POM_PLUGIN_REMOVED", GenericPomValidationCheck.class);
        registry.put("POM_DEPENDENCY_REMOVED", GenericPomValidationCheck.class);
        registry.put("POM_DEPENDENCY_ADDED", GenericPomValidationCheck.class);
        registry.put("CLIENTIDMAP_VALIDATOR", ClientIDMapCheck.class);
        registry.put("XML_ATTRIBUTE_EXTERNALIZED", XmlAttributeExternalizedCheck.class);
        registry.put("GENERIC_CODE_CHECK", GenericTokenSearchCheck.class);
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
