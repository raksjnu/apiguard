package com.raks.raksanalyzer.manual;
import com.raks.raksanalyzer.analyzer.tibco.TibcoAnalyzer;
import com.raks.raksanalyzer.domain.model.AnalysisResult;
import com.raks.raksanalyzer.generator.word.TibcoWordGenerator;
import org.junit.jupiter.api.Test;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.*;
public class ManualTibcoAnalysisTest {
    @Test
    public void generateFullTibcoReport() {
        String projectPath = "c:/raks/apiguard/raksanalyzer/testdata/TibcoApp1";
        System.out.println("Starting Analysis on: " + projectPath);
        TibcoAnalyzer analyzer = new TibcoAnalyzer(Paths.get(projectPath), new java.util.ArrayList<>());
        AnalysisResult result = analyzer.analyze();
        assertNotNull(result, "Analysis result should not be null");
        assertFalse(result.getFlows().isEmpty(), "Should have found flows");
        System.out.println("Flows found: " + result.getFlows().size());
        TibcoWordGenerator generator = new TibcoWordGenerator();
        String outputPath = generator.generate(result);
        System.out.println("Generated Report: " + outputPath);
        assertNotNull(outputPath, "Output path should not be null");
    }
}
