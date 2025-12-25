package com.raks.raksanalyzer.generator.pdf;

import com.raks.raksanalyzer.core.config.ConfigurationManager;
import com.raks.raksanalyzer.domain.model.*;
import com.raks.raksanalyzer.domain.model.tibco.*;
import com.raks.raksanalyzer.generator.TibcoDocumentHelper;
import com.raks.raksanalyzer.generator.TibcoDocumentHelper.SectionNumbering;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vandeseer.easytable.RepeatedHeaderTableDrawer;
import org.vandeseer.easytable.TableDrawer;
import org.vandeseer.easytable.structure.Row;
import org.vandeseer.easytable.structure.Table;
import org.vandeseer.easytable.structure.cell.TextCell;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.File;
import com.raks.raksanalyzer.generator.tibco.TibcoDiagramGenerator;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TIBCO PDF document generator - matches Word document structure exactly.
 * Uses TibcoDocumentHelper for all shared logic.
 */
public class TibcoPdfGenerator {
    private static final Logger logger = LoggerFactory.getLogger(TibcoPdfGenerator.class);
    

    private static final float MARGIN = 50;
    private static final float FONT_SIZE_TITLE = 24;
    private static final float FONT_SIZE_H1 = 16;
    private static final float FONT_SIZE_H2 = 14;
    private static final float FONT_SIZE_H3 = 12;
    private static final float FONT_SIZE_H4 = 11;
    private static final float FONT_SIZE_BODY = 10;
    private static final float FONT_SIZE_FOOTER = 9;
    
    private final ConfigurationManager config;
    private final TibcoDocumentHelper helper;
    private final SectionNumbering numbering;
    private final TibcoDiagramGenerator diagramGenerator = new TibcoDiagramGenerator();
    
    private PDDocument document;
    private PDDocumentOutline rootOutline;
    private PDPage currentPage;
    private PDPageContentStream contentStream;
    private float currentY;
    
    private PDFont fontRegular;
    private PDFont fontBold;
    private PDFont fontItalic;
    
    private List<TibcoGlobalVariable> currentProperties;
    private List<TOCEntry> tocEntries;
    
    // Track last header for auto-repeat on new pages
    private String lastHeaderText = null;
    

    private static class TOCEntry {
        String title;
        PDPage page;
        int level;
        
        TOCEntry(String title, PDPage page, int level) {
            this.title = title;
            this.page = page;
            this.level = level;
        }
    }
    
    public TibcoPdfGenerator() {
        this.config = ConfigurationManager.getInstance();
        this.helper = new TibcoDocumentHelper();
        this.numbering = new SectionNumbering();
        this.fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        this.fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        this.fontItalic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
    }
    
    public String generate(AnalysisResult result) throws IOException {
        logger.info("Starting TIBCO PDF generation for: {}", result.getProjectInfo().getProjectName());
        
        // Flatten global variables map to list for easier lookup
        this.currentProperties = new ArrayList<>();
        if (result.getGlobalVariables() != null) {
            for (List<TibcoGlobalVariable> list : result.getGlobalVariables().values()) {
                this.currentProperties.addAll(list);
            }
        }
        
        this.tocEntries = new ArrayList<>();
        
        document = new PDDocument();
        rootOutline = new PDDocumentOutline();
        document.getDocumentCatalog().setDocumentOutline(rootOutline);
        
        try {

            createCoverPage(result);
            

            if (config.getBooleanProperty("word.tibco.section.project.info.enabled", true)) {
                createProjectInformationSection(result);
            }
            
            if (config.getBooleanProperty("word.tibco.section.starter.processes.enabled", true)) {
                createTibcoServicesSection(result);
            }
            
            if (config.getBooleanProperty("word.tibco.section.nonstart.processes.enabled", true)) {
                createTibcoProcessesSection(result);
            }
            
            if (config.getBooleanProperty("word.tibco.section.connections.enabled", true)) {
                createConnectionsSection(result);
            }
            
            if (config.getBooleanProperty("word.tibco.section.adapters.enabled", true)) {
                createAdaptersAndPluginsSection(result);
            }
            
            if (config.getBooleanProperty("word.tibco.section.global.variables.enabled", true)) {
                createGlobalVariablesSection(result);
            }
            
            if (config.getBooleanProperty("word.tibco.section.files.inventory.enabled", true)) {
                createFilesInventorySection(result);
            }
            
            if (config.getBooleanProperty("word.tibco.section.archives.enabled", true)) {
                createArchivesSection(result);
            }
            
            if (config.getBooleanProperty("word.tibco.section.references.enabled", true)) {
                createReferencesSection();
            }
            

            createTableOfContents();
            

            addPageNumbers();
            

            String outputDir = result.getOutputDirectory();
            if (outputDir == null) {
                outputDir = config.getProperty("framework.output.directory", "./output");
            }
            Files.createDirectories(Paths.get(outputDir));
            
            String fileName = result.getProjectInfo().getProjectName() + "_Tibco_Documentation_" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".pdf";
            Path outputPath = Paths.get(outputDir).resolve(fileName);
            
            document.save(outputPath.toFile());
            logger.info("TIBCO PDF generated successfully: {}", outputPath);
            return outputPath.toString();
            
        } finally {
            if (document != null) {
                document.close();
            }
        }
    }
    

    
    private void createCoverPage(AnalysisResult result) throws IOException {
        newPage();
        
        float y = 700;
        

        String title = config.getProperty("word.tibco.cover.title", "Tibco BusinessWorks Project Analysis");
        drawTextCentered(title, fontBold, FONT_SIZE_TITLE, y);
        y -= 60;
        

        drawTextCentered(result.getProjectInfo().getProjectName(), fontBold, 18, y);
        y -= 40;
        

        String desc = config.getProperty("word.tibco.cover.description",
                "Comprehensive analysis and documentation of Tibco BW 5.x project");
        drawTextCentered(desc, fontRegular, 12, y);
        y -= 40;
        

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
        String formattedDate = now.atZone(ZoneId.systemDefault()).format(formatter);
        drawTextCentered("Generated: " + formattedDate, fontRegular, 10, y);
        
        closeContentStream();
    }
    

    
    private void createTableOfContents() throws IOException {
        if (tocEntries.isEmpty()) {

            newPage();
            addBookmark("Table of Contents");
            drawSectionHeader("Table of Contents");
            drawText("Please use the Bookmarks panel to navigate this document.", fontItalic, FONT_SIZE_BODY);
            closeContentStream();
            return;
        }
        

        PDPage tocPage = new PDPage(PDRectangle.A4);
        

        if (document.getNumberOfPages() > 1) {
            document.getPages().insertBefore(tocPage, document.getPage(1));
        } else {
            document.addPage(tocPage);
        }
        
        PDPage currentTocPage = tocPage;
        PDPageContentStream currentTocStream = new PDPageContentStream(document, currentTocPage);
        float tocY = PDRectangle.A4.getHeight() - 80;
        float pageWidth = PDRectangle.A4.getWidth();
        float pageHeight = PDRectangle.A4.getHeight();
        
        try {

            currentTocStream.beginText();
            currentTocStream.setFont(fontBold, 18);
            currentTocStream.newLineAtOffset(MARGIN, pageHeight - 50);
            currentTocStream.showText("Table of Contents");
            currentTocStream.endText();
            
            for (TOCEntry entry : tocEntries) {
                if (tocY < MARGIN + 20) {
                    currentTocStream.close();
                    PDPage nextTocPage = new PDPage(PDRectangle.A4);
                    document.getPages().insertAfter(nextTocPage, currentTocPage);
                    currentTocPage = nextTocPage;
                    currentTocStream = new PDPageContentStream(document, currentTocPage);
                    tocY = pageHeight - MARGIN;
                }
                

                float xindent = (entry.level - 1) * 20f;
                int validPageNum = document.getPages().indexOf(entry.page) + 1;
                

                currentTocStream.beginText();
                currentTocStream.setFont(fontRegular, 11);
                currentTocStream.newLineAtOffset(MARGIN + xindent, tocY);
                currentTocStream.showText(entry.title);
                currentTocStream.endText();
                

                float titleWidth = fontRegular.getStringWidth(entry.title) / 1000 * 11;
                float dotsStart = MARGIN + xindent + titleWidth + 5;
                float dotsEnd = pageWidth - MARGIN - 70;
                if (dotsEnd > dotsStart) {
                    currentTocStream.beginText();
                    currentTocStream.newLineAtOffset(dotsStart, tocY);
                    float dotComboWidth = fontRegular.getStringWidth(" .") / 1000 * 11;
                    int repeatCount = (int)((dotsEnd - dotsStart) / dotComboWidth);
                    if (repeatCount > 0) {
                        String dots = " .".repeat(repeatCount);
                        currentTocStream.showText(dots);
                    }
                    currentTocStream.endText();
                }
                

                String pageNumStr = String.valueOf(validPageNum);
                float pageNumWidth = fontRegular.getStringWidth(pageNumStr) / 1000 * 11;
                currentTocStream.beginText();
                currentTocStream.newLineAtOffset(pageWidth - MARGIN - pageNumWidth, tocY);
                currentTocStream.showText(pageNumStr);
                currentTocStream.endText();
                

                org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink link = 
                    new org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink();
                

                org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary borderStyle = 
                    new org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary();
                borderStyle.setWidth(0);
                link.setBorderStyle(borderStyle);
                
                org.apache.pdfbox.pdmodel.common.PDRectangle rect = new org.apache.pdfbox.pdmodel.common.PDRectangle();
                rect.setLowerLeftX(MARGIN + xindent);
                rect.setLowerLeftY(tocY - 2);
                rect.setUpperRightX(pageWidth - MARGIN);
                rect.setUpperRightY(tocY + 10);
                link.setRectangle(rect);
                

                org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination dest = 
                    new org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination();
                dest.setPage(entry.page);
                dest.setTop((int)pageHeight);
                dest.setLeft(0);
                dest.setZoom(-1);
                
                org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo action = 
                    new org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo();
                action.setDestination(dest);
                link.setAction(action);
                
                currentTocPage.getAnnotations().add(link);
                
                tocY -= 15;
            }
            
        } finally {
            currentTocStream.close();
        }
    }
    

    
    private void createProjectInformationSection(AnalysisResult result) throws IOException {
        numbering.incrementSection(0);
        String title = numbering.getSectionNumber() + " Project Information";
        newPage();
        addBookmark(title);
        drawSectionHeader(title);
        
        ProjectInfo info = result.getProjectInfo();
        

        numbering.incrementSection(1);
        String subTitle = numbering.getSectionNumber() + " Basic Information";
        drawSubSectionHeader(subTitle);
        
        String projectPath = info.getProjectPath();
        try {
            projectPath = Paths.get(projectPath).toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            logger.warn("Failed to normalize project path", e);
        }
        
        List<String[]> basicData = new ArrayList<>();
        basicData.add(new String[]{"Project Name", info.getProjectName()});
        basicData.add(new String[]{"Project Type", "Tibco BusinessWorks 5.x"});
        basicData.add(new String[]{"Project Path", projectPath});
        if (info.getTibcoVersion() != null && !info.getTibcoVersion().isEmpty()) {
            basicData.add(new String[]{"TIBCO Version", info.getTibcoVersion()});
        }
        
        drawTable(basicData.toArray(new String[0][]), new String[]{"Attribute", "Value"}, new float[]{0.3f, 0.7f});
        

        numbering.incrementSection(1);
        subTitle = numbering.getSectionNumber() + " Project Statistics";
        drawSubSectionHeader(subTitle);
        
        List<String[]> statsData = buildProjectStatistics(result);
        drawTable(statsData.toArray(new String[0][]), new String[]{"Metric", "Count"}, new float[]{0.6f, 0.4f});
        
        closeContentStream();
    }
    
    private List<String[]> buildProjectStatistics(AnalysisResult result) {
        List<String[]> data = new ArrayList<>();
        

        Map<String, Object> processStats = helper.calculateProcessStatistics(result);
        int totalProcesses = (int) processStats.get("total");
        int starterProcesses = (int) processStats.get("starter");
        int nonStarterProcesses = (int) processStats.get("normal");
        
        if (totalProcesses > 0) {
            data.add(new String[]{"Total Processes", String.valueOf(totalProcesses)});
            if (starterProcesses > 0) {
                data.add(new String[]{"  - Starter Processes", String.valueOf(starterProcesses)});
                

                Map<String, Integer> starterTypeCounts = new HashMap<>();
                if (result.getFlows() != null) {
                    for (FlowInfo flow : result.getFlows()) {
                        if (flow.getStarterConfig() != null && !flow.getStarterConfig().isEmpty()) {

                            String starterType = "Unknown";
                            if (flow.getDescription() != null && flow.getDescription().contains("Type: ")) {
                                starterType = flow.getDescription().substring(flow.getDescription().indexOf("Type: ") + 6);
                                if (starterType.contains("|")) {
                                    starterType = starterType.substring(0, starterType.indexOf("|")).trim();
                                }
                            }
                            starterTypeCounts.put(starterType, starterTypeCounts.getOrDefault(starterType, 0) + 1);
                        }
                    }
                }
                

                List<String> sortedTypes = new ArrayList<>(starterTypeCounts.keySet());
                Collections.sort(sortedTypes);
                for (String type : sortedTypes) {
                    data.add(new String[]{"    • " + type, String.valueOf(starterTypeCounts.get(type))});
                }
            }
            if (nonStarterProcesses > 0) {
                data.add(new String[]{"  - Non-Starter Processes", String.valueOf(nonStarterProcesses)});
            }
        }
        

        int totalActivities = result.getComponents() != null ? result.getComponents().size() : 0;
        

        if (totalActivities == 0 && result.getFlows() != null) {
            for (FlowInfo flow : result.getFlows()) {
                if (flow.getComponents() != null) {
                    totalActivities += flow.getComponents().size();
                }
            }
        }
        
        if (totalActivities > 0) {
            data.add(new String[]{"Total Activities", String.valueOf(totalActivities)});
        }
        

        @SuppressWarnings("unchecked")
        Map<String, Integer> serviceTypeCounts = (Map<String, Integer>) result.getMetadata("serviceTypeCounts");
        if (serviceTypeCounts != null) {
            if (serviceTypeCounts.containsKey("SOAP")) {
                data.add(new String[]{"SOAP Services", serviceTypeCounts.get("SOAP").toString()});
            }
            if (serviceTypeCounts.containsKey("REST")) {
                data.add(new String[]{"REST Services", serviceTypeCounts.get("REST").toString()});
            }
            if (serviceTypeCounts.containsKey("ServiceAgent")) {
                data.add(new String[]{"Service Agents", serviceTypeCounts.get("ServiceAgent").toString()});
            }
        }
        

        List<ResourceInfo> resources = result.getResources();
        if (resources != null && !resources.isEmpty()) {
            Map<String, Integer> connTypeCounts = new HashMap<>();
            for (ResourceInfo res : resources) {
                String type = res.getType();
                if (type != null && !type.contains("Adapter") && !type.contains("TIDManager")) {
                    connTypeCounts.put(type, connTypeCounts.getOrDefault(type, 0) + 1);
                }
            }
            for (Map.Entry<String, Integer> entry : connTypeCounts.entrySet()) {
                data.add(new String[]{entry.getKey(), String.valueOf(entry.getValue())});
            }
        }
        

        @SuppressWarnings("unchecked")
        Map<String, Integer> adapterUsage = (Map<String, Integer>) result.getMetadata("adapterUsage");
        if (adapterUsage != null && !adapterUsage.isEmpty()) {
            int totalAdapters = adapterUsage.values().stream().mapToInt(Integer::intValue).sum();
            data.add(new String[]{"Total Adapters", String.valueOf(totalAdapters)});
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Integer> pluginUsage = (Map<String, Integer>) result.getMetadata("pluginUsage");
        if (pluginUsage != null && !pluginUsage.isEmpty()) {
            int totalPlugins = pluginUsage.values().stream().mapToInt(Integer::intValue).sum();
            data.add(new String[]{"Total Plugins", String.valueOf(totalPlugins)});
        }
        

        Map<String, List<TibcoGlobalVariable>> globalVars = result.getGlobalVariables();
        if (globalVars != null && !globalVars.isEmpty()) {
            int totalGVFiles = globalVars.size();
            int totalGVCount = globalVars.values().stream().mapToInt(List::size).sum();
            data.add(new String[]{"Global Variables Files", String.valueOf(totalGVFiles)});
            data.add(new String[]{"Total Global Variables", String.valueOf(totalGVCount)});
        }
        

        @SuppressWarnings("unchecked")
        Map<String, Integer> fileTypeCounts = (Map<String, Integer>) result.getMetadata("fileTypeCounts");
        if (fileTypeCounts != null && !fileTypeCounts.isEmpty()) {
            int totalFiles = fileTypeCounts.values().stream().mapToInt(Integer::intValue).sum();
            data.add(new String[]{"Total Files", String.valueOf(totalFiles)});
            
            int processFiles = fileTypeCounts.getOrDefault(".process", 0);
            int archiveFiles = fileTypeCounts.getOrDefault(".archive", 0);
            int substvarFiles = fileTypeCounts.getOrDefault(".substvar", 0);
            
            if (processFiles > 0) {
                data.add(new String[]{"  - Process Files (.process)", String.valueOf(processFiles)});
            }
            if (archiveFiles > 0) {
                data.add(new String[]{"  - Archive Files (.archive)", String.valueOf(archiveFiles)});
            }
            if (substvarFiles > 0) {
                data.add(new String[]{"  - Variable Files (.substvar)", String.valueOf(substvarFiles)});
            }
        }
        
        return data;
    }
    

    
    private void createTibcoServicesSection(AnalysisResult result) throws IOException {
        numbering.incrementSection(0);
        String title = numbering.getSectionNumber() + " Tibco Services";
        newPage();
        addBookmark(title);
        drawSectionHeader(title);
        

        numbering.incrementSection(1);
        String subTitle = numbering.getSectionNumber() + " Services Summary";
        drawSubSectionHeader(subTitle);
        
        List<String[]> summaryData = new ArrayList<>();
        

        for (FlowInfo flow : result.getFlows()) {
            if (helper.isStarterProcess(flow)) {
                String rawType = helper.getRawStarterType(flow);
                String friendlyType = helper.getFriendlyStarterType(rawType);
                if (flow.getDescription() != null && flow.getDescription().contains("REST Service")) {
                    friendlyType = "REST Service";
                }
                summaryData.add(new String[]{flow.getName(), friendlyType});
            }
        }
        

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> serviceAgents = (List<Map<String, Object>>) result.getMetadata().get("serviceAgents");
        if (serviceAgents != null) {
            for (Map<String, Object> agent : serviceAgents) {
                summaryData.add(new String[]{
                    (String) agent.getOrDefault("name", "Unknown Agent"),
                    "Service Agent"
                });
            }
        }
        
        if (summaryData.isEmpty()) {
            drawText("No starter processes or services found.", fontRegular, FONT_SIZE_BODY);
        } else {
            summaryData.sort(Comparator.comparing(r -> r[1])); // Sort by Type
            drawTable(summaryData.toArray(new String[0][]), 
                     new String[]{"Service/Process Name", "Type"}, 
                     new float[]{0.6f, 0.4f});
        }
        

        createDynamicStarterSections(result);
        
        closeContentStream();
    }
    
    private void createDynamicStarterSections(AnalysisResult result) throws IOException {

        Map<String, List<FlowInfo>> flowsByType = new HashMap<>();
        
        for (FlowInfo flow : result.getFlows()) {
            if (helper.isStarterProcess(flow)) {
                String rawType = helper.getRawStarterType(flow);
                String friendlyType = helper.getFriendlyStarterType(rawType);
                

                if (flow.getDescription() != null && flow.getDescription().contains("REST Service")) {
                    friendlyType = "REST Service";
                }
                

                if (friendlyType.equals("RestAdapterActivity")) friendlyType = "REST Service";
                
                flowsByType.computeIfAbsent(friendlyType, k -> new ArrayList<>()).add(flow);
            }
        }
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> serviceAgents = (List<Map<String, Object>>) result.getMetadata().get("serviceAgents");
        







        

        if (flowsByType.containsKey("On-Start Up")) {
            createGenericStarterSection("On-Start Up Processes", flowsByType.remove("On-Start Up"), result);
        }
        

        if (flowsByType.containsKey("SOAP Service")) {
            createSoapServicesSection(flowsByType.remove("SOAP Service"), result);
        }
        

        if (serviceAgents != null && !serviceAgents.isEmpty()) {
            createServiceAgentsSection(serviceAgents, result);
        }
        

        List<FlowInfo> restFlows = flowsByType.remove("REST Service");
        if (result.getMetadata("restServices") != null || (restFlows != null && !restFlows.isEmpty())) {
            createRestServicesSection(result, restFlows);
        }
        

        List<FlowInfo> adapters = new ArrayList<>();
        if (flowsByType.containsKey("Adapter Subscriber")) adapters.addAll(flowsByType.remove("Adapter Subscriber"));
        if (flowsByType.containsKey("Adapter Service")) adapters.addAll(flowsByType.remove("Adapter Service"));
        if (!adapters.isEmpty()) {
            createAdapterServicesSection(adapters, result);
        }
        

        List<String> remainingTypes = new ArrayList<>(flowsByType.keySet());
        Collections.sort(remainingTypes);
        
        for (String type : remainingTypes) {
            createGenericStarterSection(type + "s", flowsByType.get(type), result);
        }
    }
    
    private void createGenericStarterSection(String sectionTitle, List<FlowInfo> flows, AnalysisResult result) throws IOException {
        if (flows == null || flows.isEmpty()) return;
        
        numbering.incrementSection(1);
        drawSubSectionHeader(numbering.getSectionNumber() + " " + sectionTitle);
        
        List<String> interestingKeys = helper.getInterestingKeysForType(sectionTitle);
        
        int subIndex = 1;
        for (FlowInfo flow : flows) {
            drawH3Header(numbering.getSectionNumber() + "." + subIndex + " " + flow.getName());
            
            Map<String, String> config = flow.getStarterConfig();
            if (config.isEmpty()) {
                drawText("(No configuration detected)", fontRegular, FONT_SIZE_BODY);
                currentY -= 15;
            } else {
                List<String[]> confRows = new ArrayList<>();
                List<String> sortedKeys = new ArrayList<>(config.keySet());
                Collections.sort(sortedKeys);
                
                for (String key : sortedKeys) {
                    if (interestingKeys == null || interestingKeys.contains(key)) {
                        String displayKey = key.substring(key.lastIndexOf(".") + 1);
                        String val = helper.resolveGlobalVariable(config.get(key), currentProperties);
                        val = helper.formatConfigValue(displayKey, val);
                        confRows.add(new String[]{displayKey, val});
                    }
                }
                
                if (!confRows.isEmpty()) {
                    drawTable(confRows.toArray(new String[0][]), 
                             new String[]{"Property", "Value"}, 
                             new float[]{0.4f, 0.6f});
                }
            }
            
            
            // Integration Diagram
            String relPath = flow.getRelativePath();
            String projPath = result.getProjectInfo().getProjectPath();
            if (relPath == null) relPath = flow.getName();
            
            subIndex++;
            String diagramTitle = numbering.getSectionNumber() + "." + subIndex + " " + flow.getName() + " Integration";
            drawH3Header(diagramTitle);
            insertIntegrationDiagram(diagramTitle, relPath, projPath);

            subIndex++;
            checkPageSpace(50);
        }
    }
    
    private void createSoapServicesSection(List<FlowInfo> flows, AnalysisResult result) throws IOException {
        numbering.incrementSection(1);
        drawSubSectionHeader(numbering.getSectionNumber() + " SOAP Services");
        
        int subIndex = 1;
        for (FlowInfo flow : flows) {
            drawH3Header(numbering.getSectionNumber() + "." + subIndex + " " + flow.getName());
            
            Map<String, String> config = flow.getStarterConfig();
            
            String op = helper.resolveGlobalVariable(config.getOrDefault("operation", ""), currentProperties);
            String addr = helper.resolveGlobalVariable(config.getOrDefault("serviceAddress", config.getOrDefault("httpURI", "")), currentProperties);
            String action = helper.resolveGlobalVariable(config.getOrDefault("soapAction", ""), currentProperties);
            
            String[][] data = {{flow.getName(), op, addr, action}};
            drawTable(data, new String[]{"Service Process", "Operation", "Service Address", "SOAP Action"}, 
                     new float[]{0.25f, 0.25f, 0.3f, 0.2f});
            
            
            // Integration Diagram
            String relPath = flow.getRelativePath();
            String projPath = result.getProjectInfo().getProjectPath();
            if (relPath == null) relPath = flow.getName();

            subIndex++;
            String diagramTitle = numbering.getSectionNumber() + "." + subIndex + " " + flow.getName() + " Integration";
            drawH3Header(diagramTitle);
            insertIntegrationDiagram(diagramTitle, relPath, projPath);

            subIndex++;
            checkPageSpace(50);
        }
    }
    
    private void createServiceAgentsSection(List<Map<String, Object>> serviceAgents, AnalysisResult result) throws IOException {
        numbering.incrementSection(1);
        drawSubSectionHeader(numbering.getSectionNumber() + " Service Agents");
        
        int idx = 1;
        for (Map<String, Object> agent : serviceAgents) {
            String name = (String) agent.getOrDefault("name", "Unknown");
            drawH3Header(numbering.getSectionNumber() + "." + idx + " " + name);
            
            drawText("Path: " + agent.getOrDefault("path", ""), fontRegular, FONT_SIZE_BODY);
            currentY -= 12;
            
            String wsdlLoc = (String) agent.getOrDefault("wsdlLocation", "");
            if (wsdlLoc != null && !wsdlLoc.isEmpty()) {
                drawText("WSDL Source: " + wsdlLoc, fontRegular, FONT_SIZE_BODY);
                currentY -= 12;
            }
            
            String addr = helper.resolveGlobalVariable((String)agent.getOrDefault("address", ""), currentProperties);
            drawText("Address: " + addr, fontRegular, FONT_SIZE_BODY);
            currentY -= 15;
            
            @SuppressWarnings("unchecked")
            List<Map<String, String>> ops = (List<Map<String, String>>) agent.get("operations");
            if (ops != null && !ops.isEmpty()) {
                List<String[]> opsData = new ArrayList<>();
                for (Map<String, String> op : ops) {
                    opsData.add(new String[]{op.get("name"), op.get("implementation"), op.get("soapAction")});
                }
                drawTable(opsData.toArray(new String[0][]), 
                         new String[]{"Operation", "Implementation Process", "SOAP Action"}, 
                         new float[]{0.3f, 0.5f, 0.2f});
            }
            
            
            // Integration Diagrams for Operations
            if (ops != null) {
                checkPageSpace(150);
                int opIdx = 1;
                for (Map<String, String> op : ops) {
                    String opName = op.get("name");
                    String impl = op.get("implementation");
                    if (impl != null && !impl.isEmpty()) {
                        String projPath = result.getProjectInfo().getProjectPath();
                        // Use opIdx to distinct operations
                    String diagramTitle = numbering.getSectionNumber() + "." + idx + "." + opIdx + " " + opName + " Integration";
                    drawH3Header(diagramTitle);
                    insertIntegrationDiagram(diagramTitle, impl, projPath, "Service Agent");
                    }
                    opIdx++;
                }
            }
            
            idx++;
            checkPageSpace(50);
        }
    }



    
    private void createRestServicesSection(AnalysisResult result, List<FlowInfo> flows) throws IOException {
        numbering.incrementSection(1);
        drawSubSectionHeader(numbering.getSectionNumber() + " REST Services");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> restServices = (List<Map<String, Object>>) result.getMetadata("restServices");
        Set<String> processedPaths = new HashSet<>();
        
        int idx = 1;
        if (restServices != null) {
            for (Map<String, Object> service : restServices) {
                String procName = (String) service.get("processName");
                processedPaths.add((String)service.get("processPath"));
                
                drawH3Header(numbering.getSectionNumber() + "." + idx + " " + procName);
                
                drawText("Process Path: " + service.get("processPath"), fontRegular, FONT_SIZE_BODY);
                currentY -= 12;
                drawText("WADL Source: " + service.get("wadlSource"), fontRegular, FONT_SIZE_BODY);
                currentY -= 15;
                
                @SuppressWarnings("unchecked")
                List<Map<String, String>> bindings = (List<Map<String, String>>) service.get("bindings");
                if (bindings != null && !bindings.isEmpty()) {
                    List<String[]> bindData = new ArrayList<>();
                    for (Map<String, String> b : bindings) {
                        bindData.add(new String[]{b.get("method"), b.get("resourcePath"), b.get("implementationProcess")});
                    }
                    drawTable(bindData.toArray(new String[0][]), 
                             new String[]{"Method", "Resource Path", "Implementation Process"}, 
                             new float[]{0.2f, 0.4f, 0.4f});
                }
                
                // Add Integration Diagram for REST service (Added to match Word)
                idx++;
                String path = (String) service.get("processFile"); // Use processFile (absolute) not processPath (relative)
                if (path != null) {
                    drawH3Header(numbering.getSectionNumber() + "." + idx + " " + procName + " Integration");
                    insertIntegrationDiagram(numbering.getSectionNumber() + "." + idx + " " + procName + " Integration",
                                           path, result.getProjectInfo().getProjectPath());
                }
                
                idx++;
                checkPageSpace(50);
            }
        }
        

        if (flows != null) {
            for (FlowInfo flow : flows) {
                String flowPath = flow.getFileName().replace("\\", "/");
                boolean alreadyProcessed = false;
                
                for (String processed : processedPaths) {
                    if (flowPath.endsWith(processed) || processed.endsWith(flowPath)) {
                        alreadyProcessed = true;
                        break;
                    }
                }
                
                if (!alreadyProcessed) {
                    drawH3Header(numbering.getSectionNumber() + "." + idx + " " + flow.getName());
                    drawText("Detected as REST Service but detailed metadata unavailable.", fontRegular, FONT_SIZE_BODY);
                    currentY -= 15;
                    idx++;
                }
            }
        }
    }
    
    private void createAdapterServicesSection(List<FlowInfo> flows, AnalysisResult result) throws IOException {
        numbering.incrementSection(1);
        drawSubSectionHeader(numbering.getSectionNumber() + " Adapter Services");
        
        int idx = 1;
        for (FlowInfo flow : flows) {
            drawH3Header(numbering.getSectionNumber() + "." + idx + " " + flow.getName());
            
            Map<String, String> config = flow.getStarterConfig();
            

            String adapterServicePath = config.getOrDefault("adapterService", 
                                        config.getOrDefault("ae.aepalette.sharedProperties.adapterService", "N/A"));
            
            drawText("Adapter Service: " + adapterServicePath, fontBold, FONT_SIZE_BODY);
            currentY -= 15;
            
            String transport = config.getOrDefault("transportChoice", 
                               config.getOrDefault("ae.aepalette.sharedProperties.transportChoice", "default"));
            String displayTransport = "default".equalsIgnoreCase(transport) ? "Adapter Service Default" : transport;
            
            String subjectDest = "";
            String providerUrl = "";
            
            boolean isJms = transport.toLowerCase().contains("jms");
        
        if (isJms) {
            if (config.containsKey("jmsQueueSessionQueue")) subjectDest = config.get("jmsQueueSessionQueue");
            else if (config.containsKey("jmsTopicSessionTopic")) subjectDest = config.get("jmsTopicSessionTopic");
            else if (config.containsKey("jmsTopicSessionName")) subjectDest = config.get("jmsTopicSessionName");
            else if (config.containsKey("ae.aepalette.sharedProperties.jmsQueueSessionQueue")) subjectDest = config.get("ae.aepalette.sharedProperties.jmsQueueSessionQueue");
            else if (config.containsKey("ae.aepalette.sharedProperties.jmsTopicSessionTopic")) subjectDest = config.get("ae.aepalette.sharedProperties.jmsTopicSessionTopic");
            else subjectDest = config.entrySet().stream()
                     .filter(e -> e.getKey().toLowerCase().contains("queue") || e.getKey().toLowerCase().contains("topic"))
                     .map(Map.Entry::getValue).findFirst().orElse("");
            
            providerUrl = config.getOrDefault("jmsSessionProviderURL", 
                          config.getOrDefault("ae.aepalette.sharedProperties.jmsSessionProviderURL", ""));
        } else {
            subjectDest = config.getOrDefault("rvSubject", 
                          config.getOrDefault("ae.aepalette.sharedProperties.rvSubject", ""));
                          
            String service = config.getOrDefault("rvSessionService", 
                             config.getOrDefault("ae.aepalette.sharedProperties.rvSessionService", ""));
                             
            String network = config.getOrDefault("rvSessionNetwork", 
                             config.getOrDefault("ae.aepalette.sharedProperties.rvSessionNetwork", ""));
                             
            String daemon = config.getOrDefault("rvSessionDaemon", 
                            config.getOrDefault("ae.aepalette.sharedProperties.rvSessionDaemon", ""));
                            
            providerUrl = service + ":" + network + ":" + daemon;
             if (providerUrl.equals("::")) providerUrl = "";
             else if (providerUrl.startsWith(":")) providerUrl = providerUrl.substring(1);
             else if (providerUrl.endsWith(":")) providerUrl = providerUrl.substring(0, providerUrl.length() - 1);
        }
            
            subjectDest = helper.resolveGlobalVariable(subjectDest, currentProperties);
            providerUrl = helper.resolveGlobalVariable(providerUrl, currentProperties);
            
            String[][] data = {{displayTransport, subjectDest, providerUrl}};
            drawTable(data, new String[]{"Transport", "Subject / Destination", "Provider Url"}, 
                     new float[]{0.3f, 0.4f, 0.3f});
            
            // Add Integration Diagram (missing in PDF previously)
            String processPath = flow.getRelativePath();
            if (processPath == null || processPath.isEmpty()) {
                processPath = flow.getFileName(); // Fallback to absolute path if relative is missing
            }
            
            idx++;
            drawH3Header(numbering.getSectionNumber() + "." + idx + " " + flow.getName() + " Integration");
            
            insertIntegrationDiagram(numbering.getSectionNumber() + "." + idx + " " + flow.getName() + " Integration",
                                   processPath, result.getProjectInfo().getProjectPath());

            idx++;
            checkPageSpace(50);
        }
    }
    

    
    private void createTibcoProcessesSection(AnalysisResult result) throws IOException {
        numbering.incrementSection(0);
        String title = numbering.getSectionNumber() + " Tibco Processes";
        newPage();
        addBookmark(title);
        drawSectionHeader(title);
        

        drawText("Process Statistics", fontBold, FONT_SIZE_H2);
        currentY -= 15;
        
        Map<String, Object> stats = helper.calculateProcessStatistics(result);
        List<String[]> statsData = new ArrayList<>();
        statsData.add(new String[]{"Total Process Count", stats.get("total").toString()});
        statsData.add(new String[]{"Starter Process Count", stats.get("starter").toString()});
        statsData.add(new String[]{"Normal Process Count", stats.get("normal").toString()});
        
        drawTable(statsData.toArray(new String[0][]), 
                 new String[]{"Property", "Value"}, 
                 new float[]{0.7f, 0.3f});
        

        numbering.incrementSection(1);
        String subTitle = numbering.getSectionNumber() + " Processes Summary";
        drawSubSectionHeader(subTitle);
        
        List<String[]> summaryData = new ArrayList<>();
        List<FlowInfo> flows = new ArrayList<>(result.getFlows());
        flows.sort(Comparator.comparing(FlowInfo::getName));
        
        for (FlowInfo flow : flows) {
            String relPath = flow.getRelativePath() != null ? flow.getRelativePath() : flow.getName();
            String starter = "N/A";
            String type = "Normal Process";
            
            if (helper.isStarterProcess(flow)) {
                String desc = flow.getDescription();
                if (desc != null && desc.contains("Starter: ")) {
                    int pipeIdx = desc.indexOf("|");
                    if (pipeIdx > 0) {
                        starter = desc.substring(desc.indexOf("Starter: ") + 9, pipeIdx);
                        type = desc.substring(desc.indexOf("Type: ") + 6);
                    } else {
                        starter = desc.substring(desc.indexOf("Starter: ") + 9);
                    }
                }
                if ("N/A".equals(starter) || starter.isEmpty()) starter = "Unknown";
                if (type.isEmpty()) type = "Unknown";
            }
            
            summaryData.add(new String[]{relPath, starter, type});
        }
        
        drawTable(summaryData.toArray(new String[0][]), 
                 new String[]{"Process Name", "Starter", "Type"}, 
                 new float[]{0.5f, 0.25f, 0.25f});
        

        boolean showConfig = config.getBooleanProperty("word.tibco.process.activity.configuration.details", true);
        boolean showBindings = config.getBooleanProperty("word.tibco.process.activity.inputBindings.details", true);
        

        flows.sort(Comparator.comparing(f -> f.getRelativePath() != null ? f.getRelativePath() : f.getFileName()));
        
        for (FlowInfo flow : flows) {
            numbering.incrementSection(1);
            drawSubSectionHeader(numbering.getSectionNumber() + " " + flow.getName());
            
            String relPath = flow.getRelativePath() != null ? flow.getRelativePath() : flow.getFileName();
            
            // Flow Diagram - shows all activities within process scope
            // Use sub-numbering .1 for Flow Diagram
            drawH4Header(numbering.getSectionNumber() + ".1 Flow Diagram");
            insertFlowDiagram(flow);

            createProcessActivityStatsTable(flow);
            
            // Adjust Activity Index to start from 2, since 1 is Flow Diagram
            int activityIdx = 2;
            for (ComponentInfo activity : flow.getComponents()) {
                String type = activity.getType();
                String simpleType = type;
                if (type != null && type.contains(".")) {
                    simpleType = type.substring(type.lastIndexOf(".") + 1);
                }
                

                drawH4Header(numbering.getSectionNumber() + "." + activityIdx + " " + 
                        activity.getName() + " ( " + simpleType + " )");
                
                drawText("Name: " + activity.getName(), fontRegular, FONT_SIZE_BODY);
                currentY -= 10;
                drawText("Type: " + simpleType, fontRegular, FONT_SIZE_BODY);
                currentY -= 15;
                

                if (showConfig && !activity.getAttributes().isEmpty()) {
                    drawText(numbering.getSectionNumber() + "." + activityIdx + ".1 Configurations", 
                            fontBold, FONT_SIZE_H4);
                    currentY -= 12;
                    
                    List<String[]> configRows = new ArrayList<>();
                    List<String> keys = new ArrayList<>(activity.getAttributes().keySet());
                    Collections.sort(keys);
                    
                    for (String key : keys) {
                        String val = helper.resolveGlobalVariable(activity.getAttributes().get(key), currentProperties);
                        val = helper.formatConfigValue(key, val);
                        configRows.add(new String[]{key, val});
                    }
                    
                    drawTable(configRows.toArray(new String[0][]), 
                             new String[]{"Attribute Name", "Attribute Value"}, 
                             new float[]{0.5f, 0.5f});
                }
                

                if (showBindings && !activity.getInputBindings().isEmpty()) {
                    drawText(numbering.getSectionNumber() + "." + activityIdx + ".2 Mappings", 
                            fontBold, FONT_SIZE_H4);
                    currentY -= 12;
                    
                    List<String[]> mappingRows = new ArrayList<>();
                    List<String> keys = new ArrayList<>(activity.getInputBindings().keySet());
                    Collections.sort(keys);
                    
                    for (String key : keys) {
                        String val = helper.resolveGlobalVariable(activity.getInputBindings().get(key), currentProperties);
                        mappingRows.add(new String[]{key, val});
                    }
                    
                    drawTable(mappingRows.toArray(new String[0][]), 
                             new String[]{"Attribute Name", "Attribute Value"}, 
                             new float[]{0.5f, 0.5f});
                }
                
                activityIdx++;
                checkPageSpace(100);
            }
            
            checkPageSpace(50);
        }
        
        closeContentStream();
    }
    
    /**
     * Create activity statistics table for a single process.
     */
    private void createProcessActivityStatsTable(FlowInfo flow) throws IOException {
        Map<String, Object> stats = helper.calculateActivityStatistics(flow);
        
        List<String[]> statsData = new ArrayList<>();
        statsData.add(new String[]{"Total Activity Count", stats.get("total").toString()});
        
        @SuppressWarnings("unchecked")
        Map<String, Integer> typeCounts = (Map<String, Integer>) stats.get("typeCounts");
        List<String> sortedTypes = new ArrayList<>(typeCounts.keySet());
        Collections.sort(sortedTypes);
        
        for (String type : sortedTypes) {
            statsData.add(new String[]{type + " type count", typeCounts.get(type).toString()});
        }
        
        drawTable(statsData.toArray(new String[0][]), 
                 new String[]{"Property", "Value"}, 
                 new float[]{0.7f, 0.3f});
    }
    

    
    private void createConnectionsSection(AnalysisResult result) throws IOException {
        numbering.incrementSection(0);
        String title = numbering.getSectionNumber() + " Connections & Configurations";
        newPage();
        addBookmark(title);
        drawSectionHeader(title);
        
        List<ResourceInfo> resources = result.getResources();
        
        if (resources == null || resources.isEmpty()) {
            drawText("No connections or configurations found.", fontRegular, FONT_SIZE_BODY);
            closeContentStream();
            return;
        }
        

        Map<String, List<ResourceInfo>> groupedResources = resources.stream()
            .filter(r -> !r.getType().contains("Adapter") && !r.getType().contains("TIDManager"))
            .collect(Collectors.groupingBy(ResourceInfo::getType));
        
        List<String> sortedTypes = new ArrayList<>(groupedResources.keySet());
        Collections.sort(sortedTypes);
        
        for (String type : sortedTypes) {
            numbering.incrementSection(1);
            drawSubSectionHeader(numbering.getSectionNumber() + " " + type);
            
            List<ResourceInfo> resourceList = groupedResources.get(type);
            resourceList.sort(Comparator.comparing(ResourceInfo::getName));
            
            int resourceIdx = 1;
            for (ResourceInfo res : resourceList) {
                drawText(numbering.getSectionNumber() + "." + resourceIdx + " " + res.getName(), fontBold, FONT_SIZE_H3);
                currentY -= 15;
                drawText("Path: " + res.getRelativePath(), fontRegular, FONT_SIZE_BODY);
                currentY -= 10;
                
                Map<String, String> config = res.getConfiguration();
                if (config != null && !config.isEmpty()) {
                    List<String[]> configData = new ArrayList<>();
                    List<String> sortedKeys = new ArrayList<>(config.keySet());
                    Collections.sort(sortedKeys);
                    
                    for (String key : sortedKeys) {
                        String val = helper.resolveGlobalVariable(config.get(key), currentProperties);
                        val = helper.formatConfigValue(key, val);
                        configData.add(new String[]{key, val});
                    }
                    
                    drawTable(configData.toArray(new String[0][]), 
                             new String[]{"Property", "Value"}, 
                             new float[]{0.4f, 0.6f});
                } else {
                    drawText("No configuration details available.", fontRegular, FONT_SIZE_BODY);
                    currentY -= 10;
                }
                
                resourceIdx++;
                checkPageSpace(50);
            }
        }
        
        closeContentStream();
    }
    

    
    private void createAdaptersAndPluginsSection(AnalysisResult result) throws IOException {
        numbering.incrementSection(0);
        String title = numbering.getSectionNumber() + " Adapters & Plugins";
        newPage();
        addBookmark(title);
        drawSectionHeader(title);
        
        List<ResourceInfo> resources = result.getResources();
        Map<String, List<ResourceInfo>> adapterResources = new HashMap<>();
        
        if (resources != null) {
            adapterResources = resources.stream()
                .filter(r -> r.getType().contains("Adapter") || r.getType().contains("TIDManager"))
                .collect(Collectors.groupingBy(ResourceInfo::getType));
        }
        
        if (adapterResources.isEmpty()) {
            drawText("No adapters found.", fontRegular, FONT_SIZE_BODY);
        } else {
            List<String> sortedTypes = new ArrayList<>(adapterResources.keySet());
            Collections.sort(sortedTypes);
            
            for (String type : sortedTypes) {
                numbering.incrementSection(1);
                drawSubSectionHeader(numbering.getSectionNumber() + " " + type);
                
                List<ResourceInfo> list = adapterResources.get(type);
                list.sort(Comparator.comparing(ResourceInfo::getName));
                
                int resIdx = 1;
                for (ResourceInfo res : list) {
                    drawText(numbering.getSectionNumber() + "." + resIdx + " " + res.getName(), fontBold, FONT_SIZE_H3);
                    currentY -= 15;
                    drawText("Path: " + res.getRelativePath(), fontRegular, FONT_SIZE_BODY);
                    currentY -= 10;
                    
                    Map<String, String> config = res.getConfiguration();
                    if (config != null && !config.isEmpty()) {
                        List<String[]> configData = new ArrayList<>();
                        List<String> keys = new ArrayList<>(config.keySet());
                        Collections.sort(keys);
                        
                        for (String key : keys) {
                            String val = helper.resolveGlobalVariable(config.get(key), currentProperties);
                            val = helper.formatConfigValue(key, val);
                            configData.add(new String[]{key, val != null ? val : ""});
                        }
                        
                        drawTable(configData.toArray(new String[0][]), 
                                 new String[]{"Property", "Value"}, 
                                 new float[]{0.4f, 0.6f});
                    } else {
                        drawText("No configuration details available.", fontRegular, FONT_SIZE_BODY);
                        currentY -= 10;
                    }
                    
                    resIdx++;
                    checkPageSpace(50);
                }
            }
        }
        

        @SuppressWarnings("unchecked")
        Map<String, Integer> pluginUsage = (Map<String, Integer>) result.getMetadata("pluginUsage");
        if (pluginUsage != null && !pluginUsage.isEmpty()) {
            numbering.incrementSection(1);
            drawSubSectionHeader(numbering.getSectionNumber() + " Plugins");
            
            List<String[]> pluginData = new ArrayList<>();
            pluginUsage.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> pluginData.add(new String[]{entry.getKey(), String.valueOf(entry.getValue())}));
            
            drawTable(pluginData.toArray(new String[0][]), 
                     new String[]{"Plugin Type", "Usage Count"}, 
                     new float[]{0.7f, 0.3f});
        }
        
        closeContentStream();
    }
    

    
    private void createGlobalVariablesSection(AnalysisResult result) throws IOException {
        numbering.incrementSection(0);
        String title = numbering.getSectionNumber() + " Global Variables";
        newPage();
        addBookmark(title);
        drawSectionHeader(title);
        
        Map<String, List<TibcoGlobalVariable>> globalVars = result.getGlobalVariables();
        
        if (globalVars == null || globalVars.isEmpty()) {
            drawText("No global variables found.", fontRegular, FONT_SIZE_BODY);
            closeContentStream();
            return;
        }
        

        int totalCount = globalVars.values().stream().mapToInt(List::size).sum();
        drawText("Total Global Variables Count: " + totalCount, fontBold, FONT_SIZE_H2, new Color(0, 0, 255));
        currentY -= 20;
        

        List<String> sortedGroups = new ArrayList<>(globalVars.keySet());
        Collections.sort(sortedGroups);
        
        int subIndex = 1;
        for (String group : sortedGroups) {
            List<TibcoGlobalVariable> vars = globalVars.get(group);
            if (vars == null || vars.isEmpty()) continue;
            

            String subTitle = numbering.getSectionNumber() + "." + subIndex + " " + group + " (Count: " + vars.size() + ")";
            drawSubSectionHeader(subTitle);
            

            vars.sort(Comparator.comparing(TibcoGlobalVariable::getName));
            

            List<String[]> tableData = new ArrayList<>();
            for (TibcoGlobalVariable gv : vars) {
                String name = gv.getName();
                String value = gv.getValue() != null ? gv.getValue() : "";
                String type = helper.abbreviateType(gv.getType());
                tableData.add(new String[]{name, value, type});
            }
            
            drawTable(tableData.toArray(new String[0][]), 
                     new String[]{"Name", "Value", "Type"}, 
                     new float[]{0.4f, 0.45f, 0.15f});
            
            subIndex++;
        }
        
        closeContentStream();
    }
    

    
    private void createFilesInventorySection(AnalysisResult result) throws IOException {
        numbering.incrementSection(0);
        String title = numbering.getSectionNumber() + " Files Inventory";
        newPage();
        addBookmark(title);
        drawSectionHeader(title);
        
        @SuppressWarnings("unchecked")
        Map<String, Integer> fileCountByType = (Map<String, Integer>) result.getMetadata("fileInventory.countByType");
        @SuppressWarnings("unchecked")
        Map<String, Long> fileSizeByType = (Map<String, Long>) result.getMetadata("fileInventory.sizeByType");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> filesList = (List<Map<String, String>>) result.getMetadata("fileInventory.filesList");
        
        if (fileCountByType == null || fileCountByType.isEmpty()) {
            drawText("No file inventory data available.", fontRegular, FONT_SIZE_BODY);
            closeContentStream();
            return;
        }
        
        // Build table data first so we can estimate height
        List<String[]> fileTypeData = new ArrayList<>();
        fileCountByType.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                String fileType = entry.getKey();
                int count = entry.getValue();
                long sizeBytes = fileSizeByType != null ? fileSizeByType.getOrDefault(fileType, 0L) : 0L;
                long sizeKB = sizeBytes / 1024;
                fileTypeData.add(new String[]{fileType, String.valueOf(count), String.format("%,d", sizeKB)});
            });
        
        // Use SectionContext to ensure header + content + table stay together
        SectionContext ctx = new SectionContext(this);
        ctx.addHeaderHeight(25);  // Header
        ctx.addTextHeight(15);    // "Summary of project files..."
        ctx.addSpacing(15);       // Spacing
        ctx.addTextHeight(15);    // "Total Activities Count: XXX"
        ctx.addSpacing(15);       // Spacing
        ctx.addTableHeight(estimateTableHeight(fileTypeData.toArray(new String[0][])));
        
        if (ctx.needsNewPage()) {
            newPage();
            closeContentStream();
        }

        numbering.incrementSection(1);
        drawSubSectionHeader(numbering.getSectionNumber() + " File Types Count");
        
        drawTable(fileTypeData.toArray(new String[0][]), 
                 new String[]{"File Type", "Count", "Total Size (KB)"}, 
                 new float[]{0.5f, 0.25f, 0.25f});
        

        if (filesList != null && !filesList.isEmpty()) {
            numbering.incrementSection(1);
            drawSubSectionHeader(numbering.getSectionNumber() + " Files List");
            
            drawText("Complete list of all project files:", fontRegular, FONT_SIZE_BODY);
            currentY -= 15;
            
            List<String[]> filesData = new ArrayList<>();
            filesList.stream()
                .sorted((a, b) -> a.get("path").compareTo(b.get("path")))
                .forEach(fileInfo -> filesData.add(new String[]{fileInfo.get("name"), fileInfo.get("path")}));
            
            drawTable(filesData.toArray(new String[0][]), 
                     new String[]{"File Name", "Relative Path"}, 
                     new float[]{0.3f, 0.7f});
        }
        
        closeContentStream();
    }
    

    
    private void createArchivesSection(AnalysisResult result) throws IOException {
        numbering.incrementSection(0);
        String title = numbering.getSectionNumber() + " Archives";
        newPage();
        addBookmark(title);
        drawSectionHeader(title);
        
        @SuppressWarnings("unchecked")
        List<TibcoArchive> archives = (List<TibcoArchive>) result.getMetadata("archives");
        
        if (archives == null || archives.isEmpty()) {
            drawText("No archive definitions found.", fontRegular, FONT_SIZE_BODY);
            closeContentStream();
            return;
        }
        
        for (TibcoArchive ear : archives) {
            numbering.incrementSection(1);
            drawSubSectionHeader(numbering.getSectionNumber() + " " + ear.getName());
            
            drawText("Type: Enterprise Archive", fontRegular, FONT_SIZE_BODY);
            currentY -= 12;
            drawText("Path: " + ear.getFilePath(), fontRegular, FONT_SIZE_BODY);
            currentY -= 12;
            
            if (ear.getAuthor() != null) {
                drawText("Author: " + ear.getAuthor(), fontRegular, FONT_SIZE_BODY);
                currentY -= 12;
            }
            if (ear.getVersion() != null) {
                drawText("Version: " + ear.getVersion(), fontRegular, FONT_SIZE_BODY);
                currentY -= 12;
            }
            
            currentY -= 10;
            

            if (ear.getChildren() != null && !ear.getChildren().isEmpty()) {
                for (TibcoArchive child : ear.getChildren()) {
                    String childType = child.getType() != null ? child.getType() : "UNKNOWN";
                    drawText("Child Archive: " + child.getName() + " (" + childType + ")", fontBold, FONT_SIZE_H3);
                    currentY -= 15;
                    

                    if (!child.getProcessesIncluded().isEmpty()) {
                        drawText("Included Processes:", fontItalic, FONT_SIZE_BODY);
                        currentY -= 10;
                        
                        List<String[]> processData = new ArrayList<>();
                        for (String proc : child.getProcessesIncluded()) {
                            processData.add(new String[]{proc});
                        }
                        drawTable(processData.toArray(new String[0][]), 
                                 new String[]{"Process Path"}, 
                                 new float[]{1.0f});
                    }
                    

                    if (!child.getSharedResourcesIncluded().isEmpty()) {
                        drawText("Shared Resources:", fontItalic, FONT_SIZE_BODY);
                        currentY -= 10;
                        
                        List<String[]> resData = new ArrayList<>();
                        for (String res : child.getSharedResourcesIncluded()) {
                            resData.add(new String[]{res});
                        }
                        drawTable(resData.toArray(new String[0][]), 
                                 new String[]{"Resource Path"}, 
                                 new float[]{1.0f});
                    }
                    
                    currentY -= 10;
                    checkPageSpace(50);
                }
            }
        }
        
        closeContentStream();
    }
    

    
    private void createReferencesSection() throws IOException {
        numbering.incrementSection(0);
        String title = numbering.getSectionNumber() + " References";
        newPage();
        addBookmark(title);
        drawSectionHeader(title);
        
        drawText("References section is placeholder for any reference details team requires.", 
                fontItalic, FONT_SIZE_BODY, Color.GRAY);
        currentY -= 20;
        
        drawText("• TIBCO ActiveMatrix BusinessWorks 5.x Documentation", fontRegular, FONT_SIZE_BODY);
        currentY -= 15;
        drawText("• TIBCO Designer User Guide", fontRegular, FONT_SIZE_BODY);
        currentY -= 15;
        drawText("• TIBCO Administrator Guide", fontRegular, FONT_SIZE_BODY);
        
        closeContentStream();
    }
    

    
    private void newPage() throws IOException {
        closeContentStream();
        currentPage = new PDPage(PDRectangle.LETTER);
        document.addPage(currentPage);
        contentStream = new PDPageContentStream(document, currentPage);
        currentY = PDRectangle.LETTER.getHeight() - MARGIN;
    }
    
    private void closeContentStream() throws IOException {
        if (contentStream != null) {
            contentStream.close();
            contentStream = null;
        }
    }
    
    private void checkPageSpace(float required) throws IOException {
        if (currentY - required < MARGIN + 50) {
            newPage();
            currentY = PDRectangle.LETTER.getHeight() - MARGIN; // Reset currentY like Mule does
        }
    }
    
    private void drawText(String text, PDFont font, float fontSize) throws IOException {
        drawText(text, font, fontSize, Color.BLACK);
    }
    
    private void drawText(String text, PDFont font, float fontSize, Color color) throws IOException {
        checkPageSpace(20);
        contentStream.beginText();
        contentStream.setFont(font, fontSize);
        contentStream.setNonStrokingColor(color);
        contentStream.newLineAtOffset(MARGIN, currentY);
        contentStream.showText(text != null ? text : "");
        contentStream.endText();
        currentY -= (fontSize + 5);
    }
    
    private void drawTextCentered(String text, PDFont font, float fontSize, float y) throws IOException {
        float width = font.getStringWidth(text) / 1000 * fontSize;
        float x = (PDRectangle.LETTER.getWidth() - width) / 2;
        
        contentStream.beginText();
        contentStream.setFont(font, fontSize);
        contentStream.newLineAtOffset(x, y);
        contentStream.showText(text);
        contentStream.endText();
    }
    
    private void drawSectionHeader(String title) throws IOException {
        checkPageSpace(40);
        contentStream.beginText();
        contentStream.setFont(fontBold, FONT_SIZE_H1);
        contentStream.setNonStrokingColor(Color.BLACK);
        contentStream.newLineAtOffset(MARGIN, currentY);
        contentStream.showText(title);
        contentStream.endText();
        currentY -= 30;
    }
    
    private void drawSubSectionHeader(String title) throws IOException {
        // Check if we have enough space for header + minimum table size
        // This prevents headers from being orphaned on one page while tables are on the next
        float minSpaceNeeded = 25 + 150; // Header height + minimum table space
        float availableSpace = currentY - MARGIN;
        
        if (availableSpace < minSpaceNeeded) {
            newPage();
            closeContentStream();
            // Create new content stream for the new page
            contentStream = new PDPageContentStream(document, currentPage, PDPageContentStream.AppendMode.APPEND, true, true);
        }
        
        // Track this header for potential redraw on new page
        lastHeaderText = title;
        contentStream.beginText();
        contentStream.setFont(fontBold, FONT_SIZE_H2);
        contentStream.setNonStrokingColor(Color.BLACK);
        contentStream.newLineAtOffset(MARGIN, currentY);
        contentStream.showText(title);
        contentStream.endText();

        currentY -= 25; // Match Mule PDF spacing

        

        if (tocEntries != null) {
            tocEntries.add(new TOCEntry(title, currentPage, 2));
        }
    }
    
    private void drawH3Header(String title) throws IOException {
        checkPageSpace(25);
        contentStream.beginText();
        contentStream.setFont(fontBold, FONT_SIZE_H3);
        contentStream.setNonStrokingColor(Color.BLACK);
        contentStream.newLineAtOffset(MARGIN, currentY);
        contentStream.showText(title);
        contentStream.endText();
        currentY -= 25; // Increased from 20 to prevent table overlap
        

        if (tocEntries != null) {
            tocEntries.add(new TOCEntry(title, currentPage, 3));
        }
    }
    
    private void drawH4Header(String title) throws IOException {
        checkPageSpace(20);
        contentStream.beginText();
        contentStream.setFont(fontBold, FONT_SIZE_H3);
        contentStream.setNonStrokingColor(Color.BLACK);
        contentStream.newLineAtOffset(MARGIN, currentY);
        contentStream.showText(title);
        contentStream.endText();
        currentY -= 12; // Spacing after activity header
        

        if (tocEntries != null) {
            tocEntries.add(new TOCEntry(title, currentPage, 4));
        }
    }
    
    private void drawTable(String[][] data, String[] headers, float[] colWidths) throws IOException {
        if (data == null || data.length == 0) return;
        
        // No special page handling here - drawSubSectionHeader already ensured space
        // No padding before table - subsection header already provides spacing
        
        float tableWidth = PDRectangle.LETTER.getWidth() - (2 * MARGIN);
        
        Table.TableBuilder tableBuilder = Table.builder();
        
        for (float w : colWidths) {
            tableBuilder.addColumnOfWidth(tableWidth * w);
        }
        
        tableBuilder.fontSize((int)FONT_SIZE_BODY).font(fontRegular).borderColor(Color.LIGHT_GRAY);
        

        Row.RowBuilder headerRow = Row.builder().backgroundColor(new Color(230, 230, 250)).font(fontBold);
        for (String h : headers) {
            headerRow.add(TextCell.builder().text(h != null ? h : "").borderWidth(1).build());
        }
        tableBuilder.addRow(headerRow.build());
        

        for (String[] rowData : data) {
            Row.RowBuilder rb = Row.builder();
            for (String cell : rowData) {
                String txt = cell != null ? cell : "";

                txt = txt.replace("\t", "    ")  // Replace tabs with 4 spaces
                         .replace("\n", " ")      // Replace newlines with space
                         .replace("\r", "");      // Remove carriage returns
                if (txt.length() > 150) txt = txt.substring(0, 147) + "...";
                rb.add(TextCell.builder().text(txt).borderWidth(1).wordBreak(true).build());
            }
            tableBuilder.addRow(rb.build());
        }
        
        
        Table table = tableBuilder.build();
    
    // Manual Height Calculation to fix overlap issues with wordBreak(true)
    // easytable's getHeight() might not account for wrapping correctly in all versions
    float estimatedTableHeight = 20; // Header
    for (int i = 0; i < data.length; i++) {
        float maxRowHeight = 15; // Min row height
        for (int j = 0; j < data[i].length; j++) {
             String txt = data[i][j] != null ? data[i][j] : "";
             float colW = tableWidth * colWidths[j];
             // Est char width ~ 6pts for Size 10 font. 
             // length * 6 / colW = lines.
             int estimatedLines = (int) Math.ceil((txt.length() * 5.0f) / colW);
             if (estimatedLines < 1) estimatedLines = 1;
             // Line height approx 12
             float cellH = estimatedLines * 12 + 5; 
             if (cellH > maxRowHeight) maxRowHeight = cellH;
        }
        estimatedTableHeight += maxRowHeight;
    }
    
    // Use the larger of the two heights to be safe
    float renderHeight = Math.max(table.getHeight(), estimatedTableHeight);

    // Check space
    checkPageSpace(renderHeight + 30);
    

    
    // Use TableDrawer (single-page) instead of RepeatedHeaderTableDrawer (multi-page)
    // This matches Mule's approach and prevents headers from being left on previous pages
    TableDrawer.builder()
        .contentStream(contentStream)
        .table(table)
        .startX(MARGIN)
        .startY(currentY)
        .build()
        .draw();
    
    // Update currentY using our safer estimate
    currentY -= (renderHeight + 15); // Add buffer
    }
    
    private void addBookmark(String title) {
        PDOutlineItem item = new PDOutlineItem();
        item.setTitle(title);
        
        PDPageFitWidthDestination dest = new PDPageFitWidthDestination();
        dest.setPage(currentPage);
        dest.setTop((int) PDRectangle.LETTER.getHeight());
        item.setDestination(dest);
        
        rootOutline.addLast(item);
        

        if (tocEntries != null && !title.equals("Table of Contents")) {
            tocEntries.add(new TOCEntry(title, currentPage, 1));
        }
    }
    
    private void addPageNumbers() throws IOException {
        int total = document.getNumberOfPages();
        String footerText = config.getProperty("document.footer.text", "RaksAnalyzer ApiGuard Tool");
        
        for (int i = 0; i < total; i++) {
            PDPage page = document.getPage(i);
            try (PDPageContentStream stream = new PDPageContentStream(document, page, 
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                

                stream.beginText();
                stream.setFont(fontRegular, FONT_SIZE_FOOTER);
                stream.newLineAtOffset(MARGIN, MARGIN / 2);
                stream.showText(footerText);
                stream.endText();
                

                String pageText = "Page " + (i + 1) + " of " + total;
                float pageTextWidth = fontRegular.getStringWidth(pageText) / 1000 * FONT_SIZE_FOOTER;
                stream.beginText();
                stream.setFont(fontRegular, FONT_SIZE_FOOTER);
                stream.newLineAtOffset(PDRectangle.LETTER.getWidth() - MARGIN - pageTextWidth, MARGIN / 2);
                stream.showText(pageText);
                stream.endText();
            }
        }
    }


    private void insertIntegrationDiagram(String title, String processPath, String projectPath) throws IOException {
        insertIntegrationDiagram(title, processPath, projectPath, null);
    }

    private void insertIntegrationDiagram(String title, String processPath, String projectPath, String overrideStarterLabel) throws IOException {
        if (!config.getBooleanProperty("tibco.diagrams.enabled", true)) return;
        
        try {
            TibcoDiagramGenerator diagramGen = new TibcoDiagramGenerator();
            File projectRoot = new File(projectPath);
            File processFile = new File(processPath);
            
            if (!processFile.isAbsolute()) {
                // Fix: Check if processPath starts with the project directory name to avoid duplication
                String startName = projectRoot.getName() + File.separator;
                String altStartName = projectRoot.getName() + "/";
                
                String effectivePath = processPath;
                if (effectivePath.startsWith(startName)) {
                    effectivePath = effectivePath.substring(startName.length());
                } else if (effectivePath.startsWith(altStartName)) {
                    effectivePath = effectivePath.substring(altStartName.length());
                }
                
                processFile = new File(projectRoot, effectivePath);
            }
            String absProcessPath = processFile.getAbsolutePath();

            // Derive service name from flow name or title (for root node label)
            String serviceName = processPath;
            if(serviceName.contains("/")) serviceName = serviceName.substring(serviceName.lastIndexOf('/')+1);
            if(serviceName.contains("\\")) serviceName = serviceName.substring(serviceName.lastIndexOf('\\')+1);
            
            byte[] imageBytes = diagramGen.generateIntegrationDiagram(serviceName, absProcessPath, projectRoot, overrideStarterLabel);
            
            if (imageBytes != null && imageBytes.length > 0) {
                // Ensure space on page
                checkPageSpace(150); 
                
                // Draw Heading (use title directly as it now contains the full numbered string)
                drawText(title, fontBold, FONT_SIZE_H4);
                currentY -= 15;

                drawDiagramImage(imageBytes, title);
            }
        } catch (Exception e) {
             logger.warn("Failed to generate integration diagram for {}", title, e);
        }
    }
    
    private void insertFlowDiagram(FlowInfo flow) throws IOException {
        if (!config.getBooleanProperty("tibco.diagrams.enabled", true)) return;
        
        // Diagram is drawn under the current header (which we just added)
        // No need to add bookmark here if the header was already added before calling this.
        
        // String puml = diagramGenerator.generateFlowPuml(flow); // This line was in the instruction but not in original code, keeping original logic.
        
        // Generate Image from PUML
        try {
            String fPath = flow.getFileName();
            if (fPath == null) return;
            
            File processFile = new File(fPath);
            byte[] imageBytes = diagramGenerator.generateFlowDiagram(processFile);
            if (imageBytes != null && imageBytes.length > 0) {
                 checkPageSpace(200); 
                 
                 drawText("Flow Diagram: " + flow.getName(), fontBold, FONT_SIZE_H4);
                 currentY -= 15;
                 
                 drawDiagramImage(imageBytes, flow.getName());
            }
        } catch (Exception e) {
            logger.warn("Failed to generate flow diagram for {}", flow.getName(), e);
        }
    }

    private void drawDiagramImage(byte[] imageBytes, String title) throws IOException {
        // Convert to BufferedImage
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        // Create PDF Image
        PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);
        
        // Get available width
        float availableWidth = PDRectangle.LETTER.getWidth() - (2 * MARGIN);
        // Get available height on a FULL page (max possible height for an image)
        float maxPageHeight = PDRectangle.LETTER.getHeight() - (2 * MARGIN) - 50; 
        
        float imgWidth = pdImage.getWidth();
        float imgHeight = pdImage.getHeight();
        
        float scale = 1.0f;
        
        // 1. Scale to fit Width
        if (imgWidth > availableWidth) {
            scale = availableWidth / imgWidth;
        }
        
        // 2. Check if scaled height fits in Max Page Height
        float scaledHeight = imgHeight * scale;
        if (scaledHeight > maxPageHeight) {
            // If still too tall, scale down based on Height
            scale = scale * (maxPageHeight / scaledHeight);
        }
        
        float finalWidth = imgWidth * scale;
        float finalHeight = imgHeight * scale;
        
        // Calculate available space on current page
        float availableSpaceOnCurrentPage = currentY - MARGIN;
        
        // Check if image fits on current page
        if (finalHeight > availableSpaceOnCurrentPage) {
            newPage();
            currentY = PDRectangle.LETTER.getHeight() - MARGIN;
            
            // Double check fit on new page
            float availableSpaceOnNewPage = currentY - MARGIN;
            if (finalHeight > availableSpaceOnNewPage) {
                 float additionalScale = availableSpaceOnNewPage / finalHeight;
                 finalWidth *= additionalScale;
                 finalHeight *= additionalScale;
            }
        }
        
        // Draw Image
        contentStream.drawImage(pdImage, MARGIN, currentY - finalHeight, finalWidth, finalHeight);
        currentY -= (finalHeight + 20);
        
        if (scale < 0.5f) {
            drawText("(Diagram scaled to fit page)", fontItalic, 8, Color.GRAY);
            currentY -= 10;
        }
    }
    
    // ========== Helper Methods for SectionContext ==========
    
    /**
     * Get the current Y coordinate for section context calculations
     */
    float getCurrentY() {
        return currentY;
    }
    
    /**
     * Estimate the height needed for a table with given data
     * @param data Table data rows
     * @return Estimated height in points
     */
    float estimateTableHeight(String[][] data) {
        if (data == null || data.length == 0) {
            return 0;
        }
        
        // Conservative estimate:
        // - Header row: 25px
        // - Each data row: 20px (average, some may be taller with wrapping)
        // - Padding: 20px before + 5px after
        float headerHeight = 25;
        float rowHeight = 20;
        float padding = 25; // 20 before + 5 after
        
        float estimatedHeight = headerHeight + (data.length * rowHeight) + padding;
        
        // Cap at reasonable maximum for first page (if table is huge, it will span pages)
        float maxFirstPageHeight = 400; // Leave room for header and other content
        return Math.min(estimatedHeight, maxFirstPageHeight);
    }
}

