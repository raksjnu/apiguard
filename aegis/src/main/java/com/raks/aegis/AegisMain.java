package com.raks.aegis;

import com.raks.aegis.model.*;
import com.raks.aegis.engine.*;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.error.YAMLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import javax.swing.JFileChooser;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.util.concurrent.Callable;

@Command(name = "aegis", mixinStandardHelpOptions = true, version = "Aegis 1.0.0",
        description = "Aegis - Universal Code Compliance & Security Engine\n\n" +
                      "MODES:\n" +
                      "  CLI: java -jar aegis.jar -p <path> -c <rules.yaml>\n" +
                      "  GUI: java -jar aegis.jar --gui [--port <port>]")
public class AegisMain implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(AegisMain.class);

    @Option(names = {"-p", "--path", "--project"}, description = "Root folder to scan for projects", required = false)
    private Path parentFolder;

    @Option(names = {"-c", "--config"}, description = "Path to custom rules.yaml configuration", required = false)
    private String configFilePath;

    @Option(names = {"-o", "--output"}, description = "Report output directory name (default: Aegis-reports)", defaultValue = "Aegis-reports")
    private String reportDirName;

    @Option(names = {"-g", "--gui"}, description = "Start the Aegis GUI server", required = false)
    private boolean startGui;

    @Option(names = {"--port"}, description = "Port for the GUI server (default: 8080)", defaultValue = "8080")
    private int guiPort;

    @Option(names = {"-l", "--linkedConfig"}, description = "Path to the linked configuration project root", required = false)
    private Path linkedConfigPath;

    public static class AegisConfigurationException extends RuntimeException {
        public AegisConfigurationException(String message) {
            super(message);
        }
    }

    public static void main(String[] args) {
        System.exit(execute(args));
    }

    public static int execute(String[] args) {
        try {
            com.raks.aegis.license.LicenseValidator.validate(null);
            return new CommandLine(new AegisMain()).execute(args);
        } catch (AegisConfigurationException e) {
            logger.error("Aegis Configuration Error: {}", e.getMessage());
            return 1;
        }
    }

    @Override
    public Integer call() throws Exception {
        if (startGui) {
            String[] guiArgs = {String.valueOf(guiPort)};
            com.raks.aegis.gui.AegisGUI.main(guiArgs);
            // Keep the process alive while the server is running
            System.out.println("Aegis GUI is running. Press Ctrl+C to stop.");
            while (true) {
                Thread.sleep(1000);
            }
        }

        if (parentFolder == null) {
            parentFolder = showFolderDialog();
            if (parentFolder == null) {
                logger.info("No folder selected. Exiting.");
                return 0;
            }
        }

        if (!Files.isDirectory(parentFolder)) {
            logger.error("Error: Not a valid folder: {}", parentFolder);
            return 1;
        }

        logger.info("Aegis started for project: {}", parentFolder);
        logger.debug("Scanning for projects...");

        RootWrapper configWrapper = loadConfig(configFilePath);
        
        if (configWrapper.getConfig() == null) {
             logger.error("FATAL ERROR: 'config' section is missing or invalid in the configuration file.");
             throw new AegisConfigurationException("'config' section is missing or invalid in the configuration file.");
        }

        List<Rule> allRules = configWrapper.getRules();
        logger.info("Total rules loaded from config: {}", allRules != null ? allRules.size() : 0);
        Map<String, Object> projectIdConfig = configWrapper.getConfig().getProjectIdentification();
        int maxSearchDepth = (Integer) projectIdConfig.getOrDefault("maxSearchDepth", 5);

        @SuppressWarnings("unchecked")
        Map<String, Object> configFolderConfig = (Map<String, Object>) projectIdConfig.get("configFolder");
        String configFolderPattern = null;
        if (configFolderConfig != null) {
            configFolderPattern = (String) configFolderConfig.get("namePattern");
        } else {
             configFolderPattern = ".*_config"; 
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> targetProjectConfig = (Map<String, Object>) projectIdConfig.get("targetProject");
        String matchMode = (String) targetProjectConfig.getOrDefault("matchMode", "ANY");
        @SuppressWarnings("unchecked")
        List<String> markerFiles = (List<String>) targetProjectConfig.get("markerFiles");

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

        // Parse ignoredFiles configuration
        @SuppressWarnings("unchecked")
        Map<String, Object> ignoredFilesConfig = (Map<String, Object>) projectIdConfig.get("ignoredFiles");
        List<String> ignoredFileNames = new ArrayList<>();
        List<String> ignoredFilePrefixesList = new ArrayList<>();

        if (ignoredFilesConfig != null) {
            @SuppressWarnings("unchecked")
            List<String> exact = (List<String>) ignoredFilesConfig.get("exactNames");
            if (exact != null) ignoredFileNames.addAll(exact);

            @SuppressWarnings("unchecked")
            List<String> prefixes = (List<String>) ignoredFilesConfig.get("prefixes");
            if (prefixes != null) ignoredFilePrefixesList.addAll(prefixes);
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

        Path reportsRoot = parentFolder.resolve(reportDirName);
        try {
            Files.createDirectories(reportsRoot);
        } catch (IOException e) {
            logger.error("Failed to create reports directory: {}", e.getMessage());
            return 1;
        }

        List<Path> discoveredProjects = com.raks.aegis.util.ProjectDiscovery.findProjects(
                parentFolder,
                maxSearchDepth,
                markerFiles,
                matchMode,
                configFolderPattern,
                exactIgnoredNames,
                ignoredPrefixes);

        // Initialize ProjectTypeClassifier if projectTypes are defined
        com.raks.aegis.util.ProjectTypeClassifier projectTypeClassifier = null;
        Map<String, ProjectTypeDefinition> projectTypes = configWrapper.getConfig().getProjectTypes();
        if (projectTypes != null && !projectTypes.isEmpty()) {
            projectTypeClassifier = new com.raks.aegis.util.ProjectTypeClassifier(projectTypes);
            logger.info("Project type filtering enabled with {} type(s): {}", 
                projectTypes.size(), String.join(", ", projectTypes.keySet()));
        }

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
                        boolean defaultRanges = (configRuleStart == 0 && configRuleEnd == Integer.MAX_VALUE);

                        if (globalEnvironments != null && !globalEnvironments.isEmpty()) {
                            if (rule.getChecks() != null) {
                                rule.getChecks().forEach(check -> {
                                    if (check.getParams() == null) {
                                        check.setParams(new java.util.HashMap<>());
                                    }
                                    @SuppressWarnings("unchecked")
                                    List<String> envs = (List<String>) check.getParams().get("environments");
                                    if (envs != null && envs.size() == 1 && "ALL".equalsIgnoreCase(envs.get(0))) {
                                        check.getParams().put("environments", new ArrayList<>(globalEnvironments));
                                    } else if (envs == null || envs.isEmpty()) {
                                        if (isConfigRule) {
                                            check.getParams().put("environments", new ArrayList<>(globalEnvironments));
                                        }
                                    }
                                });
                            }
                        }

                        if (defaultRanges) return true;
                        return isConfigProject == isConfigRule;
                    }).collect(Collectors.toList());

            Path currentLinkedConfig = (linkedConfigPath != null) ? linkedConfigPath : com.raks.aegis.util.ProjectContextHelper.findLinkedConfig(apiDir, discoveredProjects).orElse(null);
            ValidationEngine engine = new ValidationEngine(applicableRules, apiDir, projectTypeClassifier, ignoredFileNames, ignoredFilePrefixesList, currentLinkedConfig);
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
            logger.error("FAILED TO GENERATE CONSOLIDATED REPORT!", t);
        }

        int totalPassed = results.stream().mapToInt(r -> r.passed).sum();
        int totalFailed = results.stream().mapToInt(r -> r.failed).sum();
        logger.info("Aegis Validation Complete | Total APIs: {} | Passed: {} | Failed: {}", results.size(), totalPassed, totalFailed);
        logger.info("Consolidated report: {}", reportsRoot.resolve("CONSOLIDATED-REPORT.html"));

        return 0;
    }
    public static Map<String, Object> validateAndReturnResults(String projectPath, String customRulesPath) {
        return validateAndReturnResults(projectPath, customRulesPath, null, null);
    }
    public static Map<String, Object> validateAndReturnResults(String projectPath, String customRulesPath, String displayName) {
        return validateAndReturnResults(projectPath, customRulesPath, displayName, "Aegis-reports", null);
    }
    public static Map<String, Object> validateAndReturnResults(String projectPath, String customRulesPath, String displayName, String reportDirName) {
        return validateAndReturnResults(projectPath, customRulesPath, displayName, reportDirName, null);
    }
    @SuppressWarnings("unchecked")
    public static Map<String, Object> validateAndReturnResults(String projectPath, String customRulesPath,
            String displayName, String reportDirName, String licenseKey) {
        Map<String, Object> result = new HashMap<>();
        try {
            com.raks.aegis.license.LicenseValidator.validate(licenseKey);
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

            RootWrapper activeConfig = customRulesPath != null ? loadConfig(customRulesPath) : loadConfig(null);
            logger.info("Total rules loaded from config: {}", activeConfig.getRules() != null ? activeConfig.getRules().size() : 0);

            Map<String, Object> projectIdConfig = activeConfig.getConfig().getProjectIdentification();
        Map<String, Object> targetProjectConfig = (Map<String, Object>) projectIdConfig.get("targetProject");
            Map<String, Object> configFolderConfig = (Map<String, Object>) projectIdConfig.get("configFolder");
            Map<String, Object> ignoredFoldersConfig = (Map<String, Object>) projectIdConfig.get("ignoredFolders");
            Map<String, Object> ignoredFilesConfig = (Map<String, Object>) projectIdConfig.get("ignoredFiles");

            int maxSearchDepth = (Integer) projectIdConfig.getOrDefault("maxSearchDepth", 5);
            String matchMode = (String) targetProjectConfig.getOrDefault("matchMode", "ANY");
            List<String> markerFiles = (List<String>) targetProjectConfig.get("markerFiles");
            String configFolderPattern = (String) configFolderConfig.get("namePattern");
            List<String> exactIgnoredNames = new ArrayList<>();
            List<String> ignoredPrefixes = new ArrayList<>();
            if (ignoredFoldersConfig != null) {
                if (ignoredFoldersConfig.get("exactNames") != null) {
                    exactIgnoredNames = (List<String>) ignoredFoldersConfig.get("exactNames");
                }
                if (ignoredFoldersConfig.get("prefixes") != null) {
                    ignoredPrefixes = (List<String>) ignoredFoldersConfig.get("prefixes");
                }
            }
            
            List<String> ignoredFileNames = new ArrayList<>();
            List<String> ignoredFilePrefixesList = new ArrayList<>();
            if (ignoredFilesConfig != null) {
                if (ignoredFilesConfig.get("exactNames") != null) {
                    ignoredFileNames = (List<String>) ignoredFilesConfig.get("exactNames");
                }
                if (ignoredFilesConfig.get("prefixes") != null) {
                    ignoredFilePrefixesList = (List<String>) ignoredFilesConfig.get("prefixes");
                }
            }
            List<String> globalEnvironments = activeConfig.getConfig().getEnvironments();

            // Initialize ProjectTypeClassifier if projectTypes are defined
            com.raks.aegis.util.ProjectTypeClassifier projectTypeClassifier = null;
            Map<String, ProjectTypeDefinition> projectTypes = activeConfig.getConfig().getProjectTypes();
            if (projectTypes != null && !projectTypes.isEmpty()) {
                projectTypeClassifier = new com.raks.aegis.util.ProjectTypeClassifier(projectTypes);
            }

            List<Path> discoveredProjects = com.raks.aegis.util.ProjectDiscovery.findProjects(
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

                List<Rule> applicableRules = activeConfig.getRules().stream()
                        .filter(Rule::isEnabled)
                        .filter(rule -> {
                            String scope = rule.getScope();

                            if (scope == null || scope.isEmpty() || "GLOBAL".equalsIgnoreCase(scope)) {
                                return true;
                            }
                            if (isConfigProject) {
                                return "CONFIG".equalsIgnoreCase(scope) || "BOTH".equalsIgnoreCase(scope); 
                            } else {
                                return "APP".equalsIgnoreCase(scope) || "BOTH".equalsIgnoreCase(scope); 
                            }
                        })
                        .peek(rule -> {

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

                java.nio.file.Path linkedConfigPath = com.raks.aegis.util.ProjectContextHelper.findLinkedConfig(apiDir, discoveredProjects).orElse(null);
                ValidationEngine engine = new ValidationEngine(applicableRules, apiDir, projectTypeClassifier, ignoredFileNames, ignoredFilePrefixesList, linkedConfigPath);
                ValidationReport report = engine.validate();
                if (report == null) {
                    logger.error("Validation failed for {}", apiName);
                    continue;
                }

                if (activeConfig.getConfig().getLabels() != null) {
                    report.setLabels(activeConfig.getConfig().getLabels());
                }
                if (displayName != null && !displayName.trim().isEmpty()) {
                    if (displayName.contains(",")) {
                        String matchedProject = null;
                        String[] projects = displayName.split(",");

                        Path relMatch = parentFolder.relativize(apiDir);
                        String rootFolder = (relMatch.getNameCount() > 0) ? relMatch.getName(0).toString() : apiName;

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
        options.setAllowDuplicateKeys(false);
        Constructor constructor = new Constructor(RootWrapper.class, options);
        TypeDescription td = new TypeDescription(RootWrapper.class);
        td.addPropertyParameters("rules", Rule.class);
        constructor.addTypeDescription(td);
        Yaml yaml = new Yaml(constructor);
        InputStream input = null;
        if (configFilePath != null && !configFilePath.isEmpty()) {
            try {
                String content;
                try {
                    byte[] bytes = Files.readAllBytes(Paths.get(configFilePath));
                    java.nio.charset.CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
                    decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT);
                    decoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
                    content = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                } catch (java.nio.charset.CharacterCodingException e) {
                    logger.warn("Failed to read config as UTF-8. Retrying with Windows-1252 to handle special characters. File: {}", configFilePath);
                    content = new String(Files.readAllBytes(Paths.get(configFilePath)), java.nio.charset.Charset.forName("Windows-1252"));
                }
                logger.info("Loaded custom config from: {}", Paths.get(configFilePath).toAbsolutePath());
                content = sanitizeYamlContent(content);
                return yaml.loadAs(content, RootWrapper.class);
            } catch (YAMLException e) {
                logger.error("");
                logger.error("****************************************************************");
                logger.error("FATAL ERROR: Invalid YAML Configuration in: {}", Paths.get(configFilePath).toAbsolutePath());
                logger.error("Reason: {}", e.getMessage());
                logger.error("****************************************************************");
                throw new AegisConfigurationException("Invalid YAML Configuration in " + Paths.get(configFilePath).toAbsolutePath() + ": " + e.getMessage());
            } catch (IOException e) {
                logger.error("FATAL ERROR: Could not read custom config file: {}", Paths.get(configFilePath).toAbsolutePath());
                throw new AegisConfigurationException("Custom config file not found or unreadable: " + Paths.get(configFilePath).toAbsolutePath());
            }
        } else {
            input = AegisMain.class.getClassLoader().getResourceAsStream("rules/rules.yaml");
        }
        if (input == null) {
            logger.error("rules.yaml not found!");
            throw new AegisConfigurationException("Default rules.yaml not found!");
        }
        try {
            return yaml.loadAs(input, RootWrapper.class);
        } catch (YAMLException e) {
            logger.error("FATAL ERROR: Invalid YAML in default rules.yaml or fallback!");
            logger.error("Reason: {}", e.getMessage());
            throw new AegisConfigurationException("Invalid YAML in default rules.yaml: " + e.getMessage());
        }
    }

    private static String sanitizeYamlContent(String content) {
        if (content == null) return null;
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            
            // Normalize non-breaking space (0xA0) to regular space
            if (ch == 0xA0) {
                sb.append(' ');
                continue;
            }

            // Strip Byte Order Mark (BOM) 0xFEFF
            if (ch == '\uFEFF') {
                 continue;
            }

            // Strip Next Line (NEL) 0x85 - Treat as invalid/control to force standard newlines
            if (ch == 0x85) {
                // Optionally replace with \n if it was acting as newline, but usually better to let existing \n handle it.
                // If the file uses purely NEL newlines, stripping it breaks lines.
                // But normally we have CRLF or LF. Let's assume standard files. 
                // Replacing with \n is safer if it IS a newline.
                sb.append('\n'); 
                continue; 
            }

            // Allow:
            // 0x09 (Tab), 0x0A (LF), 0x0D (CR)
            // 0x20-0x7E (Printable ASCII)
            // 0xA0-0xD7FF (Unicode printable) - handled above for A0
            // 0xE000-0xFFFD
            
            boolean isControl = (ch <= 0x1F && ch != 0x09 && ch != 0x0A && ch != 0x0D) || 
                                (ch >= 0x7F && ch <= 0x9F); // 0x85 checked specifically above
            
            if (!isControl) {
                sb.append(ch);
            }
        }
        return sb.toString();
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
        private Map<String, ProjectTypeDefinition> projectTypes;  // NEW: Centralized project type definitions
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
        public Map<String, ProjectTypeDefinition> getProjectTypes() {
            return projectTypes;
        }
        public void setProjectTypes(Map<String, ProjectTypeDefinition> projectTypes) {
            this.projectTypes = projectTypes;
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
