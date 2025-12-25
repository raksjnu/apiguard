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
    
    public Optional<String> getInputSourceType() {
        return Optional.ofNullable(arguments.get("input-type"));
    }
    
    public Optional<String> getOutputPath() {
        return Optional.ofNullable(arguments.get("output"));
    }
    
    public Optional<String> getOutputTypes() {
        return Optional.ofNullable(arguments.get("output-type"));
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
        System.out.println("  --input <path>     Input project path");
        System.out.println("  --input-type <type> Input source type: folder, zip, ear, jar, git");
        System.out.println("                     (auto-detected if not specified)");
        System.out.println("  --output <path>    Output directory for generated documents");
        System.out.println("  --output-type <types> Output formats: pdf, word, excel, or comma-separated");
        System.out.println("                     (default: all formats)");
        System.out.println("  --port <number>    Server port (default: 8080, UI mode only)");
        System.out.println("  --no-browser       Don't auto-open browser (UI mode only)");
        System.out.println("  --help, -h         Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Start UI mode");
        System.out.println("  java -jar raksanalyzer.jar");
        System.out.println();
        System.out.println("  # CLI mode with local folder");
        System.out.println("  java -jar raksanalyzer.jar --cli --type tibco5 --input /path/to/project");
        System.out.println();
        System.out.println("  # CLI mode with ZIP file");
        System.out.println("  java -jar raksanalyzer.jar --cli --type tibco5 --input project.zip --input-type zip");
        System.out.println();
        System.out.println("  # CLI mode with EAR file");
        System.out.println("  java -jar raksanalyzer.jar --cli --type tibco5 --input project.ear --input-type ear");
        System.out.println();
        System.out.println("  # CLI mode with Git repository");
        System.out.println("  java -jar raksanalyzer.jar --cli --type mule --input https://github.com/user/repo.git --input-type git");
        System.out.println();
        System.out.println("  # Generate only PDF");
        System.out.println("  java -jar raksanalyzer.jar --cli --type tibco5 --input project.zip --output-type pdf");
        System.out.println();
        System.out.println("  # Generate PDF and Word");
        System.out.println("  java -jar raksanalyzer.jar --cli --type mule --input /path/to/project --output-type pdf,word");
        System.out.println();
        System.out.println("  # With custom config and output");
        System.out.println("  java -jar raksanalyzer.jar --cli --config custom.properties --type tibco5 --input project.zip --output /output/path");
        System.out.println();
    }
}
