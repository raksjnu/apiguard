package com.raks.raksanalyzer;

import com.raks.raksanalyzer.analyzer.AnalyzerFactory;
import com.raks.raksanalyzer.domain.enums.ProjectType;
import com.raks.raksanalyzer.domain.model.AnalysisRequest;
import com.raks.raksanalyzer.domain.model.AnalysisResult;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.net.URI;

public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    
    private static final int DEFAULT_SERVER_PORT = 8080;
    private static final String DEFAULT_CONTEXT_PATH = "/";
    
    public static void main(String[] args) {
        try {
            ApplicationArguments arguments = ApplicationArguments.parse(args);
            
            if (arguments.isHelpRequested()) {
                ApplicationArguments.printHelp();
                System.exit(0);
            }
            
            if (arguments.isCliMode()) {
                runCliMode(arguments);
            } else {
                runUiMode(arguments);
            }
            
        } catch (Exception e) {
            logger.error("Failed to start RaksAnalyzer", e);
            System.exit(1);
        }
    }
    
    private static void runCliMode(ApplicationArguments arguments) {
        logger.info("Running in CLI mode");
        
        String inputPath = arguments.getProjectInputPath()
            .orElseThrow(() -> new IllegalArgumentException("--input is required in CLI mode"));
        
        String typeStr = arguments.getProjectTechnologyType()
            .orElseThrow(() -> new IllegalArgumentException("--type is required in CLI mode"));
        
        ProjectType projectType = ProjectType.valueOf(typeStr.toUpperCase().replace("TIBCO5", "TIBCO_BW5"));
        
        arguments.getCustomConfigPath().ifPresent(path -> {
            System.setProperty("raksanalyzer.config.path", path);
            logger.info("Custom configuration path set: {}", path);
        });
        
        AnalysisRequest request = new AnalysisRequest();
        request.setInputPath(inputPath);
        request.setProjectTechnologyType(projectType);
        
        String inputSourceType = arguments.getInputSourceType()
            .orElseGet(() -> detectInputSourceType(inputPath));
        request.setInputSourceType(inputSourceType);
        logger.info("Input source type: {}", inputSourceType);
        
        arguments.getOutputPath().ifPresent(outputPath -> {
            request.setOutputDirectory(outputPath);
            logger.info("Custom output directory set: {}", outputPath);
        });
        
        com.raks.raksanalyzer.model.OutputFormatConfig formatConfig = new com.raks.raksanalyzer.model.OutputFormatConfig();
        arguments.getOutputTypes().ifPresent(outputTypes -> {
            String[] types = outputTypes.toLowerCase().split(",");
            
            boolean hasPdf = false, hasWord = false, hasExcel = false;
            for (String type : types) {
                type = type.trim();
                if ("pdf".equals(type)) hasPdf = true;
                else if ("word".equals(type)) hasWord = true;
                else if ("excel".equals(type)) hasExcel = true;
            }
            
            formatConfig.setPdfEnabled(hasPdf);
            formatConfig.setWordEnabled(hasWord);
            formatConfig.setExcelEnabled(hasExcel);
            
            logger.info("Output formats: PDF={}, Word={}, Excel={}", hasPdf, hasWord, hasExcel);
        });
        
        if (!formatConfig.isPdfEnabled() && !formatConfig.isWordEnabled() && !formatConfig.isExcelEnabled()) {
            formatConfig.setPdfEnabled(true);
            formatConfig.setWordEnabled(true);
            formatConfig.setExcelEnabled(true);
            logger.info("No output types specified, defaulting to all formats");
        }
        
        request.setOutputFormatConfig(formatConfig);
        
        logger.info("Analyzing project: {}", inputPath);
        AnalysisResult result = AnalyzerFactory.analyze(request);
        
        if (!result.isSuccess()) {
            logger.error("Analysis failed: {}", result.getErrorMessage());
            System.exit(1);
        }
        
        logger.info("Analysis completed successfully");
        
        try {
            com.raks.raksanalyzer.core.config.ConfigurationManager config = 
                com.raks.raksanalyzer.core.config.ConfigurationManager.getInstance();
            
            if (formatConfig.isPdfEnabled()) {
                if (projectType == ProjectType.TIBCO_BW5) {
                    com.raks.raksanalyzer.generator.pdf.TibcoPdfGenerator pdfGen = 
                        new com.raks.raksanalyzer.generator.pdf.TibcoPdfGenerator();
                    String pdfPath = pdfGen.generate(result);
                    logger.info("PDF generated: {}", pdfPath);
                } else {
                    com.raks.raksanalyzer.generator.pdf.PdfGenerator pdfGen = 
                        new com.raks.raksanalyzer.generator.pdf.PdfGenerator(config);
                    java.nio.file.Path pdfPath = pdfGen.generate(result);
                    logger.info("PDF generated: {}", pdfPath);
                }
            }
            
            if (formatConfig.isWordEnabled()) {
                if (projectType == ProjectType.TIBCO_BW5) {
                    com.raks.raksanalyzer.generator.word.TibcoWordGenerator wordGen = 
                        new com.raks.raksanalyzer.generator.word.TibcoWordGenerator();
                    String wordPath = wordGen.generate(result);
                    logger.info("Word document generated: {}", wordPath);
                } else {
                    com.raks.raksanalyzer.generator.word.WordGenerator wordGen = 
                        new com.raks.raksanalyzer.generator.word.WordGenerator();
                    java.nio.file.Path wordPath = wordGen.generate(result);
                    logger.info("Word document generated: {}", wordPath);
                }
            }
            
            if (formatConfig.isExcelEnabled()) {
                if (projectType == ProjectType.TIBCO_BW5) {
                    com.raks.raksanalyzer.generator.excel.TibcoExcelGenerator excelGen = 
                        new com.raks.raksanalyzer.generator.excel.TibcoExcelGenerator();
                    String excelPath = excelGen.generate(result);
                    logger.info("Excel report generated: {}", excelPath);
                } else {
                    com.raks.raksanalyzer.generator.excel.ExcelGenerator excelGen = 
                        new com.raks.raksanalyzer.generator.excel.ExcelGenerator();
                    java.nio.file.Path excelPath = excelGen.generate(result);
                    logger.info("Excel report generated: {}", excelPath);
                }
            }
            
            logger.info("All documents generated successfully in: {}", result.getOutputDirectory());
            System.exit(0);
            
        } catch (Exception e) {
            logger.error("Failed to generate documents", e);
            System.exit(1);
        }
    }
    
    private static String detectInputSourceType(String inputPath) {
        String lowerPath = inputPath.toLowerCase();
        
        if (lowerPath.startsWith("http://") || lowerPath.startsWith("https://") || lowerPath.startsWith("git@")) {
            return "git";
        } else if (lowerPath.endsWith(".zip")) {
            return "zip";
        } else if (lowerPath.endsWith(".ear")) {
            return "ear";
        } else if (lowerPath.endsWith(".jar")) {
            return "jar";
        } else {
            return "folder";
        }
    }
    
    private static void runUiMode(ApplicationArguments arguments) {
        com.raks.raksanalyzer.service.CleanupScheduler cleanupScheduler = null;
        
        try {
            logger.info("Starting RaksAnalyzer in UI mode...");
            
            int serverPort = arguments.getServerPort().orElse(DEFAULT_SERVER_PORT);
            
            cleanupScheduler = new com.raks.raksanalyzer.service.CleanupScheduler();
            cleanupScheduler.start();
            
            arguments.getCustomConfigPath().ifPresent(path -> {
                System.setProperty("raksanalyzer.config.path", path);
                logger.info("Custom configuration path set: {}", path);
            });
            
            Server server = createServer(serverPort);
            server.start();
            
            logger.info("RaksAnalyzer started successfully on port {}", serverPort);
            
            if (arguments.shouldAutoOpenBrowser()) {
                openBrowser(serverPort);
            }
            
            final com.raks.raksanalyzer.service.CleanupScheduler finalScheduler = cleanupScheduler;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down RaksAnalyzer...");
                if (finalScheduler != null) {
                    finalScheduler.stop();
                }
                try {
                    server.stop();
                } catch (Exception e) {
                    logger.error("Error stopping server", e);
                }
            }));
            
            server.join();
            
        } catch (Exception e) {
            logger.error("Failed to start RaksAnalyzer", e);
            if (cleanupScheduler != null) {
                cleanupScheduler.stop();
            }
            throw new RuntimeException(e);
        }
    }
    
    private static Server createServer(int port) {
        Server server = new Server(port);
        
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath(DEFAULT_CONTEXT_PATH);
        server.setHandler(context);
        
        ServletHolder jerseyServlet = context.addServlet(
            org.glassfish.jersey.servlet.ServletContainer.class, "/api/*");
        jerseyServlet.setInitOrder(0);
        jerseyServlet.setInitParameter(
            "jakarta.ws.rs.Application",
            "com.raks.raksanalyzer.api.rest.RestApplication"
        );
        
        ServletHolder staticServlet = context.addServlet(
            org.eclipse.jetty.servlet.DefaultServlet.class,
            "/*"
        );
        staticServlet.setInitParameter("resourceBase", 
            Application.class.getResource("/web").toExternalForm());
        staticServlet.setInitParameter("dirAllowed", "false");
        
        return server;
    }
    
    private static void openBrowser(int port) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                URI uri = new URI(String.format("http://localhost:%d", port));
                Desktop.getDesktop().browse(uri);
                logger.info("Opened browser to {}", uri);
            }
        } catch (Exception e) {
            logger.warn("Could not auto-open browser: {}", e.getMessage());
        }
    }
}
