package com.raks.raksanalyzer;

import com.raks.raksanalyzer.util.TibcoConfigParser;
import com.raks.raksanalyzer.domain.model.tibco.TibcoGlobalVariable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class ReproHttpPort {
    public static void main(String[] args) {
        try {
            System.out.println("Starting ReproHttpPort Test...");
            
            Path projectPath = Paths.get("testdata/customerOrder");
            Path customConfigPath = Paths.get("testdata/TIBCO.xml");
            
            System.out.println("Project Path: " + projectPath.toAbsolutePath());
            System.out.println("Custom Config: " + customConfigPath.toAbsolutePath());
            
            // 1. Parse Global Variables
            System.out.println("\n--- Parsing Global Variables ---");
            Map<String, List<TibcoGlobalVariable>> vars = TibcoConfigParser.parseGlobalVariables(projectPath, customConfigPath);
            
            // 2. Check for httpConnection/httpport
            System.out.println("\n--- checking values ---");
            checkValue(vars, "httpConnection/httpport");
            checkValue(vars, "httpport");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // Mimic TibcoAnalyzer.resolveGV
    private static void checkValue(Map<String, List<TibcoGlobalVariable>> globalVariables, String key) {
        
        System.out.println("--- Dumping matches for '" + key + "' ---");
        boolean accessFound = false;
        
        for (Map.Entry<String, List<TibcoGlobalVariable>> entry : globalVariables.entrySet()) {
            String groupName = entry.getKey();
            List<TibcoGlobalVariable> vars = entry.getValue();
            
            if (vars != null) {
                for (TibcoGlobalVariable gv : vars) {
                    if (gv.getFullName().contains(key) || gv.getName().contains(key)) {
                        System.out.println("Match in Group [" + groupName + "]: " + gv.getFullName() + " = " + gv.getValue() + " (Group: " + gv.getGroupName() + ", Name: " + gv.getName() + ")");
                        accessFound = true;
                    }
                }
            }
        }
        
        if (!accessFound) {
            System.out.println("No match found for key '" + key + "' in any group.");
        }
    }
}
