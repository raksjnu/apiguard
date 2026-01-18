package com.raks.aegis;

import com.raks.aegis.model.Rule;
import com.raks.aegis.AegisMain.RootWrapper;
import com.raks.aegis.AegisMain.ConfigSection;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.FileWriter;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RulesReorganizer {
    private static final Logger logger = LoggerFactory.getLogger(RulesReorganizer.class);

    public static void main(String[] args) {
        try {

            InputStream input = Files.newInputStream(Paths.get("src/main/resources/rules/rules.yaml"));
            LoaderOptions options = new LoaderOptions();
            Constructor constructor = new Constructor(RootWrapper.class, options);
            TypeDescription td = new TypeDescription(RootWrapper.class);
            td.addPropertyParameters("rules", Rule.class);
            constructor.addTypeDescription(td);
            Yaml loader = new Yaml(constructor);
            RootWrapper wrapper = loader.loadAs(input, RootWrapper.class);

            Map<String, List<Rule>> groupedRules = new LinkedHashMap<>();

            List<String> typeOrder = Arrays.asList(
                "GENERIC_TOKEN_SEARCH_FORBIDDEN",
                "GENERIC_TOKEN_SEARCH_REQUIRED",
                "GENERIC_CODE_CHECK", 
                "POM_VALIDATION_REQUIRED", "POM_VALIDATION_FORBIDDEN",
                "XML_ATTRIBUTE_EXISTS", "XML_ATTRIBUTE_NOT_EXISTS",
                "XML_ATTRIBUTE_EXTERNALIZED", 
                "XML_XPATH_EXISTS", "XML_XPATH_NOT_EXISTS",
                "XML_ELEMENT_CONTENT_FORBIDDEN", "XML_ELEMENT_CONTENT_REQUIRED",
                "JSON_VALIDATION_REQUIRED", "JSON_VALIDATION_FORBIDDEN",
                "MANDATORY_SUBSTRING_CHECK", "MANDATORY_PROPERTY_VALUE_CHECK",
                "OPTIONAL_PROPERTY_VALUE_CHECK", 
                "GENERIC_TOKEN_SEARCH",
                "CONDITIONAL_CHECK",
                "PROJECT_CONTEXT",
                "FILE_EXISTS"
            );

            for (String type : typeOrder) {
                groupedRules.put(type, new ArrayList<>());
            }
            groupedRules.put("OTHERS", new ArrayList<>());

            for (Rule rule : wrapper.getRules()) {
                if (rule.getChecks() == null || rule.getChecks().isEmpty()) {
                    groupedRules.get("OTHERS").add(rule);
                    continue;
                }
                String type = rule.getChecks().get(0).getType();
                if (groupedRules.containsKey(type)) {
                    groupedRules.get(type).add(rule);
                } else {
                    groupedRules.get("OTHERS").add(rule);
                }
            }

            List<String> finalTypeOrder = new ArrayList<>(typeOrder);
            finalTypeOrder.add("OTHERS");

            List<Rule> sortedRules = new ArrayList<>();
            for (String type : finalTypeOrder) {
                List<Rule> rules = groupedRules.get(type);
                if (!rules.isEmpty()) {

                    rules.sort(Comparator.comparing(Rule::getId));
                    sortedRules.addAll(rules);
                }
            }

            wrapper.setRules(sortedRules);

            List<Map<String, Object>> orderedRules = new ArrayList<>();
            for (Rule r : sortedRules) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", r.getId());
                map.put("name", r.getName());
                if (r.getDescription() != null) map.put("description", r.getDescription());
                map.put("enabled", r.isEnabled());
                map.put("severity", r.getSeverity());

                if (r.getUseCase() != null) map.put("useCase", r.getUseCase());
                if (r.getRationale() != null) map.put("rationale", r.getRationale());
                if (r.getExampleGood() != null) map.put("exampleGood", r.getExampleGood());
                if (r.getExampleBad() != null) map.put("exampleBad", r.getExampleBad());
                if (r.getDocLink() != null) map.put("docLink", r.getDocLink());

                List<Map<String, Object>> orderedChecks = new ArrayList<>();
                if (r.getChecks() != null) {
                    for (com.raks.aegis.model.Check c : r.getChecks()) {
                        Map<String, Object> cMap = new LinkedHashMap<>();
                        cMap.put("type", c.getType());
                        if (c.getDescription() != null) cMap.put("description", c.getDescription());
                        cMap.put("params", c.getParams());
                        orderedChecks.add(cMap);
                    }
                }
                map.put("checks", orderedChecks);
                orderedRules.add(map);
            }

            Map<String, Object> rootMap = new LinkedHashMap<>();
            rootMap.put("config", wrapper.getConfig());
            rootMap.put("rules", orderedRules);

            DumperOptions dumperOptions = new DumperOptions();
            dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            dumperOptions.setPrettyFlow(true);
            dumperOptions.setIndent(2);
            dumperOptions.setIndicatorIndent(0); 
            dumperOptions.setSplitLines(false); 
            dumperOptions.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);

            Representer representer = new Representer(dumperOptions);
            representer.addClassTag(RootWrapper.class, Tag.MAP);
            representer.addClassTag(ConfigSection.class, Tag.MAP);
            representer.addClassTag(Rule.class, Tag.MAP);

            Yaml dumper = new Yaml(representer, dumperOptions);
            String output = dumper.dump(rootMap);

            String headerMetadata = "# =============================================================================\n" +
                                   "# Aegis Rules Configuration\n" +
                                   "# Scope: Universal Code Compliance (Java, Python, MuleSoft, TIBCO, DevOps)\n" +
                                   "# Author: Rakesh Kumar (rakesh.kumar@ibm.com)\n" +
                                   "# =============================================================================\n\n" +
                                   "# =============================================================================\n" +
                                   "# RULE TYPES QUICK REFERENCE\n" +
                                   "# =============================================================================\n" +
                                   "# \n" +
                                   "# UNIVERSAL RULE TYPES (Apply to ANY text-based project)\n" +
                                   "# -------------------------------------------------------------------------\n" +
                                   "# 1.  GENERIC_TOKEN_SEARCH_REQUIRED      - Ensures required tokens exist in files\n" +
                                   "# 2.  GENERIC_TOKEN_SEARCH_FORBIDDEN     - Ensures forbidden tokens do NOT exist in files\n" +
                                   "# 3.  XML_XPATH_EXISTS                   - Validates required XPath expressions exist in XML\n" +
                                   "# 4.  XML_XPATH_NOT_EXISTS               - Validates forbidden XPath expressions do NOT exist in XML\n" +
                                   "# 5.  XML_ATTRIBUTE_EXISTS               - Ensures required XML attributes exist with correct values\n" +
                                   "# 6.  XML_ATTRIBUTE_NOT_EXISTS           - Ensures forbidden XML attributes do NOT exist\n" +
                                   "# 7.  XML_ELEMENT_CONTENT_REQUIRED       - Validates required content exists within XML elements\n" +
                                   "# 8.  XML_ELEMENT_CONTENT_FORBIDDEN      - Validates forbidden content does NOT exist within XML elements\n" +
                                   "# 9.  POM_VALIDATION_REQUIRED            - Ensures required POM elements exist (parent, dependencies, plugins, properties)\n" +
                                   "# 10. POM_VALIDATION_FORBIDDEN           - Ensures forbidden POM elements do NOT exist (dependencies, plugins)\n" +
                                   "# 11. JSON_VALIDATION_REQUIRED           - Validates required JSON elements exist (mule-artifact.json)\n" +
                                   "# 12. JSON_VALIDATION_FORBIDDEN          - Validates forbidden JSON elements do NOT exist\n" +
                                   "#\n" +
                                   "# 4.  GENERIC_TOKEN_SEARCH               - Advanced token search with REGEX support and environment filtering\n" +
                                    "#\n" +
                                   "# CONDITIONAL STANDARDS (Universal \"If-Then\" Engine)\n" +
                                   "# -------------------------------------------------------------------------\n" +
                                   "# 1.  CONDITIONAL_CHECK       - Logic engine. Runs 'onSuccess' only if 'preconditions' are met.\n" +
                                   "# \n" +
                                   "# COMMON TRIGGERS (Can be used in 'preconditions'):\n" +
                                   "# - PROJECT_CONTEXT           - Trigger by Project Name patterns (e.g., contains '-exp-').\n" +
                                   "# - FILE_EXISTS               - Trigger by file presence (e.g., 'secure-properties.xml').\n" +
                                   "# - XML_XPATH_EXISTS          - Trigger by XML content (e.g., check for DB/MQ connectors).\n" +
                                   "# - MANDATORY_PROPERTY_VALUE_CHECK - Trigger by property existence or specific value.\n" +
                                   "# \n" +
                                   "# NOTE: *ANY* check type above can be used as a trigger for the conditional engine!\n" +
                                   "# =============================================================================\n\n";

            int lastRulesIndex = output.lastIndexOf("\nrules:");
            if (lastRulesIndex != -1) {
                String configPart = output.substring(0, lastRulesIndex + 7);
                String rulesPart = output.substring(lastRulesIndex + 7);

                rulesPart = rulesPart.replaceAll("(?m)^", "  ");

                rulesPart = rulesPart.replaceAll("  - id: (RULE-\\d+)", "  - id: \"$1\"");

                rulesPart = rulesPart.replaceAll("  - id: \"", "\n  - id: \"");

                for (String type : typeOrder) {
                    if (!groupedRules.get(type).isEmpty()) {
                        String firstId = groupedRules.get(type).stream()
                            .sorted(Comparator.comparing(Rule::getId)).findFirst().get().getId();

                        String target = "\n  - id: \"" + firstId + "\"";
                        String sectionTitle = type;
                        if (type.equals("CONDITIONAL_CHECK") || type.equals("PROJECT_CONTEXT") || type.equals("FILE_EXISTS")) {
                            sectionTitle = "CONDITIONAL STANDARDS: " + type;
                        } else if (typeOrder.indexOf(type) < 13) {
                            sectionTitle = "CODE RULES: " + type;
                        } else {
                            sectionTitle = "CONFIG RULES: " + type;
                        }

                        String sectionHeader = "\n  # " + "-".repeat(75) + "\n" +
                                              "  # " + sectionTitle + "\n" +
                                              "  # " + "-".repeat(75) + "\n" +
                                              target;
                        rulesPart = rulesPart.replace(target, sectionHeader);
                    }
                }
                output = configPart + rulesPart;
            }

            output = output.replaceAll("!!com.raks.aegis.AegisMain\\$ConfigSection", "");
            output = headerMetadata + output;

            FileWriter writer = new FileWriter("src/main/resources/rules/rules.yaml");
            writer.write(output);
            writer.close();

            logger.info("Perfected rules (full list) written to src/main/resources/rules/rules.yaml");

        } catch (Exception e) {
            logger.error("Error reorganizing rules", e);
        }
    }

    public static class LoaderOptions extends org.yaml.snakeyaml.LoaderOptions {
    }
}
