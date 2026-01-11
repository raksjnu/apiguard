package com.raks.raksanalyzer.generator.tibco;

/**
 * Configuration for TIBCO diagram generation.
 * Controls behavior for both Integration (Section 2) and Flow (Section 3) diagrams.
 */
public class DiagramGenerationConfig {
    private final boolean showAllActivities;
    private final boolean traverseSubprocesses;
    private final int maxDepth;
    private final boolean detectCircularReferences;
    private final boolean showSpawnOverride;
    private final boolean usePartitions;
    private final int maxActivitiesPerPage;

    private DiagramGenerationConfig(boolean showAllActivities, boolean traverseSubprocesses, 
                                   int maxDepth, boolean detectCircularReferences,
                                   boolean showSpawnOverride, boolean usePartitions,
                                   int maxActivitiesPerPage) {
        this.showAllActivities = showAllActivities;
        this.traverseSubprocesses = traverseSubprocesses;
        this.maxDepth = maxDepth;
        this.detectCircularReferences = detectCircularReferences;
        this.showSpawnOverride = showSpawnOverride;
        this.usePartitions = usePartitions;
        this.maxActivitiesPerPage = maxActivitiesPerPage;
    }

    /**
     * Configuration for Integration Diagrams (Section 2):
     * - Show only connectors
     * - Recursive subprocess traversal
     * - Circular reference detection enabled
     */
    public static DiagramGenerationConfig forIntegrationDiagram() {
        return new DiagramGenerationConfig(
            false,  
            true,   
            50,     
            true,   
            true,   
            true,   
            50      
        );
    }

    /**
     * Configuration for Flow Diagrams (Section 3):
     * - Show all activities
     * - Single-level only (no subprocess traversal)
     * - No circular reference detection needed
     */
    public static DiagramGenerationConfig forFlowDiagram() {
        return new DiagramGenerationConfig(
            true,   
            false,  
            0,      
            false,  
            true,   
            true,   
            50      
        );
    }

    public boolean isShowAllActivities() {
        return showAllActivities;
    }

    public boolean isTraverseSubprocesses() {
        return traverseSubprocesses;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public boolean isDetectCircularReferences() {
        return detectCircularReferences;
    }

    public boolean isShowSpawnOverride() {
        return showSpawnOverride;
    }

    public boolean isUsePartitions() {
        return usePartitions;
    }

    public int getMaxActivitiesPerPage() {
        return maxActivitiesPerPage;
    }
}
