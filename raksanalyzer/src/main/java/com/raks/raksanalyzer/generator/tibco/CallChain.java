package com.raks.raksanalyzer.generator.tibco;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the call chain of subprocess invocations to detect circular references.
 * Prevents infinite loops when Process A calls Process B which calls Process A.
 */
public class CallChain {
    private final List<String> chain;

    public CallChain() {
        this.chain = new ArrayList<>();
    }

    private CallChain(List<String> existingChain) {
        this.chain = new ArrayList<>(existingChain);
    }

    /**
     * Check if a process path is already in the call chain (circular reference).
     */
    public boolean contains(String processPath) {
        if (processPath == null) return false;
        

        String normalized = normalizePath(processPath);
        
        for (String existing : chain) {
            if (normalizePath(existing).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create a new CallChain with the given process added.
     * Returns a new instance to maintain immutability.
     */
    public CallChain push(String processPath) {
        CallChain newChain = new CallChain(this.chain);
        newChain.chain.add(processPath);
        return newChain;
    }

    /**
     * Get a message indicating recursive call detected.
     */
    public String getRecursionMessage(String processPath) {
        String processName = extractProcessName(processPath);
        return "⟲ Recursive call to " + processName;
    }

    /**
     * Get current depth of the call chain.
     */
    public int getDepth() {
        return chain.size();
    }

    /**
     * Extract process name from full path.
     */
    private String extractProcessName(String processPath) {
        if (processPath == null) return "Unknown";
        
        String normalized = normalizePath(processPath);
        int lastSlash = normalized.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        

        if (fileName.endsWith(".process")) {
            fileName = fileName.substring(0, fileName.length() - 8);
        }
        
        return fileName;
    }

    /**
     * Normalize path for consistent comparison.
     */
    private String normalizePath(String path) {
        if (path == null) return "";
        

        String normalized = path.startsWith("/") ? path.substring(1) : path;
        

        normalized = normalized.replace('\\', '/');
        
        return normalized;
    }

    @Override
    public String toString() {
        return "CallChain" + chain;
    }
}
