package com.raks.gitanalyzer;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.net.URI;

import com.raks.gitanalyzer.core.ConfigManager;

public class GitAnalyzerTool {
    private static final Logger logger = LoggerFactory.getLogger(GitAnalyzerTool.class);
    
    public static void main(String[] args) {
        try {
            startServer();
        } catch (Exception e) {
            logger.error("Failed to start GitAnalyzer", e);
            System.exit(1);
        }
    }

    private static void startServer() throws Exception {
        int port = Integer.parseInt(ConfigManager.get("http.port", "8080"));
        Server server = new Server(port);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        // API Servlet
        ServletHolder jerseyServlet = context.addServlet(
            org.glassfish.jersey.servlet.ServletContainer.class, "/api/*");
        jerseyServlet.setInitOrder(0);
        jerseyServlet.setInitParameter(
            "jakarta.ws.rs.Application",
            "com.raks.gitanalyzer.api.RestApplication"
        );

        // Web UI Servlet
        ServletHolder staticServlet = context.addServlet(
            org.eclipse.jetty.servlet.DefaultServlet.class, "/*");
        staticServlet.setInitParameter("resourceBase", 
            GitAnalyzerTool.class.getResource("/web").toExternalForm());
        staticServlet.setInitParameter("dirAllowed", "false");

        server.start();
        logger.info("GitAnalyzer started on http://localhost:{}", port);
        
        openBrowser(port);
        
        server.join();
    }

    private static void openBrowser(int port) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI("http://localhost:" + port));
            }
        } catch (Exception e) {
            logger.warn("Could not auto-open browser", e);
        }
    }
}
