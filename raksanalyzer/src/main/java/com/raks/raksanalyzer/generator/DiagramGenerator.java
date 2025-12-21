package com.raks.raksanalyzer.generator;

import com.raks.raksanalyzer.domain.model.ComponentInfo;
import com.raks.raksanalyzer.domain.model.FlowInfo;
import net.sourceforge.plantuml.SourceStringReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

public class DiagramGenerator {

    /**
     * Generates a textual representation of the flow structure.
     * Uses flat list for simple sequential representation.
     */
    public static String generateTextFlow(FlowInfo flow) {
        StringBuilder sb = new StringBuilder();
        sb.append("Start");

        List<ComponentInfo> components = flow.getComponents();
        if (components != null) {
            for (ComponentInfo comp : components) {
                // Only showing top level or vital components might be better, 
                // but for now we follow the linear list which includes everything.
                // To improve readability, we might want to skip "when" or "otherwise" wrappers in text?
                // But the user requested structure, so seeing "Choice -> When -> Logger" is accurate to the XML.
                
                sb.append(" -> ");
                String name = comp.getType(); 
                if (name.contains(":")) {
                    name = name.substring(name.indexOf(":") + 1);
                }
                if (name.length() > 0) {
                   name = name.substring(0, 1).toUpperCase() + name.substring(1);
                }
                sb.append(name);
            }
        }

        sb.append(" -> End");
        return sb.toString();
    }
    
    /**
     * Visually centers text by adding padding spaces.
     * This approximates center alignment for PlantUML activities.
     * 
     * @param text Text to center
     * @param referenceLength Length of reference text (usually the longer detail line)
     * @return Padded text for visual centering
     */
    private static String centerText(String text, int referenceLength) {
        if (text == null || text.length() >= referenceLength) {
            return text; // No padding needed if text is already long
        }
        
        // Calculate padding needed (approximate, as font is not monospace)
        int diff = referenceLength - text.length();
        int leftPad = diff / 4; // Divide by 4 for better visual balance
        
        if (leftPad > 0) {
            // Add spaces before text
            StringBuilder padded = new StringBuilder();
            for (int i = 0; i < leftPad; i++) {
                padded.append("  "); // Two spaces for better effect
            }
            padded.append(text);
            return padded.toString();
        }
        
        return text;
    }

    /**
     * Generates a PlantUML diagram image (PNG) for the given flow.
     * @param flow The flow to generate diagram for
     * @param maxDepth Maximum depth of nested components to show (0=root only, -1=unlimited)
     */
    public static byte[] generatePlantUmlImage(FlowInfo flow, int maxDepth) throws IOException {
        return generatePlantUmlImage(flow, maxDepth, false, false);
    }
    
    /**
     * Generates a PlantUML diagram image (PNG) for the given flow with advanced options.
     * @param flow The flow to generate diagram for
     * @param maxDepth Maximum depth of nested components to show (0=root only, -1=unlimited)
     * @param useFullNames If true, use full element names (e.g., "http:request" instead of "request")
     * @param configRefOnly If true, only show components with config-ref attribute
     */
    public static byte[] generatePlantUmlImage(FlowInfo flow, int maxDepth, boolean useFullNames, boolean configRefOnly) throws IOException {
        String source = generatePlantUmlSource(flow, maxDepth, useFullNames, configRefOnly);
        SourceStringReader reader = new SourceStringReader(source);
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            // Write PNG to output stream
            reader.outputImage(os);
            return os.toByteArray();
        }
    }

    private static String generatePlantUmlSource(FlowInfo flow, int maxDepth) {
        return generatePlantUmlSource(flow, maxDepth, false, false);
    }
    
    private static String generatePlantUmlSource(FlowInfo flow, int maxDepth, boolean useFullNames, boolean configRefOnly) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("skinparam shadowing false\n");
        sb.append("skinparam activity {\n");
        sb.append("  BackgroundColor #E6E6FA\n"); 
        sb.append("  BorderColor #663399\n");     
        sb.append("  ArrowColor #663399\n");
        sb.append("}\n");
        
        sb.append("start\n");

        List<ComponentInfo> components = flow.getComponents();
        if (components != null) {
            // Filter for root components (depth 0)
            List<ComponentInfo> roots = new ArrayList<>();
            for (ComponentInfo c : components) {
                if ("0".equals(c.getAttributes().getOrDefault("_depth", "0"))) {
                    // Don't filter by config-ref at root level when configRefOnly=true
                    // because structural elements (choice, try) don't have config-ref
                    // but may contain config-ref components inside them
                    roots.add(c);
                }
            }
            
            for (ComponentInfo root : roots) {
                generateComponent(sb, root, 0, maxDepth, useFullNames, configRefOnly);
            }
        }

        sb.append("stop\n");
        sb.append("@enduml\n");
        return sb.toString();
    }

    private static void generateComponent(StringBuilder sb, ComponentInfo comp, int currentDepth, int maxDepth, boolean useFullNames, boolean configRefOnly) {
        String type = comp.getType();
        
        // Determine display name based on useFullNames setting
        String name;
        if (useFullNames) {
            // Use full name (e.g., "http:request")
            name = comp.getType();
        } else {
            // Use short name (e.g., "request")
            name = comp.getType();
            if (name.contains(":")) {
                name = name.substring(name.indexOf(":") + 1);
            }
        }
        
        // When configRefOnly is true, skip rendering structural/control-flow elements
        // and just traverse through them to find config-ref components
        if (configRefOnly) {
            boolean isStructuralElement = type.endsWith("choice") || type.endsWith("when") || type.endsWith("otherwise") ||
                                         type.endsWith("try") || type.endsWith("foreach") || type.endsWith("async") ||
                                         type.endsWith("until-successful") || type.endsWith("transactional") ||
                                         type.endsWith("scatter-gather") || type.endsWith("route") ||
                                         type.endsWith("error-handler") || type.endsWith("on-error-continue") ||
                                         type.endsWith("on-error-propagate") || type.endsWith("router");
            
            if (isStructuralElement) {
                // Don't render this element, just traverse its children
                List<ComponentInfo> children = comp.getChildren();
                if (children != null && !children.isEmpty()) {
                    for (ComponentInfo child : children) {
                        generateComponent(sb, child, currentDepth, maxDepth, useFullNames, configRefOnly);
                    }
                }
                return; // Don't render anything for this structural element
            }
            
            // If not a structural element, check if it has config-ref
            if (comp.getConfigRef() == null || comp.getConfigRef().isEmpty()) {
                // No config-ref, skip this component entirely (don't render, don't traverse children)
                return;
            }
            
            // Has config-ref, render it as a simple activity with connection details
            // Format: **bold label** on first line, <size:10>smaller details</size> on second line
            if (comp.getConnectionDetails() != null && !comp.getConnectionDetails().isEmpty()) {
                // Multi-line label with formatting:
                // Line 1: **ComponentName** (bold)
                // Line 2: <size:10>connection details</size> (smaller font)
                sb.append(":**" + name + "**\n<size:10>" + comp.getConnectionDetails() + "</size>;\n");
            } else {
                // Just the bold name
                sb.append(":**" + name + "**;\n");
            }
            return;
        }
        
        // Normal rendering (configRefOnly=false) - original logic below
        
        // Handle Switching Logic (Choice)
        if (type.endsWith("choice") || type.endsWith("router")) {
            // Use 'switch' to represent branching from a single point
            sb.append("switch (Choice)\n");
            
            List<ComponentInfo> children = comp.getChildren();
            if (children != null && !children.isEmpty()) {
                for (ComponentInfo child : children) {
                    if (child.getType().endsWith("when")) {
                         // Simplify label
                         sb.append("case ( When )\n");
                         
                         // Process 'when' children
                         for (ComponentInfo inner : child.getChildren()) {
                             generateComponent(sb, inner, currentDepth + 1, maxDepth, useFullNames, configRefOnly);
                         }
                    } else if (child.getType().endsWith("otherwise")) {
                         sb.append("case ( Default )\n");
                         // Process 'otherwise' children
                         for (ComponentInfo inner : child.getChildren()) {
                             generateComponent(sb, inner, currentDepth + 1, maxDepth, useFullNames, configRefOnly);
                         }
                    }
                }
            } else {
                sb.append("case ( Empty )\n");
            }
            sb.append("endswitch\n");
            
        } 
        // Handle Scatter-Gather
        else if (type.endsWith("scatter-gather")) {
            sb.append("fork\n");
            List<ComponentInfo> children = comp.getChildren();
            if (children != null && !children.isEmpty()) {
                boolean first = true;
                for (ComponentInfo child : children) {
                    if (!first) {
                        sb.append("fork again\n");
                    }
                    // Usually children of scatter-gather are 'route' or direct components
                    if (child.getType().endsWith("route")) {
                         for (ComponentInfo inner : child.getChildren()) {
                             generateComponent(sb, inner, currentDepth + 1, maxDepth, useFullNames, configRefOnly);
                         }
                    } else {
                        // Direct component in scatter gather
                        generateComponent(sb, child, currentDepth + 1, maxDepth, useFullNames, configRefOnly);
                    }
                    first = false;
                }
            }
            sb.append("end fork\n");
        }
        // Standard Component
        else {
            // Clean name for display
            if (name.length() > 20) {
                 name = name.substring(0, 20) + "\\n" + name.substring(20);
            }
            sb.append(":").append(name).append(";\n");
            
            // Should not have children usually, unless it's a scope like 'try', 'foreach', 'until-successful'
            // For now, if it has children, just sequence them?
            // Scopes behavior:
            List<ComponentInfo> children = comp.getChildren();
            
            // Exception: ONLY try blocks should ALWAYS show their children (they contain implementation)
            // error-handler, on-error-continue, on-error-propagate should respect depth filtering
            boolean isTryBlock = type.endsWith("try");
            
            // Only process children if we haven't exceeded max depth (or maxDepth is -1 for unlimited)
            // OR if this is a try block (always show their children)
            if (children != null && !children.isEmpty() && (maxDepth < 0 || currentDepth < maxDepth || isTryBlock)) {
                // For scopes (try, foreach, async), we might want a group
                boolean isScope = type.endsWith("try") || type.endsWith("foreach") || type.endsWith("async") || type.endsWith("until-successful") || type.endsWith("transactional");
                
                if (isScope) {
                     sb.append("partition " + name + " {\n");
                     for (ComponentInfo child : children) {
                         // Skip children without config-ref if configRefOnly is true
                         // UNLESS the child is a scope (try, foreach, etc.) that might contain config-ref components
                         boolean isChildScope = child.getType().endsWith("try") || child.getType().endsWith("foreach") || 
                                               child.getType().endsWith("async") || child.getType().endsWith("until-successful") || 
                                               child.getType().endsWith("transactional") || child.getType().endsWith("error-handler");
                         if (configRefOnly && !isChildScope && (child.getConfigRef() == null || child.getConfigRef().isEmpty())) {
                             continue;
                         }
                         generateComponent(sb, child, currentDepth + 1, maxDepth, useFullNames, configRefOnly); // Recurse
                     }
                     sb.append("}\n");
                } else {
                    // Just sequence them if strictly hierarchical wrappers (like error-handler?)
                    for (ComponentInfo child : children) {
                        // Skip children without config-ref if configRefOnly is true
                        // UNLESS the child is a scope that might contain config-ref components
                        boolean isChildScope = child.getType().endsWith("try") || child.getType().endsWith("foreach") || 
                                              child.getType().endsWith("async") || child.getType().endsWith("until-successful") || 
                                              child.getType().endsWith("transactional") || child.getType().endsWith("error-handler");
                        if (configRefOnly && !isChildScope && (child.getConfigRef() == null || child.getConfigRef().isEmpty())) {
                            continue;
                        }
                        generateComponent(sb, child, currentDepth + 1, maxDepth, useFullNames, configRefOnly);
                    }
                }
            }
        }
    }
}
