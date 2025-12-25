package com.raks.raksanalyzer;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.net.URI;

/**
 * Main application entry point for RaksAnalyzer.
 * 
 * Features:
 * - Starts embedded Jetty server
 * - Auto-opens browser when run without arguments
 * - Supports command-line configuration override
 */
public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    
    private static final int DEFAULT_SERVER_PORT = 8080;
    private static final String DEFAULT_CONTEXT_PATH = "/";
    
    public static void main(String[] args) {
        com.raks.raksanalyzer.service.CleanupScheduler cleanupScheduler = null;
        
        try {
            logger.info("Starting RaksAnalyzer...");
            
            // Parse command-line arguments
            ApplicationArguments arguments = ApplicationArguments.parse(args);
            
            // Determine server port
            int serverPort = arguments.getServerPort().orElse(DEFAULT_SERVER_PORT);
            
            // Start cleanup scheduler
            cleanupScheduler = new com.raks.raksanalyzer.service.CleanupScheduler();
            cleanupScheduler.start();
            logger.info("Cleanup scheduler started");
            
            // Set custom config path system property if provided
            arguments.getCustomConfigPath().ifPresent(path -> {
                System.setProperty("raksanalyzer.config.path", path);
                logger.info("Custom configuration path set: {}", path);
            });
            
            // Start embedded Jetty server
            Server server = createServer(serverPort);
            server.start();
            
            logger.info("RaksAnalyzer started successfully on port {}", serverPort);
            
            // Auto-open browser if enabled
            if (arguments.shouldAutoOpenBrowser()) {
                openBrowser(serverPort);
            }
            
            // Add shutdown hook for cleanup
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
            
            // Wait for server to stop
            server.join();
            
        } catch (Exception e) {
            logger.error("Failed to start RaksAnalyzer", e);
            if (cleanupScheduler != null) {
                cleanupScheduler.stop();
            }
            System.exit(1);
        }
    }
    
    private static Server createServer(int port) {
        Server server = new Server(port);
        
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath(DEFAULT_CONTEXT_PATH);
        server.setHandler(context);
        
        // Configure Jersey servlet for REST API
        ServletHolder jerseyServlet = context.addServlet(
            org.glassfish.jersey.servlet.ServletContainer.class, "/api/*");
        jerseyServlet.setInitOrder(0);
        jerseyServlet.setInitParameter(
            "jakarta.ws.rs.Application",
            "com.raks.raksanalyzer.api.rest.RestApplication"
        );
        
        // Serve static web UI files
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
