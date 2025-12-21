package com.raks.raksanalyzer.api.rest;

import com.raks.raksanalyzer.core.config.ConfigurationManager;
import com.raks.raksanalyzer.core.config.ConfigurationManager.EnvironmentConfig;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API endpoint for configuration.
 * 
 * Endpoints:
 * - GET /api/config/ui-labels - Get all UI labels
 * - GET /api/config/environments - Get environment list
 * - GET /api/config/framework - Get framework settings
 */
@jakarta.ws.rs.Path("/config")
public class ConfigurationResource {
    
    private final ConfigurationManager config = ConfigurationManager.getInstance();
    
    /**
     * Get all UI labels.
     * 
     * GET /api/config/ui-labels
     */
    @GET
    @jakarta.ws.rs.Path("/ui-labels")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> getUILabels() {
        Map<String, String> labels = new HashMap<>();
        
        // Get all properties that start with "ui."
        config.getAllProperties().forEach((key, value) -> {
            String keyStr = key.toString();
            if (keyStr.startsWith("ui.")) {
                labels.put(keyStr, value.toString());
            }
        });
        
        return labels;
    }
    
    /**
     * Get list of environments.
     * 
     * GET /api/config/environments
     */
    @GET
    @jakarta.ws.rs.Path("/environments")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Map<String, String>> getEnvironments() {
        return config.getEnvironments().stream()
            .map(env -> {
                Map<String, String> envMap = new HashMap<>();
                envMap.put("code", env.getCode());
                envMap.put("displayName", env.getDisplayName());
                return envMap;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Get framework configuration.
     * 
     * GET /api/config/framework
     */
    @GET
    @jakarta.ws.rs.Path("/framework")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> getFrameworkConfig() {
        Map<String, String> settings = new HashMap<>();
        
        // Only expose non-sensitive settings
        settings.put("serverPort", config.getProperty("framework.server.port", "8080"));
        settings.put("outputDirectory", config.getProperty("framework.output.directory", "./output"));
        settings.put("defaultProjectPath", config.getProperty("project.input.path", "./testdata"));
        settings.put("maxThreads", config.getProperty("analyzer.max.threads", "4"));
        settings.put("version", "1.0.0");
        
        return settings;
    }
}
