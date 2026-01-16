package com.raks.aegis.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Session manager for Aegis to handle session lifecycle and cleanup.
 * 
 * Features:
 * - Generate unique session IDs
 * - Track session creation times
 * - Clean up old sessions automatically
 * - Shutdown hook for cleanup on JVM exit
 */
public class SessionManager {
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
    private static final ConcurrentHashMap<String, Long> activeSessions = new ConcurrentHashMap<>();
    private static final int DEFAULT_MAX_AGE_HOURS = 24;
    private static boolean shutdownHookRegistered = false;

    /**
     * Create a new session and return its unique ID.
     * 
     * @return Unique session ID
     */
    public static String createSession() {
        String sessionId = UUID.randomUUID().toString();
        activeSessions.put(sessionId, System.currentTimeMillis());
        logger.debug("Created new session: {}", sessionId);
        

        if (!shutdownHookRegistered) {
            registerShutdownHook();
        }
        
        return sessionId;
    }

    /**
     * Clean up a specific session's temporary files.
     * 
     * @param sessionId Session ID to clean up
     */
    public static void cleanupSession(String sessionId) {
        cleanupSession(sessionId, System.getProperty("java.io.tmpdir"));
    }

    /**
     * Clean up a specific session's temporary files.
     * 
     * @param sessionId Session ID to clean up
     * @param baseTempDir Base temporary directory
     */
    public static void cleanupSession(String sessionId, String baseTempDir) {
        try {
            ArchiveExtractor.cleanupSession(sessionId, baseTempDir);
            activeSessions.remove(sessionId);
            logger.info("Session cleaned up: {}", sessionId);
        } catch (Exception e) {
            logger.warn("Failed to cleanup session: {}", sessionId, e);
        }
    }

    /**
     * Clean up sessions older than the specified age.
     * 
     * @param maxAgeHours Maximum age in hours
     */
    public static void cleanupOldSessions(int maxAgeHours) {
        cleanupOldSessions(maxAgeHours, System.getProperty("java.io.tmpdir"));
    }

    /**
     * Clean up sessions older than the specified age.
     * 
     * @param maxAgeHours Maximum age in hours
     * @param baseTempDir Base temporary directory
     */
    public static void cleanupOldSessions(int maxAgeHours, String baseTempDir) {
        long cutoffTime = System.currentTimeMillis() - (maxAgeHours * 60 * 60 * 1000L);
        int cleanedCount = 0;

        for (ConcurrentHashMap.Entry<String, Long> entry : activeSessions.entrySet()) {
            if (entry.getValue() < cutoffTime) {
                cleanupSession(entry.getKey(), baseTempDir);
                cleanedCount++;
            }
        }

        if (cleanedCount > 0) {
            logger.info("Cleaned up {} old sessions (older than {} hours)", cleanedCount, maxAgeHours);
        }
    }

    /**
     * Clean up all sessions in the Aegis-sessions directory.
     * This is useful for cleanup on startup or shutdown.
     * 
     * @param baseTempDir Base temporary directory
     */
    public static void cleanupAllSessions(String baseTempDir) {
        try {
            Path sessionsDir = Paths.get(baseTempDir, "Aegis-sessions");
            if (Files.exists(sessionsDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(sessionsDir)) {
                    int cleanedCount = 0;
                    for (Path sessionDir : stream) {
                        if (Files.isDirectory(sessionDir)) {
                            try {
                                ArchiveExtractor.deleteRecursively(sessionDir);
                                cleanedCount++;
                            } catch (IOException e) {
                                logger.warn("Failed to delete session directory: {}", sessionDir, e);
                            }
                        }
                    }
                    if (cleanedCount > 0) {
                        logger.info("Cleaned up {} session directories", cleanedCount);
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to cleanup all sessions", e);
        }
        

        activeSessions.clear();
    }

    /**
     * Register a shutdown hook to clean up sessions when JVM exits.
     */
    public static synchronized void registerShutdownHook() {
        if (!shutdownHookRegistered) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown hook triggered - cleaning up all sessions");
                cleanupAllSessions(System.getProperty("java.io.tmpdir"));
            }));
            shutdownHookRegistered = true;
            logger.debug("Shutdown hook registered for session cleanup");
        }
    }

    /**
     * Get the number of active sessions.
     * 
     * @return Number of active sessions
     */
    public static int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Check if a session exists.
     * 
     * @param sessionId Session ID to check
     * @return true if session exists, false otherwise
     */
    public static boolean sessionExists(String sessionId) {
        return activeSessions.containsKey(sessionId);
    }
}
