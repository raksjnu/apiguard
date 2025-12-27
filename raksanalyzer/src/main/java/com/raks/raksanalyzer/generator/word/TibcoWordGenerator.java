package com.raks.raksanalyzer.generator.word;

import com.raks.raksanalyzer.core.config.ConfigurationManager;
import com.raks.raksanalyzer.domain.model.*;
import com.raks.raksanalyzer.domain.model.tibco.*;
import com.raks.raksanalyzer.generator.tibco.TibcoDiagramGenerator;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.regex.*;

/**
 * Tibco Word document generator.
 * Generates comprehensive documentation for Tibco BusinessWorks 5.x projects.
 */
public class TibcoWordGenerator {
    private static final Logger logger = LoggerFactory.getLogger(TibcoWordGenerator.class);
    
    private XWPFDocument document;
    private final ConfigurationManager config;
    private final List<Integer> sectionNumbers = new ArrayList<>();
    private List<TibcoGlobalVariable> currentProperties;
    private final TibcoDiagramGenerator diagramGenerator = new TibcoDiagramGenerator();
    
    public TibcoWordGenerator() {
        this.config = ConfigurationManager.getInstance();
        this.sectionNumbers.add(0); // Initialize with level 0
    }
    
    /**
     * Generate Tibco Word document.
     */
    public String generate(AnalysisResult result) {
        logger.info("Starting Tibco Word document generation");
        
        try {
            // Initialize global variables for property resolution
            this.currentProperties = new ArrayList<>();
            Map<String, List<TibcoGlobalVariable>> gvs = result.getGlobalVariables();
            if (gvs != null) {
                for (List<TibcoGlobalVariable> list : gvs.values()) {
                    this.currentProperties.addAll(list);
                }
            }

            loadTemplate();
            

            createCoverPage(result);
            createTableOfContents();
            

            if (config.getBooleanProperty("word.tibco.section.project.info.enabled", true)) {
                createProjectInformationSection(result);
            }
            
            if (config.getBooleanProperty("word.tibco.section.starter.processes.enabled", true)) {
                createStarterProcessesSection(result);
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
            

            createFooter();
            

            String outputPath = saveDocument(result);
            logger.info("Tibco Word document generated successfully: {}", outputPath);
            
            return outputPath;
            
        } catch (Exception e) {
            logger.error("Failed to generate Tibco Word document", e);
            throw new RuntimeException("Tibco Word document generation failed", e);
        }
    }
    
    /**
     * Load Word template or create new document.
     */
    private void loadTemplate() throws IOException {
        boolean useTemplate = config.getBooleanProperty("word.tibco.template.use", true);
        String templatePath = config.getProperty("word.tibco.template.path", "template/Tibco Project Documentation Template.docx");
        
        if (useTemplate && Files.exists(Paths.get(templatePath))) {
            try (FileInputStream fis = new FileInputStream(templatePath)) {
                document = new XWPFDocument(fis);
            logger.info("Loaded Tibco Word template from file: {}", templatePath);
            }
        } else {
            // Try to load from classpath
            InputStream templateStream = getClass().getClassLoader().getResourceAsStream(templatePath);
            if (templateStream != null) {
                try {
                    document = new XWPFDocument(templateStream);
                    logger.info("Loaded Tibco Word template from classpath: {}", templatePath);
                } catch (IOException e) {
                    logger.error("Failed to load template from classpath", e);
                    document = new XWPFDocument();
                }
            } else {
                document = new XWPFDocument();
                logger.info("Created new Tibco Word document (template not found: {})", templatePath);
            }
        }
    }
    
    /**
     * Create cover page.
     */
    private void createCoverPage(AnalysisResult result) {
        XWPFParagraph titlePara = document.createParagraph();
        titlePara.setAlignment(ParagraphAlignment.CENTER);
        titlePara.setSpacingAfter(400);
        
        XWPFRun titleRun = titlePara.createRun();
        titleRun.setText(config.getProperty("word.tibco.cover.title", "Tibco BusinessWorks Project Analysis"));
        titleRun.setBold(true);
        titleRun.setFontSize(24);
        titleRun.addBreak();
        titleRun.addBreak();
        

        XWPFRun projectRun = titlePara.createRun();
        projectRun.setText(result.getProjectInfo().getProjectName());
        projectRun.setFontSize(18);
        projectRun.addBreak();
        projectRun.addBreak();
        

        String description = config.getProperty("word.tibco.cover.description", 
            "Comprehensive analysis and documentation of Tibco BW 5.x project");
        XWPFRun descRun = titlePara.createRun();
        descRun.setText(description);
        descRun.setFontSize(12);
        descRun.addBreak();
        descRun.addBreak();
        

        XWPFRun dateRun = titlePara.createRun();
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
        String formattedDate = now.atZone(java.time.ZoneId.systemDefault()).format(formatter);
        dateRun.setText("Generated: " + formattedDate);
        dateRun.setFontSize(10);
        

        document.createParagraph().createRun().addBreak(BreakType.PAGE);
    }
    
    /**
     * Create table of contents with proper field code.
     */
    private void createTableOfContents() {
        XWPFParagraph tocPara = document.createParagraph();
        XWPFRun tocRun = tocPara.createRun();
        tocRun.setText("Table of Contents");
        tocRun.setBold(true);
        tocRun.setFontSize(16);
        

        XWPFParagraph tocField = document.createParagraph();
        CTSimpleField toc = tocField.getCTP().addNewFldSimple();
        toc.setInstr("TOC \\o \"1-3\" \\h \\z \\u");
        toc.setDirty(true); // Force update on open
        
        document.createParagraph().createRun().addBreak(BreakType.PAGE);
    }
    
    /**
     * Section 1: Project Information.
     */
    private void createProjectInformationSection(AnalysisResult result) {
        incrementSection(0);
        createHeading1(getSectionNumber() + " Project Information");
        
        ProjectInfo info = result.getProjectInfo();
        

        incrementSection(1);
        createHeading2(getSectionNumber() + " Basic Information");
        
        XWPFTable basicTable = createTableWithHeader("Attribute", "Value");
        
        addTableRow(basicTable, "Project Name", info.getProjectName());
        addTableRow(basicTable, "Project Type", "Tibco BusinessWorks 5.x");
        

        String projectPath = info.getProjectPath();
        try {
            projectPath = java.nio.file.Paths.get(projectPath).toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            logger.warn("Failed to normalize project path: {}", projectPath, e);
        }
        addTableRow(basicTable, "Project Path", projectPath);
        

        if (info.getTibcoVersion() != null && !info.getTibcoVersion().isEmpty()) {
            addTableRow(basicTable, "TIBCO Version", info.getTibcoVersion());
        }
        
        document.createParagraph(); // Spacing
        

        incrementSection(1);
        createHeading2(getSectionNumber() + " Project Statistics");
        
        XWPFTable statsTable = createTableWithHeader("Metric", "Count");
        

        int totalProcesses = result.getFlows() != null ? result.getFlows().size() : 0;
        int totalActivities = result.getComponents() != null ? result.getComponents().size() : 0;
        

        if (totalActivities == 0 && result.getFlows() != null) {
            for (FlowInfo flow : result.getFlows()) {
                if (flow.getComponents() != null) {
                    totalActivities += flow.getComponents().size();
                }
            }
        }
        

        int starterProcesses = 0;
        int nonStarterProcesses = 0;
        if (result.getFlows() != null) {
            for (FlowInfo flow : result.getFlows()) {
                if (isStarterProcess(flow)) {
                    starterProcesses++;
                } else {
                    nonStarterProcesses++;
                }
            }
        }
        
        if (totalProcesses > 0) {
            addTableRow(statsTable, "Total Processes", String.valueOf(totalProcesses));
            if (starterProcesses > 0) {
                addTableRow(statsTable, "  - Starter Processes", String.valueOf(starterProcesses));
                

                Map<String, Integer> starterTypeCounts = new HashMap<>();
                if (result.getFlows() != null) {
                    for (FlowInfo flow : result.getFlows()) {
                        if (isStarterProcess(flow)) {

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
                    addTableRow(statsTable, "    • " + type, String.valueOf(starterTypeCounts.get(type)));
                }
            }
            if (nonStarterProcesses > 0) {
                addTableRow(statsTable, "  - Non-Starter Processes", String.valueOf(nonStarterProcesses));
            }
        }
        
        if (totalActivities > 0) {
            addTableRow(statsTable, "Total Activities", String.valueOf(totalActivities));
        }
        

        Map<String, Integer> serviceTypeCounts = (Map<String, Integer>) result.getMetadata("serviceTypeCounts");
        if (serviceTypeCounts != null && !serviceTypeCounts.isEmpty()) {
            int soapCount = serviceTypeCounts.getOrDefault("SOAP", 0);
            int restCount = serviceTypeCounts.getOrDefault("REST", 0);
            int agentCount = serviceTypeCounts.getOrDefault("ServiceAgent", 0);
            
            if (soapCount > 0) {
                addTableRow(statsTable, "SOAP Services", String.valueOf(soapCount));
            }
            if (restCount > 0) {
                addTableRow(statsTable, "REST Services", String.valueOf(restCount));
            }
            if (agentCount > 0) {
                addTableRow(statsTable, "Service Agents", String.valueOf(agentCount));
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
                addTableRow(statsTable, entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        

        Map<String, Integer> adapterUsage = (Map<String, Integer>) result.getMetadata("adapterUsage");
        if (adapterUsage != null && !adapterUsage.isEmpty()) {
            int totalAdapters = adapterUsage.values().stream().mapToInt(Integer::intValue).sum();
            addTableRow(statsTable, "Total Adapters", String.valueOf(totalAdapters));
        }
        
        Map<String, Integer> pluginUsage = (Map<String, Integer>) result.getMetadata("pluginUsage");
        if (pluginUsage != null && !pluginUsage.isEmpty()) {
            int totalPlugins = pluginUsage.values().stream().mapToInt(Integer::intValue).sum();
            addTableRow(statsTable, "Total Plugins", String.valueOf(totalPlugins));
        }
        

        Map<String, List<TibcoGlobalVariable>> globalVars = result.getGlobalVariables();
        if (globalVars != null && !globalVars.isEmpty()) {
            int totalGVFiles = globalVars.size();
            int totalGVCount = globalVars.values().stream().mapToInt(List::size).sum();
            
            addTableRow(statsTable, "Global Variables Files", String.valueOf(totalGVFiles));
            addTableRow(statsTable, "Total Global Variables", String.valueOf(totalGVCount));
        }
        

        Map<String, Integer> fileTypeCounts = (Map<String, Integer>) result.getMetadata("fileTypeCounts");
        if (fileTypeCounts != null && !fileTypeCounts.isEmpty()) {
            int totalFiles = fileTypeCounts.values().stream().mapToInt(Integer::intValue).sum();
            addTableRow(statsTable, "Total Files", String.valueOf(totalFiles));
            

            int processFiles = fileTypeCounts.getOrDefault(".process", 0);
            int archiveFiles = fileTypeCounts.getOrDefault(".archive", 0);
            int substvarFiles = fileTypeCounts.getOrDefault(".substvar", 0);
            
            if (processFiles > 0) {
                addTableRow(statsTable, "  - Process Files (.process)", String.valueOf(processFiles));
            }
            if (archiveFiles > 0) {
                addTableRow(statsTable, "  - Archive Files (.archive)", String.valueOf(archiveFiles));
            }
            if (substvarFiles > 0) {
                addTableRow(statsTable, "  - Variable Files (.substvar)", String.valueOf(substvarFiles));
            }
        }
        
        document.createParagraph(); // Spacing
    }
    
    /**
     * Section 2: Tibco Services (Refactored).
     * Includes Process Summary and dynamic detail sections for each starter type.
     */
    private void createStarterProcessesSection(AnalysisResult result) {
        incrementSection(0);
        createHeading1(getSectionNumber() + " Tibco Services");
        

        createStarterSummaryTable(result);
        

        createDynamicStarterSections(result);
        
        document.createParagraph();
    }
    
    private void createStarterSummaryTable(AnalysisResult result) {

        incrementSection(1); 
        createHeading2(getSectionNumber() + " Services Summary");
        
        List<Map<String, String>> summaryData = new ArrayList<>();
        

        for (FlowInfo flow : result.getFlows()) {
            if (isStarterProcess(flow)) {
                String rawType = getRawStarterType(flow);
                String friendlyType = getFriendlyStarterType(rawType);
                if (flow.getDescription() != null && flow.getDescription().contains("REST Service")) {
                    friendlyType = "REST Service";
                }
                
                Map<String, String> row = new HashMap<>();
                row.put("Name", flow.getName());
                row.put("Type", friendlyType);
                summaryData.add(row);
            }
        }
        

        List<Map<String, Object>> serviceAgents = (List<Map<String, Object>>) result.getMetadata().get("serviceAgents");
        if (serviceAgents != null) {
            for (Map<String, Object> agent : serviceAgents) {
                Map<String, String> row = new HashMap<>();
                row.put("Name", (String) agent.getOrDefault("name", "Unknown Agent"));
                row.put("Type", "Service Agent");
                summaryData.add(row);
            }
        }
        





        
        if (summaryData.isEmpty()) {
            document.createParagraph().createRun().setText("No starter processes or services found.");
        } else {

            summaryData.sort(Comparator.comparing((Map<String, String> m) -> m.get("Type"))
                .thenComparing(m -> m.get("Name")));
            
            XWPFTable table = createTableWithHeader("Service/Process Name", "Type");
            for (Map<String, String> row : summaryData) {
                addTableRow(table, row.get("Name"), row.get("Type"));
            }
        }
        
        document.createParagraph();
    }
    
    private void createDynamicStarterSections(AnalysisResult result) {

        Map<String, List<FlowInfo>> flowsByType = new HashMap<>();
        
        for (FlowInfo flow : result.getFlows()) {
            if (isStarterProcess(flow)) {
                String rawType = getRawStarterType(flow);
                String friendlyType = getFriendlyStarterType(rawType);
                

                if (flow.getDescription() != null && flow.getDescription().contains("REST Service")) {
                    friendlyType = "REST Service";
                }
                

                if (friendlyType.equals("RestAdapterActivity")) friendlyType = "REST Service";

                flowsByType.computeIfAbsent(friendlyType, k -> new ArrayList<>()).add(flow);
            }
        }
        

        List<Map<String, Object>> serviceAgents = (List<Map<String, Object>>) result.getMetadata().get("serviceAgents");
        if (serviceAgents != null && !serviceAgents.isEmpty()) {

        }
        







        

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
        

        // AE Adapters - handle each type as a separate section with specialized metadata display
        if (flowsByType.containsKey("Adapter Subscriber")) {
            createAdapterServicesSection(flowsByType.remove("Adapter Subscriber"), result);
        }
        if (flowsByType.containsKey("Adapter Service")) {
            createAdapterServicesSection(flowsByType.remove("Adapter Service"), result);
        }
        

        List<String> remainingTypes = new ArrayList<>(flowsByType.keySet());
        Collections.sort(remainingTypes);
        
        for (String type : remainingTypes) {
            createGenericStarterSection(type + "s", flowsByType.get(type), result);
        }
    }
    
    private void createGenericStarterSection(String sectionTitle, List<FlowInfo> flows, AnalysisResult result) {
        if (flows == null || flows.isEmpty()) return;
        

        incrementSection(1); 
        createHeading2(getSectionNumber() + " " + sectionTitle);
        

        List<String> interestingKeys = getInterestingKeysForType(sectionTitle);
        
        int subIndex = 1;
        for (FlowInfo flow : flows) {
            createHeading3(getSectionNumber() + "." + subIndex + " " + flow.getName());
            
            Map<String, String> config = flow.getStarterConfig();
            if (config.isEmpty()) {
                document.createParagraph().createRun().setText("(No configuration detected)");
            } else {
                XWPFTable table = createTableWithHeader("Property", "Value");
                
                List<String> sortedKeys = new ArrayList<>(config.keySet());
                Collections.sort(sortedKeys);
                
                for (String key : sortedKeys) {
                    if (interestingKeys == null || interestingKeys.contains(key)) {
                        String displayKey = key.substring(key.lastIndexOf(".") + 1);
                        String val = resolveGlobalVariable(config.get(key), currentProperties);
                        
                        if ("fullsource".equals(displayKey) && val.length() > 0) {
                            String[] lines = val.split("\r?\n");
                            if (lines.length > 3) {
                                val = String.join("\n", Arrays.copyOf(lines, 3)) + "\n... (Source truncated)";
                            }
                        }
                        
                        addTableRow(table, displayKey, val);
                    }
                }
            }
            document.createParagraph();
            
            // Integration Diagram
            String projectPath = result.getProjectInfo().getProjectPath();
            String relPath = flow.getRelativePath();
            
            subIndex++; // Increment for sibling diagram section
            String diagramTitle = getSectionNumber() + "." + subIndex + " " + flow.getName() + " Integration";
            insertIntegrationDiagram(diagramTitle, relPath, projectPath);

            subIndex++;
        }
        document.createParagraph();
        document.createParagraph();
    }



    private List<String> getInterestingKeysForType(String typeTitle) {
        if (typeTitle.contains("File")) {
            return Arrays.asList("fileName", "pollInterval", "mode", "encoding", "sortby", "includeCurrent", "excludePattern");
        } else if (typeTitle.contains("Timer") || typeTitle.contains("Scheduler")) {
            return Arrays.asList("StartTime", "Frequency", "TimeInterval", "FrequencyIndex");
        } else if (typeTitle.contains("JMS")) {
            return Arrays.asList("SessionAttributes.destination", "ConfigurableHeaders.JMSDeliveryMode", "ConnectionReference", "ApplicationProperties", "SessionAttributes.acknowledgeMode");
        } else if (typeTitle.contains("Adapter")) {
             return Arrays.asList("transport.type", "transport.jms.destination", "transport.jms.connectionReference", "subject", "endpoint");
        }
        return null; // Return null to show ALL keys for unknown types
    }
    
    private String getRawStarterType(FlowInfo flow) {
        if (flow.getDescription() != null && flow.getDescription().contains("Type: ")) {
            return flow.getDescription().substring(flow.getDescription().indexOf("Type: ") + 6).trim();
        }
        return "";
    }
    
    private String getFriendlyStarterType(String rawType) {
        if (rawType.contains("FileEventSource")) return "File Poller";
        if (rawType.contains("JMSQueueEventSource")) return "JMS Queue Receiver";
        if (rawType.contains("JMSTopicEventSource")) return "JMS Topic Subscriber";
        if (rawType.contains("TimerEventSource")) return "Timer";
        if (rawType.contains("SOAPEventSource")) return "SOAP Service";
        if (rawType.contains("AESubscriberActivity")) return "Adapter Subscriber";
        if (rawType.contains("AERPCServerActivity")) return "Adapter Service";
        if (rawType.contains("HTTPEventSource")) return "HTTP Receiver";
        if (rawType.contains("RendezvousSubscriber")) return "RV Subscriber";
        if (rawType.contains("OnStartupEventSource")) return "On-Start Up";
        if (rawType.contains("RestAdapterActivity")) return "REST Service";
        if (rawType.isEmpty()) return "Unknown";
        return rawType; 
    }

    private void createSoapServicesSection(List<FlowInfo> flows, AnalysisResult result) {
        incrementSection(1);
        createHeading2(getSectionNumber() + " SOAP Services");
        
        int subIndex = 1;
        for (FlowInfo flow : flows) {
            createHeading3(getSectionNumber() + "." + subIndex + " " + flow.getName());
            
            XWPFTable table = createTableWithHeader("Service Process", "Operation", "Service Address", "SOAP Action");
            Map<String, String> config = flow.getStarterConfig();
            
            String op = resolveGlobalVariable(config.getOrDefault("operation", ""), currentProperties);
            String addr = resolveGlobalVariable(config.getOrDefault("serviceAddress", config.getOrDefault("httpURI", "")), currentProperties);
            String action = resolveGlobalVariable(config.getOrDefault("soapAction", ""), currentProperties);
            
            addTableRow(table, flow.getName(), op, addr, action);
            
            document.createParagraph();
            
            // Integration Diagram
            String projectPath = result.getProjectInfo().getProjectPath();
            String relPath = flow.getRelativePath();
            
            subIndex++;
            String diagramTitle = getSectionNumber() + "." + subIndex + " " + flow.getName() + " Integration";
            insertIntegrationDiagram(diagramTitle, relPath, projectPath);
            
            subIndex++;
        }
        document.createParagraph();
    }


    
    private void createServiceAgentsSection(List<Map<String, Object>> serviceAgents, AnalysisResult result) {
         incrementSection(1);
         createHeading2(getSectionNumber() + " Service Agents");
         
         
         // int idx = 1; // Removed unused
         for (Map<String, Object> agent : serviceAgents) {
            String name = (String) agent.getOrDefault("name", "Unknown");
            incrementSection(2);
            createHeading3(getSectionNumber() + " " + name);
            
            XWPFParagraph p = document.createParagraph();
            p.createRun().setText("Path: " + agent.getOrDefault("path", ""));
            p.createRun().addBreak();
            

            String wsdlLoc = (String) agent.getOrDefault("wsdlLocation", "");
            if (wsdlLoc != null && !wsdlLoc.isEmpty()) {
                 p.createRun().setText("WSDL Source: " + wsdlLoc);
                 p.createRun().addBreak();
            }

            String addr = resolveGlobalVariable((String)agent.getOrDefault("address", ""), currentProperties);
            p.createRun().setText("Address: " + addr);
            
            List<Map<String, String>> ops = (List<Map<String, String>>) agent.get("operations");
            if (ops != null) {
                XWPFTable table = createTableWithHeader("Operation", "Implementation Process", "SOAP Action");
                for (Map<String, String> op : ops) {
                    addTableRow(table, 
                        op.get("name"), 
                        op.get("implementation"), 
                        resolveGlobalVariable(op.get("soapAction"), currentProperties));
                        
                    // Integration Diagram per Operation
                    document.createParagraph();
                    
                    // Note: Ideally we want a unique number for each op diagram, 
                    // but 'idx' is for the Agent. We can't really make these siblings of the Agent 
                    // without breaking the Agent grouping. 
                    // However, the User demanded "Same Level".
                    // If we make it sibling, we break the "Service Agent" container concept?
                    // Assuming "Same Level" means H3.
                    // But if an Agent has multiple operations, we can't just increment 'idx' unless we split the Agent section?
                    
                    // Compromise: Use .x for Ops, but if User insists on "2.2.2", maybe just leave it as H4 but remove .1? 
                    // User Example: "2.2.2 :process name Integration"
                    // This implies the process itself is 2.2.1.
                    
                    // For Service Agents, we have 2.4.1 AgentName.
                    // If we add diagram, maybe 2.4.1.1 is correct, but users hated it?
                    // Let's stick to H4/Subsection for nested items like Operations inside an Agent.
                    // BUT remove the weird numbering if possible or just use a clearer label?
                    // Let's use H4 but with clearer hierarchy?
                    // OR: just follow the requested pattern: Sibling?
                    // 2.4.1 Agent
                    // 2.4.2 Agent Operation 1 Integration
                    // This separates content from header.
                    
                    // Let's increment idx.
                    // BUT we have a loop of operations.
                    // We can't easily make them all siblings of the Agent Header.
                    
                    // Let's fallback to "Subsection" style (H4) for Agents but clean Numbering for main sections.
                    // The User complaint was about "On-Start Up Processes" (Main Flows).
                    
                    // For consistency, let's keep Agents as H4 (Subsection) for now, OR:
                    // Just use Heading4 with proper sub-numbering.
                    incrementSection(2);
                    String diagramTitle = getSectionNumber() + " " + op.get("name") + " Integration";
                    // Just use the previous logic for Agents, effectively.
                    insertIntegrationDiagram(diagramTitle, op.get("implementation"), result.getProjectInfo().getProjectPath(), "Service Agent");
                }
            }
            document.createParagraph();
            // idx++; // Removed
         }
         document.createParagraph();
    }

    private void createRestServicesSection(AnalysisResult result, List<FlowInfo> flows) {
         incrementSection(1);
         createHeading2(getSectionNumber() + " REST Services");
         
         List<Map<String, Object>> restServices = (List<Map<String, Object>>) result.getMetadata("restServices");
         Set<String> processedPaths = new HashSet<>();
         
         if (restServices != null) {
             for (Map<String, Object> service : restServices) {
                String procName = (String) service.get("processName");
                processedPaths.add((String)service.get("processPath")); // Track processed
                
                // Increment subsection for Process Detail (e.g., 2.5.1)
                incrementSection(2);
                createHeading3(getSectionNumber() + " " + procName);
                
                XWPFParagraph p = document.createParagraph();
                p.createRun().setText("Process Path: " + service.get("processPath"));
                p.createRun().addBreak();
                p.createRun().setText("WADL Source: " + service.get("wadlSource"));
                
                List<Map<String, String>> bindings = (List<Map<String, String>>) service.get("bindings");
                if (bindings != null) {
                    XWPFTable table = createTableWithHeader("Method", "Resource Path", "Implementation Process");
                    for (Map<String, String> b : bindings) {
                        addTableRow(table, b.get("method"), b.get("resourcePath"), b.get("implementationProcess"));
                    }
                }
                
                // Add Integration Diagram for REST service
                if (this.config.getBooleanProperty("tibco.diagrams.enabled", true)) {
                    try {
                        String processPath = (String) service.get("processFile");
                        String projectPath = result.getProjectInfo().getProjectPath();
                        if (processPath != null && projectPath != null) {
                            File projectRoot = new File(projectPath);
                            // Normalize service name for PlantUML to avoid rendering issues with special characters
                            String normalizedName = normalizeForDiagram(procName);
                            byte[] img = diagramGenerator.generateIntegrationDiagram(normalizedName, processPath, projectRoot);
                            if (img != null && img.length > 0) {
                                // Increment subsection for integration diagram (e.g., 2.5.2)
                                incrementSection(2);
                                String processPathDisplay = (String) service.get("relativePath");
                                if (processPathDisplay == null) processPathDisplay = (String) service.get("processPath"); // Fallback
                                createHeading3(getSectionNumber() + " " + processPathDisplay + " Integration");
                                
                                // Add note if character normalization occurred
                                if (needsNormalization(procName)) {
                                    XWPFParagraph noteP = document.createParagraph();
                                    XWPFRun noteRun = noteP.createRun();
                                    noteRun.setItalic(true);
                                    noteRun.setFontSize(9);
                                    noteRun.setText("Note: Special characters in service name normalized for diagram rendering.");
                                }
                                
                                
                                // Use Mule-style dynamic scaling instead of fixed 500x400
                                BufferedImage buffImg = ImageIO.read(new ByteArrayInputStream(img));
                                int width = buffImg.getWidth();
                                int height = buffImg.getHeight();
                                
                                // Convert to EMU (1 px = 9525 EMU)
                                int widthEMU = width * 9525;
                                int heightEMU = height * 9525;
                                
                                // Scale to Fit Width (approx 6 inches = 5486400 EMU)
                                // Scale to Fit Height (approx 9 inches = 8229600 EMU)
                                int maxWidthEMU = 5486400;
                                int maxHeightEMU = 8229600;
                                
                                double scale = 1.0;
                                
                                // 1. Scale to fit width if needed
                                if (widthEMU > maxWidthEMU) {
                                    scale = (double) maxWidthEMU / widthEMU;
                                }
                                
                                // 2. Check if scaled height fits, if not scale down further
                                int scaledHeightEMU = (int) (heightEMU * scale);
                                if (scaledHeightEMU > maxHeightEMU) {
                                    scale = scale * ((double) maxHeightEMU / scaledHeightEMU);
                                }
                                
                                // Apply final scale
                                widthEMU = (int) (widthEMU * scale);
                                heightEMU = (int) (heightEMU * scale);
                                
                                XWPFParagraph pDiag = document.createParagraph();
                                pDiag.setAlignment(ParagraphAlignment.CENTER);
                                XWPFRun rDiag = pDiag.createRun();
                                rDiag.addPicture(new ByteArrayInputStream(img), XWPFDocument.PICTURE_TYPE_PNG, procName + "_integration.png", widthEMU, heightEMU);
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Error adding integration diagram for REST service {}", procName);
                    }
                }
                 document.createParagraph();
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
                      incrementSection(2);
                      createHeading3(getSectionNumber() + " " + flow.getName());
                      document.createParagraph().createRun().setText("Detected as REST Service but detailed metadata unavailable.");
                 }
             }
         }
         document.createParagraph();
    }
    
    private void createAdapterServicesSection(List<FlowInfo> flows, AnalysisResult result) {
        incrementSection(1);
        createHeading2(getSectionNumber() + " Adapter Services");
        
        
        // int idx = 1; // Removed unused
        for (FlowInfo flow : flows) {
            incrementSection(2);
            createHeading3(getSectionNumber() + " " + flow.getName());
             
            Map<String, String> config = flow.getStarterConfig();
            

            String adapterServicePath = config.getOrDefault("adapterService", 
                                        config.getOrDefault("ae.aepalette.sharedProperties.adapterService", "N/A"));
            
            XWPFParagraph pHeader = document.createParagraph();
            XWPFRun rSvc = pHeader.createRun();
            rSvc.setBold(true);
            rSvc.setText("Adapter Service: ");
            pHeader.createRun().setText(adapterServicePath);
            
            XWPFTable table = createTableWithHeader("Transport", "Subject / Destination", "Provider Url");
            
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
            
            subjectDest = resolveGlobalVariable(subjectDest, currentProperties);
            providerUrl = resolveGlobalVariable(providerUrl, currentProperties);
            
            


            
            addTableRow(table, displayTransport, subjectDest, providerUrl);
            document.createParagraph();

            // Add Integration Diagram for AE Adapter service
            if (this.config.getBooleanProperty("tibco.diagrams.enabled", true)) {
                try {
                    String fullPath = flow.getFileName();
                    String projectPath = result.getProjectInfo().getProjectPath();
                    if (projectPath != null) {
                        File projectRoot = new File(projectPath);
                        // Normalize service name for PlantUML to avoid rendering issues with special characters
                        String normalizedName = normalizeForDiagram(flow.getName());
                        byte[] img = diagramGenerator.generateIntegrationDiagram(normalizedName, fullPath, projectRoot);
                        if (img != null && img.length > 0) {
                            // Increment subsection for integration diagram (e.g., 2.7.2)
                            incrementSection(2);
                            String processPathDisplay = flow.getRelativePath() != null ? flow.getRelativePath() : flow.getFileName();
                            createHeading3(getSectionNumber() + " " + processPathDisplay + " Integration");
                            
                            // Add note if character normalization occurred
                            if (needsNormalization(flow.getName())) {
                                XWPFParagraph noteP = document.createParagraph();
                                XWPFRun noteRun = noteP.createRun();
                                noteRun.setItalic(true);
                                noteRun.setFontSize(9);
                                noteRun.setText("Note: Special characters in process name normalized for diagram rendering.");
                            }
                            
                            // Use Mule-style dynamic scaling
                            BufferedImage buffImg = ImageIO.read(new ByteArrayInputStream(img));
                            int width = buffImg.getWidth();
                            int height = buffImg.getHeight();
                            int widthEMU = width * 9525;
                            int heightEMU = height * 9525;
                            int maxWidthEMU = 5486400;
                            int maxHeightEMU = 8229600;
                            double scale = 1.0;
                            if (widthEMU > maxWidthEMU) scale = (double) maxWidthEMU / widthEMU;
                            int scaledHeightEMU = (int) (heightEMU * scale);
                            if (scaledHeightEMU > maxHeightEMU) scale = scale * ((double) maxHeightEMU / scaledHeightEMU);
                            widthEMU = (int) (widthEMU * scale);
                            heightEMU = (int) (heightEMU * scale);
                            
                            XWPFParagraph pDiag = document.createParagraph();
                            pDiag.setAlignment(ParagraphAlignment.CENTER);
                            XWPFRun rDiag = pDiag.createRun();
                            rDiag.addPicture(new ByteArrayInputStream(img), XWPFDocument.PICTURE_TYPE_PNG, flow.getName() + "_integration.png", widthEMU, heightEMU);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error adding integration diagram for AE adapter service {}", flow.getName());
                }
            }

            // idx++; // Removed
        }
        document.createParagraph();
    }

    
    /**
     * Section 4: Non-Starter Processes.
     */
    /**
     * Section 3: Tibco Processes (All Processes).
     * Includes 3.1 Processes Summary and 3.X Process Details.
     */
    private void createTibcoProcessesSection(AnalysisResult result) {
        incrementSection(0);
        createHeading1(getSectionNumber() + " Tibco Processes");
        

        createProcessStatsTable(result);
        

        createProcessesSummaryTable(result);
        

        createProcessDetailSections(result);
        
        document.createParagraph();
    }
    
    private void createProcessesSummaryTable(AnalysisResult result) {
        incrementSection(1);
        createHeading2(getSectionNumber() + " Processes Summary");
        
        XWPFTable table = createTableWithHeader("Process Name", "Starter", "Type");
        

        List<FlowInfo> flows = new ArrayList<>(result.getFlows());
        flows.sort(Comparator.comparing(FlowInfo::getName));
        
        for (FlowInfo flow : flows) {
             String relPath = flow.getRelativePath();
             if (relPath == null) relPath = flow.getName(); // Fallback
             
             String starter = "N/A";
             String type = "Normal Process";
             
             if (isStarterProcess(flow)) {

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
             
             addTableRow(table, relPath, starter, type);
        }
        document.createParagraph();
    }
    
    private void createProcessDetailSections(AnalysisResult result) {
         List<FlowInfo> flows = new ArrayList<>(result.getFlows());

         flows.sort(Comparator.comparing(f -> f.getRelativePath() != null ? f.getRelativePath() : f.getFileName()));
         
         boolean showConfig = config.getBooleanProperty("word.tibco.process.activity.configuration.details", true);
         boolean showBindings = config.getBooleanProperty("word.tibco.process.activity.inputBindings.details", true);
         
         for (FlowInfo flow : flows) {
             incrementSection(1);

             createHeading2(getSectionNumber() + " " + flow.getName());
             
             XWPFParagraph p = document.createParagraph();
             String relPath = flow.getRelativePath() != null ? flow.getRelativePath() : flow.getFileName();
             p.createRun().setText("Path: " + relPath);
             
             int activityIdx = 1;

             // Flow Diagram
             if (config.getBooleanProperty("tibco.diagrams.enabled", true)) {
                 try {
                     // Need absolute path for flow diagram
                     String fullPath = flow.getFileName(); // Usually absolute
                     byte[] img = diagramGenerator.generateFlowDiagram(new File(fullPath));
                     if (img != null && img.length > 0) {
                         // Use activityIdx to ensure unique, sequential numbering
                         createHeading3(getSectionNumber() + "." + activityIdx + " Flow Diagram");
                         // Use Mule-style dynamic scaling
                         BufferedImage buffImg = ImageIO.read(new ByteArrayInputStream(img));
                         int width = buffImg.getWidth();
                         int height = buffImg.getHeight();
                         int widthEMU = width * 9525;
                         int heightEMU = height * 9525;
                         int maxWidthEMU = 5486400;
                         int maxHeightEMU = 8229600;
                         double scale = 1.0;
                         if (widthEMU > maxWidthEMU) scale = (double) maxWidthEMU / widthEMU;
                         int scaledHeightEMU = (int) (heightEMU * scale);
                         if (scaledHeightEMU > maxHeightEMU) scale = scale * ((double) maxHeightEMU / scaledHeightEMU);
                         widthEMU = (int) (widthEMU * scale);
                         heightEMU = (int) (heightEMU * scale);
                         
                         XWPFParagraph pDiag = document.createParagraph();
                         pDiag.setAlignment(ParagraphAlignment.CENTER);
                         XWPFRun rDiag = pDiag.createRun();
                         rDiag.addPicture(new ByteArrayInputStream(img), XWPFDocument.PICTURE_TYPE_PNG, flow.getName() + "_flow.png", widthEMU, heightEMU);
                         activityIdx++;
                     }
                 } catch (Exception e) {
                     logger.warn("Error adding flow diagram for {}", flow.getName());
                 }
             }

             createProcessActivityStatsTable(flow);
             
             for (ComponentInfo activity : flow.getComponents()) {
                 String type = activity.getType();
                 String simpleType = type;
                 if (type != null && type.contains(".")) {
                     simpleType = type.substring(type.lastIndexOf(".") + 1);
                 }
                 

                 createHeading3(getSectionNumber() + "." + activityIdx + " " + activity.getName() + " ( " + simpleType + " )");
                 
                 XWPFParagraph pAct = document.createParagraph();
                 pAct.createRun().setText("Name: " + activity.getName());
                 pAct.createRun().addBreak();
                 pAct.createRun().setText("Type: " + simpleType);
                 

                 if (showConfig && !activity.getAttributes().isEmpty()) {
                     createHeading4(getSectionNumber() + "." + activityIdx + ".1 Configurations");
                     XWPFTable table = createTableWithHeader("Attribute Name", "Attribute Value");
                     

                     List<String> keys = new ArrayList<>(activity.getAttributes().keySet());
                     Collections.sort(keys);
                     
                      for (String key : keys) {
                          String val = resolveGlobalVariable(activity.getAttributes().get(key), currentProperties);
                          val = formatConfigValue(key, val);
                          addTableRow(table, key, val);
                      }
                     document.createParagraph();
                 }
                 

                 if (showBindings && !activity.getInputBindings().isEmpty()) {
                     createHeading4(getSectionNumber() + "." + activityIdx + ".2 Mappings");
                     XWPFTable table = createTableWithHeader("Attribute Name", "Attribute Value");
                     
                     List<String> keys = new ArrayList<>(activity.getInputBindings().keySet());
                     Collections.sort(keys);
                     
                     for (String key : keys) {
                         String val = resolveGlobalVariable(activity.getInputBindings().get(key), currentProperties);
                         addTableRow(table, key, val);
                     }
                     document.createParagraph();
                 }
                 
                 activityIdx++;
             }
             
             document.createParagraph();
         }
    }
    
    /**
     * Section 4: Connections & Configurations.
     */
    private void createConnectionsSection(AnalysisResult result) {
        incrementSection(0);
        createHeading1(getSectionNumber() + " Connections & Configurations");
        
        List<ResourceInfo> resources = result.getResources();
        
        if (resources == null || resources.isEmpty()) {
            document.createParagraph().createRun().setText("No connections or configurations found.");
            document.createParagraph();
            return;
        }
        

        Map<String, List<ResourceInfo>> groupedResources = resources.stream()
            .collect(Collectors.groupingBy(ResourceInfo::getType));
            

        List<String> sortedTypes = new ArrayList<>(groupedResources.keySet());
        Collections.sort(sortedTypes);
        
        for (String type : sortedTypes) {

            if (type.contains("Adapter") || type.contains("TIDManager")) {
                continue;
            }

            List<ResourceInfo> resourceList = groupedResources.get(type);
            

            incrementSection(1);
            createHeading2(getSectionNumber() + " " + type);
            

            resourceList.sort(Comparator.comparing(ResourceInfo::getName));
            
            int resourceIdx = 1;
            
            for (ResourceInfo res : resourceList) {

                createHeading3(getSectionNumber() + "." + resourceIdx + " " + res.getName());
                

                XWPFParagraph p = document.createParagraph();
                p.createRun().setText("Path: " + res.getRelativePath());
                

                Map<String, String> config = res.getConfiguration();
                if (config != null && !config.isEmpty()) {
                    XWPFTable table = createTableWithHeader("Property", "Value");
                    

                    List<String> sortedKeys = new ArrayList<>(config.keySet());
                    Collections.sort(sortedKeys);
                    
                    for (String key : sortedKeys) {
                        String val = config.get(key);

                        if (val != null && val.contains("%%")) {
                             val = resolveGlobalVariable(val, currentProperties);
                        }

                        val = formatConfigValue(key, val);
                        addTableRow(table, key, val);
                    }
                } else {
                     document.createParagraph().createRun().setText("No configuration details available.");
                }
                
                document.createParagraph();
                resourceIdx++;
            }
            
        }
        
        document.createParagraph();
    }
    
    /**
     * Section 6: Other Resources (Shared Variables, Job Shared Variables, Data Formats, Alias Libraries).
     */
    /**
     * Section 5: Adapters & Plugins.
     */
    @SuppressWarnings("unchecked")
    private void createAdaptersAndPluginsSection(AnalysisResult result) {
        incrementSection(0);
        createHeading1(getSectionNumber() + " Adapters & Plugins");
        

        List<ResourceInfo> resources = result.getResources();
        Map<String, List<ResourceInfo>> adapterResources = new HashMap<>();
        
        if (resources != null) {
            adapterResources = resources.stream()
                .filter(r -> r.getType().contains("Adapter") || r.getType().contains("TIDManager"))
                .collect(Collectors.groupingBy(ResourceInfo::getType));
        }
        

        Map<String, Integer> adapterUsage = (Map<String, Integer>) result.getMetadata("adapterUsage");
        boolean hasDetailedAdapters = !adapterResources.isEmpty();
        boolean hasUsageAdapters = adapterUsage != null && !adapterUsage.isEmpty();

        if (hasDetailedAdapters) {
            List<String> sortedTypes = new ArrayList<>(adapterResources.keySet());
            Collections.sort(sortedTypes);
            
            int subsectionIdx = 1;
            for (String type : sortedTypes) {
                List<ResourceInfo> list = adapterResources.get(type);
                list.sort(Comparator.comparing(ResourceInfo::getName));
                
                incrementSection(1);
                createHeading2(getSectionNumber() + " " + type);
                
                int resIdx = 1;
                for (ResourceInfo res : list) {
                    createHeading3(getSectionNumber() + "." + resIdx + " " + res.getName());
                    
                    XWPFParagraph p = document.createParagraph();
                    p.createRun().setText("Path: " + res.getRelativePath());
                    
                    Map<String, String> config = res.getConfiguration();
                    if (config != null && !config.isEmpty()) {
                        XWPFTable table = createTableWithHeader("Property", "Value");
                        List<String> keys = new ArrayList<>(config.keySet());
                        Collections.sort(keys);
                        
                        for (String key : keys) {
                            String val = config.get(key);
                            if (val != null && val.contains("%%")) {
                                val = resolveGlobalVariable(val, currentProperties);
                            }
                            val = formatConfigValue(key, val);
                            addTableRow(table, key, val);
                        }
                    } else {
                        document.createParagraph().createRun().setText("No configuration details available.");
                    }
                    document.createParagraph();
                    resIdx++;
                }
                subsectionIdx++;
            }
        } else if (hasUsageAdapters) {

            incrementSection(1);
            createHeading2(getSectionNumber() + " Adapters Summary");
            XWPFTable table = createTableWithHeader("Adapter Type", "Usage Count");
            adapterUsage.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> addTableRow(table, entry.getKey(), String.valueOf(entry.getValue())));
            document.createParagraph();
        } else {
             document.createParagraph().createRun().setText("No adapters found.");
             document.createParagraph();
        }


        Map<String, Integer> pluginUsage = (Map<String, Integer>) result.getMetadata("pluginUsage");
        if (pluginUsage != null && !pluginUsage.isEmpty()) {
             incrementSection(1);
            createHeading2(getSectionNumber() + " Plugins");
            
            XWPFTable table = createTableWithHeader("Plugin Type", "Usage Count");
            pluginUsage.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> addTableRow(table, entry.getKey(), String.valueOf(entry.getValue())));
                
            document.createParagraph();
        }
    }

    
    /**
     * Section 9: Global Variables.
     */
    private void createGlobalVariablesSection(AnalysisResult result) {
        incrementSection(0);
        

        Map<String, List<TibcoGlobalVariable>> globalVars = result.getGlobalVariables();
        
        if (globalVars == null || globalVars.isEmpty()) {
            createHeading1(getSectionNumber() + " Global Variables");
            document.createParagraph().createRun().setText("No global variables found.");
            document.createParagraph();
            return;
        }
        

        int totalCount = globalVars.values().stream()
            .mapToInt(List::size)
            .sum();
        

        createHeading1(getSectionNumber() + " Global Variables");
        

        XWPFParagraph totalPara = document.createParagraph();
        XWPFRun totalRun = totalPara.createRun();
        totalRun.setText("Total Global Variables Count: ");
        totalRun.setBold(false);
        
        XWPFRun countRun = totalPara.createRun();
        countRun.setText(String.valueOf(totalCount));
        countRun.setBold(true);
        countRun.setColor("0000FF"); // Blue color
        
        document.createParagraph(); // Spacing
        

        List<String> sortedGroups = new ArrayList<>(globalVars.keySet());
        Collections.sort(sortedGroups);
        
        int subIndex = 1;
        for (String group : sortedGroups) {
            List<TibcoGlobalVariable> vars = globalVars.get(group);
            if (vars == null || vars.isEmpty()) continue;
            

            createHeading2(getSectionNumber() + "." + subIndex + " " + group + " (Count: " + vars.size() + ")");
            

            vars.sort(Comparator.comparing(TibcoGlobalVariable::getName));
            

            XWPFTable table = createTableWithHeader("Name", "Value", "Type");
            
            for (TibcoGlobalVariable gv : vars) {
                String name = gv.getName();
                String value = gv.getValue();
                String type = abbreviateType(gv.getType());
                

                if (value == null) value = "";
                

                addTableRow(table, name, value, type);
            }
            
            document.createParagraph();
            subIndex++;
        }
        
        document.createParagraph();
    }

    private String abbreviateType(String type) {
        if (type == null) return "Str"; // Default
        String t = type.toLowerCase();
        if (t.startsWith("string")) return "Str";
        if (t.startsWith("password")) return "Pwd";
        if (t.startsWith("integer") || t.startsWith("int")) return "Int";
        if (t.startsWith("boolean") || t.startsWith("bool")) return "Bool";
        return type; // Fallback to original if unknown
    }
    
    /**
     * Section 11: Files Inventory.
     */
    @SuppressWarnings("unchecked")
    private void createFilesInventorySection(AnalysisResult result) {
        incrementSection(0);
        createHeading1(getSectionNumber() + " Files Inventory");
        

        Map<String, Integer> fileCountByType = (Map<String, Integer>) result.getMetadata("fileInventory.countByType");
        Map<String, Long> fileSizeByType = (Map<String, Long>) result.getMetadata("fileInventory.sizeByType");
        List<Map<String, String>> filesList = (List<Map<String, String>>) result.getMetadata("fileInventory.filesList");
        
        if (fileCountByType == null || fileCountByType.isEmpty()) {
            document.createParagraph().createRun().setText("No file inventory data available.");
            return;
        }
        

        enterSubsection();
        createHeading2(getSectionNumber() + " File Types Count");
        
        XWPFParagraph desc = document.createParagraph();
        desc.createRun().setText("Summary of project files grouped by type:");
        

        long totalActivities = 0;
        if (result.getFlows() != null) {
            totalActivities = result.getFlows().stream()
                .mapToLong(f -> f.getComponents().size())
                .sum();
        }
        XWPFParagraph activityCountPara = document.createParagraph();
        XWPFRun acRun = activityCountPara.createRun();
        acRun.setText("Total Activities Count: " + totalActivities);
        acRun.setBold(true);
        acRun.setColor("0000FF"); // Blue color for emphasis
        
        XWPFTable table = createTableWithHeader("File Type", "Count", "Total Size (KB)");
        

        fileCountByType.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                String fileType = entry.getKey();
                int count = entry.getValue();
                long sizeBytes = fileSizeByType.getOrDefault(fileType, 0L);
                long sizeKB = sizeBytes / 1024;
                
                addTableRow(table, fileType, String.valueOf(count), String.format("%,d", sizeKB));
            });
        
        document.createParagraph();
        

        if (filesList != null && !filesList.isEmpty()) {

            incrementSection(sectionNumbers.size() - 1);
            createHeading2(getSectionNumber() + " Files List");
            
            XWPFParagraph desc2 = document.createParagraph();
            desc2.createRun().setText("Complete list of all project files:");
            
            XWPFTable table2 = createTableWithHeader("File Name", "Relative Path");
            

            filesList.stream()
                .sorted((a, b) -> a.get("path").compareTo(b.get("path")))
                .forEach(fileInfo -> {
                    addTableRow(table2, fileInfo.get("name"), fileInfo.get("path"));
                });
            
            document.createParagraph();
            exitSubsection();
        }
    }
    
    /**
     * Section 10 (now 8): Archives.
     */
    @SuppressWarnings("unchecked")
    private void createArchivesSection(AnalysisResult result) {
        incrementSection(0);
        createHeading1(getSectionNumber() + " Archives");
        

        Object archivesObj = result.getMetadata("archives");
        if (archivesObj != null) {
            List<TibcoArchive> archives = (List<TibcoArchive>) archivesObj;
            if (!archives.isEmpty()) {
                enterSubsection();
                
                for (TibcoArchive ear : archives) {
                    createHeading2(getSectionNumber() + " " + ear.getName());
                    

                    XWPFParagraph p = document.createParagraph();
                    p.createRun().setText("Type: Enterprise Archive");
                    p.createRun().addBreak();
                    p.createRun().setText("Path: " + getRelativePath(result.getProjectInfo().getProjectPath(), ear.getFilePath()));
                    if (ear.getAuthor() != null) {
                        p.createRun().addBreak();
                        p.createRun().setText("Author: " + ear.getAuthor());
                    }
                    if (ear.getVersion() != null) {
                         p.createRun().addBreak();
                         p.createRun().setText("Version: " + ear.getVersion());
                    }
                    

                    if (ear.getChildren() != null && !ear.getChildren().isEmpty()) {
                        enterSubsection(); // Enter Level 3 (8.1.1) matches Child Archive
                        
                        for (TibcoArchive child : ear.getChildren()) {
                             String childType = child.getType() != null ? child.getType() : "UNKNOWN";

                             createHeading3(getSectionNumber() + " " + child.getName() + " (" + childType + ")");
                             
                             enterSubsection(); // Enter Level 4 (8.1.1.1) matches Content Categories
                             

                             createHeading4(getSectionNumber() + " Configuration");

                             XWPFParagraph cp = document.createParagraph();
                             cp.createRun().setText("Type: " + childType);
                             if (child.getAuthor() != null) {
                                 cp.createRun().addBreak();
                                 cp.createRun().setText("Author: " + child.getAuthor());
                             }
                             

                             incrementSection(sectionNumbers.size() - 1);
                             createHeading4(getSectionNumber() + " Processes");
                             if (!child.getProcessesIncluded().isEmpty()) {
                                 XWPFTable table = createTableWithHeader("Process Path");
                                 for (String proc : child.getProcessesIncluded()) {
                                     addTableRow(table, proc);
                                 }
                             } else {
                                 document.createParagraph().createRun().setText("No processes included.");
                             }
                             document.createParagraph();
                             

                             incrementSection(sectionNumbers.size() - 1);
                             createHeading4(getSectionNumber() + " Resources / Dependencies");
                             if (!child.getSharedResourcesIncluded().isEmpty()) {
                                 XWPFTable table = createTableWithHeader("Resource Path");
                                 for (String res : child.getSharedResourcesIncluded()) {
                                     addTableRow(table, res);
                                 }
                             } else {
                                 document.createParagraph().createRun().setText("No shared resources included.");
                             }
                             document.createParagraph();
                             

                             incrementSection(sectionNumbers.size() - 1);
                             createHeading4(getSectionNumber() + " Files");
                             if (!child.getFilesIncluded().isEmpty()) {
                                 XWPFTable table = createTableWithHeader("File Path");
                                 for (String f : child.getFilesIncluded()) {
                                     addTableRow(table, f);
                                 }
                             } else {
                                 document.createParagraph().createRun().setText("No files included.");
                             }
                             document.createParagraph();
                             

                             incrementSection(sectionNumbers.size() - 1);
                             createHeading4(getSectionNumber() + " Jars");
                             if (!child.getJarsIncluded().isEmpty()) {
                                 XWPFTable table = createTableWithHeader("Jar Path");
                                 for (String j : child.getJarsIncluded()) {
                                     addTableRow(table, j);
                                 }
                             } else {
                                 document.createParagraph().createRun().setText("No JARs included.");
                             }
                             document.createParagraph();
                             
                             exitSubsection(); // Exit Level 4
                             incrementSection(sectionNumbers.size() - 1); // Next Child
                        }
                        exitSubsection(); // Exit Level 3
                    }
                    
                    incrementSection(sectionNumbers.size() - 1); // Next EAR
                }
                
                exitSubsection();
                return;
            }
        }
        

        document.createParagraph().createRun().setText("No archive definitions found.");
    }
    

    private void createHeading4(String text) {
         XWPFParagraph p = document.createParagraph();
         p.setStyle("Heading4");
         XWPFRun r = p.createRun();
         r.setText(text);
         r.setBold(true);
         r.setFontSize(11); // Slightly smaller than H3
    }

    
    /**
     * Section 12: References.
     */
    private void createReferencesSection() {
        incrementSection(0);
        createHeading1(getSectionNumber() + " References");
        
        XWPFParagraph p = document.createParagraph();
        XWPFRun r = p.createRun();
        r.setText("References section is placeholder for any reference details team requires. It can have team playbook reference details or any other information for project.");
        r.setItalic(true);
        r.setColor("808080");
        
        document.createParagraph(); // Space
        
        XWPFParagraph ref1 = document.createParagraph();
        ref1.createRun().setText("• TIBCO ActiveMatrix BusinessWorks 5.x Documentation");
        
        XWPFParagraph ref2 = document.createParagraph();
        ref2.createRun().setText("• TIBCO Designer User Guide");
        
        XWPFParagraph ref3 = document.createParagraph();
        ref3.createRun().setText("• TIBCO Administrator Guide");
    }
    
    /**
     * Create footer with centered text and right-aligned page number.
     */
    private void createFooter() {
        XWPFHeaderFooter footer = document.createFooter(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
        

        XWPFTable footerTable = footer.createTable(1, 2);
        footerTable.setWidth("100%");
        

        XWPFTableCell leftCell = footerTable.getRow(0).getCell(0);
        leftCell.removeParagraph(0);
        XWPFParagraph leftPara = leftCell.addParagraph();
        leftPara.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun leftRun = leftPara.createRun();
        String footerText = config.getProperty("document.footer.text", "RaksAnalyzer ApiGuard Tool");
        leftRun.setText(footerText);
        leftRun.setFontSize(9);
        

        XWPFTableCell rightCell = footerTable.getRow(0).getCell(1);
        rightCell.removeParagraph(0);
        XWPFParagraph rightPara = rightCell.addParagraph();
        rightPara.setAlignment(ParagraphAlignment.RIGHT);
        
        XWPFRun r1 = rightPara.createRun();
        r1.setText("Page ");
        r1.setFontSize(9);
        
        rightPara.getCTP().addNewFldSimple().setInstr("PAGE");
        
        XWPFRun r2 = rightPara.createRun();
        r2.setText(" of ");
        r2.setFontSize(9);
        
        rightPara.getCTP().addNewFldSimple().setInstr("NUMPAGES");
        

        footerTable.setInsideHBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        footerTable.setInsideVBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        footerTable.setTopBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        footerTable.setBottomBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        footerTable.setLeftBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        footerTable.setRightBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
    }
    
    /**
     * Save document to file.
     */
    private String saveDocument(AnalysisResult result) throws IOException {
        String outputDir = result.getOutputDirectory();
        if (outputDir == null) {
            outputDir = config.getProperty("framework.output.directory", "./output");
        }
        
        Path outputPath = Paths.get(outputDir);
        Files.createDirectories(outputPath);
        
        String fileName = result.getProjectInfo().getProjectName() + "_Tibco_Documentation_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".docx";
        
        Path filePath = outputPath.resolve(fileName);
        
        try (FileOutputStream out = new FileOutputStream(filePath.toFile())) {
            document.write(out);
        }
        
        return filePath.toString();
    }
    

    
    private boolean isStarterProcess(FlowInfo flow) {

        return "starter-process".equals(flow.getType());
    }
    

    
    private void createHeading1(String text) {
        XWPFParagraph para = document.createParagraph();
        para.setStyle("Heading1");
        XWPFRun run = para.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(16);
    }
    
    private void createHeading2(String text) {
        XWPFParagraph para = document.createParagraph();
        para.setStyle("Heading2");
        XWPFRun run = para.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(14);
    }
    
    private void insertIntegrationDiagram(String title, String processPath, String projectPath) {
        insertIntegrationDiagram(title, processPath, projectPath, null);
    }

    private void insertIntegrationDiagram(String title, String processPath, String projectPath, String overrideStarterLabel) {
        if (!config.getBooleanProperty("tibco.diagrams.enabled", true)) return;
        
        try {
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
            
            // Derive service name from flow name or title
            String serviceName = processPath;
            if(serviceName.contains("/")) serviceName = serviceName.substring(serviceName.lastIndexOf('/')+1);
            if(serviceName.contains("\\")) serviceName = serviceName.substring(serviceName.lastIndexOf('\\')+1);
            
            byte[] imageBytes = diagramGenerator.generateIntegrationDiagram(serviceName, absProcessPath, projectRoot, overrideStarterLabel);
            
            if (imageBytes != null && imageBytes.length > 0) {
                 createHeading3(title); // Use Heading3 (Sibling level)
                 
                 XWPFParagraph p = document.createParagraph();
                 p.setAlignment(ParagraphAlignment.CENTER);
                 XWPFRun r = p.createRun();
                 
                 try (ByteArrayInputStream is = new ByteArrayInputStream(imageBytes)) {
                     // Get actual image dimensions (like Mule diagrams do)
                     BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
                     int width = img.getWidth();
                     int height = img.getHeight();
                     
                     // Convert to EMU (1 px = 9525 EMU)
                     int widthEMU = width * 9525;
                     int heightEMU = height * 9525;
                     
                     // Scale to Fit Width (approx 6 inches = 5486400 EMU)
                     // Scale to Fit Height (approx 9 inches = 8229600 EMU for page height)
                     int maxWidthEMU = 5486400;
                     int maxHeightEMU = 8229600;
                     
                     double scale = 1.0;
                     
                     // 1. Scale to fit width if needed
                     if (widthEMU > maxWidthEMU) {
                         scale = (double) maxWidthEMU / widthEMU;
                     }
                     
                     // 2. Check if scaled height fits, if not scale down further
                     int scaledHeightEMU = (int) (heightEMU * scale);
                     if (scaledHeightEMU > maxHeightEMU) {
                         scale = scale * ((double) maxHeightEMU / scaledHeightEMU);
                     }
                     
                     // Apply final scale
                     widthEMU = (int) (widthEMU * scale);
                     heightEMU = (int) (heightEMU * scale);
                     
                     // Add image to Word doc with proper dimensions
                     r.addPicture(new ByteArrayInputStream(imageBytes), XWPFDocument.PICTURE_TYPE_PNG, "Integration Diagram", 
                                  widthEMU, heightEMU);
                 }
            }
        } catch (Exception e) {
             logger.warn("Failed to generate diagram for {}", title, e);
        }
    }
    
    private void createHeading3(String text) {
        XWPFParagraph para = document.createParagraph();
        para.setStyle("Heading3");
        XWPFRun run = para.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(12);
    }


    
    /**
     * Helper to create a table with a header row, following Mule's pattern.
     * Creates a table with specific dimensions and populates the first row.
     */
    private XWPFTable createTableWithHeader(String... headers) {

        XWPFTable table = document.createTable(1, headers.length);
        table.setWidth("100%"); // Ensure table spans width
        

        XWPFTableRow headerRow = table.getRow(0);
        

        for (int i = 0; i < headers.length; i++) {
            XWPFTableCell cell = headerRow.getCell(i);
            cell.setText(headers[i]);
            styleTableHeaderCell(cell);
        }
        
        return table;
    }
    
    private void addTableRow(XWPFTable table, String... cells) {
        XWPFTableRow row = table.getRow(table.getNumberOfRows() - 1);
        



        boolean useCurrentRow = false;
        if (table.getNumberOfRows() == 1) {
            List<XWPFTableCell> rCells = row.getTableCells();

            if (rCells.size() == 1 && (rCells.get(0).getText() == null || rCells.get(0).getText().isEmpty())) {
                useCurrentRow = true;
            }
        }
        
        if (!useCurrentRow) {
            row = table.createRow();
        }
        
        for (int i = 0; i < cells.length; i++) {

            XWPFTableCell cell;
            if (i < row.getTableCells().size()) {
                cell = row.getCell(i);
            } else {
                cell = row.addNewTableCell();
            }
            cell.setText(cells[i] != null ? cells[i] : "");
            

            if (table.getRows().indexOf(row) == 0) {
                styleTableHeaderCell(cell);
            }
        }
    }
    
    /**
     * Style table header cell with background color and bold text.
     */
    private void styleTableHeaderCell(XWPFTableCell cell) {


        CTTcPr tcPr = cell.getCTTc().getTcPr();
        if (tcPr == null) {
            tcPr = cell.getCTTc().addNewTcPr();
        }
        

        CTShd shd = tcPr.isSetShd() ? tcPr.getShd() : tcPr.addNewShd();
        shd.setFill("E6E6FA");
        

        for (XWPFParagraph para : cell.getParagraphs()) {
            for (XWPFRun run : para.getRuns()) {
                run.setBold(true);
            }

            if (para.getRuns().isEmpty()) {
                String text = para.getText();
                if (text != null && !text.isEmpty()) {
                    XWPFRun run = para.createRun();
                    run.setText(text);
                    run.setBold(true);
                }
            }
        }
    }
    

    private void incrementSection(int level) {
        while (sectionNumbers.size() <= level) {
            sectionNumbers.add(0);
        }
        sectionNumbers.set(level, sectionNumbers.get(level) + 1);

        for (int i = level + 1; i < sectionNumbers.size(); i++) {
            sectionNumbers.set(i, 0);
        }
    }
    
    private void enterSubsection() {
        sectionNumbers.add(0);
        incrementSection(sectionNumbers.size() - 1);
    }
    
    private void exitSubsection() {
        if (sectionNumbers.size() > 1) {
            sectionNumbers.remove(sectionNumbers.size() - 1);
        }
    }
    
    /**
     * Get relative path from project root.
     */
    private String getRelativePath(String projectPath, String fullPath) {
        if (fullPath == null || projectPath == null) {
            return fullPath != null ? fullPath : "";
        }
        
        try {
            Path project = Paths.get(projectPath).normalize();
            Path full = Paths.get(fullPath).normalize();
            Path relative = project.relativize(full);
            return relative.toString().replace("\\", "/");
        } catch (Exception e) {
            return fullPath;
        }
    }
    
    private String getSectionNumber() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sectionNumbers.size(); i++) {
            if (sectionNumbers.get(i) > 0) {
                if (sb.length() > 0) sb.append(".");
                sb.append(sectionNumbers.get(i));
            }
        }
        return sb.toString();
    }
    
    /**
     * Resolve Global Variables in a string.
     * Replaces %%VarName%% with its value using robust matching.
     */
    private String resolveGlobalVariable(String value, List<TibcoGlobalVariable> globalVariables) {
        if (!config.getBooleanProperty("tibco.property.resolution.enabled", true)) {
            return value;
        }
        
        if (value == null || !value.contains("%%")) {
            return value;
        }
        
        // Use Regex to find all %%...%% placeholders
        Pattern pattern = Pattern.compile("%%([^%]+)%%");
        Matcher matcher = pattern.matcher(value);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String placeholderContent = matcher.group(1); // e.g., "RvService" or "Connections/FILE/inDir"
            String replacement = null;
            
            if (globalVariables != null) {
                for (TibcoGlobalVariable prop : globalVariables) {
                     String key = prop.getFullName(); // e.g. "defaultVars/SharedResources/RvService"
                     if (key == null) continue;
                     
                     // 1. Direct match
                     if (key.equals(placeholderContent)) {
                         replacement = prop.getValue();
                         break;
                     }
                     
                     // 2. Prefix match (if placeholder is just "SharedResources/RvService")
                     if (key.endsWith("/" + placeholderContent)) {
                         replacement = prop.getValue();
                         break;
                     }
                     
                     // 3. Normalized Match (Compare leaf names or parts)
                     // If key is "defaultVars/Connections/FILE/inDir", let's match "Connections/FILE/inDir"
                     if (key.contains(placeholderContent)) {
                         replacement = prop.getValue();
                         break;
                     }
                }
            }
            
            if (replacement != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } else {
                matcher.appendReplacement(sb, "%%" + Matcher.quoteReplacement(placeholderContent) + "%%");
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
    
    private void createProcessStatsTable(AnalysisResult result) {
        XWPFTable table = createTableWithHeader("Property", "Value");
        
        List<FlowInfo> flows = result.getFlows();
        int total = flows.size();
        int starterCount = 0;
        int normalCount = 0;
        Map<String, Integer> starterTypeCounts = new HashMap<>();
        
        for (FlowInfo flow : flows) {
            if (isStarterProcess(flow)) {
                starterCount++;
                String rawType = getRawStarterType(flow);

                String displayType = rawType;
                if (displayType != null && displayType.contains(".")) {
                    displayType = displayType.substring(displayType.lastIndexOf(".") + 1);
                }
                if (displayType == null || displayType.isEmpty()) {
                     displayType = "Unknown";
                }
                



                
                starterTypeCounts.put(displayType, starterTypeCounts.getOrDefault(displayType, 0) + 1);
            } else {
                normalCount++;
            }
        }
        
        addTableRow(table, "Total Process Count", String.valueOf(total));
        addTableRow(table, "Starter Process Count", String.valueOf(starterCount));
        

        List<String> sortedTypes = new ArrayList<>(starterTypeCounts.keySet());
        Collections.sort(sortedTypes);
        for (String type : sortedTypes) {
            String label = type.endsWith("Process") ? type + " Count" : type + " Process Count";
            addTableRow(table, label, String.valueOf(starterTypeCounts.get(type)));
        }
        
        addTableRow(table, "Normal Process Count", String.valueOf(normalCount));
        
        document.createParagraph();
    }
    
    private void createProcessActivityStatsTable(FlowInfo flow) {
        XWPFTable table = createTableWithHeader("Property", "Value");
        
        List<ComponentInfo> activities = flow.getComponents();
        addTableRow(table, "Total Activity Count", String.valueOf(activities.size()));
        
        Map<String, Integer> typeCounts = new HashMap<>();
        for (ComponentInfo c : activities) {
            String type = c.getType();

            if (type != null && type.contains(".")) {
                type = type.substring(type.lastIndexOf(".") + 1);
            }
            typeCounts.put(type, typeCounts.getOrDefault(type, 0) + 1);
        }
        
        List<String> sortedTypes = new ArrayList<>(typeCounts.keySet());
        Collections.sort(sortedTypes);
        
        for (String type : sortedTypes) {
             addTableRow(table, type + " type count", String.valueOf(typeCounts.get(type)));
        }
        
        document.createParagraph();
    }
    
    private String formatConfigValue(String key, String value) {
        if (value == null || value.isEmpty()) return value;
        
        if ("fullsource".equals(key)) {
            boolean showDetails = config.getBooleanProperty("word.tibco.java.sourcecode.details", false);
            if (!showDetails) {
                 return ""; // Blank as requested
            }
        }
        
        if ("bytecode".equals(key)) {
            boolean showDetails = config.getBooleanProperty("word.tibco.java.bytecode.details", false);
            if (!showDetails) {
                 return ""; // Blank as requested
            }
        }
        
        if ("WADLContent".equals(key)) {
            boolean showDetails = config.getBooleanProperty("word.tibco.rest.wadl.details", false);
            if (!showDetails) {
                 return ""; // Blank as requested
            }
        }
        
        return value;
    }
    
    private String truncateValue(String val) {
        if (val == null) return "";
        

        String[] lines = val.split("\r?\n");
        if (lines.length > 2) {
             return String.join("\n", Arrays.copyOf(lines, 2)) + "\n... [TRUNCATED " + (lines.length - 2) + " more lines]";
        }
        


        if (val.length() > 250) {
            return val.substring(0, 250) + " ... [TRUNCATED - Full Length: " + val.length() + "]";
        }
        
        return val;
    }
    
    /**
     * Normalizes special characters for PlantUML diagram generation.
     * Converts accented/special characters to their ASCII equivalents to avoid rendering issues.
     * @param text The text to normalize
     * @return Normalized text suitable for PlantUML
     */
    private String normalizeForDiagram(String text) {
        if (text == null) return null;
        
        // Normalize common special characters
        return text
            .replace("ü", "u").replace("Ü", "U")
            .replace("ö", "o").replace("Ö", "O")
            .replace("ä", "a").replace("Ä", "A")
            .replace("ñ", "n").replace("Ñ", "N")
            .replace("é", "e").replace("É", "E")
            .replace("è", "e").replace("È", "E")
            .replace("ê", "e").replace("Ê", "E")
            .replace("à", "a").replace("À", "A")
            .replace("â", "a").replace("Â", "A")
            .replace("ç", "c").replace("Ç", "C")
            .replace("ß", "ss");
    }
    
    /**
     * Checks if a string contains special characters that need normalization.
     * @param text The text to check
     * @return true if normalization is needed
     */
    private boolean needsNormalization(String text) {
        if (text == null) return false;
        return !text.equals(normalizeForDiagram(text));
    }
}
