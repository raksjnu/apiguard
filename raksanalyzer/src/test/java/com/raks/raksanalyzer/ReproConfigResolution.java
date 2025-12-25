package com.raks.raksanalyzer;

import com.raks.raksanalyzer.util.TibcoConfigParser;
import com.raks.raksanalyzer.domain.model.tibco.TibcoGlobalVariable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class ReproConfigResolution {
    public static void main(String[] args) {
        try {
            System.out.println("Starting Reproduction Test...");
            
            Path projectPath = Paths.get("testdata/customerOrder");
            Path customConfigPath = Paths.get("testdata/TIBCO.xml");
            
            System.out.println("Project Path: " + projectPath.toAbsolutePath());
            System.out.println("Custom Config: " + customConfigPath.toAbsolutePath());
            
            // 1. Parse Global Variables
            System.out.println("\n--- Parsing Global Variables ---");
            Map<String, List<TibcoGlobalVariable>> vars = TibcoConfigParser.parseGlobalVariables(projectPath, customConfigPath);
            
            // 2. Print what was loaded
            for (String env : vars.keySet()) {
                System.out.println("Environment: " + env);
                for (TibcoGlobalVariable gv : vars.get(env)) {
                    System.out.println("  " + gv.getFullName() + " = " + gv.getValue());
                }
            }
            
            // 3. Test Resolution Logic (Simulating TibcoAnalyzer.resolveGV)
            System.out.println("\n--- Testing Resolution Logic ---");
            String[] testKeys = {
                "%%defaultVars/MyUniqueVar%%",      // Should be "DefaultValue" (from defaultVars)
                "%%Connections/FILE/inDir%%"        // Should be "C:\\tibco510\\raks\\in\\" (from TIBCO.xml)
            };
            
            for (String key : testKeys) {
                String resolved = resolveGV(key, vars);
                System.out.println("Resolving " + key + " -> " + resolved);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // Mimic TibcoAnalyzer.resolveGV
    private static String resolveGV(String value, Map<String, List<TibcoGlobalVariable>> globalVariables) {
        if (value == null || !value.startsWith("%%") || !value.endsWith("%%")) {
            return value;
        }
        
        String key = value.substring(2, value.length() - 2);
        
        // Try default environment
        List<TibcoGlobalVariable> vars = globalVariables.get("default");
        if (vars != null) {
            for (TibcoGlobalVariable gv : vars) {
                if (gv.getFullName().equals(key) || gv.getName().equals(key)) {
                    return gv.getValue();
                }
            }
        }
        
        return value;
    }
}
