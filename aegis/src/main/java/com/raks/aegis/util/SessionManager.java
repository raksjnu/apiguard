package com.raks.aegis.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.*;

public class SessionManager {
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
    private static final ConcurrentHashMap<String, Long> activeSessions = new ConcurrentHashMap<>();
    private static final int DEFAULT_MAX_AGE_HOURS = 24;
    private static boolean shutdownHookRegistered = false;

    public static String createSession() {
        String sessionId = UUID.randomUUID().toString();
        activeSessions.put(sessionId, System.currentTimeMillis());
        logger.debug("Created new session: {}", sessionId);

        if (!shutdownHookRegistered) {
            registerShutdownHook();
        }

        return sessionId;
    }

    public static void cleanupSession(String sessionId) {
        cleanupSession(sessionId, System.getProperty("java.io.tmpdir"));
    }

    public static void cleanupSession(String sessionId, String baseTempDir) {
        try {
            ArchiveExtractor.cleanupSession(sessionId, baseTempDir);
            activeSessions.remove(sessionId);
            logger.info("Session cleaned up: {}", sessionId);
        } catch (Exception e) {
            logger.warn("Failed to cleanup session: {}", sessionId, e);
        }
    }

    public static void cleanupOldSessions(int maxAgeHours) {
        cleanupOldSessions(maxAgeHours, System.getProperty("java.io.tmpdir"));
    }

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

    public static int getActiveSessionCount() {
        return activeSessions.size();
    }

    public static boolean sessionExists(String sessionId) {
        return activeSessions.containsKey(sessionId);
    }
}
