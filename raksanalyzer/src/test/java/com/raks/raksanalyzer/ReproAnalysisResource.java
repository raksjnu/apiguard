package com.raks.raksanalyzer;
import com.raks.raksanalyzer.api.rest.AnalysisResource;
import com.raks.raksanalyzer.domain.model.AnalysisRequest;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Method;
public class ReproAnalysisResource {
    public static void main(String[] args) {
        try {
            System.out.println("Starting ReproAnalysisResource Test...");
            String virtualConfigPath = "C:\\raks\\apiguard\\raksanalyzer\\testdata\\1.xml";
            System.setProperty("raksanalyzer.config.path", virtualConfigPath);
            System.out.println("System Property 'raksanalyzer.config.path' set to: " + virtualConfigPath);
            AnalysisRequest request = new AnalysisRequest();
            request.setInputSourceType("folder");
            request.setInputPath("C:/test/project");
            System.out.println("Request Config Path BEFORE: " + request.getConfigFilePath());
            AnalysisResource resource = new AnalysisResource();
            Response response = resource.analyze(request);
            System.out.println("Request Config Path AFTER: " + request.getConfigFilePath());
            if (virtualConfigPath.equals(request.getConfigFilePath())) {
                System.out.println("SUCCESS: AnalysisRequest was updated with CLI config path!");
            } else {
                System.out.println("FAILURE: AnalysisRequest was NOT updated.");
            }
            System.exit(0);
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
