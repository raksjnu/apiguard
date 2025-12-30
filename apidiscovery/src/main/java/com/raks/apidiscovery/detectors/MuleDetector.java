package com.raks.apidiscovery.detectors;
import com.raks.apidiscovery.model.DiscoveryReport;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
public class MuleDetector implements ApiDetector {
    @Override
    public boolean scan(File repoRoot, DiscoveryReport report) {
        boolean isMule = false;
        File pomFile = new File(repoRoot, "pom.xml");
        if (pomFile.exists()) {
            try (FileReader reader = new FileReader(pomFile)) {
                MavenXpp3Reader mavenReader = new MavenXpp3Reader();
                Model model = mavenReader.read(reader);
                if (model.getBuild() != null && model.getBuild().getPlugins() != null) {
                    boolean hasMulePlugin = model.getBuild().getPlugins().stream()
                        .anyMatch(p -> p.getArtifactId().equals("mule-maven-plugin"));
                    if (hasMulePlugin) {
                        isMule = true;
                        report.setTechnology("MuleSoft 4");
                        report.addEvidence("Found mule-maven-plugin in pom.xml");
                    }
                }
            } catch (Exception e) {
            }
        }
        if (isMule || hasMuleArtifactJson(repoRoot)) {
            if (!isMule) {
             report.setTechnology("MuleSoft 4"); 
             isMule = true;
            }
            try (Stream<Path> paths = Files.walk(repoRoot.toPath())) {
                paths.filter(p -> p.toString().endsWith(".xml")).forEach(path -> {
                    try {
                        String content = Files.readString(path);
                        if (content.contains("http:listener")) {
                            report.setConfidenceScore(report.getConfidenceScore() + 70);
                            report.addIndicator("HTTP Listener detected in " + path.getFileName());
                            report.setClassification("API Service");
                        }
                        if (content.contains("apikit:router") || content.contains("soapkit:router")) {
                             report.setConfidenceScore(Math.min(100, report.getConfidenceScore() + 20)); 
                             report.addIndicator("APIKit Router detected in " + path.getFileName());
                        }
                    } catch (IOException e) {
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }
    private boolean hasMuleArtifactJson(File root) {
        return new File(root, "mule-artifact.json").exists();
    }
}
