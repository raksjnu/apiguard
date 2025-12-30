package com.raks.apidiscovery;
public class ApiDiscoveryServerStarter {
    private static Thread serverThread;
    private static volatile boolean isRunning = false;
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
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("[API Discovery] Server startup initiated");
    }
    public static boolean isServerRunning() {
        return isRunning;
    }
    public static synchronized void stopServer() {
        if (serverThread != null && serverThread.isAlive()) {
            serverThread.interrupt();
            isRunning = false;
            System.out.println("[API Discovery] Server stopped");
        }
    }
}
