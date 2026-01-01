package com.raks.raksanalyzer.generator.tibco;

/**
 * Tracks activity count during diagram generation to support multi-page splitting.
 * When activity count exceeds threshold, diagrams can be split across multiple pages.
 */
public class ActivityCounter {
    private int count;
    private final int threshold;

    public ActivityCounter(int threshold) {
        this.count = 0;
        this.threshold = threshold;
    }

    /**
     * Increment the activity count.
     */
    public void increment() {
        count++;
    }

    /**
     * Check if the count has reached the threshold for page splitting.
     */
    public boolean shouldSplit() {
        return count >= threshold;
    }

    /**
     * Get the current count.
     */
    public int getCount() {
        return count;
    }

    /**
     * Reset the counter (for starting a new page).
     */
    public void reset() {
        count = 0;
    }

    /**
     * Get the threshold value.
     */
    public int getThreshold() {
        return threshold;
    }
}
