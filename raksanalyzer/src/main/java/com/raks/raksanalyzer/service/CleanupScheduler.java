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
            t.setDaemon(true); 
            return t;
        });
    }
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
    private void startDailyCleanup() {
        String cronSchedule = config.getProperty("cleanup.schedule.cron", "0 2 * * *");
        String[] parts = cronSchedule.split("\\s+");
        int targetMinute = Integer.parseInt(parts[0]);
        int targetHour = Integer.parseInt(parts[1]);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.withHour(targetHour).withMinute(targetMinute).withSecond(0).withNano(0);
        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }
        long initialDelay = ChronoUnit.MINUTES.between(now, nextRun);
        long period = 24 * 60; 
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
    private void cleanupSession(String sessionId) {
        logger.info("Cleaning up session: {}", sessionId);
        Set<String> uploads = sessionManager.getSessionUploads(sessionId);
        for (String uploadId : uploads) {
            try {
                FileExtractionUtil.cleanupTempDirectory(uploadId);
                logger.debug("Cleaned upload directory: {}", uploadId);
            } catch (Exception e) {
                logger.warn("Failed to cleanup upload: {}", uploadId, e);
            }
        }
        Set<String> analyses = sessionManager.getSessionAnalyses(sessionId);
        for (String analysisId : analyses) {
            try {
                cleanupAnalysisOutput(analysisId);
                logger.debug("Cleaned analysis output: {}", analysisId);
            } catch (Exception e) {
                logger.warn("Failed to cleanup analysis: {}", analysisId, e);
            }
        }
        sessionManager.removeSession(sessionId);
        logger.info("Session cleanup completed: {}", sessionId);
    }
    private void cleanupAnalysisOutput(String analysisId) {
        try {
            String tempDir = config.getProperty("framework.temp.directory", "./temp");
            Path tempPath = Paths.get(System.getProperty("user.dir"), tempDir);
            Path analysisDir = tempPath.resolve(analysisId);
            if (Files.exists(analysisDir)) {
                FileExtractionUtil.deleteRecursively(analysisDir);
                logger.debug("Deleted analysis directory: {}", analysisDir);
            }
        } catch (Exception e) {
            logger.warn("Failed to cleanup analysis output: {}", analysisId, e);
        }
    }
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
            Path uploadsPath = tempPath.resolve("uploads");
            if (Files.exists(uploadsPath)) {
                deletedCount += cleanupOldDirectories(uploadsPath, cutoff);
            }
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
