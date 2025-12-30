package com.raks.raksanalyzer.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public class SessionManager {
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
    private static SessionManager instance;
    private final Map<String, LocalDateTime> sessionActivity;
    private final Map<String, Set<String>> sessionUploads;
    private final Map<String, Set<String>> sessionAnalyses;
    private SessionManager() {
        this.sessionActivity = new ConcurrentHashMap<>();
        this.sessionUploads = new ConcurrentHashMap<>();
        this.sessionAnalyses = new ConcurrentHashMap<>();
        logger.info("SessionManager initialized");
    }
    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }
    public void registerSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        sessionActivity.put(sessionId, LocalDateTime.now());
        sessionUploads.putIfAbsent(sessionId, ConcurrentHashMap.newKeySet());
        sessionAnalyses.putIfAbsent(sessionId, ConcurrentHashMap.newKeySet());
        logger.debug("Session registered/updated: {}", sessionId);
    }
    public void updateActivity(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        sessionActivity.put(sessionId, LocalDateTime.now());
        logger.debug("Session activity updated: {}", sessionId);
    }
    public void registerUpload(String sessionId, String uploadId) {
        if (sessionId == null || uploadId == null) {
            return;
        }
        sessionUploads.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(uploadId);
        updateActivity(sessionId);
        logger.debug("Upload {} registered to session {}", uploadId, sessionId);
    }
    public void registerAnalysis(String sessionId, String analysisId) {
        if (sessionId == null || analysisId == null) {
            return;
        }
        sessionAnalyses.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(analysisId);
        updateActivity(sessionId);
        logger.debug("Analysis {} registered to session {}", analysisId, sessionId);
    }
    public List<String> getExpiredSessions(int timeoutMinutes) {
        List<String> expired = new ArrayList<>();
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(timeoutMinutes);
        for (Map.Entry<String, LocalDateTime> entry : sessionActivity.entrySet()) {
            if (entry.getValue().isBefore(cutoff)) {
                expired.add(entry.getKey());
            }
        }
        logger.debug("Found {} expired sessions (timeout: {} minutes)", expired.size(), timeoutMinutes);
        return expired;
    }
    public Set<String> getSessionUploads(String sessionId) {
        return new HashSet<>(sessionUploads.getOrDefault(sessionId, Collections.emptySet()));
    }
    public Set<String> getSessionAnalyses(String sessionId) {
        return new HashSet<>(sessionAnalyses.getOrDefault(sessionId, Collections.emptySet()));
    }
    public void removeSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        sessionActivity.remove(sessionId);
        sessionUploads.remove(sessionId);
        sessionAnalyses.remove(sessionId);
        logger.info("Session removed from tracking: {}", sessionId);
    }
    public void removeAnalysis(String sessionId, String analysisId) {
        if (sessionId == null || analysisId == null) {
            return;
        }
        Set<String> analyses = sessionAnalyses.get(sessionId);
        if (analyses != null) {
            analyses.remove(analysisId);
            logger.debug("Analysis {} removed from session {}", analysisId, sessionId);
        }
    }
    public int getActiveSessionCount() {
        return sessionActivity.size();
    }
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeSessions", sessionActivity.size());
        stats.put("totalUploads", sessionUploads.values().stream().mapToInt(Set::size).sum());
        stats.put("totalAnalyses", sessionAnalyses.values().stream().mapToInt(Set::size).sum());
        return stats;
    }
}
