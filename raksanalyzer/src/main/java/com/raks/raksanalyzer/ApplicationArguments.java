package com.raks.raksanalyzer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
                String cleanArg = arg.replaceFirst("^-+", "");
                
                String key;
                String value;
                
                if (cleanArg.contains("=")) {
                    String[] parts = cleanArg.split("=", 2);
                    key = parts[0];
                    value = parts.length > 1 ? parts[1] : "";
                } else {
                    key = cleanArg;
                    value = (i + 1 < args.length && !args[i + 1].startsWith("-")) 
                        ? args[++i] 
                        : "true";
                }
                
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                if (value.startsWith("'") && value.endsWith("'")) {
                    value = value.substring(1, value.length() - 1);
                }
                
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
    
    public Optional<String> getOutputPath() {
        return Optional.ofNullable(arguments.get("output"));
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
    
    public boolean isCliMode() {
        return arguments.containsKey("cli");
    }
    
    public boolean isHelpRequested() {
        return arguments.containsKey("help") || arguments.containsKey("h");
    }
    
    public Map<String, String> getAllArguments() {
        return new HashMap<>(arguments);
    }
    
    public static void printHelp() {
        System.out.println("RaksAnalyzer - Integration Project Documentation Generator");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar raksanalyzer.jar [OPTIONS]");
        System.out.println();
        System.out.println("Modes:");
        System.out.println("  (no args)          Start UI mode (default) - launches web interface");
        System.out.println("  --cli              Run in CLI mode - analyze and generate without UI");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --config <path>    Path to external configuration file");
        System.out.println("  --type <type>      Project type: mule, tibco5, spring");
        System.out.println("  --input <path>     Input project path (local folder, zip, or git URL)");
        System.out.println("  --output <path>    Output directory for generated documents");
        System.out.println("  --port <number>    Server port (default: 8080, UI mode only)");
        System.out.println("  --no-browser       Don't auto-open browser (UI mode only)");
        System.out.println("  --help, -h         Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Start UI mode");
        System.out.println("  java -jar raksanalyzer.jar");
        System.out.println();
        System.out.println("  # CLI mode with custom config");
        System.out.println("  java -jar raksanalyzer.jar --cli --config myconfig.properties --type tibco5 --input project.zip");
        System.out.println();
        System.out.println("  # UI mode on custom port");
        System.out.println("  java -jar raksanalyzer.jar --port 9090");
        System.out.println();
    }
}
