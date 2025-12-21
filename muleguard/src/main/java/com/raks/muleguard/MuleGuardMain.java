package com.raks.muleguard;

import com.raks.muleguard.engine.ReportGenerator;
import com.raks.muleguard.engine.ValidationEngine;
import com.raks.muleguard.model.Rule;
import com.raks.muleguard.model.ValidationReport;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.introspector.PropertyUtils;
import org.yaml.snakeyaml.TypeDescription;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

import javax.swing.JFileChooser;

import com.raks.muleguard.license.TrialLicenseManager;

public class MuleGuardMain {

    public static void main(String[] args) {
        // TRIAL LICENSE CHECK - Comment out this block for production/full version
        try {
            TrialLicenseManager licenseManager = new TrialLicenseManager();
            licenseManager.validateTrial();
        } catch (TrialLicenseManager.LicenseException e) {
            System.err.println("\n╔════════════════════════════════════════════════════════════╗");
            System.err.println("║                  LICENSE ERROR                             ║");
            System.err.println("╠════════════════════════════════════════════════════════════╣");
            System.err.println("║  " + e.getMessage());
            System.err.println("╚════════════════════════════════════════════════════════════╝\n");
            System.exit(1);
        }
        // END TRIAL LICENSE CHECK
        
        Path parentFolder;
        String configFilePath = null;

        if (args.length == 0 || args[0].isEmpty()) {
            parentFolder = showFolderDialog();
            if (parentFolder == null) {
                System.out.println("No folder selected. Exiting.");
                return;
            }
        } else if (args.length >= 2 && "-p".equals(args[0])) {
            parentFolder = Paths.get(args[1]);

            // Check for optional --config parameter
            if (args.length >= 4 && "--config".equals(args[2])) {
                configFilePath = args[3];
                System.out.println("Using custom config file: " + configFilePath);
            }
        } else {
            System.err.println(
                    "Usage: java -jar muleguard.jar -p <folder> [--config <rules.yaml>]   OR   double-click to select folder");
            return;
        }

        if (!Files.isDirectory(parentFolder)) {
            System.err.println("Error: Not a valid folder: " + parentFolder);
            return;
        }

        System.out.println("Starting MuleGuard validation on: " + parentFolder);
        System.out.println("Scanning for Mule API projects...\n");

        RootWrapper configWrapper = loadConfig(configFilePath);
        List<Rule> allRules = configWrapper.getRules();

        // Load project identification configuration
        Map<String, Object> projectIdConfig = configWrapper.getConfig().getProjectIdentification();

        // Max search depth for nested projects
        int maxSearchDepth = (Integer) projectIdConfig.getOrDefault("maxSearchDepth", 5);

        // Config folder pattern
        @SuppressWarnings("unchecked")
        Map<String, Object> configFolderConfig = (Map<String, Object>) projectIdConfig.get("configFolder");
        String configFolderPattern = (String) configFolderConfig.get("namePattern");

        // Mule API project marker files
        @SuppressWarnings("unchecked")
        Map<String, Object> muleApiConfig = (Map<String, Object>) projectIdConfig.get("muleApiProject");
        String matchMode = (String) muleApiConfig.getOrDefault("matchMode", "ANY");
        @SuppressWarnings("unchecked")
        List<String> markerFiles = (List<String>) muleApiConfig.get("markerFiles");

        // Ignored folders configuration
        @SuppressWarnings("unchecked")
        Map<String, Object> ignoredFoldersConfig = (Map<String, Object>) projectIdConfig.get("ignoredFolders");
        @SuppressWarnings("unchecked")
        List<String> exactIgnoredNames = (List<String>) ignoredFoldersConfig.get("exactNames");
        @SuppressWarnings("unchecked")
        List<String> ignoredPrefixes = (List<String>) ignoredFoldersConfig.get("prefixes");

        int configRuleStart = configWrapper.getConfig().getRules().get("start");
        int configRuleEnd = configWrapper.getConfig().getRules().get("end");
        List<String> globalEnvironments = configWrapper.getConfig().getEnvironments();

        List<ApiResult> results = new ArrayList<>();
        Path reportsRoot = parentFolder.resolve("muleguard-reports");

        try {
            Files.createDirectories(reportsRoot);
        } catch (IOException e) {
            System.err.println("Failed to create reports directory: " + e.getMessage());
            System.exit(1);
        }

        // Use recursive project discovery instead of flat Files.list()
        List<Path> discoveredProjects = com.raks.muleguard.util.ProjectDiscovery.findMuleProjects(
                parentFolder,
                maxSearchDepth,
                markerFiles,
                matchMode,
                configFolderPattern,
                exactIgnoredNames,
                ignoredPrefixes);

        System.out.println(); // Blank line after discovery

        // Validate each discovered project
        for (Path apiDir : discoveredProjects) {
            String apiName = apiDir.getFileName().toString();
            boolean isConfigProject = apiName.matches(configFolderPattern);
            System.out.printf("Validating %s: %s%n", isConfigProject ? "Config" : "API", apiName);

            List<Rule> applicableRules = allRules.stream()
                    .filter(Rule::isEnabled)
                    .filter(rule -> {
                        int ruleIdNum = Integer.parseInt(rule.getId().replace("RULE-", ""));
                        boolean isConfigRule = (ruleIdNum >= configRuleStart && ruleIdNum <= configRuleEnd);

                        if (isConfigRule && globalEnvironments != null && !globalEnvironments.isEmpty()) {
                            rule.getChecks().forEach(check -> {
                                if (check.getParams() == null) {
                                    check.setParams(new java.util.HashMap<>());
                                }

                                // Check if environments parameter exists and contains "ALL"
                                @SuppressWarnings("unchecked")
                                List<String> envs = (List<String>) check.getParams().get("environments");

                                if (envs != null && envs.size() == 1
                                        && "ALL".equalsIgnoreCase(envs.get(0))) {
                                    // Replace "ALL" with global environment list
                                    check.getParams().put("environments",
                                            new ArrayList<>(globalEnvironments));
                                } else if (envs == null || envs.isEmpty()) {
                                    // If no environments specified, use global list
                                    check.getParams().put("environments",
                                            new ArrayList<>(globalEnvironments));
                                }
                                // Otherwise, keep the specific environments list as-is
                            });
                        }

                        return isConfigProject == isConfigRule;
                    }).collect(Collectors.toList());

            ValidationEngine engine = new ValidationEngine(applicableRules, apiDir);
            ValidationReport report = engine.validate();
            report.projectPath = apiName + " (" + apiDir.toString() + ")";

            Path apiReportDir = reportsRoot.resolve(apiName);

            try {
                Files.createDirectories(apiReportDir);
            } catch (IOException e) {
                System.err.println("Failed to create report dir for " + apiName + ": " + e.getMessage());
                continue; // Changed from return to continue
            }

            ReportGenerator.generateIndividualReports(report, apiReportDir);

            int passed = report.passed.size();
            int failed = report.failed.size();
            int skipped = report.skipped.size();
            results.add(new ApiResult(apiName, apiDir, passed, failed, skipped, apiReportDir));

            System.out.println("   " + (failed == 0 ? "PASS" : "FAIL") +
                    " | Passed: " + passed + " | Failed: " + failed + " | Skipped: " + skipped + "\n");
        }

        try {
            ReportGenerator.generateConsolidatedReport(results, reportsRoot);
        } catch (Throwable t) {
            System.err.println("FAILED TO GENERATE CONSOLIDATED REPORT!");
            System.err.println("Exception: " + t.getClass().getSimpleName());
            String msg = t.getMessage();
            if (msg != null) {
                System.err.println("Message: " + msg.replace('%', '％')); // ← Full-width percent ％ (U+FF05)
            }
            t.printStackTrace(System.err);
        }

        System.out.println("BATCH VALIDATION COMPLETE!");
        System.out.println("Consolidated report: " + reportsRoot.resolve("CONSOLIDATED-REPORT.html"));
        System.out.println("Individual reports in: " + reportsRoot);

        // System.exit(results.stream().mapToInt(r -> r.failed).sum() > 0 ? 1 : 0); //
        // Commented out to allow GUI to continue running
    }

    /**
     * Validate Mule projects and return results as a Map (for wrapper integration)
     * 
     * @param projectPath     Path to the Mule projects folder
     * @param customRulesPath Optional path to custom rules.yaml file
     * @return Map containing validation summary
     */
    public static Map<String, Object> validateAndReturnResults(String projectPath, String customRulesPath) {
        return validateAndReturnResults(projectPath, customRulesPath, null);
    }

    /**
     * Validate Mule projects and return results as a Map (for wrapper integration)
     * 
     * @param projectPath     Path to the Mule projects folder
     * @param customRulesPath Optional path to custom rules.yaml file
     * @param displayName     Optional display name (e.g., ZIP filename) to use in
     *                        reports instead of path
     * @return Map containing validation results
     */
    public static Map<String, Object> validateAndReturnResults(String projectPath, String customRulesPath,
            String displayName) {
        return validateAndReturnResults(projectPath, customRulesPath, displayName, "muleguard-reports");
    }

    /**
     * Validate Mule projects with custom report directory
     */
    public static Map<String, Object> validateAndReturnResults(String projectPath, String customRulesPath,
            String displayName, String reportDirName) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Load configuration
            Path parentFolder = Paths.get(projectPath);
            RootWrapper configWrapper = loadConfig(customRulesPath);

            @SuppressWarnings("unchecked")
            Map<String, Object> ignoredFoldersConfig = (Map<String, Object>) configWrapper.getConfig()
                    .getProjectIdentification().get("ignoredFolders");
            @SuppressWarnings("unchecked")
            List<String> exactIgnoredNames = (List<String>) ignoredFoldersConfig.get("exactNames");
            @SuppressWarnings("unchecked")
            List<String> ignoredPrefixes = (List<String>) ignoredFoldersConfig.get("prefixes");

            // Get max search depth
            @SuppressWarnings("unchecked")
            Map<String, Object> projectIdConfig = (Map<String, Object>) configWrapper.getConfig()
                    .getProjectIdentification();
            int maxSearchDepth = (Integer) projectIdConfig.getOrDefault("maxSearchDepth", 5);

            // Get marker files and match mode
            @SuppressWarnings("unchecked")
            Map<String, Object> muleApiConfig = (Map<String, Object>) projectIdConfig.get("muleApiProject");
            String matchMode = (String) muleApiConfig.getOrDefault("matchMode", "ANY");
            @SuppressWarnings("unchecked")
            List<String> markerFiles = (List<String>) muleApiConfig.get("markerFiles");

            // Get config folder pattern
            @SuppressWarnings("unchecked")
            Map<String, Object> configFolderConfig = (Map<String, Object>) projectIdConfig.get("configFolder");
            String configFolderPattern = (String) configFolderConfig.get("namePattern");

            int configRuleStart = configWrapper.getConfig().getRules().get("start");
            int configRuleEnd = configWrapper.getConfig().getRules().get("end");
            List<String> globalEnvironments = configWrapper.getConfig().getEnvironments();

            List<ApiResult> results = new ArrayList<>();
            Path reportsRoot = parentFolder
                    .resolve(reportDirName != null && !reportDirName.isEmpty() ? reportDirName : "muleguard-reports");

            try {
                Files.createDirectories(reportsRoot);
            } catch (IOException e) {
                result.put("status", "ERROR");
                result.put("message", "Failed to create reports directory: " + e.getMessage());
                result.put("passed", new ArrayList<>());
                result.put("failed", new ArrayList<>());
                result.put("skipped", new ArrayList<>());
                return result;
            }

            // Use recursive project discovery
            List<Path> discoveredProjects = com.raks.muleguard.util.ProjectDiscovery.findMuleProjects(
                    parentFolder,
                    maxSearchDepth,
                    markerFiles,
                    matchMode,
                    configFolderPattern,
                    exactIgnoredNames,
                    ignoredPrefixes);

            // Run validation on each discovered project
            for (Path apiDir : discoveredProjects) {
                String apiName = apiDir.getFileName().toString();
                Path apiReportDir = reportsRoot.resolve(apiName);

                try {
                    Files.createDirectories(apiReportDir);
                } catch (IOException e) {
                    System.err.println("Failed to create report directory for " + apiName);
                    continue;
                }

                // Determine if project is Config or Code
                boolean isConfigProject = apiName.matches(configFolderPattern);

                // Filter rules based on project type
                List<Rule> applicableRules = configWrapper.getRules().stream()
                        .filter(Rule::isEnabled)
                        .filter(rule -> {
                            try {
                                int ruleIdNum = Integer.parseInt(rule.getId().replace("RULE-", ""));
                                boolean isConfigRule = (ruleIdNum >= configRuleStart
                                        && ruleIdNum <= configRuleEnd);

                                // Handle global environments for config rules
                                if (isConfigRule && globalEnvironments != null
                                        && !globalEnvironments.isEmpty()) {
                                    rule.getChecks().forEach(check -> {
                                        if (check.getParams() == null) {
                                            check.setParams(new HashMap<>());
                                        }

                                        @SuppressWarnings("unchecked")
                                        List<String> envs = (List<String>) check.getParams()
                                                .get("environments");

                                        if (envs != null && envs.size() == 1
                                                && "ALL".equalsIgnoreCase(envs.get(0))) {
                                            check.getParams().put("environments",
                                                    new ArrayList<>(globalEnvironments));
                                        } else if (envs == null || envs.isEmpty()) {
                                            check.getParams().put("environments",
                                                    new ArrayList<>(globalEnvironments));
                                        }
                                    });
                                }

                                return isConfigProject == isConfigRule;
                            } catch (NumberFormatException e) {
                                return false; // Skip rules with invalid IDs
                            }
                        }).collect(Collectors.toList());

                ValidationEngine engine = new ValidationEngine(applicableRules, apiDir);
                ValidationReport report = engine.validate();

                if (report == null) {
                    System.err.println("Validation failed for " + apiName);
                    continue;
                }

                // Use display name (ZIP filename) if provided, otherwise show full local path
                // This ensures reports for uploaded ZIPs don't show internal temp paths
                if (displayName != null && !displayName.trim().isEmpty()) {
                    report.projectPath = displayName;
                } else {
                    report.projectPath = apiName + " (" + apiDir.toString() + ")";
                }

                ReportGenerator.generateIndividualReports(report, apiReportDir);

                int passed = report.passed.size();
                int failed = report.failed.size();
                int skipped = report.skipped.size();
                results.add(new ApiResult(apiName, apiDir, passed, failed, skipped, apiReportDir));

                System.out.println("Validating API: " + apiName);
                System.out.println("   " + (failed == 0 ? "PASS" : "FAIL") +
                        " | Passed: " + passed + " | Failed: " + failed + " | Skipped: " + skipped + "\n");
            }

            // Generate consolidated report
            try {
                ReportGenerator.generateConsolidatedReport(results, reportsRoot);
            } catch (Throwable t) {
                System.err.println("Failed to generate consolidated report: " + t.getMessage());
                t.printStackTrace();
            }

            // Calculate totals
            int totalPassed = results.stream().mapToInt(r -> r.passed).sum();
            int totalFailed = results.stream().mapToInt(r -> r.failed).sum();
            int totalSkipped = results.stream().mapToInt(r -> r.skipped).sum();
            String overallStatus = (totalFailed > 0) ? "FAIL" : "PASS";

            // Create result arrays
            List<Map<String, Object>> passed = new ArrayList<>();
            List<Map<String, Object>> failed = new ArrayList<>();
            List<String> skipped = new ArrayList<>();

            // Add placeholder entries based on counts
            for (int i = 0; i < totalPassed; i++) {
                passed.add(new HashMap<>());
            }
            for (int i = 0; i < totalFailed; i++) {
                failed.add(new HashMap<>());
            }
            for (int i = 0; i < totalSkipped; i++) {
                skipped.add("Skipped Rule");
            }

            result.put("status", overallStatus);
            result.put("passed", passed);
            result.put("failed", failed);
            result.put("skipped", skipped);
            result.put("reportsPath", reportsRoot.toString());
            result.put("customRulesUsed", customRulesPath != null);

            System.out.println("BATCH VALIDATION COMPLETE!");
            System.out.println("Validation results: Passed=" + totalPassed + ", Failed=" + totalFailed + ", Status="
                    + overallStatus);

        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("passed", new ArrayList<>());
            result.put("failed", new ArrayList<>());
            result.put("skipped", new ArrayList<>());
            result.put("message", e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    private static RootWrapper loadConfig(String configFilePath) {
        LoaderOptions options = new LoaderOptions();
        Constructor constructor = new Constructor(RootWrapper.class, options);
        TypeDescription td = new TypeDescription(RootWrapper.class);
        td.addPropertyParameters("rules", Rule.class);
        constructor.addTypeDescription(td);

        Yaml yaml = new Yaml(constructor);
        InputStream input;

        // If custom config file path is provided, use it; otherwise use embedded
        // rules.yaml
        if (configFilePath != null && !configFilePath.isEmpty()) {
            try {
                input = Files.newInputStream(Paths.get(configFilePath));
                System.out.println("Loaded custom config from: " + configFilePath);
            } catch (IOException e) {
                System.err.println("Error loading custom config file: " + configFilePath);
                System.err.println("Falling back to embedded rules.yaml");
                input = MuleGuardMain.class.getClassLoader().getResourceAsStream("rules/rules.yaml");
            }
        } else {
            input = MuleGuardMain.class.getClassLoader().getResourceAsStream("rules/rules.yaml");
        }

        if (input == null) {
            System.err.println("rules.yaml not found!");
            System.exit(1);
        }
        return yaml.loadAs(input, RootWrapper.class);
    }

    private static Path showFolderDialog() {

        final Path[] selected = new Path[1];
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Select Parent Folder containing Mule APIs");
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setAcceptAllFileFilterUsed(false);

                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    selected[0] = chooser.getSelectedFile().toPath();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return selected[0];
    }

    public static class ConfigSection {
        private Map<String, Object> projectIdentification;
        private Map<String, Integer> rules;
        private List<String> environments;

        // Legacy support for old folderPattern (for backward compatibility)
        private String folderPattern;

        public Map<String, Object> getProjectIdentification() {
            return projectIdentification;
        }

        public void setProjectIdentification(Map<String, Object> projectIdentification) {
            this.projectIdentification = projectIdentification;
        }

        public Map<String, Integer> getRules() {
            return rules;
        }

        public void setRules(Map<String, Integer> rules) {
            this.rules = rules;
        }

        public List<String> getEnvironments() {
            return environments;
        }

        public void setEnvironments(List<String> environments) {
            this.environments = environments;
        }

        // Legacy getter for backward compatibility
        public String getFolderPattern() {
            return folderPattern;
        }

        public void setFolderPattern(String folderPattern) {
            this.folderPattern = folderPattern;
        }
    }

    public static class RootWrapper {
        private ConfigSection config;
        private List<Rule> rules;

        public ConfigSection getConfig() {
            return config;
        }

        public void setConfig(ConfigSection config) {
            this.config = config;
        }

        public List<Rule> getRules() {
            return rules;
        }

        public void setRules(List<Rule> rules) {
            this.rules = rules;
        }
    }

    public static class ApiResult {
        public final String name;
        public final Path path;
        public final int passed, failed, skipped;
        public final Path reportDir;

        public ApiResult(String name, Path path, int passed, int failed, int skipped, Path reportDir) {
            this.name = name;
            this.path = path;
            this.passed = passed;
            this.failed = failed;
            this.skipped = skipped;
            this.reportDir = reportDir;
        }
    }
}