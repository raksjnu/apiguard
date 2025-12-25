package com.raks.raksanalyzer;

import com.raks.raksanalyzer.api.rest.AnalysisResource;
import com.raks.raksanalyzer.domain.model.AnalysisRequest;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Method;

public class ReproAnalysisResource {
    public static void main(String[] args) {
        try {
            System.out.println("Starting ReproAnalysisResource Test...");
            
            // 1. Set System Property (Simulate CLI arg)
            String virtualConfigPath = "C:\\raks\\apiguard\\raksanalyzer\\testdata\\1.xml";
            System.setProperty("raksanalyzer.config.path", virtualConfigPath);
            System.out.println("System Property 'raksanalyzer.config.path' set to: " + virtualConfigPath);
            
            // 2. Create Analysis Request (Simulate UI request)
            AnalysisRequest request = new AnalysisRequest();
            request.setInputSourceType("folder");
            request.setInputPath("C:/test/project");
            // configFilePath is explicitly NULL/Empty here
            
            System.out.println("Request Config Path BEFORE: " + request.getConfigFilePath());
            
            // 3. Execute Logic
            // Since AnalysisResource has dependencies, we just test the specific logic block 
            // by creating a minimal version or invoking the method if possible.
            // AnalysisResource constructor might trigger ConfigurationManager loading which is fine.
            
            AnalysisResource resource = new AnalysisResource();
            Response response = resource.analyze(request);
            
            // 4. Verify Request Object Modification
            System.out.println("Request Config Path AFTER: " + request.getConfigFilePath());
            
            if (virtualConfigPath.equals(request.getConfigFilePath())) {
                System.out.println("SUCCESS: AnalysisRequest was updated with CLI config path!");
            } else {
                System.out.println("FAILURE: AnalysisRequest was NOT updated.");
            }
            
            // Force exit to kill any threads spawned by AnalysisResource
            System.exit(0);
            
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
