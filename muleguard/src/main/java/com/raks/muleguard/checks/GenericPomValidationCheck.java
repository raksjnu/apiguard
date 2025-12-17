package com.raks.muleguard.checks;

import com.raks.muleguard.model.Check;
import com.raks.muleguard.model.CheckResult;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Generic POM validation check that consolidates all POM-based validation
 * rules.
 * 
 * Replaces:
 * - RULE_003_PomPluginRemovedCheck (plugin removal validation)
 * - RULE_004_PomDependencyRemovedCheck (dependency removal validation)
 * - RULE_006_PomDependencyAddedCheck (dependency existence validation)
 * - RULE_002_PluginVersionCheck (property validation - optional)
 * 
 * Validation Types:
 * - DEPENDENCY_EXISTS: Dependencies MUST be present
 * - DEPENDENCY_NOT_EXISTS: Dependencies must NOT be present
 * - PLUGIN_EXISTS: Plugins MUST be present
 * - PLUGIN_NOT_EXISTS: Plugins must NOT be present
 * - PROPERTY_EXISTS: Properties MUST be present
 * - PROPERTY_NOT_EXISTS: Properties must NOT be present
 * 
 * Supports multiple items per check - add unlimited dependencies, plugins, or
 * properties!
 * 
 * @author Rakesh Kumar (raksjnu@gmail.com)
 */
public class GenericPomValidationCheck extends AbstractCheck {

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        String validationType = (String) check.getParams().get("validationType");

        if (validationType == null) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'validationType' parameter is required");
        }

        Path pomPath = projectRoot.resolve("pom.xml");
        if (!Files.exists(pomPath)) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    "No pom.xml found in project root");
        }

        try {
            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model model = reader.read(new FileReader(pomPath.toFile()));

            switch (validationType.toUpperCase()) {
                case "DEPENDENCY_EXISTS":
                    return validateDependencies(model, check, true);

                case "DEPENDENCY_NOT_EXISTS":
                    return validateDependencies(model, check, false);

                case "PLUGIN_EXISTS":
                    return validatePlugins(model, check, true);

                case "PLUGIN_NOT_EXISTS":
                    return validatePlugins(model, check, false);

                case "PROPERTY_EXISTS":
                    return validateProperties(model, check, true);

                case "PROPERTY_NOT_EXISTS":
                    return validateProperties(model, check, false);

                default:
                    return CheckResult.fail(check.getRuleId(), check.getDescription(),
                            "Unknown validationType: " + validationType);
            }

        } catch (Exception e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Error reading pom.xml: " + e.getMessage());
        }
    }

    /**
     * Validate dependencies existence or non-existence
     * 
     * @param model       Maven model
     * @param check       Check configuration
     * @param shouldExist true = dependencies MUST exist, false = must NOT exist
     */
    private CheckResult validateDependencies(Model model, Check check, boolean shouldExist) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> expectedDeps = (List<Map<String, String>>) check.getParams().get("dependencies");

        if (expectedDeps == null || expectedDeps.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'dependencies' parameter is required");
        }

        List<Dependency> actualDeps = model.getDependencies();
        if (actualDeps == null) {
            actualDeps = new ArrayList<>();
        }

        List<String> failures = new ArrayList<>();

        for (Map<String, String> expectedDep : expectedDeps) {
            String groupId = expectedDep.get("groupId");
            String artifactId = expectedDep.get("artifactId");

            if (groupId == null || artifactId == null) {
                failures.add("Invalid dependency configuration: groupId and artifactId are required");
                continue;
            }

            boolean found = actualDeps.stream()
                    .anyMatch(dep -> groupId.equals(dep.getGroupId()) && artifactId.equals(dep.getArtifactId()));

            if (shouldExist && !found) {
                failures.add(String.format("Required dependency not found: %s:%s", groupId, artifactId));
            } else if (!shouldExist && found) {
                failures.add(String.format("Forbidden dependency found: %s:%s", groupId, artifactId));
            }
        }

        if (failures.isEmpty()) {
            String message = shouldExist ? "All required dependencies are present" : "No forbidden dependencies found";
            return CheckResult.pass(check.getRuleId(), check.getDescription(), message);
        } else {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    String.join("\n", failures));
        }
    }

    /**
     * Validate plugins existence or non-existence
     * 
     * @param model       Maven model
     * @param check       Check configuration
     * @param shouldExist true = plugins MUST exist, false = must NOT exist
     */
    private CheckResult validatePlugins(Model model, Check check, boolean shouldExist) {
        @SuppressWarnings("unchecked")
        List<String> expectedPlugins = (List<String>) check.getParams().get("plugins");

        if (expectedPlugins == null || expectedPlugins.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'plugins' parameter is required");
        }

        List<Plugin> actualPlugins = new ArrayList<>();
        if (model.getBuild() != null && model.getBuild().getPlugins() != null) {
            actualPlugins = model.getBuild().getPlugins();
        }

        List<String> failures = new ArrayList<>();

        for (String expectedPlugin : expectedPlugins) {
            String[] parts = expectedPlugin.split(":");
            if (parts.length != 2) {
                failures.add(
                        String.format("Invalid plugin format '%s'. Expected 'groupId:artifactId'", expectedPlugin));
                continue;
            }

            String groupId = parts[0];
            String artifactId = parts[1];

            boolean found = actualPlugins.stream()
                    .anyMatch(
                            plugin -> groupId.equals(plugin.getGroupId()) && artifactId.equals(plugin.getArtifactId()));

            if (shouldExist && !found) {
                failures.add(String.format("Required plugin not found: %s", expectedPlugin));
            } else if (!shouldExist && found) {
                failures.add(String.format("Forbidden plugin found: %s", expectedPlugin));
            }
        }

        if (failures.isEmpty()) {
            String message = shouldExist ? "All required plugins are present" : "No forbidden plugins found";
            return CheckResult.pass(check.getRuleId(), check.getDescription(), message);
        } else {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    String.join("\n", failures));
        }
    }

    /**
     * Validate properties existence or non-existence
     * 
     * @param model       Maven model
     * @param check       Check configuration
     * @param shouldExist true = properties MUST exist, false = must NOT exist
     */
    private CheckResult validateProperties(Model model, Check check, boolean shouldExist) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> expectedProps = (List<Map<String, String>>) check.getParams().get("properties");

        if (expectedProps == null || expectedProps.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'properties' parameter is required");
        }

        Properties actualProps = model.getProperties();
        if (actualProps == null) {
            actualProps = new Properties();
        }

        List<String> failures = new ArrayList<>();

        for (Map<String, String> expectedProp : expectedProps) {
            String propertyName = expectedProp.get("name");
            String expectedValue = expectedProp.get("value");

            if (propertyName == null) {
                failures.add("Invalid property configuration: 'name' is required");
                continue;
            }

            String actualValue = actualProps.getProperty(propertyName);
            boolean found = actualValue != null;

            if (shouldExist && !found) {
                failures.add(String.format("Required property not found: %s", propertyName));
            } else if (!shouldExist && found) {
                failures.add(String.format("Forbidden property found: %s", propertyName));
            } else if (shouldExist && found && expectedValue != null && !expectedValue.equals(actualValue)) {
                // Optional: Check property value if specified
                failures.add(String.format("Property '%s' has incorrect value. Expected: '%s', Found: '%s'",
                        propertyName, expectedValue, actualValue));
            }
        }

        if (failures.isEmpty()) {
            String message = shouldExist ? "All required properties are present" : "No forbidden properties found";
            return CheckResult.pass(check.getRuleId(), check.getDescription(), message);
        } else {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    String.join("\n", failures));
        }
    }
}
