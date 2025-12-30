package com.raks.raksanalyzer.generator.excel;
import com.raks.raksanalyzer.core.config.ConfigurationManager;
import com.raks.raksanalyzer.core.utils.XmlUtils;
import com.raks.raksanalyzer.domain.model.*;
import com.raks.raksanalyzer.domain.model.tibco.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
public class TibcoExcelGenerator {
    private static final Logger logger = LoggerFactory.getLogger(TibcoExcelGenerator.class);
    private static final int EXCEL_CELL_LIMIT = 32767; 
    private final ConfigurationManager config;
    private XSSFWorkbook workbook;
    private Map<String, CellStyle> styles;
    private List<PropertyInfo> currentProperties;
    public TibcoExcelGenerator() {
        this.config = ConfigurationManager.getInstance();
    }
    public String generate(AnalysisResult result) {
        logger.info("Starting TIBCO Excel document generation");
        try {
            this.currentProperties = result.getProperties();
            workbook = new XSSFWorkbook();
            createStyles();
            createSummaryTab(result);
            createProcessViewTab(result);  
            createRawExtractTab(result);   
            String outputPath = saveWorkbook(result);
            logger.info("TIBCO Excel document generated successfully: {}", outputPath);
            return outputPath;
        } catch (Exception e) {
            logger.error("Failed to generate TIBCO Excel document", e);
            throw new RuntimeException("Excel generation failed", e);
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException e) {
                    logger.warn("Failed to close workbook", e);
                }
            }
        }
    }
    private void createStyles() {
        styles = new HashMap<>();
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.LAVENDER.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font headerFont = workbook.createFont();
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.LEFT);  
        headerStyle.setVerticalAlignment(VerticalAlignment.TOP);  
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);
        headerStyle.setWrapText(true);
        styles.put("header", headerStyle);
        CellStyle sectionStyle = workbook.createCellStyle();
        sectionStyle.setFillForegroundColor(IndexedColors.SEA_GREEN.getIndex());
        sectionStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font sectionFont = workbook.createFont();
        sectionFont.setColor(IndexedColors.WHITE.getIndex());
        sectionFont.setBold(true);
        sectionFont.setFontHeightInPoints((short) 11);
        sectionStyle.setFont(sectionFont);
        sectionStyle.setAlignment(HorizontalAlignment.LEFT);
        sectionStyle.setVerticalAlignment(VerticalAlignment.TOP);
        sectionStyle.setBorderBottom(BorderStyle.THIN);
        sectionStyle.setBorderTop(BorderStyle.THIN);
        sectionStyle.setBorderLeft(BorderStyle.THIN);
        sectionStyle.setBorderRight(BorderStyle.THIN);
        styles.put("section", sectionStyle);
        CellStyle labelStyle = workbook.createCellStyle();
        Font labelFont = workbook.createFont();
        labelFont.setBold(true);
        labelStyle.setFont(labelFont);
        labelStyle.setAlignment(HorizontalAlignment.LEFT);
        labelStyle.setVerticalAlignment(VerticalAlignment.TOP);
        labelStyle.setBorderBottom(BorderStyle.THIN);
        labelStyle.setBorderLeft(BorderStyle.THIN);
        labelStyle.setBorderRight(BorderStyle.THIN);
        styles.put("label", labelStyle);
        CellStyle valueStyle = workbook.createCellStyle();
        valueStyle.setAlignment(HorizontalAlignment.LEFT);
        valueStyle.setVerticalAlignment(VerticalAlignment.TOP);
        valueStyle.setBorderBottom(BorderStyle.THIN);
        valueStyle.setBorderLeft(BorderStyle.THIN);
        valueStyle.setBorderRight(BorderStyle.THIN);
        valueStyle.setWrapText(true);
        styles.put("value", valueStyle);
        CellStyle numberStyle = workbook.createCellStyle();
        Font numberFont = workbook.createFont();
        numberFont.setBold(true);
        numberFont.setColor(IndexedColors.DARK_BLUE.getIndex());
        numberStyle.setFont(numberFont);
        numberStyle.setAlignment(HorizontalAlignment.RIGHT);
        numberStyle.setVerticalAlignment(VerticalAlignment.TOP);
        numberStyle.setBorderBottom(BorderStyle.THIN);
        numberStyle.setBorderLeft(BorderStyle.THIN);
        numberStyle.setBorderRight(BorderStyle.THIN);
        styles.put("number", numberStyle);
        CellStyle altRowStyle = workbook.createCellStyle();
        XSSFColor lightBlue = new XSSFColor(new byte[]{(byte)217, (byte)225, (byte)242}, null);
        ((XSSFCellStyle)altRowStyle).setFillForegroundColor(lightBlue);
        altRowStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        altRowStyle.setAlignment(HorizontalAlignment.LEFT);
        altRowStyle.setVerticalAlignment(VerticalAlignment.TOP);
        altRowStyle.setBorderBottom(BorderStyle.THIN);
        altRowStyle.setBorderLeft(BorderStyle.THIN);
        altRowStyle.setBorderRight(BorderStyle.THIN);
        altRowStyle.setWrapText(true);
        styles.put("altRow", altRowStyle);
    }
    private void createSummaryTab(AnalysisResult result) {
        XSSFSheet sheet = workbook.createSheet("Summary");
        sheet.createFreezePane(0, 1); 
        int rowNum = 0;
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("TIBCO BusinessWorks Project Analysis - Summary");
        titleCell.setCellStyle(styles.get("header"));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));
        rowNum++; 
        rowNum = createSummarySection(sheet, rowNum, "PROJECT OVERVIEW", result);
        rowNum++; 
        rowNum = createProcessStatsSection(sheet, rowNum, result);
        rowNum++; 
        rowNum = createServicesSection(sheet, rowNum, result);
        rowNum++; 
        rowNum = createResourcesSection(sheet, rowNum, result);
        for (int i = 0; i < 4; i++) {
            sheet.autoSizeColumn(i);
        }
    }
    private int createSummarySection(XSSFSheet sheet, int startRow, String title, AnalysisResult result) {
        int rowNum = startRow;
        Row headerRow = sheet.createRow(rowNum++);
        Cell headerCell = headerRow.createCell(0);
        headerCell.setCellValue(title);
        headerCell.setCellStyle(styles.get("section"));
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 3));
        ProjectInfo info = result.getProjectInfo();
        addSummaryRow(sheet, rowNum++, "Project Name", info.getProjectName());
        addSummaryRow(sheet, rowNum++, "Project Type", "TIBCO BusinessWorks 5.x");
        String projectPath = info.getProjectPath();
        if (projectPath.startsWith(".\\" )) {
            projectPath = projectPath.substring(2);
        } else if (projectPath.startsWith("./")) {
            projectPath = projectPath.substring(2);
        }
        addSummaryRow(sheet, rowNum++, "Project Path", projectPath);
        if (info.getTibcoVersion() != null && !info.getTibcoVersion().isEmpty()) {
            addSummaryRow(sheet, rowNum++, "TIBCO Version", info.getTibcoVersion());
        }
        LocalDateTime now = LocalDateTime.now();
        java.time.ZonedDateTime zonedNow = now.atZone(java.time.ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
        addSummaryRow(sheet, rowNum++, "Analysis Date", zonedNow.format(formatter));
        return rowNum;
    }
    private int createProcessStatsSection(XSSFSheet sheet, int startRow, AnalysisResult result) {
        int rowNum = startRow;
        Row headerRow = sheet.createRow(rowNum++);
        Cell headerCell = headerRow.createCell(0);
        headerCell.setCellValue("PROCESS STATISTICS");
        headerCell.setCellStyle(styles.get("section"));
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 3));
        int totalProcesses = result.getFlows() != null ? result.getFlows().size() : 0;
        int totalActivities = result.getComponents() != null ? result.getComponents().size() : 0;
        if (totalActivities == 0 && result.getFlows() != null) {
            for (FlowInfo flow : result.getFlows()) {
                if (flow.getComponents() != null) {
                    totalActivities += flow.getComponents().size();
                }
            }
        }
        int starterCount = 0;
        int nonStarterCount = 0;
        if (result.getFlows() != null) {
            for (FlowInfo flow : result.getFlows()) {
                if ("starter-process".equals(flow.getType())) {
                    starterCount++;
                } else {
                    nonStarterCount++;
                }
            }
        }
        addSummaryRow(sheet, rowNum++, "Total Processes", String.valueOf(totalProcesses));
        if (starterCount > 0) {
            addSummaryRow(sheet, rowNum++, "  - Starter Processes", String.valueOf(starterCount));
            Map<String, Integer> starterTypeCounts = new HashMap<>();
            if (result.getFlows() != null) {
                for (FlowInfo flow : result.getFlows()) {
                    if ("starter-process".equals(flow.getType())) {
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
                addSummaryRow(sheet, rowNum++, "    • " + type, String.valueOf(starterTypeCounts.get(type)));
            }
        }
        if (nonStarterCount > 0) {
            addSummaryRow(sheet, rowNum++, "  - Non-Starter Processes", String.valueOf(nonStarterCount));
        }
        addSummaryRow(sheet, rowNum++, "Total Activities", String.valueOf(totalActivities));
        return rowNum;
    }
    private int createServicesSection(XSSFSheet sheet, int startRow, AnalysisResult result) {
        int rowNum = startRow;
        Row headerRow = sheet.createRow(rowNum++);
        Cell headerCell = headerRow.createCell(0);
        headerCell.setCellValue("SERVICES & CONNECTIONS");
        headerCell.setCellStyle(styles.get("section"));
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 3));
        @SuppressWarnings("unchecked")
        Map<String, Integer> serviceTypeCounts = (Map<String, Integer>) result.getMetadata("serviceTypeCounts");
        if (serviceTypeCounts != null && !serviceTypeCounts.isEmpty()) {
            int soapCount = serviceTypeCounts.getOrDefault("SOAP", 0);
            int restCount = serviceTypeCounts.getOrDefault("REST", 0);
            int agentCount = serviceTypeCounts.getOrDefault("ServiceAgent", 0);
            if (soapCount > 0) addSummaryRow(sheet, rowNum++, "SOAP Services", String.valueOf(soapCount));
            if (restCount > 0) addSummaryRow(sheet, rowNum++, "REST Services", String.valueOf(restCount));
            if (agentCount > 0) addSummaryRow(sheet, rowNum++, "Service Agents", String.valueOf(agentCount));
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
                addSummaryRow(sheet, rowNum++, entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return rowNum;
    }
    private int createResourcesSection(XSSFSheet sheet, int startRow, AnalysisResult result) {
        int rowNum = startRow;
        Row headerRow = sheet.createRow(rowNum++);
        Cell headerCell = headerRow.createCell(0);
        headerCell.setCellValue("RESOURCES");
        headerCell.setCellStyle(styles.get("section"));
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 3));
        @SuppressWarnings("unchecked")
        Map<String, Integer> adapterUsage = (Map<String, Integer>) result.getMetadata("adapterUsage");
        if (adapterUsage != null && !adapterUsage.isEmpty()) {
            int totalAdapters = adapterUsage.values().stream().mapToInt(Integer::intValue).sum();
            addSummaryRow(sheet, rowNum++, "Total Adapters", String.valueOf(totalAdapters));
        }
        @SuppressWarnings("unchecked")
        Map<String, Integer> pluginUsage = (Map<String, Integer>) result.getMetadata("pluginUsage");
        if (pluginUsage != null && !pluginUsage.isEmpty()) {
            int totalPlugins = pluginUsage.values().stream().mapToInt(Integer::intValue).sum();
            addSummaryRow(sheet, rowNum++, "Total Plugins", String.valueOf(totalPlugins));
        }
        Map<String, List<TibcoGlobalVariable>> globalVars = result.getGlobalVariables();
        if (globalVars != null && !globalVars.isEmpty()) {
            int totalGVFiles = globalVars.size();
            int totalGVCount = globalVars.values().stream().mapToInt(List::size).sum();
            addSummaryRow(sheet, rowNum++, "Global Variables Files", String.valueOf(totalGVFiles));
            addSummaryRow(sheet, rowNum++, "Total Global Variables", String.valueOf(totalGVCount));
        }
        return rowNum;
    }
    private void addSummaryRow(XSSFSheet sheet, int rowNum, String label, String value) {
        Row row = sheet.createRow(rowNum);
        row.setHeightInPoints(20); 
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(styles.get("label"));
        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value);
        try {
            Integer.parseInt(value);
            valueCell.setCellStyle(styles.get("number"));
        } catch (NumberFormatException e) {
            valueCell.setCellStyle(styles.get("value"));
        }
    }
    private void createProcessViewTab(AnalysisResult result) {
        XSSFSheet sheet = workbook.createSheet("ProcessView");
        sheet.createFreezePane(0, 1); 
        int rowNum = 0;
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.setHeightInPoints(20); 
        String[] headers = {"ProcessName", "ActivityName", "ActivityType", "Config", "Mapping"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.get("header"));
        }
        String projectPath = result.getProjectInfo().getProjectPath();
        try {
            Path projectDir = Paths.get(projectPath);
            List<Path> processFiles = new ArrayList<>();
            Files.walk(projectDir)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".process"))
                .filter(path -> !path.toString().contains("target") && !path.toString().contains(".git"))
                .forEach(processFiles::add);
            for (Path processFile : processFiles) {
                try {
                    Document doc = XmlUtils.parseXmlFile(processFile);
                    Element root = doc.getDocumentElement();
                    String processName = projectDir.relativize(processFile).toString();
                    if (processName.startsWith(".\\")) {
                        processName = processName.substring(2);
                    } else if (processName.startsWith("./")) {
                        processName = processName.substring(2);
                    }
                    List<Element> activities = new ArrayList<>();
                    Element starter = XmlUtils.getFirstChildElement(root, "pd:starter");
                    if (starter != null) {
                        activities.add(starter);
                    }
                    activities.addAll(XmlUtils.getChildElements(root, "pd:activity"));
                    List<Element> groups = XmlUtils.getChildElements(root, "pd:group");
                    for (Element group : groups) {
                        activities.add(group);
                        activities.addAll(XmlUtils.getChildElements(group, "pd:activity"));
                    }
                    if (activities.isEmpty()) {
                        Row row = sheet.createRow(rowNum++);
                        row.setHeightInPoints(20);
                        boolean isAltRow = (rowNum % 2 == 0);
                        setCellValue(row, 0, processName, isAltRow);
                        setCellValue(row, 1, "[No Activities]", isAltRow);
                        setCellValue(row, 2, "N/A", isAltRow);
                        setCellValue(row, 3, "", isAltRow);
                        setCellValue(row, 4, "", isAltRow);
                    } else {
                        for (Element activity : activities) {
                            Row row = sheet.createRow(rowNum++);
                            row.setHeightInPoints(20);
                            boolean isAltRow = (rowNum % 2 == 0);
                            setCellValue(row, 0, processName, isAltRow);
                            String activityName = XmlUtils.getAttributeValue(activity, "name");
                            if (activityName == null) {
                                activityName = activity.getTagName().equals("pd:starter") ? "[Starter]" : "[Unnamed Activity]";
                            }
                            setCellValue(row, 1, activityName, isAltRow);
                            Element typeElem = XmlUtils.getFirstChildElement(activity, "pd:type");
                            if (typeElem == null) {
                                typeElem = XmlUtils.getFirstChildElement(activity, "pd:resourceType");
                            }
                            String activityType = typeElem != null ? XmlUtils.getElementText(typeElem) : "N/A";
                            setCellValue(row, 2, activityType, isAltRow);
                            Element configElem = XmlUtils.getFirstChildElement(activity, "config");
                            String config = configElem != null ? XmlUtils.elementToString(configElem) : "";
                            setCellValue(row, 3, config, isAltRow);
                            Element inputBindings = XmlUtils.getFirstChildElement(activity, "pd:inputBindings");
                            String mapping = inputBindings != null ? XmlUtils.elementToString(inputBindings) : "";
                            setCellValue(row, 4, mapping, isAltRow);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to process file: {} - {}", processFile, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to create ProcessView tab", e);
        }
        sheet.setAutoFilter(new CellRangeAddress(0, rowNum - 1, 0, headers.length - 1));
        sheet.setColumnWidth(0, 10000); 
        sheet.setColumnWidth(1, 8000);  
        sheet.setColumnWidth(2, 10000); 
        sheet.setColumnWidth(3, 15000); 
        sheet.setColumnWidth(4, 15000); 
    }
    private void createRawExtractTab(AnalysisResult result) {
        XSSFSheet sheet = workbook.createSheet("RawExtract");
        sheet.createFreezePane(0, 1); 
        int rowNum = 0;
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.setHeightInPoints(20); 
        String[] headers = {"FilePath", "ResourceType", "ProcessType", "Config", "RawContent"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.get("header"));
        }
        String projectPath = result.getProjectInfo().getProjectPath();
        @SuppressWarnings("unchecked")
        List<Map<String, String>> filesList = (List<Map<String, String>>) result.getMetadata("fileInventory.filesList");
        try {
            Path projectDir = Paths.get(projectPath);
            if (filesList != null && !filesList.isEmpty()) {
                logger.info("Processing {} files from inventory for RawExtract tab", filesList.size());
                for (Map<String, String> fileInfo : filesList) {
                    String relativePath = fileInfo.get("path");
                    if (relativePath == null) {
                        relativePath = fileInfo.get("relativePath");
                    }
                    if (relativePath == null) {
                        continue;
                    }
                    String normalizedPath = relativePath.replace("/", java.io.File.separator).replace("\\", java.io.File.separator);
                    Path file = projectDir.resolve(normalizedPath);
                    if (!java.nio.file.Files.exists(file)) {
                        String parentDirName = projectDir.getFileName().toString();
                        if (normalizedPath.startsWith(parentDirName + java.io.File.separator)) {
                             String strippedPath = normalizedPath.substring(parentDirName.length() + 1);
                             Path fixedFile = projectDir.resolve(strippedPath);
                             if (java.nio.file.Files.exists(fixedFile)) {
                                 file = fixedFile;
                             }
                        }
                    }
                    Row row = sheet.createRow(rowNum++);
                    row.setHeightInPoints(20);
                    boolean isAltRow = (rowNum % 2 == 0);
                    setCellValue(row, 0, relativePath, isAltRow);
                    processFileContent(file, row, isAltRow);
                }
            } else {
                logger.warn("fileInventory.filesList metadata not found, falling back to file walking");
                List<Path> allFiles = new ArrayList<>();
                Files.walk(projectDir)
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        return !fileName.startsWith(".") && 
                               !fileName.endsWith("~") &&
                               !path.toString().contains("target") &&
                               !path.toString().contains(".git");
                    })
                    .forEach(allFiles::add);
                for (Path file : allFiles) {
                    Row row = sheet.createRow(rowNum++);
                    row.setHeightInPoints(20);
                    boolean isAltRow = (rowNum % 2 == 0);
                    String relativePath = projectDir.relativize(file).toString();
                    if (relativePath.startsWith(".\\")) {
                        relativePath = relativePath.substring(2);
                    } else if (relativePath.startsWith("./")) {
                        relativePath = relativePath.substring(2);
                    }
                    setCellValue(row, 0, relativePath, isAltRow);
                    processFileContent(file, row, isAltRow);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to create RawExtract tab", e);
        }
        sheet.setAutoFilter(new CellRangeAddress(0, rowNum - 1, 0, headers.length - 1));
        sheet.setColumnWidth(0, 8000); 
        sheet.setColumnWidth(1, 8000); 
        sheet.setColumnWidth(2, 3000); 
        sheet.setColumnWidth(3, 15000); 
        sheet.setColumnWidth(4, 15000); 
    }
    private void processFileContent(Path file, Row row, boolean isAltRow) {
        try {
            String fileName = file.getFileName().toString();
            String extension = "";
            int lastDot = fileName.lastIndexOf('.');
            if (lastDot > 0) {
                extension = fileName.substring(lastDot + 1);
            }
            if (extension.matches("process|substvar|sharedvariable|sharedparse|sharedhttp|serviceagent|projlib")) {
                try {
                    Document doc = XmlUtils.parseXmlFile(file);
                    Element root = doc.getDocumentElement();
                    String resourceType = extractResourceType(root);
                    setCellValue(row, 1, resourceType, isAltRow);
                    String processType = extension.equals("process") ? determineProcessType(root) : "N/A";
                    setCellValue(row, 2, processType, isAltRow);
                    String config = extractConfig(root);
                    setCellValue(row, 3, (config == null || config.trim().isEmpty()) ? "N/A" : config, isAltRow);
                    readAndSetContent(file, row, isAltRow);
                } catch (Exception e) {
                    setCellValue(row, 1, extension.toUpperCase(), isAltRow);
                    setCellValue(row, 2, "N/A", isAltRow);
                    setCellValue(row, 3, "N/A", isAltRow);
                    readAndSetContent(file, row, isAltRow);
                }
            } else {
                setCellValue(row, 1, extension.isEmpty() ? "NO_EXTENSION" : extension.toUpperCase(), isAltRow);
                setCellValue(row, 2, "N/A", isAltRow);
                setCellValue(row, 3, "N/A", isAltRow);
                readAndSetContent(file, row, isAltRow);
            }
        } catch (Exception e) {
            logger.warn("Failed to process file: {} - {}", file, e.getMessage());
            setCellValue(row, 1, "ERROR", isAltRow);
            setCellValue(row, 2, "N/A", isAltRow);
            setCellValue(row, 3, "N/A", isAltRow);
            setCellValue(row, 4, "[Error processing file: " + e.getMessage() + "]", isAltRow);
        }
    }
    private void readAndSetContent(Path file, Row row, boolean isAltRow) {
        try {
            String content;
            try {
                content = Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                content = Files.readString(file, java.nio.charset.StandardCharsets.ISO_8859_1);
            }
            if (content.length() > 32767) {
                content = content.substring(0, 32764) + "...";
            }
            setCellValue(row, 4, content, isAltRow);
        } catch (Exception ex) {
            if (!java.nio.file.Files.exists(file)) {
                 setCellValue(row, 4, "[File not found: " + file.toAbsolutePath() + "]", isAltRow);
            } else {
                 setCellValue(row, 4, "[Unable to read file: " + ex.getMessage() + "]", isAltRow);
            }
        }
    }
    private void setCellValue(Row row, int col, String value, boolean isAltRow) {
        Cell cell = row.createCell(col);
        value = resolveGlobalVariable(value, currentProperties);
        if (value != null && value.length() > EXCEL_CELL_LIMIT) {
            value = value.substring(0, EXCEL_CELL_LIMIT - 50) + "\n...[TRUNCATED]";
        }
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(isAltRow ? styles.get("altRow") : styles.get("value"));
    }
    private String resolveGlobalVariable(String value, List<PropertyInfo> globalVariables) {
        if (!config.getBooleanProperty("tibco.property.resolution.enabled", true)) {
            return value;
        }
        if (value == null || !value.contains("%%") || globalVariables == null) {
            return value;
        }
        String resolved = value;
        for (PropertyInfo prop : globalVariables) {
            String fullKey = prop.getKey();
            String varValue = prop.getDefaultValue();
            if (varValue == null) continue;
            String placeholderFull = "%%" + fullKey + "%%";
            if (resolved.contains(placeholderFull)) {
                resolved = resolved.replace(placeholderFull, varValue);
            }
            if (fullKey.startsWith("defaultVars/")) {
                String rootVar = fullKey.substring("defaultVars/".length());
                String placeholderRoot = "%%" + rootVar + "%%";
                if (resolved.contains(placeholderRoot)) {
                    resolved = resolved.replace(placeholderRoot, varValue);
                }
            }
        }
        return resolved;
    }
    private String extractResourceType(Element root) {
        Element starter = XmlUtils.getFirstChildElement(root, "pd:starter");
        if (starter != null) {
            String resourceType = XmlUtils.getElementText(XmlUtils.getFirstChildElement(starter, "pd:resourceType"));
            if (resourceType != null) return resourceType;
        }
        Element activity = XmlUtils.getFirstChildElement(root, "pd:activity");
        if (activity != null) {
            String resourceType = XmlUtils.getElementText(XmlUtils.getFirstChildElement(activity, "pd:resourceType"));
            if (resourceType != null) return resourceType;
        }
        return "N/A";
    }
    private String determineProcessType(Element root) {
        Element starter = XmlUtils.getFirstChildElement(root, "pd:starter");
        if (starter != null) {
            return "Starter";
        }
        return "Normal";
    }
    private String extractConfig(Element root) {
        Element starter = XmlUtils.getFirstChildElement(root, "pd:starter");
        if (starter != null) {
            Element configElem = XmlUtils.getFirstChildElement(starter, "config");
            if (configElem != null) {
                return XmlUtils.elementToString(configElem);
            }
        }
        Element activity = XmlUtils.getFirstChildElement(root, "pd:activity");
        if (activity != null) {
            Element configElem = XmlUtils.getFirstChildElement(activity, "config");
            if (configElem != null) {
                return XmlUtils.elementToString(configElem);
            }
        }
        return "";
    }
    private String saveWorkbook(AnalysisResult result) throws IOException {
        String outputDir = result.getOutputDirectory();
        if (outputDir == null) {
            outputDir = config.getProperty("excel.output.directory", "output");
        }
        Path outputPath = Paths.get(outputDir);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }
        String projectName = result.getProjectInfo().getProjectName();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = String.format("%s_Analysis_%s.xlsx", projectName, timestamp);
        Path filePath = outputPath.resolve(fileName);
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            workbook.write(fos);
        }
        return filePath.toAbsolutePath().toString();
    }
}
