package com.raks.apidiscovery;

/**
 * Wrapper class to start API Discovery server in a background thread
 * This allows Mule to invoke the server without blocking
 */
public class ApiDiscoveryServerStarter {
    
    private static Thread serverThread;
    private static volatile boolean isRunning = false;
    
    /**
     * Start the API Discovery server in a background daemon thread
     * This method returns immediately, allowing Mule flow to continue
     */
    public static synchronized void startServer() {
        if (isRunning) {
            System.out.println("[API Discovery] Server already running");
            return;
        }
        
        serverThread = new Thread(() -> {
            try {
                System.out.println("[API Discovery] Starting server in background thread...");
                isRunning = true;
                ApiDiscoveryTool.main(new String[0]);
            } catch (Exception e) {
                System.err.println("[API Discovery] Failed to start server: " + e.getMessage());
                e.printStackTrace();
                isRunning = false;
            }
        });
        
        serverThread.setDaemon(true);
        serverThread.setName("API-Discovery-Server");
        serverThread.start();
        
        // Wait a bit for server to start
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("[API Discovery] Server startup initiated");
    }
    
    /**
     * Check if server is running
     */
    public static boolean isServerRunning() {
        return isRunning;
    }
    
    /**
     * Stop the server (if needed)
     */
    public static synchronized void stopServer() {
        if (serverThread != null && serverThread.isAlive()) {
            serverThread.interrupt();
            isRunning = false;
            System.out.println("[API Discovery] Server stopped");
        }
    }
}
