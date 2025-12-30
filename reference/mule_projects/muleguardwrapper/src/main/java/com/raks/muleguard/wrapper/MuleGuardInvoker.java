package com.raks.muleguard.wrapper;
import com.raks.muleguard.MuleGuardMain;
import java.util.Map;
public class MuleGuardInvoker {
    public static Map<String, Object> validate(String projectPath, String customRulesPath, String displayName,
            String reportDirName) {
        return MuleGuardMain.validateAndReturnResults(projectPath, customRulesPath, displayName, reportDirName);
    }
}
