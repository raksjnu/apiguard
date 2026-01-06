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
    public static String generateTextFlow(FlowInfo flow) {
        StringBuilder sb = new StringBuilder();
        sb.append("Start");
        List<ComponentInfo> components = flow.getComponents();
        if (components != null) {
            for (ComponentInfo comp : components) {
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
    private static String centerText(String text, int referenceLength) {
        if (text == null || text.length() >= referenceLength) {
            return text; 
        }
        int diff = referenceLength - text.length();
        int leftPad = diff / 4; 
        if (leftPad > 0) {
            StringBuilder padded = new StringBuilder();
            for (int i = 0; i < leftPad; i++) {
                padded.append("  "); 
            }
            padded.append(text);
            return padded.toString();
        }
        return text;
    }
    public static byte[] generatePlantUmlImage(FlowInfo flow, int maxDepth) throws IOException {
        return generatePlantUmlImage(flow, maxDepth, false, false, new HashMap<>());
    }
    public static byte[] generatePlantUmlImage(FlowInfo flow, int maxDepth, boolean useFullNames, boolean configRefOnly) throws IOException {
        return generatePlantUmlImage(flow, maxDepth, useFullNames, configRefOnly, new HashMap<>());
    }
    public static byte[] generatePlantUmlImage(FlowInfo flow, int maxDepth, boolean useFullNames, boolean configRefOnly, Map<String, FlowInfo> allFlows) throws IOException {
        String source = generatePlantUmlSource(flow, maxDepth, useFullNames, configRefOnly, allFlows);
        SourceStringReader reader = new SourceStringReader(source);
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
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
            List<ComponentInfo> roots = new ArrayList<>();
            for (ComponentInfo c : components) {
                if ("0".equals(c.getAttributes().getOrDefault("_depth", "0"))) {
                    roots.add(c);
                }
            }
            Set<String> visitedFlows = new HashSet<>();
            visitedFlows.add(flow.getName()); 
            for (ComponentInfo root : roots) {
                generateComponent(sb, root, 0, maxDepth, useFullNames, configRefOnly, allFlows, visitedFlows);
            }
        }
        sb.append("stop\n");
        sb.append("@enduml\n");
        return sb.toString();
    }
    private static java.nio.file.Path iconsDir;
    private static final Map<String, String> SPECIAL_ICONS = new HashMap<>();
    
    static {
        try {
            String tempPath = System.getProperty("java.io.tmpdir");
            iconsDir = java.nio.file.Paths.get(tempPath, "raks-mule-icons-cache");
            if (!java.nio.file.Files.exists(iconsDir)) {
                java.nio.file.Files.createDirectories(iconsDir);
            }
            // iterate over known icons to ensure they are available? 
            // Lazy loading in getIconPath handles individual files.
        } catch (IOException e) {
            e.printStackTrace();
            // Fallback
            iconsDir = java.nio.file.Paths.get(".");
        }

        SPECIAL_ICONS.put("async", "async-scope");
        SPECIAL_ICONS.put("async-scope", "async-scope");
        SPECIAL_ICONS.put("parallel-foreach", "foreach"); 
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
        SPECIAL_ICONS.put("email:send", "javadoc");
        SPECIAL_ICONS.put("email:list-imap", "javadoc");
        SPECIAL_ICONS.put("sockets:send", "http-request");
        SPECIAL_ICONS.put("apikit:router", "apikit");
        SPECIAL_ICONS.put("apikit-soap:router", "apikit");
    }

    private static java.nio.file.Path getIconPath(String filename) {
        if (iconsDir == null) return null;
        java.nio.file.Path p = iconsDir.resolve(filename);
        if (java.nio.file.Files.exists(p)) return p;
        
        try (java.io.InputStream is = DiagramGenerator.class.getResourceAsStream("/images/mule/" + filename)) {
            if (is != null) {
                java.nio.file.Files.copy(is, p, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return p;
            }
        } catch (Exception e) {
            // Ignore extraction errors
        }
        return null;
    }

    private static String getIconForType(String type) {
        if (type == null) return null;
        String cleanType = type;
        if (cleanType.contains(":")) {
             cleanType = cleanType.substring(cleanType.indexOf(":")+1);
        }
        
        if (SPECIAL_ICONS.containsKey(type)) {
             String specialName = SPECIAL_ICONS.get(type);
             String[] specialCandidates = {"mule_" + specialName + ".png", "mule_core-" + specialName + ".png", "mule_unknown-" + specialName + ".png"};
             for (String cand : specialCandidates) {
                 java.nio.file.Path p = getIconPath(cand);
                 if (p != null) {
                     return "<img:" + p.toAbsolutePath().toString().replace("\\", "/") + ">";
                 }
             }
        }
        
        if (SPECIAL_ICONS.containsKey(cleanType)) {
             String specialName = SPECIAL_ICONS.get(cleanType);
             String[] specialCandidates = {"mule_" + specialName + ".png", "mule_core-" + specialName + ".png", "mule_unknown-" + specialName + ".png"};
             for (String cand : specialCandidates) {
                 java.nio.file.Path p = getIconPath(cand);
                 if (p != null) {
                     return "<img:" + p.toAbsolutePath().toString().replace("\\", "/") + ">";
                 }
             }
        }

        String filename = "mule_" + cleanType + ".png";
        java.nio.file.Path iconPath = getIconPath(filename);
        if (iconPath != null) {
            return "<img:" + iconPath.toAbsolutePath().toString().replace("\\", "/") + ">";
        }
        
        if (type.contains(":")) {
            String dashedType = type.replace(":", "-");
            filename = "mule_" + dashedType + ".png";
            iconPath = getIconPath(filename);
            if (iconPath != null) {
                return "<img:" + iconPath.toAbsolutePath().toString().replace("\\", "/") + ">";
            }
        }
        
        filename = "mule_unknown-" + cleanType + ".png";
        iconPath = getIconPath(filename);
        if (iconPath != null) {
             return "<img:" + iconPath.toAbsolutePath().toString().replace("\\", "/") + ">";
        }
        
        java.nio.file.Path defaultIcon = getIconPath("mule_mule.png");
        if (defaultIcon != null) {
             return "<img:" + defaultIcon.toAbsolutePath().toString().replace("\\", "/") + ">";
        }
        
        return null;
    }
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
        String iconImg = getIconForType(type); 
        String name;
        if (useFullNames) {
            name = comp.getType();
        } else {
            name = comp.getType();
            if (name.contains(":")) {
                name = name.substring(name.indexOf(":") + 1);
            }
        }
        if (configRefOnly) {
            if ((isStructuralElement(type) || type.endsWith("choice") || 
                 type.endsWith("scatter-gather") || isFlowRef(type)) 
                 && !hasIntegrationContent(comp, allFlows, visitedFlows)) {
                return;
            }
            if (type.endsWith("choice")) {
                sb.append("switch (" + (iconImg != null ? iconImg + " " : "") + "Choice)\n");
                List<ComponentInfo> children = comp.getChildren();
                boolean hasAnyCase = false;
                if (children != null && !children.isEmpty()) {
                    for (ComponentInfo child : children) {
                        if (!hasIntegrationContent(child, allFlows, visitedFlows)) {
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
                    sb.append("case ( Empty )\n");
                }
                sb.append("endswitch\n");
                return;
            }
            if (isStructuralElement(type)) {
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
            if (isIntegrationTrigger(type)) {
                 sb.append(":**" + (iconImg != null ? iconImg + " " : "") + name + "**;\n");
                 return;
            }
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
        if (type.endsWith("choice")) {
            sb.append("switch (" + (iconImg != null ? iconImg + " " : "") + "Choice)\n");
            List<ComponentInfo> children = comp.getChildren();
            if (children != null && !children.isEmpty()) {
                for (ComponentInfo child : children) {
                    if (child.getType().endsWith("when")) {
                         sb.append("case ( When )\n");
                         for (ComponentInfo inner : child.getChildren()) {
                             generateComponent(sb, inner, currentDepth + 1, maxDepth, useFullNames, configRefOnly, allFlows, visitedFlows);
                         }
                    } else if (child.getType().endsWith("otherwise")) {
                         sb.append("case ( Default )\n");
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
        else if (type.endsWith("scatter-gather")) {
            sb.append("fork\n");
            List<ComponentInfo> children = comp.getChildren();
            if (children != null && !children.isEmpty()) {
                boolean first = true;
                for (ComponentInfo child : children) {
                    if (!first) {
                        sb.append("fork again\n");
                    }
                    if (child.getType().endsWith("route")) {
                         for (ComponentInfo inner : child.getChildren()) {
                             generateComponent(sb, inner, currentDepth + 1, maxDepth, useFullNames, configRefOnly, allFlows, visitedFlows);
                         }
                    } else {
                        generateComponent(sb, child, currentDepth + 1, maxDepth, useFullNames, configRefOnly, allFlows, visitedFlows);
                    }
                    first = false;
                }
            }
            sb.append("end fork\n");
        }
        else if (isStructuralElement(type) || type.endsWith("until-successful") || type.endsWith("transactional")) {
            String scopeName = name;
            if (scopeName.length() > 0) {
                 scopeName = scopeName.substring(0, 1).toUpperCase() + scopeName.substring(1);
            }
            sb.append("partition \"" + (iconImg != null ? iconImg + " " : "") + scopeName + "\" {\n");
            List<ComponentInfo> children = comp.getChildren();
            boolean isTryBlock = type.endsWith("try");
            if (children != null && !children.isEmpty()) {
                for (ComponentInfo child : children) {
                    if (maxDepth < 0 || currentDepth < maxDepth || isTryBlock) {
                         generateComponent(sb, child, currentDepth + 1, maxDepth, useFullNames, configRefOnly, allFlows, visitedFlows);
                    } else {
                }
            }
            sb.append("}\n");
        }
    }
        else if (isFlowRef(type)) {
            String targetFlowName = comp.getAttributes().get("name");
            if (targetFlowName != null && !targetFlowName.isEmpty()) {
                if (visitedFlows.contains(targetFlowName)) {
                    sb.append(":" + (iconImg != null ? iconImg + " " : "") + "Ref: " + targetFlowName + "\\n**(Recursive)**;\n");
                } else if (allFlows != null && allFlows.containsKey(targetFlowName)) {
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
                        sb.append(":" + (iconImg != null ? iconImg + " " : "") + "Ref: " + targetFlowName + ";\n");
                    }
                } else {
                    sb.append(":" + (iconImg != null ? iconImg + " " : "") + "Ref: " + targetFlowName + ";\n");
                }
            } else {
                sb.append(":" + (iconImg != null ? iconImg + " " : "") + "Ref: (Unknown);\n");
            }
        }
        else {
            if (name.length() > 20) {
                 name = name.substring(0, 20) + "\\n" + name.substring(20);
            }
            String details = "";
            if ((isLikelyConnector(type) || (comp.getConfigRef() != null && !comp.getConfigRef().isEmpty())) && 
                comp.getConnectionDetails() != null && !comp.getConnectionDetails().isEmpty()) {
                 details = "\\n<size:10>" + comp.getConnectionDetails() + "</size>";
            }
            sb.append(":").append((iconImg != null ? iconImg + " " : "")).append(name).append(details).append(";\n");
            List<ComponentInfo> children = comp.getChildren();
            if (children != null && !children.isEmpty() && (maxDepth < 0 || currentDepth < maxDepth)) {
                 for (ComponentInfo child : children) {
                     generateComponent(sb, child, currentDepth + 1, maxDepth, useFullNames, configRefOnly, allFlows, visitedFlows);
                 }
            }
        }
    }
    private static boolean hasIntegrationContent(ComponentInfo comp, Map<String, FlowInfo> allFlows, Set<String> visitedFlows) {
        String type = comp.getType();
        if (isIntegrationTrigger(type)) return true;
        if (comp.getConfigRef() != null && !comp.getConfigRef().isEmpty()) return true;
        if (isLikelyConnector(type)) return true;
        if (isFlowRef(type)) {
            String targetFlowName = comp.getAttributes().get("name");
            if (targetFlowName != null && allFlows != null && allFlows.containsKey(targetFlowName)) {
                if (visitedFlows.contains(targetFlowName)) {
                    return false; 
                }
                FlowInfo targetFlow = allFlows.get(targetFlowName);
                Set<String> nextVisited = new HashSet<>(visitedFlows);
                nextVisited.add(targetFlowName);
                List<ComponentInfo> targetComps = targetFlow.getComponents();
                if (targetComps != null) {
                    for (ComponentInfo targetComp : targetComps) {
                        if ("0".equals(targetComp.getAttributes().getOrDefault("_depth", "0"))) {
                             if (hasIntegrationContent(targetComp, allFlows, nextVisited)) return true;
                        }
                    }
                }
            }
            return false;
        }
        List<ComponentInfo> children = comp.getChildren();
        if (children != null) {
            for (ComponentInfo child : children) {
                if (hasIntegrationContent(child, allFlows, visitedFlows)) return true;
            }
        }
        return false;
    }
    private static boolean isLikelyConnector(String type) {
        if (!type.contains(":")) return false;
        if (type.startsWith("ee:") || 
            type.startsWith("batch:") || 
            type.startsWith("tracking:") || 
            type.startsWith("doc:") ||
            type.startsWith("validation:") ||
            type.startsWith("compression:") ||
            type.startsWith("scripting:")) {
            return false;
        }
        if (isStructuralElement(type)) return false;
        if (type.endsWith("-config") || type.endsWith(":config")) return false;
        return true;
    }
}
