package com.raks.raksanalyzer;
import com.raks.raksanalyzer.util.TibcoConfigParser;
import com.raks.raksanalyzer.domain.model.tibco.TibcoGlobalVariable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
public class ReproCustomConfig {
    public static void main(String[] args) {
        try {
            System.out.println("Starting ReproCustomConfig Test...");
            Path projectPath = Paths.get("testdata/customerOrder");
            Path customConfigPath = Paths.get("testdata/1.xml");
            System.out.println("Project Path: " + projectPath.toAbsolutePath());
            System.out.println("Custom Config Path: " + customConfigPath.toAbsolutePath());
            System.out.println("\n--- Parsing Global Variables ---");
            Map<String, List<TibcoGlobalVariable>> vars = TibcoConfigParser.parseGlobalVariables(projectPath, customConfigPath);
            System.out.println("\n--- Checking Results ---");
            checkValue(vars, "httpConnection/httpport");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static void checkValue(Map<String, List<TibcoGlobalVariable>> globalVariables, String key) {
        System.out.println("--- Dumping matches for '" + key + "' ---");
        boolean matchFound = false;
        for (Map.Entry<String, List<TibcoGlobalVariable>> entry : globalVariables.entrySet()) {
            String groupName = entry.getKey();
            for (TibcoGlobalVariable gv : entry.getValue()) {
                if (gv.getFullName().contains(key) || gv.getName().contains(key)) {
                    System.out.println("Match in Group [" + groupName + "]: " + 
                                       gv.getFullName() + " = " + gv.getValue() + 
                                       " (Group: " + gv.getGroupName() + ")");
                    matchFound = true;
                }
            }
        }
        if (!matchFound) {
            System.out.println("No match found for key '" + key + "'");
        }
    }
}
