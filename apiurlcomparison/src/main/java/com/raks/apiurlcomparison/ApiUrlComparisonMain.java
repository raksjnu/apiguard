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
            "--config" }, description = "Path to the configuration YAML file", required = true)
    private File configFile;

    @CommandLine.Option(names = { "-o",
            "--output" }, description = "Path for the output JSON report", defaultValue = "results.json")
    private String outputReportPath;

    @Override
    public Integer call() throws Exception {
        logger.info("Starting API URL Comparison Tool (CLI)...");
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        // 1. Load Configuration
        Config config = mapper.readValue(configFile, Config.class);
        logger.info("Configuration loaded successfully from: {}", configFile.getAbsolutePath());

        // 2. Execute Comparison
        ComparisonService service = new ComparisonService();
        List<ComparisonResult> allResults = service.execute(config);

        // 3. Generate Report
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

        return 0; // Indicate success
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ApiUrlComparisonMain()).execute(args);
        System.exit(exitCode);
    }
}
