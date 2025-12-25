package com.raks.raksanalyzer.generator;

import com.raks.raksanalyzer.domain.model.ComponentInfo;
import com.raks.raksanalyzer.domain.model.FlowInfo;
import net.sourceforge.plantuml.SourceStringReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;

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
        return generatePlantUmlImage(flow, maxDepth, false, false, new HashMap<>());
    }
    
    /**
     * Generates a PlantUML diagram image (PNG) for the given flow with advanced options.
     * @param flow The flow to generate diagram for
     * @param maxDepth Maximum depth of nested components to show (0=root only, -1=unlimited)
     * @param useFullNames If true, use full element names (e.g., "http:request" instead of "request")
     * @param configRefOnly If true, only show components with config-ref attribute
     */
    public static byte[] generatePlantUmlImage(FlowInfo flow, int maxDepth, boolean useFullNames, boolean configRefOnly) throws IOException {
        return generatePlantUmlImage(flow, maxDepth, useFullNames, configRefOnly, new HashMap<>());
    }

    /**
     * Generates a PlantUML diagram image (PNG) for the given flow with advanced options.
     * @param flow The flow to generate diagram for
     * @param maxDepth Maximum depth of nested components to show (0=root only, -1=unlimited)
     * @param useFullNames If true, use full element names (e.g., "http:request" instead of "request")
     * @param configRefOnly If true, only show components with config-ref attribute
     * @param allFlows Map of all flows/sub-flows for recursion
     */
    public static byte[] generatePlantUmlImage(FlowInfo flow, int maxDepth, boolean useFullNames, boolean configRefOnly, Map<String, FlowInfo> allFlows) throws IOException {
        String source = generatePlantUmlSource(flow, maxDepth, useFullNames, configRefOnly, allFlows);
        SourceStringReader reader = new SourceStringReader(source);
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            // Write PNG to output stream
            reader.outputImage(os);
            return os.toByteArray();
        }
    }

    private static String generatePlantUmlSource(FlowInfo flow, int maxDepth, boolean useFullNames, boolean configRefOnly, Map<String, FlowInfo> allFlows) {
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
            
            Set<String> visitedFlows = new HashSet<>();
            visitedFlows.add(flow.getName()); // Prevent self-recursion immediately
            
            for (ComponentInfo root : roots) {
                generateComponent(sb, root, 0, maxDepth, useFullNames, configRefOnly, allFlows, visitedFlows);
            }
        }

        sb.append("stop\n");
        sb.append("@enduml\n");
        return sb.toString();
    }
    
    private static java.nio.file.Path iconsDir = java.nio.file.Paths.get("c:/raks/apiguard/raksanalyzer/src/main/resources/images/mule");

    private static final Map<String, String> SPECIAL_ICONS = new HashMap<>();
    static {
        SPECIAL_ICONS.put("async", "async-scope");
        SPECIAL_ICONS.put("async-scope", "async-scope");
        SPECIAL_ICONS.put("parallel-foreach", "foreach"); // Fallback to foreach if parallel specific missing
        SPECIAL_ICONS.put("foreach", "foreach");
        SPECIAL_ICONS.put("choice", "choice");
        SPECIAL_ICONS.put("scatter-gather", "scatter-gather");
        SPECIAL_ICONS.put("round-robin", "round-robin");
        SPECIAL_ICONS.put("route", "route");
        SPECIAL_ICONS.put("batch:job", "batch-scope");
        SPECIAL_ICONS.put("batch:step", "batch-step");
        SPECIAL_ICONS.put("batch:process-records", "batch-scope");
        SPECIAL_ICONS.put("batch:on-complete", "batch-scope");
        SPECIAL_ICONS.put("flow-ref", "flow-reference");
        SPECIAL_ICONS.put("tracking:transaction", "transaction");
        
        // Connector mapping fallbacks
        // Email -> Javadoc (User Request)
        SPECIAL_ICONS.put("email:send", "javadoc");
        SPECIAL_ICONS.put("email:list-imap", "javadoc");
        // Sockets -> HTTP icon (User Request)
        SPECIAL_ICONS.put("sockets:send", "http-request");
        
        // ApiKit Routers
        SPECIAL_ICONS.put("apikit:router", "apikit");
        SPECIAL_ICONS.put("apikit-soap:router", "apikit");
        
        // Others -> Remove specific mapping so they hit the universal fallback 'mule_mule.png'
        // (Sockets, JMS, VM will now use default Mule icon if their specific icon is missing)
    }

    private static String getIconForType(String type) {
        if (type == null) return null;
        
        String cleanType = type;
        if (cleanType.contains(":")) {
             cleanType = cleanType.substring(cleanType.indexOf(":")+1);
        }
        
        // 0. Check Special Mappings
        if (SPECIAL_ICONS.containsKey(type)) {
             String specialName = SPECIAL_ICONS.get(type);
             String[] specialCandidates = {"mule_" + specialName + ".png", "mule_core-" + specialName + ".png", "mule_unknown-" + specialName + ".png"};
             for (String cand : specialCandidates) {
                 java.nio.file.Path p = iconsDir.resolve(cand);
                 if (java.nio.file.Files.exists(p)) {
                     return "<img:" + p.toAbsolutePath().toString().replace("\\", "/") + ">";
                 }
             }
        }
        if (SPECIAL_ICONS.containsKey(cleanType)) {
             String specialName = SPECIAL_ICONS.get(cleanType);
             String[] specialCandidates = {"mule_" + specialName + ".png", "mule_core-" + specialName + ".png", "mule_unknown-" + specialName + ".png"};
             for (String cand : specialCandidates) {
                 java.nio.file.Path p = iconsDir.resolve(cand);
                 if (java.nio.file.Files.exists(p)) {
                     return "<img:" + p.toAbsolutePath().toString().replace("\\", "/") + ">";
                 }
             }
        }

        // 1. Try full name
        String filename = "mule_" + cleanType + ".png";
        java.nio.file.Path iconPath = iconsDir.resolve(filename);
        if (java.nio.file.Files.exists(iconPath)) {
            return "<img:" + iconPath.toAbsolutePath().toString().replace("\\", "/") + ">";
        }
        
        // 2. Try replacing ':' with '-'
        if (type.contains(":")) {
            String dashedType = type.replace(":", "-");
            filename = "mule_" + dashedType + ".png";
            iconPath = iconsDir.resolve(filename);
            if (java.nio.file.Files.exists(iconPath)) {
                return "<img:" + iconPath.toAbsolutePath().toString().replace("\\", "/") + ">";
            }
        }
        
        // 3. Try "unknown" prefix
        filename = "mule_unknown-" + cleanType + ".png";
        iconPath = iconsDir.resolve(filename);
        if (java.nio.file.Files.exists(iconPath)) {
             return "<img:" + iconPath.toAbsolutePath().toString().replace("\\", "/") + ">";
        }

        // 4. Universal Fallback (Mule Icon)
        // If absolutely nothing found, return the generic Mule icon.
        java.nio.file.Path defaultIcon = iconsDir.resolve("mule_mule.png");
        if (java.nio.file.Files.exists(defaultIcon)) {
             return "<img:" + defaultIcon.toAbsolutePath().toString().replace("\\", "/") + ">";
        }

        return null;
    }
    
    // --- Helper Methods for Element Classification ---

    private static boolean isStructuralElement(String type) {
        return type.endsWith("choice") || type.endsWith("when") || type.endsWith("otherwise") ||
               type.endsWith("try") || type.endsWith("foreach") || type.endsWith("async") ||
               type.endsWith("until-successful") || type.endsWith("transactional") ||
               type.endsWith("scatter-gather") || type.endsWith("route") ||
               type.endsWith("error-handler") || type.endsWith("on-error-continue") ||
               type.endsWith("on-error-propagate") ||
               type.endsWith("batch:job") || type.endsWith("batch:process-records") || 
               type.endsWith("batch:step") || type.endsWith("batch:on-complete");
    }

    private static boolean isIntegrationTrigger(String type) {
        // Elements that should be shown in integration diagram even without config-ref
        return type.endsWith("scheduler");
    }

    private static boolean isFlowRef(String type) {
        return type.endsWith("flow-ref");
    }

    private static void generateComponent(StringBuilder sb, ComponentInfo comp, int currentDepth, int maxDepth, 
                                          boolean useFullNames, boolean configRefOnly, 
                                          Map<String, FlowInfo> allFlows, Set<String> visitedFlows) {
        String type = comp.getType();
        String cleanType = type.contains(":") ? type.substring(type.indexOf(":") + 1) : type;
        
        // Look up icon
        String iconImg = getIconForType(type); // Pass FULL type to match special mappings like batch:job
        
        // Determine display name based on useFullNames setting
        String name;
        if (useFullNames) {
            name = comp.getType();
        } else {
            name = comp.getType();
            if (name.contains(":")) {
                name = name.substring(name.indexOf(":") + 1);
            }
        }
        
        // When configRefOnly is true (Integration Diagrams)
        if (configRefOnly) {
            
            // Check if this component (Scope/Choice/FlowRef) actually contains any integration points
            // If not, we skip rendering it entirely to declutter the diagram.
            if ((isStructuralElement(type) || type.endsWith("choice") || 
                 type.endsWith("scatter-gather") || isFlowRef(type)) 
                 && !hasIntegrationContent(comp, allFlows, visitedFlows)) {
                return;
            }

            // 1. Handle Choice (Switch) - but NOT apikit:router or apikit-soap:router
            if (type.endsWith("choice")) {
                sb.append("switch (" + (iconImg != null ? iconImg + " " : "") + "Choice)\n");
                
                List<ComponentInfo> children = comp.getChildren();
                boolean hasAnyCase = false;
                if (children != null && !children.isEmpty()) {
                    for (ComponentInfo child : children) {
                        // Check content of the case (when/otherwise)
                        if (!hasIntegrationContent(child, allFlows, visitedFlows)) {
                            // If a branch is empty of integration points, we can either:
                            // A) Skip it (diagram might look disconnected if using switch)
                            // B) Show an empty case.
                            // The user requested: "unless the blocks contain the config-ref... don't need to show".
                            // However, skipping a case in a switch statement might be structurally misleading?
                            // Let's hide the content, but keep the case label if we are inside a switch.
                            // Actually, if we skip the case entireley, PlantUML handles it.
                            continue;
                        }

                        if (child.getType().endsWith("when")) {
                             sb.append("case ( When )\n");
                             hasAnyCase = true;
                             for (ComponentInfo inner : child.getChildren()) {
                                 generateComponent(sb, inner, currentDepth + 1, maxDepth, useFullNames, configRefOnly, allFlows, visitedFlows);
                             }
                        } else if (child.getType().endsWith("otherwise")) {
                             sb.append("case ( Default )\n");
                             hasAnyCase = true;
                             for (ComponentInfo inner : child.getChildren()) {
                                 generateComponent(sb, inner, currentDepth + 1, maxDepth, useFullNames, configRefOnly, allFlows, visitedFlows);
                             }
                        }
                    }
                } 
                if (!hasAnyCase) {
                    // All branches were empty. 
                    // This case should have been caught by the top-level check !hasIntegrationContent
                    // But just in case:
                    sb.append("case ( Empty )\n");
                }
                sb.append("endswitch\n");
                return;
            }

            // 2. Handle Structural Scopes (Partitions)
            if (isStructuralElement(type)) {
                // Determine scope label
                String scopeName = type;
                if (scopeName.contains(":")) scopeName = scopeName.substring(scopeName.indexOf(":")+1);
                scopeName = scopeName.substring(0,1).toUpperCase() + scopeName.substring(1);

                sb.append("partition \"" + (iconImg != null ? iconImg + " " : "") + scopeName + "\" {\n");
                List<ComponentInfo> children = comp.getChildren();
                if (children != null && !children.isEmpty()) {
                    for (ComponentInfo child : children) {
                        generateComponent(sb, child, currentDepth + 1, maxDepth, useFullNames, configRefOnly, allFlows, visitedFlows);
                    }
                }
                sb.append("}\n");
                return;
            }
            
            // 3. Handle Flow References (Recursion with Partition)
            if (isFlowRef(type)) {
                String targetFlowName = comp.getAttributes().get("name");
                if (targetFlowName != null && !targetFlowName.isEmpty()) {
                    if (visitedFlows.contains(targetFlowName)) {
                        sb.append(":**(Recursive)** " + targetFlowName + ";\n");
                        return;
                    }
                    
                    if (allFlows != null && allFlows.containsKey(targetFlowName)) {
                        FlowInfo targetFlow = allFlows.get(targetFlowName);
                        Set<String> nextVisited = new HashSet<>(visitedFlows);
                        nextVisited.add(targetFlowName);
                        
                        sb.append("partition \"" + (iconImg != null ? iconImg + " " : "") + "Ref: " + targetFlowName + "\" {\n");
                        List<ComponentInfo> targetComponents = targetFlow.getComponents();
                        if (targetComponents != null) {
                            for (ComponentInfo targetComp : targetComponents) {
                                if ("0".equals(targetComp.getAttributes().getOrDefault("_depth", "0"))) {
                                    generateComponent(sb, targetComp, currentDepth, maxDepth, useFullNames, configRefOnly, allFlows, nextVisited);
                                }
                            }
                        }
                        sb.append("}\n");
                    }
                }
                return;
            }
            
            // 4. Handle Integration Triggers
            if (isIntegrationTrigger(type)) {
                 sb.append(":**" + (iconImg != null ? iconImg + " " : "") + name + "**;\n");
                 return;
            }
            
            // 5. Handle Standard Config-Ref Components OR Known Connectors
            // Even if config-ref is missing, if it looks like a connector (e.g. http:request), show it.
            if (isLikelyConnector(type) || (comp.getConfigRef() != null && !comp.getConfigRef().isEmpty())) {
                 if (comp.getConnectionDetails() != null && !comp.getConnectionDetails().isEmpty()) {
                      sb.append(":**" + (iconImg != null ? iconImg + " " : "") + name + "**\n<size:10>" + comp.getConnectionDetails() + "</size>;\n");
                  } else {
                      sb.append(":**" + (iconImg != null ? iconImg + " " : "") + name + "**;\n");
                 }
                 return;
            }
            
            return;
        }
        
        // Normal rendering (configRefOnly=false) 
        // ... (Keep existing logic identical for visual diagrams) ...
        
        // Handle Switching Logic (Choice)
        if (type.endsWith("choice")) {
            // Use 'switch' to represent branching from a single point
            sb.append("switch (" + (iconImg != null ? iconImg + " " : "") + "Choice)\n");
            
            List<ComponentInfo> children = comp.getChildren();
            if (children != null && !children.isEmpty()) {
                for (ComponentInfo child : children) {
                    if (child.getType().endsWith("when")) {
                         // Simplify label
                         sb.append("case ( When )\n");
                         
                         // Process 'when' children
                         for (ComponentInfo inner : child.getChildren()) {
                             generateComponent(sb, inner, currentDepth + 1, maxDepth, useFullNames, configRefOnly, allFlows, visitedFlows);
                         }
                    } else if (child.getType().endsWith("otherwise")) {
                         sb.append("case ( Default )\n");
                         // Process 'otherwise' children
                         for (ComponentInfo inner : child.getChildren()) {
                             generateComponent(sb, inner, currentDepth + 1, maxDepth, useFullNames, configRefOnly, allFlows, visitedFlows);
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
                             generateComponent(sb, inner, currentDepth + 1, maxDepth, useFullNames, configRefOnly, allFlows, visitedFlows);
                         }
                    } else {
                        // Direct component in scatter gather
                        generateComponent(sb, child, currentDepth + 1, maxDepth, useFullNames, configRefOnly, allFlows, visitedFlows);
                    }
                    first = false;
                }
            }
            sb.append("end fork\n");
        }
        // Handle Structural Scopes (Partitions) - Visual Diagram Mode
        else if (isStructuralElement(type) || type.endsWith("until-successful") || type.endsWith("transactional")) {
            // Determine scope label
            String scopeName = name;
            // Capitalize
            if (scopeName.length() > 0) {
                 scopeName = scopeName.substring(0, 1).toUpperCase() + scopeName.substring(1);
            }

            sb.append("partition \"" + (iconImg != null ? iconImg + " " : "") + scopeName + "\" {\n");
            
            List<ComponentInfo> children = comp.getChildren();
            // Always show children of scopes if possible, or respect maxDepth loosely?
            // User reported missing content in Async. We should try to show it.
            // If we are strictly at maxDepth, maybe we stop. But scopes usually warrant checking inside.
            // Let's allow one more level for scopes if we are at boundary? 
            // Or component logic already checks depth.
            // Let's iterate children.
            
            boolean isTryBlock = type.endsWith("try");
             
            if (children != null && !children.isEmpty()) {
                for (ComponentInfo child : children) {
                    // Check depth: if maxDepth is set (-1 is unlimited), respect it.
                    // BUT for scopes, if we just show empty partition it's ugly.
                    // Let's rely on standard currentDepth check for children.
                    if (maxDepth < 0 || currentDepth < maxDepth || isTryBlock) {
                         generateComponent(sb, child, currentDepth + 1, maxDepth, useFullNames, configRefOnly, allFlows, visitedFlows);
                    } else {
                        // Depth limit reached, maybe visually indicate?
                        // sb.append(":...;\n"); 
                        // For now just skip to avoid clutter.
                }
            }
            sb.append("}\n");
        }
    }
        
        // Handle Flow References (Recursion with Partition) - Visual Diagram Mode
        else if (isFlowRef(type)) {
            String targetFlowName = comp.getAttributes().get("name");
            if (targetFlowName != null && !targetFlowName.isEmpty()) {
                if (visitedFlows.contains(targetFlowName)) {
                    // Cycle detected
                    sb.append(":" + (iconImg != null ? iconImg + " " : "") + "Ref: " + targetFlowName + "\\n**(Recursive)**;\n");
                } else if (allFlows != null && allFlows.containsKey(targetFlowName)) {
                    // Check depth before expanding
                    if (maxDepth < 0 || currentDepth < maxDepth) {
                        FlowInfo targetFlow = allFlows.get(targetFlowName);
                        Set<String> nextVisited = new HashSet<>(visitedFlows);
                        nextVisited.add(targetFlowName);
                        
                        sb.append("partition \"" + (iconImg != null ? iconImg + " " : "") + "Ref: " + targetFlowName + "\" {\n");
                        List<ComponentInfo> targetComponents = targetFlow.getComponents();
                        if (targetComponents != null) {
                            for (ComponentInfo targetComp : targetComponents) {
                                if ("0".equals(targetComp.getAttributes().getOrDefault("_depth", "0"))) {
                                    generateComponent(sb, targetComp, currentDepth + 1, maxDepth, useFullNames, configRefOnly, allFlows, nextVisited);
                                }
                            }
                        }
                        sb.append("}\n");
                    } else {
                         // Depth limit reached
                        sb.append(":" + (iconImg != null ? iconImg + " " : "") + "Ref: " + targetFlowName + ";\n");
                    }
                } else {
                    // Flow not found
                    sb.append(":" + (iconImg != null ? iconImg + " " : "") + "Ref: " + targetFlowName + ";\n");
                }
            } else {
                sb.append(":" + (iconImg != null ? iconImg + " " : "") + "Ref: (Unknown);\n");
            }
        }
        // Standard Component
        else {
            // Clean name for display
            if (name.length() > 20) {
                 name = name.substring(0, 20) + "\\n" + name.substring(20);
            }
            String details = "";
            if ((isLikelyConnector(type) || (comp.getConfigRef() != null && !comp.getConfigRef().isEmpty())) && 
                comp.getConnectionDetails() != null && !comp.getConnectionDetails().isEmpty()) {
                 details = "\\n<size:10>" + comp.getConnectionDetails() + "</size>";
            }
            sb.append(":").append((iconImg != null ? iconImg + " " : "")).append(name).append(details).append(";\n");
            
            // Handle children for standard components (if any unexpected ones exist, or generic wrappers)
            List<ComponentInfo> children = comp.getChildren();
            if (children != null && !children.isEmpty() && (maxDepth < 0 || currentDepth < maxDepth)) {
                 for (ComponentInfo child : children) {
                     generateComponent(sb, child, currentDepth + 1, maxDepth, useFullNames, configRefOnly, allFlows, visitedFlows);
                 }
            }
        }
    }
    
    // Helper to check if a component or any of its children has integration content
    private static boolean hasIntegrationContent(ComponentInfo comp, Map<String, FlowInfo> allFlows, Set<String> visitedFlows) {
        String type = comp.getType();
        
        // 1. Direct integration point?
        if (isIntegrationTrigger(type)) return true;
        if (comp.getConfigRef() != null && !comp.getConfigRef().isEmpty()) return true;
        if (isLikelyConnector(type)) return true;
        
        // 2. Flow Reference? Recursively check target flow
        if (isFlowRef(type)) {
            String targetFlowName = comp.getAttributes().get("name");
            if (targetFlowName != null && allFlows != null && allFlows.containsKey(targetFlowName)) {
                if (visitedFlows.contains(targetFlowName)) {
                    return false; // Break cycle
                }
                
                FlowInfo targetFlow = allFlows.get(targetFlowName);
                Set<String> nextVisited = new HashSet<>(visitedFlows);
                nextVisited.add(targetFlowName);
                
                List<ComponentInfo> targetComps = targetFlow.getComponents();
                if (targetComps != null) {
                    for (ComponentInfo targetComp : targetComps) {
                        // Check roots
                        if ("0".equals(targetComp.getAttributes().getOrDefault("_depth", "0"))) {
                             if (hasIntegrationContent(targetComp, allFlows, nextVisited)) return true;
                        }
                    }
                }
            }
            return false;
        }
        
        // 3. Structural/Scope? Check children
        List<ComponentInfo> children = comp.getChildren();
        if (children != null) {
            for (ComponentInfo child : children) {
                if (hasIntegrationContent(child, allFlows, visitedFlows)) return true;
            }
        }
        
        return false;
    }

    private static boolean isLikelyConnector(String type) {
        // Must contain namespace separator
        if (!type.contains(":")) return false;
        
        // Exclude known non-integration namespaces
        if (type.startsWith("ee:") || 
            type.startsWith("batch:") || 
            type.startsWith("tracking:") || 
            type.startsWith("doc:") ||
            type.startsWith("validation:") ||
            type.startsWith("compression:") ||
            type.startsWith("scripting:")) {
            return false;
        }
        
        // Avoid treating known structural elements as connectors (doubly sure)
        if (isStructuralElement(type)) return false;
        
        // If it's a "config" element (global config), it's not a flow component/connector activity
        if (type.endsWith("-config") || type.endsWith(":config")) return false;
        
        return true;
    }
}

