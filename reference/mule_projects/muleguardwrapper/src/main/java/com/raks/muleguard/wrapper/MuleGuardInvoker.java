package com.raks.muleguard.wrapper;

import com.raks.muleguard.MuleGuardMain;
import java.util.Map;

/**
 * MuleGuard Invoker - Wrapper class to invoke MuleGuard from Mule flows
 */
public class MuleGuardInvoker {

    /**
     * Validate Mule projects using MuleGuard
     * 
     * @param projectPath     Path to the Mule projects folder
     * @param customRulesPath Optional path to custom rules.yaml file
     * @param displayName     Optional display name (e.g., ZIP filename)
     * @return Map containing validation results
     */
    public static Map<String, Object> validate(String projectPath, String customRulesPath, String displayName,
            String reportDirName) {
        // Delegate to MuleGuardMain's new method that returns results
        return MuleGuardMain.validateAndReturnResults(projectPath, customRulesPath, displayName, reportDirName);
    }
}
