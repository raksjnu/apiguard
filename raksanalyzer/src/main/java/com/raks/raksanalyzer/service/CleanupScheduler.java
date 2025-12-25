package com.raks.raksanalyzer.service;

import com.raks.raksanalyzer.core.config.ConfigurationManager;
import com.raks.raksanalyzer.util.FileExtractionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages background cleanup tasks for temporary files and expired sessions.
 * Runs two scheduled tasks:
 * 1. Session timeout checker (every minute)
 * 2. Daily cleanup (at configured time, default 2 AM)
 */
public class CleanupScheduler {
    private static final Logger logger = LoggerFactory.getLogger(CleanupScheduler.class);
    
    private final SessionManager sessionManager;
    private final ConfigurationManager config;
    private final ScheduledExecutorService scheduler;
    
    private volatile boolean running = false;
    
    public CleanupScheduler() {
        this.sessionManager = SessionManager.getInstance();
        this.config = ConfigurationManager.getInstance();
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "CleanupScheduler");
            t.setDaemon(true); // Daemon thread won't prevent JVM shutdown
            return t;
        });
    }
    
    /**
     * Start all cleanup tasks.
     */
    public void start() {
        if (running) {
            logger.warn("CleanupScheduler already running");
            return;
        }
        
        running = true;
        startSessionTimeoutChecker();
        startDailyCleanup();
        
        logger.info("CleanupScheduler started successfully");
    }
    
    /**
     * Stop all cleanup tasks.
     */
    public void stop() {
        if (!running) {
            return;
        }
        
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            logger.info("CleanupScheduler stopped");
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Start session timeout checker.
     * Runs every minute to check for expired sessions.
     */
    private void startSessionTimeoutChecker() {
        int checkIntervalMinutes = config.getIntProperty("session.check.interval.minutes", 1);
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkExpiredSessions();
            } catch (Exception e) {
                logger.error("Error in session timeout checker", e);
            }
        }, checkIntervalMinutes, checkIntervalMinutes, TimeUnit.MINUTES);
        
        logger.info("Session timeout checker started (interval: {} minutes)", checkIntervalMinutes);
    }
    
    /**
     * Start daily cleanup task.
     * Runs at configured time (default 2 AM).
     */
    private void startDailyCleanup() {
        // Parse cron-like schedule (simplified: only supports "hour minute")
        String cronSchedule = config.getProperty("cleanup.schedule.cron", "0 2 * * *");
        
        // Extract hour and minute from cron (format: "minute hour day month dayOfWeek")
        String[] parts = cronSchedule.split("\\s+");
        int targetMinute = Integer.parseInt(parts[0]);
        int targetHour = Integer.parseInt(parts[1]);
        
        // Calculate initial delay until next scheduled time
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.withHour(targetHour).withMinute(targetMinute).withSecond(0).withNano(0);
        
        if (now.isAfter(nextRun)) {
            // If we've passed today's run time, schedule for tomorrow
            nextRun = nextRun.plusDays(1);
        }
        
        long initialDelay = ChronoUnit.MINUTES.between(now, nextRun);
        long period = 24 * 60; // 24 hours in minutes
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                performDailyCleanup();
            } catch (Exception e) {
                logger.error("Error in daily cleanup", e);
            }
        }, initialDelay, period, TimeUnit.MINUTES);
        
        logger.info("Daily cleanup scheduled at {}:{:02d} (next run: {})", 
            targetHour, targetMinute, nextRun);
    }
    
    /**
     * Check for and cleanup expired sessions.
     */
    private void checkExpiredSessions() {
        int timeoutMinutes = config.getIntProperty("session.timeout.minutes", 15);
        
        logger.debug("Checking for expired sessions (timeout: {} minutes)", timeoutMinutes);
        
        java.util.List<String> expiredSessions = sessionManager.getExpiredSessions(timeoutMinutes);
        
        if (expiredSessions.isEmpty()) {
            logger.debug("No expired sessions found");
            return;
        }
        
        logger.info("Found {} expired sessions, starting cleanup", expiredSessions.size());
        
        int cleanedCount = 0;
        for (String sessionId : expiredSessions) {
            try {
                cleanupSession(sessionId);
                cleanedCount++;
            } catch (Exception e) {
                logger.error("Failed to cleanup session: {}", sessionId, e);
            }
        }
        
        logger.info("Session timeout cleanup completed: {} sessions cleaned", cleanedCount);
    }
    
    /**
     * Cleanup all resources associated with a session.
     */
    private void cleanupSession(String sessionId) {
        logger.info("Cleaning up session: {}", sessionId);
        
        // Cleanup uploads
        Set<String> uploads = sessionManager.getSessionUploads(sessionId);
        for (String uploadId : uploads) {
            try {
                FileExtractionUtil.cleanupTempDirectory(uploadId);
                logger.debug("Cleaned upload directory: {}", uploadId);
            } catch (Exception e) {
                logger.warn("Failed to cleanup upload: {}", uploadId, e);
            }
        }
        
        // Cleanup analysis output directories
        Set<String> analyses = sessionManager.getSessionAnalyses(sessionId);
        for (String analysisId : analyses) {
            try {
                cleanupAnalysisOutput(analysisId);
                logger.debug("Cleaned analysis output: {}", analysisId);
            } catch (Exception e) {
                logger.warn("Failed to cleanup analysis: {}", analysisId, e);
            }
        }
        
        // Remove session from tracking
        sessionManager.removeSession(sessionId);
        
        logger.info("Session cleanup completed: {}", sessionId);
    }
    
    /**
     * Cleanup analysis output directory.
     */
    private void cleanupAnalysisOutput(String analysisId) {
        try {
            String tempDir = config.getProperty("framework.temp.directory", "./temp");
            Path tempPath = Paths.get(System.getProperty("user.dir"), tempDir);
            
            // Look for analysis output directory
            Path analysisDir = tempPath.resolve(analysisId);
            
            if (Files.exists(analysisDir)) {
                FileExtractionUtil.deleteRecursively(analysisDir);
                logger.debug("Deleted analysis directory: {}", analysisDir);
            }
        } catch (Exception e) {
            logger.warn("Failed to cleanup analysis output: {}", analysisId, e);
        }
    }
    
    /**
     * Perform daily cleanup of old temp folders.
     */
    private void performDailyCleanup() {
        logger.info("Starting daily cleanup");
        
        int retentionDays = config.getIntProperty("temp.retention.days", 1);
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        
        int deletedCount = 0;
        long deletedSize = 0;
        
        try {
            String tempDir = config.getProperty("framework.temp.directory", "./temp");
            Path tempPath = Paths.get(System.getProperty("user.dir"), tempDir);
            
            if (!Files.exists(tempPath)) {
                logger.info("Temp directory does not exist: {}", tempPath);
                return;
            }
            
            // Cleanup uploads directory
            Path uploadsPath = tempPath.resolve("uploads");
            if (Files.exists(uploadsPath)) {
                deletedCount += cleanupOldDirectories(uploadsPath, cutoff);
            }
            
            // Cleanup analysis output directories (direct children of temp)
            try (var stream = Files.list(tempPath)) {
                stream.filter(Files::isDirectory)
                      .filter(p -> !p.getFileName().toString().equals("uploads"))
                      .forEach(dir -> {
                          try {
                              FileTime lastModified = Files.getLastModifiedTime(dir);
                              if (lastModified.toInstant().isBefore(cutoff)) {
                                  long size = getDirectorySize(dir);
                                  FileExtractionUtil.deleteRecursively(dir);
                                  logger.info("Deleted old directory: {} (size: {} bytes)", dir, size);
                              }
                          } catch (Exception e) {
                              logger.warn("Failed to process directory: {}", dir, e);
                          }
                      });
            }
            
        } catch (Exception e) {
            logger.error("Error during daily cleanup", e);
        }
        
        logger.info("Daily cleanup completed: {} directories deleted", deletedCount);
    }
    
    /**
     * Cleanup old directories within a parent directory.
     */
    private int cleanupOldDirectories(Path parentDir, Instant cutoff) {
        int count = 0;
        
        try (var stream = Files.list(parentDir)) {
            var dirs = stream.filter(Files::isDirectory).toList();
            
            for (Path dir : dirs) {
                try {
                    FileTime lastModified = Files.getLastModifiedTime(dir);
                    if (lastModified.toInstant().isBefore(cutoff)) {
                        long size = getDirectorySize(dir);
                        FileExtractionUtil.deleteRecursively(dir);
                        logger.info("Deleted old directory: {} (size: {} bytes)", dir, size);
                        count++;
                    }
                } catch (Exception e) {
                    logger.warn("Failed to cleanup directory: {}", dir, e);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to list directories in: {}", parentDir, e);
        }
        
        return count;
    }
    
    /**
     * Calculate total size of a directory.
     */
    private long getDirectorySize(Path dir) {
        try (var stream = Files.walk(dir)) {
            return stream
                .filter(Files::isRegularFile)
                .mapToLong(p -> {
                    try {
                        return Files.size(p);
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .sum();
        } catch (IOException e) {
            return 0;
        }
    }
}
