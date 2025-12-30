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
public class SpringDetector implements ApiDetector {
    @Override
    public boolean scan(File repoRoot, DiscoveryReport report) {
        boolean isSpring = false;
        File pomFile = new File(repoRoot, "pom.xml");
        if (pomFile.exists()) {
            try (FileReader reader = new FileReader(pomFile)) {
                MavenXpp3Reader mavenReader = new MavenXpp3Reader();
                Model model = mavenReader.read(reader);
                if (model.getDependencies() != null) {
                    boolean hasSpringWeb = model.getDependencies().stream()
                        .anyMatch(d -> d.getArtifactId().contains("spring-boot-starter-web"));
                    if (hasSpringWeb) {
                        isSpring = true;
                        report.setTechnology("Spring Boot");
                        report.addEvidence("Found spring-boot-starter-web in pom.xml");
                    }
                     boolean hasSwagger = model.getDependencies().stream()
                        .anyMatch(d -> d.getArtifactId().contains("springdoc") || d.getArtifactId().contains("swagger"));
                     if (hasSwagger) {
                         report.setConfidenceScore(report.getConfidenceScore() + 20);
                         report.addIndicator("OpenAPI/Swagger dependency found");
                     }
                }
            } catch (Exception e) {
            }
        }
        if (isSpring) {
            try (Stream<Path> paths = Files.walk(repoRoot.toPath())) {
                paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                    try {
                        String content = Files.readString(path);
                        if (content.contains("@RestController")) {
                            if (!report.getIndicators().contains("HTTP Endpoint (@RestController)")) {
                                report.setConfidenceScore(report.getConfidenceScore() + 70);
                                report.addIndicator("HTTP Endpoint (@RestController) detected in " + path.getFileName());
                                report.setClassification("API Service");
                            }
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
}
