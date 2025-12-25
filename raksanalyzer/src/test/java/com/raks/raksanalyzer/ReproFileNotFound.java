package com.raks.raksanalyzer;

import com.raks.raksanalyzer.util.TibcoConfigParser;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

public class ReproFileNotFound {
    public static void main(String[] args) {
        System.out.println("Starting ReproFileNotFound Test...");
        
        // Use a definitely non-existent path
        Path projectPath = Paths.get("testdata/customerOrder");
        Path badConfigPath = Paths.get("non_existent_config.xml");
        
        System.out.println("Invoking parser with bad config path: " + badConfigPath.toAbsolutePath());
        
        try {
            TibcoConfigParser.parseGlobalVariables(projectPath, badConfigPath);
            System.out.println("Parser invocation complete.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
