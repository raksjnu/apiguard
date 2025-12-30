package com.raks.raksanalyzer.generator.pdf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    public void addHeaderHeight(float height) {
        estimatedHeight += height;
    }
    public void addTextHeight(float height) {
        estimatedHeight += height;
    }
    public void addSpacing(float spacing) {
        estimatedHeight += spacing;
    }
    public void addTableHeight(float height) {
        estimatedHeight += height;
    }
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
    public float getEstimatedHeight() {
        return estimatedHeight;
    }
    public boolean isSpaceEnsured() {
        return spaceEnsured;
    }
}
