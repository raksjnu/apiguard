package com.raks.raksanalyzer.generator.word;

import com.raks.raksanalyzer.core.config.ConfigurationManager;
import com.raks.raksanalyzer.domain.model.*;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Word document generator using Apache POI.
 * Refactored to support Grouped generation (Metadata, Properties) + Folder traversal.
 */
public class WordGenerator {
    private static final Logger logger = LoggerFactory.getLogger(WordGenerator.class);
    
    private final ConfigurationManager config = ConfigurationManager.getInstance();
    private XWPFDocument document;
    private Set<String> processedPaths; 
    
    public Path generate(AnalysisResult result) throws IOException {
        logger.info("Generating Word document: {}", result.getAnalysisId());
        processedPaths = new HashSet<>();
        
        try {
            document = new XWPFDocument();
            
            // 1. Cover Page
            createCoverPage(result);
            
            // 2. Project Info & Artifact List
            createProjectSection(result);
            
            // 3. Metadata Files (POM, JSONs)
            createMetadataSection(result);
            
            // 4. Property Files (Properties, YAML)
            createPropertyFilesSection(result);
            
            // 5. Main Content (Ordered Folders)
            if (result.getProjectStructure() != null) {
                createOrderedContent(result.getProjectStructure(), result);
            }
            
            // Save
            Path outputPath = getOutputPath(result);
            try (FileOutputStream out = new FileOutputStream(outputPath.toFile())) {
                document.write(out);
            }
            document.close();
            return outputPath;
            
        } catch (Throwable e) {
            logger.error("Error generating Word doc", e);
            throw new IOException("Failed execution", e);
        }
    }
    
    // --- Section 1: Cover Page ---
    
    private void createCoverPage(AnalysisResult result) {
        ProjectInfo projectInfo = result.getProjectInfo();
        
        XWPFParagraph title = document.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleRun = title.createRun();
        titleRun.setText(config.getProperty("document.word.report.title", "Project Analysis Report"));
        titleRun.setBold(true);
        titleRun.setFontSize(24);
        titleRun.addBreak();
        titleRun.addBreak();
        
        if (projectInfo != null) {
            XWPFRun rn = title.createRun();
            rn.setText(projectInfo.getProjectName());
            rn.setFontSize(18);
            rn.addBreak(); 
            if (projectInfo.getDescription() != null && !projectInfo.getDescription().isEmpty()) {
                 XWPFRun descRun = title.createRun();
                 descRun.setText(projectInfo.getDescription());
                 descRun.setFontSize(12);
                 descRun.setItalic(true);
                 descRun.addBreak();
            }
            rn.addBreak();
        }
        
        XWPFRun dateRun = title.createRun();
        java.time.ZonedDateTime zdt = result.getStartTime().atZone(java.time.ZoneId.systemDefault());
        dateRun.setText(zdt.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy HH:mm:ss z")));
        dateRun.setFontSize(12);
        
        addPageBreak();
    }
    
    // --- Section 2: Project Info & Artifacts ---
    
    private void createProjectSection(AnalysisResult result) {
        createHeading1("Project Information");
        createProjectInfoTable(result.getProjectInfo());
        addParagraphSpace();

        createHeading2("Project Artifact List");
        List<ProjectNode> allFiles = collectAllFiles(result.getProjectStructure());
        createArtifactListTable(allFiles);
        
        addPageBreak();
    }

    // --- Section 3: Metadata Files ---
    
    private void createMetadataSection(AnalysisResult result) {
        // Collect POM and specific JSON files from Root
        ProjectNode root = result.getProjectStructure();
        List<ProjectNode> metadataFiles = new ArrayList<>();
        
        // Find pom.xml, mule-artifact.json, releaseinfo.json in the whole tree (usually root)
        // Or strictly root? "come in first... after Project Information".
        // Let's scan for them.
        List<String> priorityNames = Arrays.asList("pom.xml", "mule-artifact.json", "releaseinfo.json");
        
        collectFilesByName(root, priorityNames, metadataFiles);
        
        if (!metadataFiles.isEmpty()) {
            createHeading1("Project Configuration Files");
            for(ProjectNode node : metadataFiles) {
                if (processedPaths.contains(node.getAbsolutePath())) continue;
                
                if (node.getName().equals("pom.xml")) {
                    createPomSection(node);
                } else {
                    renderGenericFileWithContent(node, result);
                }
                processedPaths.add(node.getAbsolutePath());
            }
            addPageBreak(); 
        }
    }
    
    // --- Section 4: Property Files ---
    
    private void createPropertyFilesSection(AnalysisResult result) {
         // Find all .properties and .yaml files
         List<ProjectNode> propFiles = new ArrayList<>();
         collectFilesByExtension(result.getProjectStructure(), Arrays.asList(".properties", ".yaml"), propFiles);
         
         if (!propFiles.isEmpty()) {
             createHeading1("Configuration Properties");
             for(ProjectNode node : propFiles) {
                 if (processedPaths.contains(node.getAbsolutePath())) continue;
                 
                 createHeading2(node.getName());
                 addPathSubtitle(node);
                 
                 // Properties Table
                 renderNodeContent(node, result);
                 
                 // Add content if enabled (not typical for props as we have table, but user asked for content based on config)
                 // "for json file pl enable and bring the ocntent." - implicitly implies strictly types.
                 // For now properties table is sufficient unless user wants source.
                 
                 processedPaths.add(node.getAbsolutePath());
             }
             addPageBreak();
         }
    }

    // --- Section 5: Ordered Content ---
    
    private void createOrderedContent(ProjectNode root, AnalysisResult result) {
        
        ProjectNode srcMainMule = findNodeByPath(root, "src/main/mule");
        if (srcMainMule != null) createSection(srcMainMule, "Mule Applications (Flows)", result);
        
        ProjectNode srcMainJava = findNodeByPath(root, "src/main/java");
        boolean includeJavaContent = Boolean.parseBoolean(config.getProperty("document.include.java.content", "false"));
        if (srcMainJava != null) createJavaSection(srcMainJava, includeJavaContent, result);

        ProjectNode srcMainRes = findNodeByPath(root, "src/main/resources");
        if (srcMainRes != null) createSection(srcMainRes, "Configuration Resources", result);
        
        ProjectNode srcTestMunit = findNodeByPath(root, "src/test/munit");
        if (srcTestMunit != null) createSection(srcTestMunit, "MUnit Tests", result);
        
        // Other files not yet processed
        // Traverse root and print anything remaining (e.g. root files not metadata)
        createHeading1("Other Project Files");
        traverseChildren(root, 1, result);
    }
    
    // --- Traversal & Rendering ---
    
    private void createSection(ProjectNode folderNode, String title, AnalysisResult result) {
        createHeading1(title);
        traverseChildren(folderNode, 1, result); // Start sub-level
    }

    private void traverseChildren(ProjectNode node, int depth, AnalysisResult result) {
        List<ProjectNode> children = new ArrayList<>(node.getChildren());
        children.sort(Comparator.comparing(ProjectNode::getName));
        
        for (ProjectNode child : children) {
            String absPath = child.getAbsolutePath();
            if (processedPaths.contains(absPath) || isIgnored(child)) continue;
            
            if (child.getType() == ProjectNode.NodeType.FILE) {
                renderFile(child, depth, result);
                processedPaths.add(absPath);
            } else {
                traverseChildren(child, depth + 1, result); // flatten directories slightly or recurse
            }
        }
    }

    private void renderFile(ProjectNode node, int depth, AnalysisResult result) {
        String title = node.getName();
        if (depth == 1) createHeading2(title);
        else if (depth == 2) createHeading3(title);
        else createHeading4(title);
        
        addPathSubtitle(node);
        
        if (node.getName().endsWith(".xml")) {
             if (node.getMetadata("muleFlows") != null && !((List)node.getMetadata("muleFlows")).isEmpty()) {
                 renderNodeContent(node, result);
             } else {
                 renderGenericFileWithContent(node, result);
             }
        } else {
            renderGenericFileWithContent(node, result);
        }
    }
    
    private void renderGenericFileWithContent(ProjectNode node, AnalysisResult result) {
        // If not a heading created yet (manual calls)
         // Assuming header already created
        
        // Determine if we should print content
        if (shouldIncludeContent(node.getName())) {
            try {
                // Read content
                String content = Files.readString(Paths.get(node.getAbsolutePath()), StandardCharsets.UTF_8);
                // Limit content size?
                if (content.length() > 50000) content = content.substring(0, 50000) + "... (Truncated)";
                
                XWPFParagraph p = document.createParagraph();
                p.setStyle("NoSpacing"); // or Code style
                XWPFRun r = p.createRun();
                r.setFontFamily("Consolas");
                r.setFontSize(9);
                r.setText(content);
                document.createParagraph(); // Space after
            } catch (Exception e) {
                XWPFParagraph p = document.createParagraph();
                p.createRun().setText("Error reading file content: " + e.getMessage());
            }
        }
        
        // Also render properties if any (e.g. log4j properties??)
        renderNodeContent(node, result);
    }
    
    private boolean shouldIncludeContent(String name) {
        String ext = name.contains(".") ? name.substring(name.lastIndexOf(".")) : "";
        switch (ext) {
            case ".java": return Boolean.parseBoolean(config.getProperty("document.include.java.content", "false"));
            case ".json": return Boolean.parseBoolean(config.getProperty("document.include.json.content", "true"));
            case ".dwl": return Boolean.parseBoolean(config.getProperty("document.include.dwl.content", "true"));
            case ".wsdl": return Boolean.parseBoolean(config.getProperty("document.include.wsdl.content", "false"));
            case ".xsd": return Boolean.parseBoolean(config.getProperty("document.include.xsd.content", "false"));
            case ".dtd": return Boolean.parseBoolean(config.getProperty("document.include.dtd.content", "false"));
            case ".xml": return false; // Usually we parse XML, unless generic? User didn't specify generic XML content.
            default: return false;
        }
    }

    private void createPomSection(ProjectNode node) {
        createHeading2(node.getName());
        addPathSubtitle(node);
        
        PomInfo info = (PomInfo) node.getMetadata("pomInfo");
        if (info != null) {
            if (!info.getDependencies().isEmpty()) {
                createHeading3("Dependencies");
                createDependenciesTable(info.getDependencies());
                addParagraphSpace();
            }
            if (!info.getPlugins().isEmpty()) {
                createHeading3("Plugins");
                createPluginsTable(info.getPlugins());
                addParagraphSpace();
            }
            if (info.getProperties() != null && !info.getProperties().isEmpty()) {
                createHeading3("Properties");
                createPomPropertiesTable(info.getProperties());
                addParagraphSpace();
            }
        }
        
        // Content for POM if strict XML content requested? Usually no.
    }
    
    @SuppressWarnings("unchecked")
    private void renderNodeContent(ProjectNode node, AnalysisResult result) {
        // Mule
        if (node.getMetadata("muleFlows") != null) {
            List<FlowInfo> flows = (List<FlowInfo>) node.getMetadata("muleFlows");
            List<ConnectorConfig> configs = (List<ConnectorConfig>) node.getMetadata("muleConfigs");
            
            if (configs != null && !configs.isEmpty()) {
                createHeading3("Connector Configurations");
                createConnectorConfigTable(configs);
                addParagraphSpace();
            }
            
            if (flows != null && !flows.isEmpty()) {
                createHeading3("Flows & Sub-Flows");
                createFlowsTable(flows);
                addParagraphSpace();
            }
        }
        
        // Properties
        if (node.getName().endsWith(".properties") || node.getName().endsWith(".yaml")) {
             List<PropertyInfo> props = new ArrayList<>();
             String rel = node.getRelativePath();
             for (PropertyInfo p : result.getProperties()) {
                if (p.getEnvironmentValues().containsKey(rel)) {
                    props.add(p);
                }
             }
             if (!props.isEmpty()) {
                 createPropertiesTable(props, rel);
                 addParagraphSpace();
             }
        }
    }
    
    private void createJavaSection(ProjectNode folderNode, boolean includeContent, AnalysisResult result) {
        createHeading1("Java Sources");
        traverseJavaChildren(folderNode, includeContent);
    }
    
    private void traverseJavaChildren(ProjectNode node, boolean includeContent) {
        if (node.getType() == ProjectNode.NodeType.FILE && node.getName().endsWith(".java")) {
            if (processedPaths.contains(node.getAbsolutePath())) return;
            
            createHeading2(node.getName());
            addPathSubtitle(node);
            
            if (includeContent) {
                 renderGenericFileWithContent(node, null);
            }
            processedPaths.add(node.getAbsolutePath());
        } else if (node.getType() == ProjectNode.NodeType.DIRECTORY) {
            for (ProjectNode child : node.getChildren()) {
                traverseJavaChildren(child, includeContent);
            }
        }
    }

    // --- Table Generators ---

    private void createConnectorConfigTable(List<ConnectorConfig> configs) {
        XWPFTable table = createFixedTable(configs.size() + 1, 3, new int[]{30, 25, 45});
        fillHeader(table, new String[]{"Config Name", "Type", "Attributes"});
        int r = 1;
        for (ConnectorConfig cfg : configs) {
            XWPFTableRow row = table.getRow(r++);
            fillCellWithWrapping(row.getCell(0), cfg.getName(), "30%");
            fillCellWithWrapping(row.getCell(1), cfg.getType(), "25%");
            fillCellWithWrapping(row.getCell(2), formatMap(cfg.getAttributes()), "45%");
        }
    }
    
    private void createFlowsTable(List<FlowInfo> flows) {
         XWPFTable table = createFixedTable(flows.size() + 1, 3, new int[]{30, 15, 55});
         fillHeader(table, new String[]{"Flow Name", "Type", "Components Summary"});
         
         int r = 1;
         for(FlowInfo f : flows) {
             XWPFTableRow row = table.getRow(r++);
             fillCellWithWrapping(row.getCell(0), f.getName(), "30%");
             fillCellWithWrapping(row.getCell(1), f.getType(), "15%");
             StringBuilder summary = new StringBuilder();
             if (f.getComponents() != null) {
                 for(ComponentInfo c : f.getComponents()) {
                     if (summary.length() > 0) summary.append(", ");
                     summary.append(c.getType());
                 }
             }
             fillCellWithWrapping(row.getCell(2), summary.toString(), "55%");
         }
         
         addParagraphSpace();
         
         for(FlowInfo f : flows) {
             if (!f.getComponents().isEmpty()) {
                 // Format: "FlowName - Type"
                 String title = f.getName() + " - " + capitalize(f.getType());
                 createHeading4(title);
                 createComponentsTable(f.getComponents());
                 addParagraphSpace();
             }
         }
    }
    
    private void createComponentsTable(List<ComponentInfo> components) {
        XWPFTable table = createFixedTable(components.size() + 1, 3, new int[]{25, 25, 50});
        fillHeader(table, new String[]{"Type", "Name", "Details"});
        int r = 1;
        for(ComponentInfo c : components) {
            XWPFTableRow row = table.getRow(r++);
            fillCellWithWrapping(row.getCell(0), c.getType(), "25%");
            fillCellWithWrapping(row.getCell(1), c.getName(), "25%");
            fillCellWithWrapping(row.getCell(2), formatMap(c.getAttributes()), "50%");
        }
    }
    
    // ... Dependencies/Plugins/Properties tables (same as before) ...
    private void createDependenciesTable(List<PomInfo.DependencyInfo> deps) {
        XWPFTable table = createFixedTable(deps.size() + 1, 3, new int[]{40, 40, 20});
        fillHeader(table, new String[]{"GroupId", "ArtifactId", "Version"});
        int r = 1;
        for(PomInfo.DependencyInfo d : deps) {
            XWPFTableRow row = table.getRow(r++);
            fillCellWithWrapping(row.getCell(0), d.getGroupId(), "40%");
            fillCellWithWrapping(row.getCell(1), d.getArtifactId(), "40%");
            fillCellWithWrapping(row.getCell(2), d.getVersion(), "20%");
        }
    }
    private void createPluginsTable(List<PomInfo.PluginInfo> plugins) {
        XWPFTable table = createFixedTable(plugins.size() + 1, 3, new int[]{40, 40, 20});
        fillHeader(table, new String[]{"GroupId", "ArtifactId", "Version"});
        int r = 1;
        for(PomInfo.PluginInfo d : plugins) {
            XWPFTableRow row = table.getRow(r++);
            fillCellWithWrapping(row.getCell(0), d.getGroupId(), "40%");
            fillCellWithWrapping(row.getCell(1), d.getArtifactId(), "40%");
            fillCellWithWrapping(row.getCell(2), d.getVersion(), "20%");
        }
    }
    private void createPomPropertiesTable(Map<String, String> props) {
        XWPFTable table = createFixedTable(props.size() + 1, 2, new int[]{40, 60});
        fillHeader(table, new String[]{"Key", "Value"});
        int r = 1;
        for(Map.Entry<String, String> e : props.entrySet()) {
            XWPFTableRow row = table.getRow(r++);
            fillCellWithWrapping(row.getCell(0), e.getKey(), "40%");
            fillCellWithWrapping(row.getCell(1), e.getValue(), "60%");
        }
    }
    private void createPropertiesTable(List<PropertyInfo> props, String filePath) {
        XWPFTable table = createFixedTable(props.size() + 1, 2, new int[]{40, 60});
        fillHeader(table, new String[]{"Key", "Value"});
        int r = 1;
        for(PropertyInfo p : props) {
            XWPFTableRow row = table.getRow(r++);
            fillCellWithWrapping(row.getCell(0), p.getKey(), "40%");
            String val = p.getEnvironmentValues().get(filePath);
            fillCellWithWrapping(row.getCell(1), val, "60%");
        }
    }

    // --- Utility Helpers ---
    
    private void addParagraphSpace() {
        document.createParagraph(); // Just an empty paragraph for spacing
    }
    
    private void addPageBreak() {
        document.createParagraph().createRun().addBreak(BreakType.PAGE);
    }
    
    private void addPathSubtitle(ProjectNode node) {
        XWPFParagraph pKey = document.createParagraph();
        XWPFRun rKey = pKey.createRun();
        rKey.setText("Path: " + node.getRelativePath());
        rKey.setItalic(true);
        rKey.setFontSize(9);
    }

    private String formatMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "";
        
        boolean includeDocId = Boolean.parseBoolean(config.getProperty("analyzer.mule.component.attribute.doc_id.enabled", "false"));
        
        StringBuilder sb = new StringBuilder();
        for(Map.Entry<String, String> e : map.entrySet()) {
            if (!includeDocId && e.getKey().equals("doc:id")) continue;
            
            if (sb.length() > 0) sb.append("\n");
            sb.append(e.getKey()).append("=").append(e.getValue());
        }
        return sb.toString();
    }
    
    // Recursively collect files by name
    private void collectFilesByName(ProjectNode node, List<String> names, List<ProjectNode> result) {
        if (node.getType() == ProjectNode.NodeType.FILE) {
             if (names.contains(node.getName())) result.add(node);
        } else {
            for(ProjectNode child : node.getChildren()) collectFilesByName(child, names, result);
        }
    }
    
    private void collectFilesByExtension(ProjectNode node, List<String> exts, List<ProjectNode> result) {
        if (node.getType() == ProjectNode.NodeType.FILE) {
             for(String ext : exts) {
                 if (node.getName().endsWith(ext)) {
                     result.add(node);
                     break;
                 }
             }
        } else {
            for(ProjectNode child : node.getChildren()) collectFilesByExtension(child, exts, result);
        }
    }
    
    private void createProjectInfoTable(ProjectInfo info) {
        if (info == null) return;
        XWPFTable table = createFixedTable(5, 2, new int[]{30, 70});
        int r = 0;
        fillRow(table.getRow(r++), "Project Name", info.getProjectName());
        fillRow(table.getRow(r++), "Version", info.getVersion());
        fillRow(table.getRow(r++), "Runtime", info.getMuleVersion()); 
        fillRow(table.getRow(r++), "Project Path", info.getProjectPath());
        cleanTable(table, r);
    }
    
    private void createArtifactListTable(List<ProjectNode> files) {
        if (files.isEmpty()) return;
        XWPFTable table = createFixedTable(files.size() + 1, 2, new int[]{40, 60});
        fillHeader(table, new String[]{"File Name", "Relative Path"});
        int r = 1;
        for (ProjectNode node : files) {
             if (isIgnored(node)) continue;
             XWPFTableRow row = table.getRow(r++);
             fillCellWithWrapping(row.getCell(0), node.getName(), "40%");
             fillCellWithWrapping(row.getCell(1), node.getRelativePath(), "60%");
        }
    }
    
    private List<ProjectNode> collectAllFiles(ProjectNode node) {
        List<ProjectNode> files = new ArrayList<>();
        if (node == null) return files;
        if (node.getType() == ProjectNode.NodeType.FILE) files.add(node);
        else for (ProjectNode child : node.getChildren()) files.addAll(collectAllFiles(child));
        return files;
    }
    
    // ... Identical helpers findNodeByPath, findNodeRecursive, isIgnored, createFixedTable, etc...
    private ProjectNode findNodeByPath(ProjectNode root, String relativePath) {
        String target = relativePath.replace("/", "\\");
        return findNodeRecursive(root, target);
    }
    
    private ProjectNode findNodeRecursive(ProjectNode node, String targetSuffix) {
        if (node.getRelativePath().replace("/", "\\").endsWith(targetSuffix)) return node;
        if (node.getType() == ProjectNode.NodeType.DIRECTORY) {
            for (ProjectNode child : node.getChildren()) {
                ProjectNode found = findNodeRecursive(child, targetSuffix);
                if (found != null) return found;
            }
        }
        return null;
    }
    
    private boolean isIgnored(ProjectNode node) { return node.getName().startsWith("."); }
    private void createHeading1(String text) { createHeading(text, "Heading1", 0, 16); }
    private void createHeading2(String text) { createHeading(text, "Heading2", 1, 14); }
    private void createHeading3(String text) { createHeading(text, "Heading3", 2, 12); }
    private void createHeading4(String text) { createHeading(text, "Heading4", 3, 11); }
    private void createHeading(String text, String styleId, int outlineLevel, int size) {
        XWPFParagraph p = document.createParagraph();
        p.setStyle(styleId);
        setOutlineLevel(p, outlineLevel);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(true);
        r.setFontSize(size);
    }
    private void setOutlineLevel(XWPFParagraph p, int level) {
        CTPPr ppr = p.getCTP().getPPr();
        if (ppr == null) ppr = p.getCTP().addNewPPr();
        CTDecimalNumber val = ppr.getOutlineLvl();
        if (val == null) val = ppr.addNewOutlineLvl();
        val.setVal(BigInteger.valueOf(level));
    }
    private XWPFTable createFixedTable(int rows, int cols, int[] colPercents) {
        XWPFTable table = document.createTable(rows, cols);
        table.setWidth("100%");
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null) tblPr = table.getCTTbl().addNewTblPr();
        CTTblLayoutType layout = tblPr.isSetTblLayout() ? tblPr.getTblLayout() : tblPr.addNewTblLayout();
        layout.setType(STTblLayoutType.FIXED);
        CTTblGrid grid = table.getCTTbl().getTblGrid();
        if (grid == null) grid = table.getCTTbl().addNewTblGrid();
        for(int pct : colPercents) grid.addNewGridCol().setW(BigInteger.valueOf( (long)(9000 * (pct/100.0)) ));
        return table;
    }
    private void fillHeader(XWPFTable table, String[] headers) {
        XWPFTableRow row = table.getRow(0);
        for(int i=0; i<headers.length; i++) {
             XWPFTableCell cell = row.getCell(i);
             if (cell == null) cell = row.createCell();
             cell.setColor("663399");
             XWPFRun r = cell.getParagraphs().get(0).createRun();
             r.setText(headers[i]);
             r.setBold(true);
             r.setColor("FFFFFF");
        }
    }
    private void fillRow(XWPFTableRow row, String k, String v) {
        fillCellWithWrapping(row.getCell(0), k, "30%");
        fillCellWithWrapping(row.getCell(1), v, "70%");
    }
    private void cleanTable(XWPFTable table, int rowsUsed) {
        while(table.getRows().size() > rowsUsed) table.removeRow(table.getRows().size()-1);
    }
    private void fillCellWithWrapping(XWPFTableCell cell, String text, String w) {
        if (text == null) text = "";
        String wrapped = text.replaceAll("([\\.\\/\\\\:_\\-@]{1})", "$1\u200B").replaceAll("(.{15})", "$1\u200B").replaceAll("\u200B\u200B", "\u200B");
        cell.setText(wrapped);
        if (w != null) cell.setWidth(w);
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    private Path getOutputPath(AnalysisResult result) {
         String dir = config.getProperty("framework.output.directory", "./output");
         String name = "design-doc";
         if (result.getProjectInfo() != null && result.getProjectInfo().getProjectName() != null)
             name = result.getProjectInfo().getProjectName().replaceAll("[^a-zA-Z0-9-_]", "_");
         String ts = result.getStartTime().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
         return Paths.get(dir, String.format("%s_%s.docx", name, ts));
    }
}
