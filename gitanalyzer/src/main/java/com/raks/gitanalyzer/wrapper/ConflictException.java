package com.raks.gitanalyzer.wrapper;

/**
 * Custom exception for expected conflicts (e.g., directory already exists).
 * This exception suppresses stack trace logging to avoid cluttering logs
 * with expected user-triggered scenarios.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }

    /**
     * Override to prevent stack trace from being filled in.
     * This significantly reduces log noise for expected conflicts.
     */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
