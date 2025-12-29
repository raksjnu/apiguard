package com.raks.apidiscovery.detectors;

import com.raks.apidiscovery.model.DiscoveryReport;
import com.raks.apidiscovery.model.RuleConfig;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ConfigurableDetector implements ApiDetector {

    private RuleConfig.Rule rule;

    public ConfigurableDetector(RuleConfig.Rule rule) {
        this.rule = rule;
    }

    @Override
    public boolean scan(File repoRoot, DiscoveryReport report) {
        // 1. Check Project Markers (Is this the right tech?)
        boolean matchesTech = true;
        
        if (rule.getProjectMarkers() == null || rule.getProjectMarkers().isEmpty()) {
            return false;
        }

        // Strict "AND" logic: ALL markers must be present
        for (RuleConfig.Marker marker : rule.getProjectMarkers()) {
            if (!checkFileExistsAndContains(repoRoot, marker.getFile(), marker.getContent())) {
                matchesTech = false;
                break;
            }
        }

        if (matchesTech) {
            report.setTechnology(rule.getTechnology());
            StringBuilder proof = new StringBuilder("Identified by presence of: ");
            for (RuleConfig.Marker m : rule.getProjectMarkers()) {
                proof.append(m.getFile());
                if (m.getContent() != null && !m.getContent().isEmpty()) {
                    proof.append(" (contains '").append(m.getContent()).append("')");
                }
                proof.append(", ");
            }
            if (proof.length() > 2) proof.setLength(proof.length() - 2);
            report.addEvidence(proof.toString());
        } else {
            return false;
        }
        
        // 2. Scan for API Indicators (Score it)
        try (Stream<Path> paths = Files.walk(repoRoot.toPath())) {
            List<Path> allFiles = paths.filter(Files::isRegularFile)
                                       .filter(p -> !p.toString().contains(".git")) // Exclude .git internals
                                       .toList();

            for (RuleConfig.Indicator indicator : rule.getApiIndicators()) {
                String filePattern = indicator.getFile().replace("*", ".*");
                Pattern pattern = Pattern.compile(filePattern);

                for (Path path : allFiles) {
                     String fileName = path.getFileName().toString();
                     // Simple glob match simulation
                     boolean nameMatch = fileName.equals(indicator.getFile()) || 
                                       (indicator.getFile().startsWith("*.") && fileName.endsWith(indicator.getFile().substring(1)));

                     if (nameMatch) {
                         // Check content if specified
                         if (indicator.getContent() != null && !indicator.getContent().isEmpty()) {
                             try {
                                 // Simple check - for large files strictly reading line by line is better
                                 String content = Files.readString(path); 
                                 if (content.contains(indicator.getContent())) {
                                     report.setConfidenceScore(report.getConfidenceScore() + indicator.getScore());
                                     report.addIndicator(indicator.getDescription() + " in " + fileName);
                                 }
                             } catch (IOException e) { /* ignore read error */ }
                         } else {
                             // File existence is enough
                             report.setConfidenceScore(report.getConfidenceScore() + indicator.getScore());
                             report.addIndicator(indicator.getDescription() + " (" + fileName + " present)");
                         }
                     }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return true;
    }

    private boolean checkFileExistsAndContains(File root, String fileNamePattern, String content) {
        // Handle specific file logic
        if (!fileNamePattern.contains("*")) {
             File f = new File(root, fileNamePattern);
             if (!f.exists()) return false;
             if (content == null || content.isEmpty()) return true;
             
             try {
                 String fileContent = Files.readString(f.toPath());
                 return fileContent.contains(content);
             } catch (IOException e) { return false; }
        }
        
        // Handle Wildcards (e.g., *.archive)
        try (Stream<Path> paths = Files.walk(root.toPath())) {
             return paths.filter(Files::isRegularFile)
                  .filter(p -> !p.toString().contains(".git"))
                 .anyMatch(path -> {
                     String name = path.getFileName().toString();
                     // Simple suffix match
                     boolean match = name.equals(fileNamePattern) || 
                                     (fileNamePattern.startsWith("*.") && name.endsWith(fileNamePattern.substring(1)));
                     
                     if (match) {
                         if (content == null || content.isEmpty()) return true;
                         try {
                              return Files.readString(path).contains(content);
                         } catch (IOException e) { return false; }
                     }
                     return false;
                 });
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
