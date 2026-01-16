package com.raks.aegis.wrapper;

import com.raks.aegis.AegisMain;
import java.util.Map;

public class AegisInvoker {
    public static Map<String, Object> validate(String projectPath, String customRulesPath, String displayName,
            String reportDirName) {
        return AegisMain.validateAndReturnResults(projectPath, customRulesPath, displayName, reportDirName);
    }
}
