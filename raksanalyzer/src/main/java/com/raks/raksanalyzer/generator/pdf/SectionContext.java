package com.raks.raksanalyzer.generator.pdf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to coordinate PDF section drawing and ensure headers, content, and tables
 * stay together on the same page. Pre-calculates total space needed and creates new pages
 * before drawing begins.
 */
public class SectionContext {
    private static final Logger logger = LoggerFactory.getLogger(SectionContext.class);
    private static final float MARGIN = 50;
    
    private final TibcoPdfGenerator generator;
    private float estimatedHeight;
    private boolean spaceEnsured;
    
    public SectionContext(TibcoPdfGenerator generator) {
        this.generator = generator;
        this.estimatedHeight = 0;
        this.spaceEnsured = false;
    }
    
    /**
     * Add estimated height for a header (typically 25-35px)
     */
    public void addHeaderHeight(float height) {
        estimatedHeight += height;
    }
    
    /**
     * Add estimated height for text content
     */
    public void addTextHeight(float height) {
        estimatedHeight += height;
    }
    
    /**
     * Add spacing between elements
     */
    public void addSpacing(float spacing) {
        estimatedHeight += spacing;
    }
    
    /**
     * Add estimated height for a table
     */
    public void addTableHeight(float height) {
        estimatedHeight += height;
    }
    
    /**
     * Check if there's enough space on the current page for all accumulated heights.
     * Returns true if a new page is needed, false if current page has enough space.
     * This method should be called ONCE before drawing the section.
     * 
     * @return true if new page is needed, false otherwise
     */
    public boolean needsNewPage() {
        if (spaceEnsured) {
            logger.warn("needsNewPage() called multiple times for same context");
            return false;
        }
        
        float currentY = generator.getCurrentY();
        float availableSpace = currentY - MARGIN;
        
        spaceEnsured = true;
        
        if (estimatedHeight > availableSpace) {
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Get the total estimated height for this section
     */
    public float getEstimatedHeight() {
        return estimatedHeight;
    }
    
    /**
     * Check if space has been ensured
     */
    public boolean isSpaceEnsured() {
        return spaceEnsured;
    }
}
