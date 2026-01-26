package com.raks.raksanalyzer.generator.word;
import com.raks.raksanalyzer.core.config.ConfigurationManager;
import com.raks.raksanalyzer.domain.model.*;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Comparator;
import java.util.stream.Collectors;
import com.raks.raksanalyzer.generator.DiagramGenerator;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashSet;
public class WordGenerator {
    private static final Logger logger = LoggerFactory.getLogger(WordGenerator.class);
    private final ConfigurationManager config = ConfigurationManager.getInstance();
    private XWPFDocument document;
    private Set<String> processedFiles;
    private List<Integer> sectionCounters;
    public Path generate(AnalysisResult result) throws IOException {
        logger.info("Generating Mule Word document: {}", result.getAnalysisId());
        processedFiles = new HashSet<>();
        sectionCounters = new ArrayList<>();
        sectionCounters.add(0); 
        try {
            document = loadTemplate();
            createCoverPage(result);
            if (getBooleanConfig("mule.word.include.toc", true)) {
                createTableOfContents();
            }
            if (getBooleanConfig("mule.word.include.project.details", true)) {
                createProjectInformationSection(result);
            }
            if (getBooleanConfig("mule.word.include.flow.diagrams.placeholder", true)) {
                createFlowDiagramSection(result);
            }
            if (getBooleanConfig("mule.word.include.global.config", true)) {
                createGlobalConfigurationSection(result);
            }
            if (getBooleanConfig("mule.word.include.connector.configs", true)) {
                createConnectorConfigurationsSection(result);
            }
            if (getBooleanConfig("mule.word.include.mule.flows", true)) {
                createMuleFlowsSection(result);
            }
            if (getBooleanConfig("mule.word.include.mule.subflows", true)) {
                createMuleSubFlowsSection(result);
            }
            if (getBooleanConfig("mule.word.include.dataweave.files", true)) {
                createDataWeaveFilesSection(result);
            }
            if (getBooleanConfig("mule.word.include.other.resources", true)) {
                createOtherResourcesSection(result);
            }
            if (getBooleanConfig("mule.word.include.references.placeholder", true)) {
                createReferencesPlaceholder();
            }
            createFooter();
            document.enforceUpdateFields();
            Path outputPath = getOutputPath(result);
            if (outputPath.getParent() != null && !Files.exists(outputPath.getParent())) {
                Files.createDirectories(outputPath.getParent());
            }
            try (FileOutputStream out = new FileOutputStream(outputPath.toFile())) {
                document.write(out);
            }
            document.close();
            logger.info("Word document generated successfully: {}", outputPath);
            return outputPath;
        } catch (Throwable e) {
            logger.error("Error generating Word document", e);
            throw new IOException("Failed to generate Word document", e);
        }
    }
    private void createFooter() {
        XWPFFooter footer = document.createFooter(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
        XWPFTable table = footer.createTable(1, 3);
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null) tblPr = table.getCTTbl().addNewTblPr();
        CTTblBorders borders = tblPr.isSetTblBorders() ? tblPr.getTblBorders() : tblPr.addNewTblBorders();
        borders.addNewBottom().setVal(STBorder.NONE);
        borders.addNewTop().setVal(STBorder.NONE);
        borders.addNewLeft().setVal(STBorder.NONE);
        borders.addNewRight().setVal(STBorder.NONE);
        borders.addNewInsideH().setVal(STBorder.NONE);
        borders.addNewInsideV().setVal(STBorder.NONE);
        CTTblWidth width = tblPr.isSetTblW() ? tblPr.getTblW() : tblPr.addNewTblW();
        width.setType(STTblWidth.PCT);
        width.setW(BigInteger.valueOf(5000)); 
        XWPFParagraph pLeft = table.getRow(0).getCell(0).getParagraphs().get(0);
        pLeft.setAlignment(ParagraphAlignment.LEFT);
        XWPFParagraph pCenter = table.getRow(0).getCell(1).getParagraphs().get(0);
        pCenter.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun rCenter = pCenter.createRun();
        rCenter.setText(config.getProperty("document.footer.text", "RaksAnalyzer ApiGuard Tool"));
        rCenter.setColor("808080"); 
        rCenter.setFontSize(9);
        XWPFParagraph pRight = table.getRow(0).getCell(2).getParagraphs().get(0);
        pRight.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun rNum = pRight.createRun();
        rNum.setText("Page ");
        rNum.setFontSize(9);
        pRight.getCTP().addNewFldSimple().setInstr("PAGE");
        rNum = pRight.createRun();
        rNum.setText(" of ");
        rNum.setFontSize(9);
        pRight.getCTP().addNewFldSimple().setInstr("NUMPAGES");
    }
    private XWPFDocument loadTemplate() throws IOException {
        String templatePath = config.getProperty("mule.word.template.path", 
            "template/MuleSoft Project Documentation Template.docx");
        try {
            Path templateFile = Paths.get(templatePath);
            if (Files.exists(templateFile)) {
                logger.info("Loading Word template from: {}", templateFile);
                try (FileInputStream fis = new FileInputStream(templateFile.toFile())) {
                    return new XWPFDocument(fis);
                }
            }
            ClassLoader ctxLoader = Thread.currentThread().getContextClassLoader();
            InputStream templateStream = ctxLoader.getResourceAsStream(templatePath);
            if (templateStream == null) {
                 templateStream = getClass().getClassLoader().getResourceAsStream(templatePath);
            }
            if (templateStream == null) {
                 templateStream = getClass().getResourceAsStream("/" + templatePath);
            }
            if (templateStream != null) {
                logger.info("Loaded Word template from classpath: {}", templatePath);
                return new XWPFDocument(templateStream);
            }
            logger.warn("Template NOT found in file system or classpath. Path checked: '{}'. Creating empty document.", templatePath);
            return new XWPFDocument();
        } catch (Exception e) {
            logger.warn("Failed to load template, creating new document. Error: {}", e.getMessage());
            return new XWPFDocument();
        }
    }
    private void createCoverPage(AnalysisResult result) {
        ProjectInfo projectInfo = result.getProjectInfo();
        XWPFParagraph title = document.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleRun = title.createRun();
        titleRun.setText(config.getProperty("mule.common.cover.title", "Project Analysis Report"));
        titleRun.setBold(true);
        titleRun.setFontSize(24);
        titleRun.addBreak();
        titleRun.addBreak();
        if (projectInfo != null) {
            XWPFRun projectRun = title.createRun();
            projectRun.setText(projectInfo.getProjectName());
            projectRun.setFontSize(18);
            projectRun.addBreak();
        }
        String subtitle = config.getProperty("mule.common.cover.subtitle", "");
        if (!subtitle.isEmpty()) {
            XWPFRun subtitleRun = title.createRun();
            subtitleRun.setText(subtitle);
            subtitleRun.setFontSize(14);
            subtitleRun.addBreak();
        }
        String description = config.getProperty("mule.common.cover.description", 
            "Comprehensive analysis and documentation");
        XWPFRun descRun = title.createRun();
        descRun.setText(description);
        descRun.setFontSize(12);
        descRun.setItalic(true);
        descRun.addBreak();
        descRun.addBreak();
        XWPFRun dateRun = title.createRun();
        java.time.ZonedDateTime zdt = result.getStartTime().atZone(java.time.ZoneId.systemDefault());
        dateRun.setText(zdt.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy HH:mm:ss z")));
        dateRun.setFontSize(12);
        addPageBreak();
    }
    private void createTableOfContents() {
        XWPFParagraph tocTitle = document.createParagraph();
        tocTitle.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = tocTitle.createRun();
        run.setText("Table of Contents");
        run.setBold(true);
        run.setFontSize(18);
        addParagraphSpace();
        XWPFParagraph tocPara = document.createParagraph();
        CTP ctP = tocPara.getCTP();
        CTSimpleField toc = ctP.addNewFldSimple();
        toc.setInstr("TOC \\o \"1-3\" \\h \\z \\u");
        addParagraphSpace();
        addPageBreak();
    }
    private void createProjectInformationSection(AnalysisResult result) {
        String sectionName = config.getProperty("mule.word.section.project.info", "Project Information");
        createHeading1(sectionName);
        if (getBooleanConfig("mule.word.include.project.details", true)) {
            String detailsName = config.getProperty("mule.word.section.project.details", "Project Details");
            createHeading2(detailsName);
            createProjectDetailsTable(result);
            addParagraphSpace();
        }
        if (getBooleanConfig("mule.word.include.pom.details", true)) {
            ProjectNode pomNode = findFileByName(result.getProjectStructure(), "pom.xml");
            if (pomNode != null) {
                String pomName = config.getProperty("mule.word.section.pom.details", "POM Details");
                createHeading2(pomName);
                addPathSubtitle(pomNode);
                PomInfo pomInfo = (PomInfo) pomNode.getMetadata("pomInfo");
                if (pomInfo != null) {
                    if (getBooleanConfig("mule.word.include.pom.basic.info", true)) {
                        String basicInfoName = config.getProperty("mule.word.subsection.pom.basic.info", "Basic Information");
                        createHeading3(basicInfoName);
                        createPomBasicInfoTable(pomInfo);
                        addParagraphSpace();
                    }
                    if (getBooleanConfig("mule.word.include.pom.properties", true) && 
                        pomInfo.getProperties() != null && !pomInfo.getProperties().isEmpty()) {
                        String propsName = config.getProperty("mule.word.subsection.pom.properties", "Properties");
                        createHeading3(propsName);
                        createPomPropertiesTable(pomInfo.getProperties());
                        addParagraphSpace();
                    }
                    if (getBooleanConfig("mule.word.include.pom.plugins", true) && !pomInfo.getPlugins().isEmpty()) {
                        String pluginsName = config.getProperty("mule.word.subsection.pom.plugins", "Plugins");
                        createHeading3(pluginsName);
                        createPluginsTable(pomInfo.getPlugins());
                        addParagraphSpace();
                    }
                    if (getBooleanConfig("mule.word.include.pom.dependencies", true) && !pomInfo.getDependencies().isEmpty()) {
                        String depsName = config.getProperty("mule.word.subsection.pom.dependencies", "Dependencies");
                        createHeading3(depsName);
                        createDependenciesTable(pomInfo.getDependencies());
                        addParagraphSpace();
                    }
                }
                processedFiles.add(pomNode.getAbsolutePath());
            }
        }
        if (getBooleanConfig("mule.word.include.mule.artifact.json", true)) {
            ProjectNode muleArtifact = findFileByName(result.getProjectStructure(), "mule-artifact.json");
            if (muleArtifact != null) {
                createHeading2("Mule-artifact.json");
                addPathSubtitle(muleArtifact);
                renderFileContentInBox(muleArtifact);
                addParagraphSpace();
                processedFiles.add(muleArtifact.getAbsolutePath());
            }
        }
        if (getBooleanConfig("mule.word.include.release.info.json", true)) {
            ProjectNode releaseInfo = findFileByName(result.getProjectStructure(), "releaseinfo.json");
            if (releaseInfo != null) {
                createHeading2("Release-info.json");
                addPathSubtitle(releaseInfo);
                renderFileContentInBox(releaseInfo);
                addParagraphSpace();
                processedFiles.add(releaseInfo.getAbsolutePath());
            }
        }
        addPageBreak();
    }
    private void createFlowDiagramSection(AnalysisResult result) {
        boolean genStructure = Boolean.parseBoolean(config.getProperty("mule.generate.flow.structure", "true"));
        boolean genDiagrams = Boolean.parseBoolean(config.getProperty("mule.generate.flow.diagrams", "true"));
        if (!genStructure && !genDiagrams) return;
        String sectionName = config.getProperty("mule.word.section.flow.diagrams", "Flow Diagrams");
        createHeading1(sectionName);
        List<FlowWithSource> allFlows = new ArrayList<>();
        collectFlowsByType(result.getProjectStructure(), "flow", allFlows);
        collectFlowsByType(result.getProjectStructure(), "sub-flow", allFlows);
        allFlows.sort(Comparator.comparing(f -> f.flow.getName()));
        if (allFlows.isEmpty()) {
            XWPFParagraph p = document.createParagraph();
            XWPFRun r = p.createRun();
            r.setText("No flows found in the project.");
            addParagraphSpace();
            addPageBreak(); 
            return;
        }
        Map<String, FlowInfo> allFlowsMap = new HashMap<>();
        for (FlowWithSource item : allFlows) {
            allFlowsMap.put(item.flow.getName(), item.flow);
        }
        if (genStructure) {
            createHeading2("Mule Project Flow Integration");
            for (FlowWithSource item : allFlows) {
                createHeading3(item.flow.getName());
                if (item.sourceFile != null) {
                    XWPFParagraph pathPara = document.createParagraph();
                    XWPFRun pathRun = pathPara.createRun();
                    pathRun.setItalic(true);
                    pathRun.setFontSize(9);
                    pathRun.setColor("808080"); 
                    pathRun.setText("Path: " + item.sourceFile.getRelativePath());
                }
                try {
                    int maxDepth = Integer.parseInt(config.getProperty("word.diagram.nested.component.max.depth", "5"));
                    byte[] imageBytes = DiagramGenerator.generatePlantUmlImage(item.flow, maxDepth, true, true, allFlowsMap);
                    if (imageBytes != null && imageBytes.length > 0) {
                        XWPFParagraph p = document.createParagraph();
                        XWPFRun r = p.createRun();
                         BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
                         int width = img.getWidth();
                         int height = img.getHeight();
                         int widthEMU = width * 9525;
                         int heightEMU = height * 9525;
                         int maxWidthEMU = 5486400;
                         int maxHeightEMU = 8229600;
                         double scale = 1.0;
                         if (widthEMU > maxWidthEMU) {
                             scale = (double) maxWidthEMU / widthEMU;
                         }
                         int scaledHeightEMU = (int) (heightEMU * scale);
                         if (scaledHeightEMU > maxHeightEMU) {
                             scale = scale * ((double) maxHeightEMU / scaledHeightEMU);
                         }
                         widthEMU = (int) (widthEMU * scale);
                         heightEMU = (int) (heightEMU * scale);
                         try (InputStream is = new ByteArrayInputStream(imageBytes)) {
                             r.addPicture(is, org.apache.poi.xwpf.usermodel.XWPFDocument.PICTURE_TYPE_PNG, "flow.png", widthEMU, heightEMU);
                         }
                    } else {
                        XWPFParagraph p = document.createParagraph();
                        XWPFRun r = p.createRun();
                        r.setText("(No integration points available)");
                    }
                } catch (Exception e) {
                    logger.error("Failed to generate integration diagram for " + item.flow.getName(), e);
                    XWPFParagraph p = document.createParagraph();
                    XWPFRun r = p.createRun();
                    r.setText("Error generating integration diagram: " + e.getMessage());
                }
                addParagraphSpace();
            }
        }
        if (genDiagrams) {
            if (genStructure) addPageBreak(); 
            createHeading2(config.getProperty("mule.word.section.flow.diagram", "Mule Project Flow Diagram"));
            for (FlowWithSource item : allFlows) {
                createHeading3(item.flow.getName());
                if (item.sourceFile != null) {
                    XWPFParagraph pathPara = document.createParagraph();
                    XWPFRun pathRun = pathPara.createRun();
                    pathRun.setItalic(true);
                    pathRun.setFontSize(9);
                    pathRun.setColor("808080"); 
                    pathRun.setText("Path: " + item.sourceFile.getRelativePath());
                }
                try {
                    int maxDepth = Integer.parseInt(config.getProperty("word.diagram.nested.component.max.depth", "5"));
                    boolean useFullNames = Boolean.parseBoolean(config.getProperty("word.diagram.element.fullname", "true"));
                    byte[] imageBytes = DiagramGenerator.generatePlantUmlImage(item.flow, maxDepth, useFullNames, false);
                    if (imageBytes != null && imageBytes.length > 0) {
                        XWPFParagraph p = document.createParagraph();
                        XWPFRun r = p.createRun();
                         BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
                         int width = img.getWidth();
                         int height = img.getHeight();
                         int widthEMU = width * 9525;
                         int heightEMU = height * 9525;
                         int maxWidthEMU = 5486400;
                         int maxHeightEMU = 8229600;
                         double scale = 1.0;
                         if (widthEMU > maxWidthEMU) {
                             scale = (double) maxWidthEMU / widthEMU;
                         }
                         int scaledHeightEMU = (int) (heightEMU * scale);
                         if (scaledHeightEMU > maxHeightEMU) {
                             scale = scale * ((double) maxHeightEMU / scaledHeightEMU);
                         }
                         widthEMU = (int) (widthEMU * scale);
                         heightEMU = (int) (heightEMU * scale);
                         try (InputStream is = new ByteArrayInputStream(imageBytes)) {
                             r.addPicture(is, org.apache.poi.xwpf.usermodel.XWPFDocument.PICTURE_TYPE_PNG, "flow.png", widthEMU, heightEMU);
                         }
                    } else {
                        XWPFParagraph p = document.createParagraph();
                        XWPFRun r = p.createRun();
                        r.setText("(No diagram available)");
                    }
                } catch (Exception e) {
                    logger.error("Failed to generate diagram for " + item.flow.getName(), e);
                    XWPFParagraph p = document.createParagraph();
                    XWPFRun r = p.createRun();
                    r.setText("Error generating diagram: " + e.getMessage());
                }
                addParagraphSpace();
            }
        }
        addPageBreak();
    }
    private void createGlobalConfigurationSection(AnalysisResult result) {
        String sectionName = config.getProperty("mule.word.section.global.config", "Global Configuration");
        createHeading1(sectionName);
        String excludedFilesStr = config.getProperty("mule.word.global.config.exclude.files", "");
        final Set<String> excludedFiles = new HashSet<>();
        if (!excludedFilesStr.isEmpty()) {
            Arrays.stream(excludedFilesStr.split(","))
                .map(String::trim)
                .forEach(excludedFiles::add);
        }
        List<ProjectNode> propFiles = new ArrayList<>();
        collectFilesByExtension(result.getProjectStructure(), Arrays.asList(".properties"), propFiles);
        propFiles = propFiles.stream()
            .filter(f -> !excludedFiles.contains(f.getName()))
            .collect(Collectors.toList());
        if (!excludedFiles.isEmpty()) {
            logger.debug("Excluding properties files from Word document: {}", excludedFiles);
        }
        propFiles.sort(Comparator.comparing(ProjectNode::getName));
        for (ProjectNode propFile : propFiles) {
            createHeading2(propFile.getName());
            addPathSubtitle(propFile);
            List<PropertyInfo> props = getPropertiesForFile(result, propFile.getRelativePath());
            if (!props.isEmpty()) {
                createPropertiesTable(props, propFile.getRelativePath());
            }
            addParagraphSpace();
            processedFiles.add(propFile.getAbsolutePath());
        }
        addPageBreak();
    }
    private void createConnectorConfigurationsSection(AnalysisResult result) {
        String sectionName = config.getProperty("mule.word.section.connector.configs", "Connector Configurations");
        createHeading1(sectionName);
        List<ConnectorConfigWithSource> allConfigs = new ArrayList<>();
        collectAllConnectorConfigs(result.getProjectStructure(), allConfigs);
        allConfigs.sort(Comparator.comparing(c -> c.config.getName()));
        for (ConnectorConfigWithSource item : allConfigs) {
            createHeading2(item.config.getName());
            addPathSubtitle(item.sourceFile);
            createConnectorConfigAttributesTable(item.config);
            addParagraphSpace();
            if (item.config.getNestedComponents() != null && !item.config.getNestedComponents().isEmpty()) {
                renderComponentsHierarchically(item.config.getNestedComponents());
            }
        }
        addPageBreak();
    }
    private void createMuleFlowsSection(AnalysisResult result) {
        String sectionName = config.getProperty("mule.word.section.mule.flows", "Mule Flows");
        createHeading1(sectionName);
        List<FlowWithSource> allFlows = new ArrayList<>();
        collectFlowsByType(result.getProjectStructure(), "flow", allFlows);
        for (FlowWithSource item : allFlows) {
            createHeading2(item.flow.getName());
            addPathSubtitle(item.sourceFile);
            renderComponentsHierarchically(item.flow.getComponents());
        }
        addPageBreak();
    }
    private void renderComponentsHierarchically(List<ComponentInfo> components) {
        int maxDepth = Integer.parseInt(config.getProperty("word.nested.component.max.depth", "3"));
        for (ComponentInfo component : components) {
            int depth = 0;
            if (component.getAttributes() != null && component.getAttributes().containsKey("_depth")) {
                try {
                    depth = Integer.parseInt(component.getAttributes().get("_depth"));
                } catch (NumberFormatException e) {
                }
            }
            if (depth > maxDepth) {
                continue;
            }
            String componentTitle = component.getType();
            if (depth == 0) {
                createHeading3(componentTitle);
            } else {
                createHeading4(componentTitle, depth);
            }
            createComponentAttributesTable(component);
            addParagraphSpace();
        }
    }
    private void createHeading4(String text, int depth) {
        int level = 2 + depth; 
        incrementSection(level);
        String numberedText = getSectionNumber() + " " + text;
        int fontSize = Math.max(10, 12 - depth);
        createHeading(numberedText, "Heading4", level, fontSize, true);
    }
    private void createMuleSubFlowsSection(AnalysisResult result) {
        String sectionName = config.getProperty("mule.word.section.mule.subflows", "Mule Sub-flows");
        createHeading1(sectionName);
        List<FlowWithSource> allSubFlows = new ArrayList<>();
        collectFlowsByType(result.getProjectStructure(), "sub-flow", allSubFlows);
        for (FlowWithSource item : allSubFlows) {
            createHeading2(item.flow.getName());
            addPathSubtitle(item.sourceFile);
            for (ComponentInfo component : item.flow.getComponents()) {
                String componentTitle = component.getType();
                createHeading3(componentTitle);
                createComponentAttributesTable(component);
                addParagraphSpace();
            }
        }
        addPageBreak();
    }
    private void createDataWeaveFilesSection(AnalysisResult result) {
        String sectionName = config.getProperty("mule.word.section.dataweave.files", "Mule DataWeave Files");
        createHeading1(sectionName);
        List<ProjectNode> dwlFiles = new ArrayList<>();
        collectFilesByExtension(result.getProjectStructure(), Arrays.asList(".dwl"), dwlFiles);
        dwlFiles.sort(Comparator.comparing(ProjectNode::getName));
        for (ProjectNode dwlFile : dwlFiles) {
            createHeading2(dwlFile.getName());
            addPathSubtitle(dwlFile);
            renderFileContentInBox(dwlFile);
            addParagraphSpace();
            processedFiles.add(dwlFile.getAbsolutePath());
        }
        addPageBreak();
    }
    private void createOtherResourcesSection(AnalysisResult result) {
        String sectionName = config.getProperty("mule.word.section.other.resources", "Other Resources");
        createHeading1(sectionName);
        List<ProjectNode> otherFiles = collectOtherFiles(result.getProjectStructure());
        if (!otherFiles.isEmpty()) {
            String headerName = config.getProperty("mule.word.table.header.file.name", "File Name");
            String headerPath = config.getProperty("mule.word.table.header.relative.path", "Relative Path");
            XWPFTable table = createFixedTable(otherFiles.size() + 1, 2, new int[]{40, 60});
            fillHeader(table, new String[]{headerName, headerPath});
            int row = 1;
            for (ProjectNode file : otherFiles) {
                XWPFTableRow tableRow = table.getRow(row++);
                fillCellWithWrapping(tableRow.getCell(0), file.getName(), "40%");
                fillCellWithWrapping(tableRow.getCell(1), file.getRelativePath(), "60%");
            }
        }
        addPageBreak();
    }
    private void createReferencesPlaceholder() {
        String sectionName = config.getProperty("mule.word.section.references", "References");
        createHeading1(sectionName);
        XWPFParagraph p = document.createParagraph();
        XWPFRun r = p.createRun();
        r.setText("References section is placeholder for any reference details team requires. It can have team playbook reference details or any other information for project.");
        r.setItalic(true);
        r.setColor("808080");
        addParagraphSpace();
        XWPFParagraph ref1 = document.createParagraph();
        ref1.createRun().setText("• MuleSoft Documentation");
        XWPFParagraph ref2 = document.createParagraph();
        ref2.createRun().setText("• MuleSoft Anypoint Platform");
        XWPFParagraph ref3 = document.createParagraph();
        ref3.createRun().setText("• MuleSoft Knowledge Base");
    }
    private static class ConnectorConfigWithSource {
        ConnectorConfig config;
        ProjectNode sourceFile;
        ConnectorConfigWithSource(ConnectorConfig config, ProjectNode sourceFile) {
            this.config = config;
            this.sourceFile = sourceFile;
        }
    }
    private static class FlowWithSource {
        FlowInfo flow;
        ProjectNode sourceFile;
        FlowWithSource(FlowInfo flow, ProjectNode sourceFile) {
            this.flow = flow;
            this.sourceFile = sourceFile;
        }
    }
    @SuppressWarnings("unchecked")
    private void collectAllConnectorConfigs(ProjectNode node, List<ConnectorConfigWithSource> result) {
        if (node.getType() == ProjectNode.NodeType.FILE && node.getName().endsWith(".xml")) {
            List<ConnectorConfig> configs = (List<ConnectorConfig>) node.getMetadata("muleConfigs");
            if (configs != null && !configs.isEmpty()) {
                for (ConnectorConfig config : configs) {
                    result.add(new ConnectorConfigWithSource(config, node));
                }
            }
        } else if (node.getType() == ProjectNode.NodeType.DIRECTORY) {
            for (ProjectNode child : node.getChildren()) {
                collectAllConnectorConfigs(child, result);
            }
        }
    }
    @SuppressWarnings("unchecked")
    private void collectFlowsByType(ProjectNode node, String flowType, List<FlowWithSource> result) {
        if (node.getType() == ProjectNode.NodeType.FILE && node.getName().endsWith(".xml")) {
            List<FlowInfo> flows = (List<FlowInfo>) node.getMetadata("muleFlows");
            if (flows != null) {
                for (FlowInfo flow : flows) {
                    if (flowType.equalsIgnoreCase(flow.getType())) {
                        result.add(new FlowWithSource(flow, node));
                    }
                }
            }
        } else if (node.getType() == ProjectNode.NodeType.DIRECTORY) {
            for (ProjectNode child : node.getChildren()) {
                collectFlowsByType(child, flowType, result);
            }
        }
    }
    private void collectFilesByExtension(ProjectNode node, List<String> extensions, List<ProjectNode> result) {
        if (node.getType() == ProjectNode.NodeType.FILE) {
            for (String ext : extensions) {
                if (node.getName().endsWith(ext)) {
                    result.add(node);
                    break;
                }
            }
        } else if (node.getType() == ProjectNode.NodeType.DIRECTORY) {
            for (ProjectNode child : node.getChildren()) {
                collectFilesByExtension(child, extensions, result);
            }
        }
    }
    private List<ProjectNode> collectOtherFiles(ProjectNode node) {
        List<ProjectNode> allFiles = new ArrayList<>();
        collectAllFiles(node, allFiles);
        return allFiles.stream()
            .filter(f -> !processedFiles.contains(f.getAbsolutePath()))
            .filter(f -> !f.getName().startsWith("."))
            .sorted(Comparator.comparing(ProjectNode::getName))
            .collect(Collectors.toList());
    }
    private void collectAllFiles(ProjectNode node, List<ProjectNode> result) {
        if (node.getType() == ProjectNode.NodeType.FILE) {
            result.add(node);
        } else if (node.getType() == ProjectNode.NodeType.DIRECTORY) {
            for (ProjectNode child : node.getChildren()) {
                collectAllFiles(child, result);
            }
        }
    }
    private ProjectNode findFileByName(ProjectNode root, String fileName) {
        if (root == null) return null;
        if (root.getType() == ProjectNode.NodeType.FILE && root.getName().equals(fileName)) {
            return root;
        }
        if (root.getType() == ProjectNode.NodeType.DIRECTORY) {
            for (ProjectNode child : root.getChildren()) {
                ProjectNode found = findFileByName(child, fileName);
                if (found != null) return found;
            }
        }
        return null;
    }
    private List<PropertyInfo> getPropertiesForFile(AnalysisResult result, String relativePath) {
        List<PropertyInfo> props = new ArrayList<>();
        String normalizedSearchPath = relativePath.replace("\\", "/");
        logger.debug("Looking for properties in file: {} (normalized: {})", relativePath, normalizedSearchPath);
        for (PropertyInfo p : result.getProperties()) {
            for (String envKey : p.getEnvironmentValues().keySet()) {
                String normalizedEnvKey = envKey.replace("\\", "/");
                if (normalizedEnvKey.equals(normalizedSearchPath)) {
                    props.add(p);
                    logger.debug("Found property: {} in file: {}", p.getKey(), envKey);
                    break; 
                }
            }
        }
        logger.debug("Found {} properties for file: {}", props.size(), relativePath);
        return props;
    }
    private void createProjectDetailsTable(AnalysisResult result) {
        ProjectInfo info = result.getProjectInfo();
        if (info == null) return;
        XWPFTable table = createFixedTable(4, 2, new int[]{30, 70});
        fillHeader(table, new String[]{"Property", "Value"});
        int r = 1;
        fillRow(table.getRow(r++), "Project Name", info.getProjectName());
        fillRow(table.getRow(r++), "Version", info.getVersion());
        String displayPath = info.getProjectPath();
        if (result.getSourceUrl() != null && !result.getSourceUrl().isEmpty()) {
            displayPath = result.getSourceUrl();
        }
        fillRow(table.getRow(r++), "Project Path", displayPath);
    }
    private void createPomBasicInfoTable(PomInfo pomInfo) {
        if (pomInfo == null) return;
        int rowCount = 0;
        if (pomInfo.getModelVersion() != null) rowCount++;
        if (pomInfo.getGroupId() != null) rowCount++;
        if (pomInfo.getArtifactId() != null) rowCount++;
        if (pomInfo.getVersion() != null) rowCount++;
        if (pomInfo.getPackaging() != null) rowCount++;
        if (pomInfo.getName() != null) rowCount++;
        if (pomInfo.getDescription() != null) rowCount++;
        if (pomInfo.getParent() != null) rowCount += 3; 
        if (rowCount == 0) return;
        XWPFTable table = createFixedTable(rowCount + 1, 2, new int[]{30, 70});
        fillHeader(table, new String[]{"Property", "Value"});
        int r = 1;
        if (pomInfo.getModelVersion() != null) {
            fillRow(table.getRow(r++), "Model Version", pomInfo.getModelVersion());
        }
        if (pomInfo.getGroupId() != null) {
            fillRow(table.getRow(r++), "Group ID", pomInfo.getGroupId());
        }
        if (pomInfo.getArtifactId() != null) {
            fillRow(table.getRow(r++), "Artifact ID", pomInfo.getArtifactId());
        }
        if (pomInfo.getVersion() != null) {
            fillRow(table.getRow(r++), "Version", pomInfo.getVersion());
        }
        if (pomInfo.getPackaging() != null) {
            fillRow(table.getRow(r++), "Packaging", pomInfo.getPackaging());
        }
        if (pomInfo.getName() != null) {
            fillRow(table.getRow(r++), "Name", pomInfo.getName());
        }
        if (pomInfo.getDescription() != null) {
            fillRow(table.getRow(r++), "Description", pomInfo.getDescription());
        }
        if (pomInfo.getParent() != null) {
            PomInfo.ParentInfo parent = pomInfo.getParent();
            fillRow(table.getRow(r++), "Parent Group ID", parent.getGroupId());
            fillRow(table.getRow(r++), "Parent Artifact ID", parent.getArtifactId());
            fillRow(table.getRow(r++), "Parent Version", parent.getVersion());
            if (parent.getRelativePath() != null) {
                fillRow(table.getRow(r++), "Parent Relative Path", parent.getRelativePath());
            }
        }
    }
    private void createPomPropertiesTable(Map<String, String> props) {
        String headerKey = config.getProperty("mule.word.table.header.key", "Key");
        String headerValue = config.getProperty("mule.word.table.header.value", "Value");
        XWPFTable table = createFixedTable(props.size() + 1, 2, new int[]{40, 60});
        fillHeader(table, new String[]{headerKey, headerValue});
        int r = 1;
        for (Map.Entry<String, String> e : props.entrySet()) {
            XWPFTableRow row = table.getRow(r++);
            fillCellWithWrapping(row.getCell(0), e.getKey(), "40%");
            fillCellWithWrapping(row.getCell(1), e.getValue(), "60%");
        }
    }
    private void createPluginsTable(List<PomInfo.PluginInfo> plugins) {
        String headerGroupId = config.getProperty("mule.word.table.header.groupid", "GroupId");
        String headerArtifactId = config.getProperty("mule.word.table.header.artifactid", "ArtifactId");
        String headerVersion = config.getProperty("mule.word.table.header.version", "Version");
        XWPFTable table = createFixedTable(plugins.size() + 1, 3, new int[]{40, 40, 20});
        fillHeader(table, new String[]{headerGroupId, headerArtifactId, headerVersion});
        int r = 1;
        for (PomInfo.PluginInfo plugin : plugins) {
            XWPFTableRow row = table.getRow(r++);
            fillCellWithWrapping(row.getCell(0), plugin.getGroupId(), "40%");
            fillCellWithWrapping(row.getCell(1), plugin.getArtifactId(), "40%");
            fillCellWithWrapping(row.getCell(2), plugin.getVersion(), "20%");
        }
    }
    private void createDependenciesTable(List<PomInfo.DependencyInfo> deps) {
        String headerGroupId = config.getProperty("mule.word.table.header.groupid", "GroupId");
        String headerArtifactId = config.getProperty("mule.word.table.header.artifactid", "ArtifactId");
        String headerVersion = config.getProperty("mule.word.table.header.version", "Version");
        XWPFTable table = createFixedTable(deps.size() + 1, 3, new int[]{40, 40, 20});
        fillHeader(table, new String[]{headerGroupId, headerArtifactId, headerVersion});
        int r = 1;
        for (PomInfo.DependencyInfo dep : deps) {
            XWPFTableRow row = table.getRow(r++);
            fillCellWithWrapping(row.getCell(0), dep.getGroupId(), "40%");
            fillCellWithWrapping(row.getCell(1), dep.getArtifactId(), "40%");
            fillCellWithWrapping(row.getCell(2), dep.getVersion(), "20%");
        }
    }
    private void createPropertiesTable(List<PropertyInfo> props, String filePath) {
        String headerKey = config.getProperty("mule.word.table.header.key", "Key");
        String headerValue = config.getProperty("mule.word.table.header.value", "Value");
        XWPFTable table = createFixedTable(props.size() + 1, 2, new int[]{40, 60});
        fillHeader(table, new String[]{headerKey, headerValue});
        int r = 1;
        for (PropertyInfo p : props) {
            XWPFTableRow row = table.getRow(r++);
            fillCellWithWrapping(row.getCell(0), p.getKey(), "40%");
            String val = p.getEnvironmentValues().get(filePath);
            fillCellWithWrapping(row.getCell(1), val, "60%");
        }
    }
    private void createConnectorConfigAttributesTable(ConnectorConfig connectorConfig) {
        String headerName = this.config.getProperty("mule.word.table.header.attribute.name", "Attribute Name");
        String headerValue = this.config.getProperty("mule.word.table.header.attribute.value", "Attribute Value");
        Map<String, String> attrs = connectorConfig.getAttributes();
        if (attrs == null || attrs.isEmpty()) return;
        XWPFTable table = createFixedTable(attrs.size() + 1, 2, new int[]{40, 60});
        fillHeader(table, new String[]{headerName, headerValue});
        int r = 1;
        for (Map.Entry<String, String> e : attrs.entrySet()) {
            XWPFTableRow row = table.getRow(r++);
            fillCellWithWrapping(row.getCell(0), e.getKey(), "40%");
            fillCellWithWrapping(row.getCell(1), e.getValue(), "60%");
        }
    }
    private void createComponentAttributesTable(ComponentInfo component) {
        String headerName = config.getProperty("mule.word.table.header.attribute.name", "Attribute Name");
        String headerValue = config.getProperty("mule.word.table.header.attribute.value", "Attribute Value");
        Map<String, String> attrs = component.getAttributes();
        if (attrs == null || attrs.isEmpty()) return;
        boolean includeDocId = getBooleanConfig("analyzer.mule.component.attribute.doc_id.enabled", false);
        String content = attrs.get("_content");
        List<Map.Entry<String, String>> filteredAttrs = attrs.entrySet().stream()
            .filter(e -> !e.getKey().startsWith("_")) 
            .filter(e -> includeDocId || !"doc:id".equals(e.getKey()))
            .collect(Collectors.toList());
        if (filteredAttrs.isEmpty() && content == null) return;
        int rowCount = filteredAttrs.size() + (content != null ? 1 : 0);
        XWPFTable table = createFixedTable(rowCount + 1, 2, new int[]{40, 60});
        fillHeader(table, new String[]{headerName, headerValue});
        int r = 1;
        for (Map.Entry<String, String> e : filteredAttrs) {
            XWPFTableRow row = table.getRow(r++);
            fillCellWithWrapping(row.getCell(0), e.getKey(), "40%");
            fillCellWithWrapping(row.getCell(1), e.getValue(), "60%");
        }
        if (content != null) {
            if (content.trim().startsWith("<![CDATA[") && content.trim().endsWith("]]>")) {
                content = content.trim();
                content = content.substring(9, content.length() - 3);
                if (content.trim().startsWith("#[") && content.trim().endsWith("]")) {
                    content = content.trim();
                    content = content.substring(2, content.length() - 1);
                }
            }
            XWPFTableRow row = table.getRow(r);
            String cdataLabel = config.getProperty("word.cdata.label", "<![CDATA[#[...]]]>");
            fillCellWithWrapping(row.getCell(0), cdataLabel, "40%");
            fillCellWithWrapping(row.getCell(1), content, "60%");
        }
    }
    private void renderFileContentInBox(ProjectNode node) {
        if (!shouldIncludeFileContent(node.getName())) {
            return;
        }
        try {
            String content = Files.readString(Paths.get(node.getAbsolutePath()), StandardCharsets.UTF_8);
            if (content.length() > 50000) {
                content = content.substring(0, 50000) + "\n... (Content truncated)";
            }
            String fileType = getFileExtension(node.getName());
            createContentBox(content, fileType);
        } catch (Exception e) {
            XWPFParagraph p = document.createParagraph();
            p.createRun().setText("Error reading file content: " + e.getMessage());
        }
    }
    private boolean shouldIncludeFileContent(String fileName) {
        String ext = getFileExtension(fileName);
        switch (ext) {
            case "java": return getBooleanConfig("mule.word.content.java", false);
            case "json": return getBooleanConfig("mule.word.content.json", true);
            case "dwl": return getBooleanConfig("mule.word.content.dwl", true);
            case "csv": return getBooleanConfig("mule.word.content.csv", false);
            case "txt": return getBooleanConfig("mule.word.content.txt", false);
            case "wsdl": return getBooleanConfig("mule.word.content.wsdl", false);
            case "xml": return getBooleanConfig("mule.word.content.xml", false);
            case "xsd": return getBooleanConfig("mule.word.content.xsd", false);
            case "dtd": return getBooleanConfig("mule.word.content.dtd", false);
            case "cpy": return getBooleanConfig("mule.word.content.cpy", false);
            default: return false;
        }
    }
    private void createContentBox(String content, String fileType) {
        String formattedContent = prettyPrintContent(content, fileType);
        XWPFTable table = document.createTable(1, 1);
        table.setWidth("100%");
        XWPFTableCell cell = table.getRow(0).getCell(0);
        CTTcPr tcPr = cell.getCTTc().getTcPr();
        if (tcPr == null) {
            tcPr = cell.getCTTc().addNewTcPr();
        }
        CTTcBorders borders = tcPr.isSetTcBorders() ? tcPr.getTcBorders() : tcPr.addNewTcBorders();
        String borderColor = config.getProperty("mule.word.box.border.color", "000000");
        int borderSize = Integer.parseInt(config.getProperty("mule.word.box.border.size", "4"));
        CTBorder borderStyle = CTBorder.Factory.newInstance();
        borderStyle.setVal(STBorder.SINGLE);
        borderStyle.setSz(BigInteger.valueOf(borderSize));
        borderStyle.setColor(borderColor);
        borders.setTop(borderStyle);
        borders.setBottom(borderStyle);
        borders.setLeft(borderStyle);
        borders.setRight(borderStyle);
        String bgColor = config.getProperty("mule.word.box.background.color", "F5F5F5");
        CTShd shd = tcPr.isSetShd() ? tcPr.getShd() : tcPr.addNewShd();
        shd.setFill(bgColor);
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        p.setSpacingBefore(100);
        p.setSpacingAfter(100);
        XWPFRun r = p.createRun();
        r.setFontFamily("Courier New");
        r.setFontSize(9);
        if (formattedContent != null) {
            String[] lines = formattedContent.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].replace("\r", "");
                line = line.replace("\t", "    ");
                r.setText(line);
                if (i < lines.length - 1) {
                    r.addBreak();
                }
            }
        }
        document.createParagraph();
    }
    private String prettyPrintContent(String content, String fileType) {
        try {
            if ("json".equals(fileType) && getBooleanConfig("mule.word.prettify.json", true)) {
                return prettyPrintJson(content);
            } else if (("xml".equals(fileType) || "wsdl".equals(fileType)) && 
                       getBooleanConfig("mule.word.prettify.xml", true)) {
                return prettyPrintXml(content);
            }
            return content;
        } catch (Exception e) {
            logger.warn("Failed to pretty-print {} content: {}", fileType, e.getMessage());
            return content;
        }
    }
    private String prettyPrintJson(String json) {
        try {
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
            com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(json);
            String pretty = gson.toJson(el);
            // Handle escaped newlines that might be literal in the string
            return pretty.replace("\\r\\n", "\n").replace("\\n", "\n");
        } catch (Exception e) {
            return json;
        }
    }
    private String prettyPrintXml(String xml) {
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            javax.xml.transform.TransformerFactory transformerFactory = javax.xml.transform.TransformerFactory.newInstance();
            javax.xml.transform.Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            java.io.StringWriter writer = new java.io.StringWriter();
            transformer.transform(new javax.xml.transform.dom.DOMSource(doc), new javax.xml.transform.stream.StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            return xml;
        }
    }
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
    private void createHeading1(String text) {
        incrementSection(0);
        String numberedText = getSectionNumber() + ". " + text;
        createHeading(numberedText, "Heading1", 0, 16, true);
    }
    private void createHeading2(String text) {
        incrementSection(1);
        String numberedText = getSectionNumber() + " " + text;
        createHeading(numberedText, "Heading2", 1, 14, true);
    }
    private void createHeading3(String text) {
        incrementSection(2);
        String numberedText = getSectionNumber() + " " + text;
        createHeading(numberedText, "Heading3", 2, 12, true);
    }
    private void createHeading4(String text) {
        incrementSection(3);
        String numberedText = getSectionNumber() + " " + text;
        createHeading(numberedText, "Heading4", 3, 11, true);
    }
    private void createHeading(String text, String styleId, int outlineLevel, int size, boolean bold) {
        XWPFParagraph p = document.createParagraph();
        p.setStyle(styleId);
        setOutlineLevel(p, outlineLevel);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(bold);
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
        for (int pct : colPercents) {
            grid.addNewGridCol().setW(BigInteger.valueOf((long)(9000 * (pct/100.0))));
        }
        return table;
    }
    private void fillHeader(XWPFTable table, String[] headers) {
        String bgColor = config.getProperty("word.table.header.background.color", "E6E6FA"); 
        String textColor = config.getProperty("word.table.header.text.color", "000000"); 
        int rowHeight = Integer.parseInt(config.getProperty("word.table.row.height", "0"));
        XWPFTableRow row = table.getRow(0);
        if (rowHeight > 0) {
            row.setHeight(rowHeight);
        }
        for (int i = 0; i < headers.length; i++) {
            XWPFTableCell cell = row.getCell(i);
            if (cell == null) cell = row.createCell();
            cell.setColor(bgColor);
            XWPFParagraph p = cell.getParagraphs().get(0);
            p.setSpacingAfter(0);
            p.setSpacingBefore(0);
            XWPFRun r = p.createRun();
            r.setText(headers[i]);
            r.setBold(true);
            r.setColor(textColor);
        }
    }
    private void fillRow(XWPFTableRow row, String key, String value) {
        int rowHeight = Integer.parseInt(config.getProperty("word.table.row.height", "0"));
        if (rowHeight > 0) {
            row.setHeight(rowHeight);
        }
        fillCellWithWrapping(row.getCell(0), key, "30%");
        fillCellWithWrapping(row.getCell(1), value, "70%");
    }
    private void fillCellWithWrapping(XWPFTableCell cell, String text, String width) {
        if (text == null) text = "";
        if (!cell.getParagraphs().isEmpty()) {
            XWPFParagraph p = cell.getParagraphs().get(0);
            p.setSpacingAfter(0);
            p.setSpacingBefore(0);
        }
        cell.setText(text);
        if (width != null) cell.setWidth(width);
    }
    private void addParagraphSpace() {
        document.createParagraph();
    }
    private void addPageBreak() {
        document.createParagraph().createRun().addBreak(BreakType.PAGE);
    }
    private void addPathSubtitle(ProjectNode node) {
        XWPFParagraph p = document.createParagraph();
        XWPFRun r = p.createRun();
        r.setText("Path: " + node.getRelativePath());
        r.setItalic(true);
        r.setFontSize(9);
        r.setColor("666666");
    }
    private Path getOutputPath(AnalysisResult result) {
        String dir = result.getOutputDirectory();
        if (dir == null || dir.isEmpty()) {
            dir = config.getProperty("framework.output.directory", "./output");
        }
        String name = "design-doc";
        if (result.getProjectInfo() != null && result.getProjectInfo().getProjectName() != null) {
            name = result.getProjectInfo().getProjectName().replaceAll("[^a-zA-Z0-9-_]", "_");
        }
        String ts = result.getStartTime().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return Paths.get(dir, String.format("%s_Analysis_Document_%s.docx", name, ts));
    }
    private boolean getBooleanConfig(String key, boolean defaultValue) {
        return Boolean.parseBoolean(config.getProperty(key, String.valueOf(defaultValue)));
    }
    private String getSectionNumber() {
        if (sectionCounters.isEmpty()) {
            return "";
        }
        return sectionCounters.stream()
            .map(String::valueOf)
            .collect(Collectors.joining("."));
    }
    private void incrementSection(int level) {
        while (sectionCounters.size() <= level) {
            sectionCounters.add(0);
        }
        sectionCounters.set(level, sectionCounters.get(level) + 1);
        while (sectionCounters.size() > level + 1) {
            sectionCounters.remove(sectionCounters.size() - 1);
        }
    }
    private void enterSubsection() {
        sectionCounters.add(0);
    }
    private void exitSubsection() {
        if (sectionCounters.size() > 1) {
            sectionCounters.remove(sectionCounters.size() - 1);
        }
    }
}
