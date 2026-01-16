package com.raks.aegis;
import com.raks.aegis.engine.ReportGenerator;
import com.raks.aegis.engine.ValidationEngine;
import com.raks.aegis.model.Rule;
import com.raks.aegis.model.ValidationReport;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.introspector.PropertyUtils;
import org.yaml.snakeyaml.TypeDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class AegisMain {
    private static final Logger logger = LoggerFactory.getLogger(AegisMain.class);
    public static void main(String[] args) {
        Path parentFolder;
        String configFilePath = null;
        if (args.length == 0 || args[0].isEmpty()) {
            parentFolder = showFolderDialog();
            if (parentFolder == null) {
                logger.info("No folder selected. Exiting.");
                return;
            }
        } else if (args.length >= 2 && "-p".equals(args[0])) {
            parentFolder = Paths.get(args[1]);
            if (args.length >= 4 && "--config".equals(args[2])) {
                configFilePath = args[3];
                logger.info("Using custom config file: {}", configFilePath);
            }
        } else {
            logger.error("Usage: java -jar Aegis.jar -p <folder> [--config <rules.yaml>]   OR   double-click to select folder");
            return;
        }
        if (!Files.isDirectory(parentFolder)) {
            logger.error("Error: Not a valid folder: {}", parentFolder);
            return;
        }
        logger.info("Aegis started for project: {}", parentFolder);
        logger.debug("Scanning for Mule API projects...");
        RootWrapper configWrapper = loadConfig(configFilePath);
        List<Rule> allRules = configWrapper.getRules();
        Map<String, Object> projectIdConfig = configWrapper.getConfig().getProjectIdentification();
        int maxSearchDepth = (Integer) projectIdConfig.getOrDefault("maxSearchDepth", 5);
        @SuppressWarnings("unchecked")
        Map<String, Object> configFolderConfig = (Map<String, Object>) projectIdConfig.get("configFolder");
        String configFolderPattern = null;
        if (configFolderConfig != null) {
            configFolderPattern = (String) configFolderConfig.get("namePattern");
        } else {
             // Default or ignore
             configFolderPattern = ".*_config"; // reasonable default?
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> muleApiConfig = (Map<String, Object>) projectIdConfig.get("muleApiProject");
        String matchMode = (String) muleApiConfig.getOrDefault("matchMode", "ANY");
        @SuppressWarnings("unchecked")
        List<String> markerFiles = (List<String>) muleApiConfig.get("markerFiles");
        @SuppressWarnings("unchecked")
        Map<String, Object> ignoredFoldersConfig = (Map<String, Object>) projectIdConfig.get("ignoredFolders");
        List<String> exactIgnoredNames = new ArrayList<>();
        List<String> ignoredPrefixes = new ArrayList<>();
        
        if (ignoredFoldersConfig != null) {
            @SuppressWarnings("unchecked")
            List<String> exact = (List<String>) ignoredFoldersConfig.get("exactNames");
            if (exact != null) exactIgnoredNames.addAll(exact);
            
            @SuppressWarnings("unchecked")
            List<String> prefixes = (List<String>) ignoredFoldersConfig.get("prefixes");
            if (prefixes != null) ignoredPrefixes.addAll(prefixes);
        }
        int tempStart = 0;
        int tempEnd = Integer.MAX_VALUE;
        if (configWrapper.getConfig().getRules() != null) {
            tempStart = configWrapper.getConfig().getRules().getOrDefault("start", 0);
            tempEnd = configWrapper.getConfig().getRules().getOrDefault("end", Integer.MAX_VALUE);
        }
        final int configRuleStart = tempStart;
        final int configRuleEnd = tempEnd;
        List<String> globalEnvironments = configWrapper.getConfig().getEnvironments();
        List<ApiResult> results = new ArrayList<>();
        Path reportsRoot = parentFolder.resolve("Aegis-reports");
        try {
            Files.createDirectories(reportsRoot);
        } catch (IOException e) {
            logger.error("Failed to create reports directory: {}", e.getMessage());
            System.exit(1);
        }
        List<Path> discoveredProjects = com.raks.aegis.util.ProjectDiscovery.findMuleProjects(
                parentFolder,
                maxSearchDepth,
                markerFiles,
                matchMode,
                configFolderPattern,
                exactIgnoredNames,
                ignoredPrefixes);
        logger.info("");
        for (Path apiDir : discoveredProjects) {
            String apiName = apiDir.getFileName().toString();
            boolean isConfigProject = apiName.matches(configFolderPattern);
            logger.info("Validating {}: {}", isConfigProject ? "Config" : "API", apiName);
            List<Rule> applicableRules = allRules.stream()
                    .filter(Rule::isEnabled)
                    .filter(rule -> {

                        String idDigits = rule.getId().replaceAll("[^0-9]", "");
                        int ruleIdNum = idDigits.isEmpty() ? -1 : Integer.parseInt(idDigits);
                        
                        boolean isConfigRule = (ruleIdNum >= configRuleStart && ruleIdNum <= configRuleEnd);
                        
                        // If ranges are defaults (0 to MAX), disable strict separation
                        // This fixes the issue where standard APIs got 0 rules because everything was deemed "Config Rule"
                        boolean defaultRanges = (configRuleStart == 0 && configRuleEnd == Integer.MAX_VALUE);
                        
                        if (globalEnvironments != null && !globalEnvironments.isEmpty()) {
                            if (rule.getChecks() != null) {
                                rule.getChecks().forEach(check -> {
                                    if (check.getParams() == null) {
                                        check.setParams(new java.util.HashMap<>());
                                    }
                                    @SuppressWarnings("unchecked")
                                    List<String> envs = (List<String>) check.getParams().get("environments");
                                    if (envs != null && envs.size() == 1
                                            && "ALL".equalsIgnoreCase(envs.get(0))) {
                                        check.getParams().put("environments",
                                                new ArrayList<>(globalEnvironments));
                                    } else if (envs == null || envs.isEmpty()) {
                                        if (isConfigRule) {
                                            check.getParams().put("environments",
                                                new ArrayList<>(globalEnvironments));
                                        }
                                    }
                                });
                            }
                        }
                        
                        if (defaultRanges) {
                            return true; // Apply all rules to all projects if no range is defined
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
                logger.error("Failed to create report dir for {}: {}", apiName, e.getMessage());
                continue; 
            }
            ReportGenerator.generateIndividualReports(report, apiReportDir);
            int passed = report.passed.size();
            int failed = report.failed.size();
            int skipped = report.skipped.size();
            Path rel = parentFolder.relativize(apiDir);
            String repoName = rel.getNameCount() > 1 ? rel.getName(0).toString() : apiName;
            
            results.add(new ApiResult(apiName, repoName, apiDir, passed, failed, skipped, apiReportDir));
            logger.info("   {} | Files Scanned: {} | Passed: {} | Failed: {}", 
                    (failed == 0 ? "PASS" : "FAIL"), (passed + failed + skipped), passed, failed);
        }
        try {
            ReportGenerator.generateConsolidatedReport(results, reportsRoot);
        } catch (Throwable t) {
            logger.error("FAILED TO GENERATE CONSOLIDATED REPORT!");
            logger.error("Exception: {}", t.getClass().getSimpleName());
            String msg = t.getMessage();
            if (msg != null) {
                logger.error("Message: {}", msg);
            }
            logger.error("Stack trace:", t);
        }
        int totalPassed = results.stream().mapToInt(r -> r.passed).sum();
        int totalFailed = results.stream().mapToInt(r -> r.failed).sum();
        logger.info("Aegis Validation Complete | Total APIs: {} | Passed: {} | Failed: {}", results.size(), totalPassed, totalFailed);
        logger.info("Consolidated report: {}", reportsRoot.resolve("CONSOLIDATED-REPORT.html"));
    }
    public static Map<String, Object> validateAndReturnResults(String projectPath, String customRulesPath) {
        return validateAndReturnResults(projectPath, customRulesPath, null);
    }
    public static Map<String, Object> validateAndReturnResults(String projectPath, String customRulesPath,
            String displayName) {
        return validateAndReturnResults(projectPath, customRulesPath, displayName, "Aegis-reports");
    }
    public static Map<String, Object> validateAndReturnResults(String projectPath, String customRulesPath,
            String displayName, String reportDirName) {
        Map<String, Object> result = new HashMap<>();
        try {
            Path parentFolder = Paths.get(projectPath);
            List<ApiResult> results = new ArrayList<>();
            Path reportsRoot = parentFolder.resolve(reportDirName != null && !reportDirName.isEmpty() ? reportDirName : "Aegis-reports");
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

            // --- STRICT CONFIG OVERRIDE: Custom Config replaces Built-in COMPLETELY ---
            RootWrapper activeConfig = customRulesPath != null ? loadConfig(customRulesPath) : loadConfig(null);
            
            // Extract Configuration
            Map<String, Object> projectIdConfig = activeConfig.getConfig().getProjectIdentification();
            Map<String, Object> muleApiConfig = (Map<String, Object>) projectIdConfig.get("muleApiProject");
            Map<String, Object> configFolderConfig = (Map<String, Object>) projectIdConfig.get("configFolder");
            Map<String, Object> ignoredFoldersConfig = (Map<String, Object>) projectIdConfig.get("ignoredFolders");
            
            int maxSearchDepth = (Integer) projectIdConfig.getOrDefault("maxSearchDepth", 5);
            String matchMode = (String) muleApiConfig.getOrDefault("matchMode", "ANY");
            List<String> markerFiles = (List<String>) muleApiConfig.get("markerFiles");
            String configFolderPattern = (String) configFolderConfig.get("namePattern");
            List<String> exactIgnoredNames = (List<String>) ignoredFoldersConfig.get("exactNames");
            List<String> ignoredPrefixes = (List<String>) ignoredFoldersConfig.get("prefixes");
            List<String> globalEnvironments = activeConfig.getConfig().getEnvironments();

            // Discover Projects
            List<Path> discoveredProjects = com.raks.aegis.util.ProjectDiscovery.findMuleProjects(
                    parentFolder,
                    maxSearchDepth,
                    markerFiles,
                    matchMode,
                    configFolderPattern,
                    exactIgnoredNames,
                    ignoredPrefixes);

            for (Path apiDir : discoveredProjects) {
                String apiName = apiDir.getFileName().toString();
                Path apiReportDir = reportsRoot.resolve(apiName);
                
                try {
                    Files.createDirectories(apiReportDir);
                } catch (IOException e) {
                    logger.error("Failed to create report directory for {}", apiName);
                    continue;
                }

                boolean isConfigProject = apiName.matches(configFolderPattern);
                
                // --- SCOPE-BASED FILTERING (No ID ranges) ---
                List<Rule> applicableRules = activeConfig.getRules().stream()
                        .filter(Rule::isEnabled)
                        .filter(rule -> {
                            String scope = rule.getScope();
                            // Default to GLOBAL if null
                            if (scope == null || scope.isEmpty() || "GLOBAL".equalsIgnoreCase(scope)) {
                                return true;
                            }
                            if (isConfigProject) {
                                return "CONFIG".equalsIgnoreCase(scope) || "BOTH".equalsIgnoreCase(scope); 
                            } else {
                                return "APP".equalsIgnoreCase(scope) || "BOTH".equalsIgnoreCase(scope); // "APP" or "CODE" logic
                            }
                        })
                        .peek(rule -> {
                             // Inject Global Environments if Rule is generic
                            if (globalEnvironments != null && !globalEnvironments.isEmpty()) {
                                if (rule.getChecks() != null) {
                                    rule.getChecks().forEach(check -> {
                                        if (check.getParams() == null) check.setParams(new HashMap<>());
                                        List<String> envs = (List<String>) check.getParams().get("environments");
                                        if (envs == null || envs.isEmpty() || (envs.size() == 1 && "ALL".equalsIgnoreCase(envs.get(0)))) {
                                             check.getParams().put("environments", new ArrayList<>(globalEnvironments));
                                        }
                                    });
                                }
                            }
                        })
                        .collect(Collectors.toList());

                ValidationEngine engine = new ValidationEngine(applicableRules, apiDir);
                ValidationReport report = engine.validate();
                if (report == null) {
                    logger.error("Validation failed for {}", apiName);
                    continue;
                }
                // --- Set Configurable Labels (PASS/SUCCESS, FAIL/ERROR) ---
                if (activeConfig.getConfig().getLabels() != null) {
                    report.setLabels(activeConfig.getConfig().getLabels());
                }
                if (displayName != null && !displayName.trim().isEmpty()) {
                    if (displayName.contains(",")) {
                        String matchedProject = null;
                        String[] projects = displayName.split(",");
                        
                        // Calculate root folder name (the cloned repo folder)
                        Path relMatch = parentFolder.relativize(apiDir);
                        String rootFolder = (relMatch.getNameCount() > 0) ? relMatch.getName(0).toString() : apiName;
                        
                        // 1. Try Exact Match with Root Folder
                        for (String proj : projects) {
                            String p = proj.trim();
                            String pName = p;
                            if (pName.endsWith("/")) pName = pName.substring(0, pName.length() - 1);
                            int lastSlash = pName.lastIndexOf('/');
                            if (lastSlash >= 0) pName = pName.substring(lastSlash + 1);
                            if (pName.endsWith(".git")) pName = pName.substring(0, pName.length() - 4);
                            
                            if (pName.trim().equalsIgnoreCase(rootFolder.trim())) {
                                matchedProject = p;
                                break;
                            }
                        }
                        
                        // 2. Fallback: Try Exact Match with ApiName (if different)
                        if (matchedProject == null && !apiName.equals(rootFolder)) {
                             for (String proj : projects) {
                                String p = proj.trim();
                                String pName = p;
                                if (pName.endsWith("/")) pName = pName.substring(0, pName.length() - 1);
                                int lastSlash = pName.lastIndexOf('/');
                                if (lastSlash >= 0) pName = pName.substring(lastSlash + 1);
                                if (pName.endsWith(".git")) pName = pName.substring(0, pName.length() - 4);
                                
                                if (pName.trim().equalsIgnoreCase(apiName.trim())) {
                                    matchedProject = p;
                                    break;
                                }
                            }
                        }
                        
                        // 3. Fallback: Contains Check (Desperate Measure)
                        if (matchedProject == null) {
                             for (String proj : projects) {
                                String p = proj.trim();
                                if (p.contains("/" + rootFolder + ".git") || p.contains("/" + rootFolder + "/")) {
                                    matchedProject = p;
                                    break;
                                }
                            }
                        }

                        if (matchedProject != null) {
                            report.projectPath = matchedProject;
                        } else {
                            report.projectPath = displayName;
                        }
                    } else {
                        report.projectPath = displayName;
                    }
                } else {
                    report.projectPath = apiName + " (" + apiDir.toString() + ")";
                }
                ReportGenerator.generateIndividualReports(report, apiReportDir);
                int passed = report.passed.size();
                int failed = report.failed.size();
                int skipped = report.skipped.size();
                Path rel = parentFolder.relativize(apiDir);
                String repoName = rel.getNameCount() > 1 ? rel.getName(0).toString() : apiName;

                results.add(new ApiResult(apiName, repoName, apiDir, passed, failed, skipped, apiReportDir));
                logger.info("Validating API: {}", apiName);
                logger.info("   {} | Passed: {} | Failed: {} | Skipped: {}",
                        (failed == 0 ? "PASS" : "FAIL"), passed, failed, skipped);
            }
            try {
                ReportGenerator.generateConsolidatedReport(results, reportsRoot);
            } catch (Throwable t) {
                logger.error("Failed to generate consolidated report: {}", t.getMessage());
                logger.error("Stack trace:", t);
            }
            int totalPassed = results.stream().mapToInt(r -> r.passed).sum();
            int totalFailed = results.stream().mapToInt(r -> r.failed).sum();
            int totalSkipped = results.stream().mapToInt(r -> r.skipped).sum();
            String overallStatus = (totalFailed > 0) ? "FAIL" : "PASS";
            List<Map<String, Object>> passed = new ArrayList<>();
            List<Map<String, Object>> failed = new ArrayList<>();
            List<String> skipped = new ArrayList<>();
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
            logger.info("Aegis Validation Complete | Total APIs: {} | Passed: {} | Failed: {} | Status: {}", results.size(), totalPassed, totalFailed, overallStatus);
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("passed", new ArrayList<>());
            result.put("failed", new ArrayList<>());
            result.put("skipped", new ArrayList<>());
            result.put("message", e.getMessage());
            logger.error("Unexpected error during validation", e);
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
        if (configFilePath != null && !configFilePath.isEmpty()) {
            try {
                input = Files.newInputStream(Paths.get(configFilePath));
                logger.info("Loaded custom config from: {}", configFilePath);
            } catch (IOException e) {
                logger.error("Error loading custom config file: {}", configFilePath);
                logger.warn("Falling back to embedded rules.yaml");
                input = AegisMain.class.getClassLoader().getResourceAsStream("rules/rules.yaml");
            }
        } else {
            input = AegisMain.class.getClassLoader().getResourceAsStream("rules/rules.yaml");
        }
        if (input == null) {
            logger.error("rules.yaml not found!");
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
            logger.error("Error opening folder dialog", e);
        }
        return selected[0];
    }
    public static class ConfigSection {
        private Map<String, Object> projectIdentification;
        private Map<String, Integer> rules;
        private List<String> environments;
        private String folderPattern;
        private Map<String, String> labels;

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
        public String getFolderPattern() {
            return folderPattern;
        }
        public void setFolderPattern(String folderPattern) {
            this.folderPattern = folderPattern;
        }
        public Map<String, String> getLabels() {
            return labels;
        }
        public void setLabels(Map<String, String> labels) {
            this.labels = labels;
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
        public final String repository;
        public final Path path;
        public final int passed, failed, skipped;
        public final Path reportDir;
        public ApiResult(String name, String repository, Path path, int passed, int failed, int skipped, Path reportDir) {
            this.name = name;
            this.repository = repository;
            this.path = path;
            this.passed = passed;
            this.failed = failed;
            this.skipped = skipped;
            this.reportDir = reportDir;
        }
    }
}
