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
                case "DEPENDENCIES_REGEX":
                     return validateDependenciesRegex(model, check);
                default:
                    return CheckResult.fail(check.getRuleId(), check.getDescription(),
                            "Unknown validationType: " + validationType);
            }
        } catch (Exception e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Error reading pom.xml: " + e.getMessage());
        }
    }

    private CheckResult validateDependenciesRegex(Model model, Check check) {
         @SuppressWarnings("unchecked")
         List<Map<String, String>> forbiddenPatterns = (List<Map<String, String>>) check.getParams().get("forbiddenDependencyPatterns");

         if (forbiddenPatterns == null || forbiddenPatterns.isEmpty()) {
             return CheckResult.fail(check.getRuleId(), check.getDescription(), "Configuration error: 'forbiddenDependencyPatterns' is required");
         }

         List<Dependency> actualDeps = model.getDependencies();
         if (actualDeps == null) actualDeps = new ArrayList<>();
         
         List<String> failures = new ArrayList<>();

         for (Map<String, String> pattern : forbiddenPatterns) {
             String groupPattern = pattern.get("groupId");
             String artifactPattern = pattern.get("artifactId");
             String versionPattern = pattern.get("versionPattern");

             for (Dependency dep : actualDeps) {
                 boolean groupMatch = (groupPattern == null) || dep.getGroupId().matches(groupPattern);
                 boolean artifactMatch = (artifactPattern == null) || dep.getArtifactId().matches(artifactPattern);
                 boolean versionMatch = (versionPattern == null) || (dep.getVersion() != null && dep.getVersion().matches(versionPattern));

                 if (groupMatch && artifactMatch && versionMatch) {
                      failures.add(String.format("Forbidden dependency pattern matched: %s:%s:%s (Matched rule: G=%s, A=%s, V=%s)",
                              dep.getGroupId(), dep.getArtifactId(), dep.getVersion(),
                              groupPattern, artifactPattern, versionPattern));
                 }
             }
         }

         if (failures.isEmpty()) {
             return CheckResult.pass(check.getRuleId(), check.getDescription(), "No forbidden dependency patterns found.");
         } else {
             return CheckResult.fail(check.getRuleId(), check.getDescription(), String.join("\n", failures));
         }
    }

    private CheckResult validateDependencies(Model model, Check check, boolean shouldExist) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> expectedDeps = (List<Map<String, String>>) check.getParams().get("dependencies");

        if (expectedDeps == null) {
             expectedDeps = (List<Map<String, String>>) check.getParams().get("forbiddenDependencies");
        }
        if (expectedDeps == null) {
              expectedDeps = (List<Map<String, String>>) check.getParams().get("requiredDependencies");
        }

        if (expectedDeps == null || expectedDeps.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'dependencies' (or 'forbiddenDependencies'/'requiredDependencies') parameter is required");
        }
        List<Dependency> actualDeps = model.getDependencies();
        if (actualDeps == null) {
            actualDeps = new ArrayList<>();
        }
        List<String> failures = new ArrayList<>();
        for (Map<String, String> expectedDep : expectedDeps) {
            String groupId = expectedDep.get("groupId");
            String artifactId = expectedDep.get("artifactId");

            String version = expectedDep.get("version"); 
            
            if (groupId == null || artifactId == null) {
                failures.add("Invalid dependency configuration: groupId and artifactId are required");
                continue;
            }
            boolean found = actualDeps.stream()
                    .anyMatch(dep -> groupId.equals(dep.getGroupId()) && artifactId.equals(dep.getArtifactId())
                             && (version == null || version.equals(dep.getVersion())));
            
            if (shouldExist && !found) {
                failures.add(String.format("Required dependency not found: %s:%s%s", groupId, artifactId, (version!=null?":"+version:"")));
            } else if (!shouldExist && found) {
                failures.add(String.format("Forbidden dependency found: %s:%s%s", groupId, artifactId, (version!=null?":"+version:"")));
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
