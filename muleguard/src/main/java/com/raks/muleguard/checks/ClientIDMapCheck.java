package com.raks.muleguard.checks;
import com.raks.muleguard.model.Check;
import com.raks.muleguard.model.CheckResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;
public class ClientIDMapCheck extends AbstractCheck {
    private static final Pattern CLIENTIDMAP_PATTERN = Pattern.compile(
            "truist\\.authz\\.policy\\.clientIDmap\\.(GET|POST|PUT|DELETE|PATCH):[^=]+=([^:;]+:[^:;]+)(;[^:;]+:[^:;]+)*;?");
    private static final Pattern SECURE_PROPERTY_PATTERN = Pattern.compile(
            "secure::.+=\\^\\{.+=\\}");
    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        @SuppressWarnings("unchecked")
        List<String> fileExtensions = (List<String>) check.getParams().get("fileExtensions");
        @SuppressWarnings("unchecked")
        List<String> environments = (List<String>) check.getParams().get("environments");
        String validationType = (String) check.getParams().getOrDefault("validationType", "CLIENTIDMAP");
        if (fileExtensions == null || fileExtensions.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'fileExtensions' parameter is required");
        }
        if (environments == null || environments.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'environments' parameter is required. " +
                            "Ensure this rule is in the config rule range (100-199) or specify environments in params.");
        }
        final List<String> finalEnvironments = environments;
        List<String> failures = new ArrayList<>();
        List<Path> matchingFiles = new ArrayList<>();
        List<String> scannedFiles = new ArrayList<>();
        boolean foundMatch = false;
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            matchingFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesEnvironmentFile(path, finalEnvironments, fileExtensions))
                    .filter(path -> !shouldIgnorePath(projectRoot, path)) 
                    .toList();
            for (Path file : matchingFiles) {
                String relativePath = projectRoot.relativize(file).toString();
                scannedFiles.add(relativePath);
                String content = Files.readString(file);
                String[] lines = content.split("\\r?\\n");
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                        continue;
                    }
                    Pattern pattern = "SECURE".equalsIgnoreCase(validationType)
                            ? SECURE_PROPERTY_PATTERN
                            : CLIENTIDMAP_PATTERN;
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.matches()) {
                        foundMatch = true;
                        if ("CLIENTIDMAP".equalsIgnoreCase(validationType)) {
                            if (!validateClientIDMapFormat(line)) {
                                failures.add(String.format(
                                        "Invalid format in %s: %s (contains double colons or invalid separators)",
                                        relativePath, line));
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Error scanning files: " + e.getMessage());
        }
        if (!foundMatch) {
            String filesScanned = scannedFiles.isEmpty()
                    ? "No matching environment files found"
                    : "Files scanned: " + String.join(", ", scannedFiles);
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    String.format("No valid properties matching the %s pattern found in environment files.\n%s\n" +
                            "Expected pattern: %s\n" +
                            "Check that your files contain the required properties with correct format.",
                            validationType,
                            filesScanned,
                            validationType.equals("SECURE")
                                    ? "secure::<name>=^{<encrypted-value>=}"
                                    : "truist.authz.policy.clientIDmap.<METHOD>:/<path>=<id>:<name>;<id>:<name>;..."));
        }
        if (!failures.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Validation failures:\n• " + String.join("\n• ", failures));
        }
        String fileList = scannedFiles.stream()
                .collect(Collectors.joining("; "));
        return CheckResult.pass(check.getRuleId(), check.getDescription(),
                "All client IDs have corresponding entries in the mapping\nFiles validated: " + fileList);
    }
    private boolean validateClientIDMapFormat(String line) {
        int equalsIndex = line.indexOf('=');
        if (equalsIndex < 0) {
            return false;
        }
        String valuePart = line.substring(equalsIndex + 1);
        String[] pairs = valuePart.split(";");
        for (String pair : pairs) {
            pair = pair.trim();
            if (pair.isEmpty()) {
                continue;
            }
            long colonCount = pair.chars().filter(ch -> ch == ':').count();
            if (colonCount != 1) {
                return false; 
            }
        }
        return true;
    }
    private boolean matchesEnvironmentFile(Path path, List<String> environments, List<String> fileExtensions) {
        String fileName = path.getFileName().toString();
        String fileBaseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        String extension = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "";
        return environments.contains(fileBaseName) && fileExtensions.contains(extension);
    }
}
