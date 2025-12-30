package com.raks.apidiscovery;
import com.google.gson.Gson;
import com.raks.apidiscovery.detectors.ApiDetector;
import com.raks.apidiscovery.detectors.ConfigurableDetector;
import com.raks.apidiscovery.model.DiscoveryReport;
import com.raks.apidiscovery.model.RuleConfig;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
public class ScannerEngine {
    private List<ApiDetector> detectors = new ArrayList<>();
    private List<RuleConfig.MetadataRule> metadataRules = new ArrayList<>();
    public ScannerEngine() {
        loadRules();
    }
    private void loadRules() {
        try (InputStreamReader reader = new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("rules-config.json"))) {
            Gson gson = new Gson();
            RuleConfig config = gson.fromJson(reader, RuleConfig.class);
            if (config != null) {
                if (config.getRules() != null) {
                    for (RuleConfig.Rule rule : config.getRules()) {
                        detectors.add(new ConfigurableDetector(rule));
                    }
                }
                if (config.getMetadataRules() != null) {
                    this.metadataRules = config.getMetadataRules();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public DiscoveryReport scanRepository(File repoRoot) {
        DiscoveryReport report = new DiscoveryReport();
        report.setRepoName(repoRoot.getName());
        report.setRepoPath(repoRoot.getAbsolutePath());
        boolean detected = false;
        for (ApiDetector detector : detectors) {
            if (detector.scan(repoRoot, report)) {
                detected = true;
                break; 
            }
        }
        if (!detected) {
            report.setTechnology("Unknown / Generic");
            report.setClassification("Standalone / Library");
        } else {
             int finalScore = Math.min(100, report.getConfidenceScore());
             report.setConfidenceScore(finalScore);
            if (finalScore >= 70) {
                report.setClassification("API Service");
            } else if (finalScore > 0) {
                report.setClassification("Potential API / Batch");
            } else {
                 report.setClassification("Batch / Worker / Library");
            }
        }
        processMetadataRules(repoRoot, report);
        return report;
    }
    private void processMetadataRules(File repoRoot, DiscoveryReport report) {
        if (this.metadataRules == null || this.metadataRules.isEmpty()) return;
        try (Stream<Path> paths = Files.walk(repoRoot.toPath())) {
            List<Path> allFiles = paths.filter(Files::isRegularFile)
                                       .filter(p -> !p.toString().contains(".git")) 
                                       .toList();
            for (RuleConfig.MetadataRule rule : this.metadataRules) {
                String filePattern = rule.getFile();
                String contentToFind = rule.getContent();
                boolean found = false;
                for (Path path : allFiles) {
                    String fileName = path.getFileName().toString();
                    boolean nameMatch = fileName.equals(filePattern) || 
                                      (filePattern.startsWith("*.") && fileName.endsWith(filePattern.substring(1)));
                    if (nameMatch) {
                        if (contentToFind == null || contentToFind.isEmpty()) {
                            found = true;
                        } else {
                            try {
                                String fileContent = Files.readString(path);
                                if (rule.isCaseInsensitive()) {
                                    if (fileContent.toLowerCase().contains(contentToFind.toLowerCase())) {
                                        found = true;
                                    }
                                } else {
                                    if (fileContent.contains(contentToFind)) {
                                        found = true;
                                    }
                                }
                            } catch (IOException e) {   }
                        }
                    }
                    if (found) break; 
                }
                if (found) {
                    String existing = report.getMetadata().get(rule.getCategory());
                    if (existing == null) {
                        report.addMetadata(rule.getCategory(), rule.getValue());
                    } else if (!existing.contains(rule.getValue())) {
                        report.addMetadata(rule.getCategory(), existing + ", " + rule.getValue());
                    }
                }
            }
            ensureMetadataPresent(report, "Logging", "Best Practices Followed (No prohibited patterns found)");
            ensureMetadataPresent(report, "Documentation", "None Detected");
            ensureMetadataPresent(report, "Security", "No Specific Framework Detected");
            ensureMetadataPresent(report, "PII Risk", "None Detected (Safe)");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void ensureMetadataPresent(DiscoveryReport report, String category, String defaultValue) {
        if (!report.getMetadata().containsKey(category)) {
            report.addMetadata(category, defaultValue);
        }
    }
}
