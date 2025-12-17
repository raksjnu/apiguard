package com.raks.raksanalyzer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Parses and holds command-line arguments for RaksAnalyzer.
 * 
 * Supported arguments:
 * - config: Path to custom configuration file
 * - type: Project technology type (MULE, TIBCO_BW5, etc.)
 * - input: Input project path
 * - mode: Execution mode (ANALYZE_ONLY, GENERATE_ONLY, FULL)
 * - envs: Environment selection (ALL or comma-separated list)
 * - port: Server port (default: 8080)
 * - no-browser: Disable auto-browser launch
 */
public class ApplicationArguments {
    
    private final Map<String, String> arguments;
    
    private ApplicationArguments(Map<String, String> arguments) {
        this.arguments = arguments;
    }
    
    public static ApplicationArguments parse(String[] args) {
        Map<String, String> parsedArgs = new HashMap<>();
        
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            
            if (arg.startsWith("-")) {
                String key = arg.substring(1); // Remove leading dash
                String value = (i + 1 < args.length && !args[i + 1].startsWith("-")) 
                    ? args[++i] 
                    : "true";
                parsedArgs.put(key, value);
            }
        }
        
        return new ApplicationArguments(parsedArgs);
    }
    
    public Optional<String> getCustomConfigPath() {
        return Optional.ofNullable(arguments.get("config"));
    }
    
    public Optional<String> getProjectTechnologyType() {
        return Optional.ofNullable(arguments.get("type"));
    }
    
    public Optional<String> getProjectInputPath() {
        return Optional.ofNullable(arguments.get("input"));
    }
    
    public Optional<String> getDocumentGenerationExecutionMode() {
        return Optional.ofNullable(arguments.get("mode"));
    }
    
    public Optional<String> getEnvironmentAnalysisScope() {
        return Optional.ofNullable(arguments.get("envs"));
    }
    
    public Optional<Integer> getServerPort() {
        return Optional.ofNullable(arguments.get("port"))
            .map(Integer::parseInt);
    }
    
    public boolean shouldAutoOpenBrowser() {
        return !arguments.containsKey("no-browser");
    }
    
    public Map<String, String> getAllArguments() {
        return new HashMap<>(arguments);
    }
}
