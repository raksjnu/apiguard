package com.raks.apiurlcomparison;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
@CommandLine.Command(name = "apiurlcomparison", mixinStandardHelpOptions = true, version = "1.0")
public class ApiUrlComparisonMain implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(ApiUrlComparisonMain.class);
    @CommandLine.Option(names = { "-c",
            "--config" }, description = "Path to the configuration YAML file", required = false)
    private File configFile;

    @CommandLine.Option(names = { "-o",
            "--output" }, description = "Path for the output JSON report", defaultValue = "results.json")
    private String outputReportPath;
    
    @CommandLine.Option(names = { "-g", "--gui" }, description = "Launch the Web-based GUI", required = false)
    private boolean guiMode;

    @Override
    public Integer call() throws Exception {
        if (guiMode) {
             logger.info("Launching GUI mode...");
             if (configFile != null) {
                 logger.info("Using provided config file for GUI: {}", configFile.getAbsolutePath());
                 ApiUrlComparisonWeb.setConfigFile(configFile);
             }
             ApiUrlComparisonWeb.main(new String[]{});
             return 0;
        }

        logger.info("Starting API URL Comparison Tool (CLI)...");
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Config config = null;

        // 1. Explicit Config
        if (configFile != null && configFile.exists()) {
             logger.info("Loading config from explicit file: {}", configFile.getAbsolutePath());
             config = mapper.readValue(configFile, Config.class);
        } 
        
        // 2. CWD Config
        if (config == null) {
            File cwdConfig = new File("config.yaml");
            if (cwdConfig.exists()) {
                logger.info("Loading config from CWD: {}", cwdConfig.getAbsolutePath());
                config = mapper.readValue(cwdConfig, Config.class);
            }
        }

        // 3. Classpath Config
        if (config == null) {
             logger.info("Loading config from classpath resources");
             try (java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("config.yaml")) {
                 if (is != null) {
                     config = mapper.readValue(is, Config.class);
                 }
             }
        }

        if (config == null) {
            logger.error("No configuration found. Please provide a config file with -c or ensure config.yaml is in the current directory or resources.");
            return 1;
        }

        logger.info("Configuration loaded successfully.");
        ComparisonService service = new ComparisonService();
        List<ComparisonResult> allResults = service.execute(config);

        try {
            logger.info("Generating JSON report...");
            File jsonReportFile = HtmlReportGenerator.generateJsonReport(allResults, outputReportPath);
            logger.info("JSON report generated successfully: {}", jsonReportFile.getAbsolutePath());
            logger.info("Generating HTML report...");
            File htmlReportFile = HtmlReportGenerator.generateHtmlReport(allResults, outputReportPath);
            logger.info("HTML report generated successfully: {}", htmlReportFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to generate report(s): {}", e.getMessage(), e);
        }

        return 0;
    }
    public static void main(String[] args) {
        int exitCode = new CommandLine(new ApiUrlComparisonMain()).execute(args);
        // Do not call System.exit(exitCode) here as it will kill the GUI server threads.
        // The JVM will terminate naturally if only daemon threads are left (CLI mode),
        // or stay alive if there are non-daemon threads (GUI mode).
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
