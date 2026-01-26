package com.raks.raksanalyzer;

import com.raks.raksanalyzer.generator.word.WordGenerator;
import com.raks.raksanalyzer.domain.model.AnalysisResult;
import java.io.File;
import java.time.LocalDateTime;

public class ReproWordGen {
    public static void main(String[] args) {
        try {
            System.out.println("Starting Word Generation Test...");
            
            AnalysisResult result = new AnalysisResult();
            result.setAnalysisId("test-repro-" + System.currentTimeMillis());
            com.raks.raksanalyzer.domain.model.ProjectInfo info = new com.raks.raksanalyzer.domain.model.ProjectInfo();
            info.setProjectName("Test Project");
            info.setVersion("1.0.0");
            result.setProjectInfo(info);
            result.setStartTime(LocalDateTime.now());
            result.setEndTime(LocalDateTime.now());
            result.setSuccess(true);
            // Removed non-existent setters
            
            System.out.println("Initializing WordGenerator...");
            WordGenerator generator = new WordGenerator(); // This triggers POI init
            
            System.out.println("Generating document...");
            java.nio.file.Path path = generator.generate(result);
            System.out.println("Generated Word Doc at: " + path.toString());
            
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("Word Gen FAILED: " + e.getMessage());
        }
    }
}
