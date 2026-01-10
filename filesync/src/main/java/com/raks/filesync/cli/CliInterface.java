package com.raks.filesync.cli;

import com.raks.filesync.config.ConfigLoader;
import com.raks.filesync.config.MappingConfig;
import com.raks.filesync.core.CsvDiscovery;
import com.raks.filesync.core.MappingExecutor;
import org.apache.commons.cli.*;

import java.util.Map;

/**
 * Command-line interface for FileSync
 */
public class CliInterface {
    
    public void run(String[] args) {
        Options options = createOptions();
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        
        try {
            CommandLine cmd = parser.parse(options, args);
            
            if (cmd.hasOption("h") || args.length == 0) {
                printHelp(formatter, options);
                return;
            }
            
            String mode = cmd.getOptionValue("mode", "execute");
            
            switch (mode.toLowerCase()) {
                case "discover":
                    runDiscovery(cmd);
                    break;
                case "execute":
                    runExecution(cmd);
                    break;
                case "validate":
                    runValidation(cmd);
                    break;
                default:
                    System.err.println("Unknown mode: " + mode);
                    printHelp(formatter, options);
            }
            
        } catch (ParseException e) {
            System.err.println("Error parsing arguments: " + e.getMessage());
            printHelp(formatter, options);
        }
    }
    
    private Options createOptions() {
        Options options = new Options();
        
        options.addOption("h", "help", false, "Show help message");
        options.addOption("m", "mode", true, "Operation mode: discover, execute, validate");
        options.addOption("s", "source", true, "Source directory path");
        options.addOption("c", "config", true, "Configuration file path");
        
        return options;
    }
    
    private void printHelp(HelpFormatter formatter, Options options) {
        System.out.println("FileSync Tool - Configuration-driven CSV transformation");
        System.out.println();
        formatter.printHelp("filesync", options);
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  Discover source files:");
        System.out.println("    java -jar filesync.jar -m discover -s C:\\path\\to\\source");
        System.out.println();
        System.out.println("  Execute transformation:");
        System.out.println("    java -jar filesync.jar -m execute -c C:\\path\\to\\config.json");
        System.out.println();
        System.out.println("  Validate configuration:");
        System.out.println("    java -jar filesync.jar -m validate -c C:\\path\\to\\config.json");
    }
    
    private void runDiscovery(CommandLine cmd) {
        String sourceDir = cmd.getOptionValue("source");
        
        if (sourceDir == null) {
            System.err.println("Error: --source directory is required for discovery mode");
            return;
        }
        
        System.out.println("Discovering CSV files in: " + sourceDir);
        System.out.println();
        
        CsvDiscovery discovery = new CsvDiscovery();
        Map<String, CsvDiscovery.FileSchema> schemas = discovery.discoverAllSchemas(sourceDir);
        
        if (schemas.isEmpty()) {
            System.out.println("No CSV files found.");
            return;
        }
        
        System.out.println("Found " + schemas.size() + " CSV file(s):");
        System.out.println();
        
        for (CsvDiscovery.FileSchema schema : schemas.values()) {
            System.out.println("File: " + schema.getFileName());
            System.out.println("  Fields (" + schema.getHeaders().size() + "):");
            for (String header : schema.getHeaders()) {
                System.out.println("    - " + header);
            }
            System.out.println();
        }
    }
    
    private void runExecution(CommandLine cmd) {
        String configPath = cmd.getOptionValue("config");
        
        if (configPath == null) {
            System.err.println("Error: --config file is required for execute mode");
            return;
        }
        
        try {
            System.out.println("Loading configuration: " + configPath);
            ConfigLoader loader = new ConfigLoader();
            MappingConfig config = loader.loadConfig(configPath);
            
            System.out.println("Configuration loaded successfully");
            System.out.println("  Source: " + config.getPaths().getSourceDirectory());
            System.out.println("  Target: " + config.getPaths().getTargetDirectory());
            System.out.println("  File mappings: " + config.getFileMappings().size());
            System.out.println();
            
            System.out.println("Executing transformations...");
            MappingExecutor executor = new MappingExecutor();
            MappingExecutor.ExecutionResult result = executor.execute(config);
            
            System.out.println();
            System.out.println(result.getSummary());
            
            if (!result.getSuccesses().isEmpty()) {
                System.out.println("\nSuccesses:");
                for (String success : result.getSuccesses()) {
                    System.out.println("  ✓ " + success);
                }
            }
            
            if (!result.getWarnings().isEmpty()) {
                System.out.println("\nWarnings:");
                for (String warning : result.getWarnings()) {
                    System.out.println("  ⚠ " + warning);
                }
            }
            
            if (!result.getErrors().isEmpty()) {
                System.out.println("\nErrors:");
                for (String error : result.getErrors()) {
                    System.out.println("  ✗ " + error);
                }
            }
            
        } catch (Exception e) {
            System.err.println("Execution failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void runValidation(CommandLine cmd) {
        String configPath = cmd.getOptionValue("config");
        
        if (configPath == null) {
            System.err.println("Error: --config file is required for validate mode");
            return;
        }
        
        try {
            System.out.println("Validating configuration: " + configPath);
            ConfigLoader loader = new ConfigLoader();
            MappingConfig config = loader.loadConfig(configPath);
            
            System.out.println("✓ Configuration is valid");
            System.out.println("  Version: " + config.getVersion());
            System.out.println("  Source: " + config.getPaths().getSourceDirectory());
            System.out.println("  Target: " + config.getPaths().getTargetDirectory());
            System.out.println("  File mappings: " + config.getFileMappings().size());
            
        } catch (Exception e) {
            System.err.println("✗ Configuration is invalid: " + e.getMessage());
        }
    }
}
