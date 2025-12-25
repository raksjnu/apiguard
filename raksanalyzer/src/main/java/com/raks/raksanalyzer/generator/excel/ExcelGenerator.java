package com.raks.raksanalyzer.generator.excel;

import com.raks.raksanalyzer.core.config.ConfigurationManager;
import com.raks.raksanalyzer.domain.model.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Excel report generator using Apache POI.
 * 
 * Generates multi-sheet workbook with:
 * - Summary sheet (project info, statistics)
 * - Flows sheet (all flows/processes)
 * - Components sheet (all components)
 * - Properties sheet (multi-environment columns)
 */
public class ExcelGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ExcelGenerator.class);
    
    private final ConfigurationManager config = ConfigurationManager.getInstance();
    private Workbook workbook;
    private CellStyle headerStyle;
    private CellStyle dataStyle;
    private CellStyle titleStyle;
    
    /**
     * Generate Excel report from analysis result.
     */
    public Path generate(AnalysisResult result) throws IOException {
        logger.info("Generating Excel report for analysis: {}", result.getAnalysisId());
        
        workbook = new XSSFWorkbook();
        createStyles();
        
        // Create sheets
        createSummarySheet(result);
        createFlowsSheet(result);
        createComponentsSheet(result);
        createPropertiesSheet(result);
        
        // Save workbook
        Path outputPath = getOutputPath(result);
        try (FileOutputStream out = new FileOutputStream(outputPath.toFile())) {
            workbook.write(out);
        }
        
        workbook.close();
        logger.info("Excel report generated: {}", outputPath);
        
        return outputPath;
    }
    
    /**
     * Create cell styles.
     */
    private void createStyles() {
        // Header style (purple background, white text, bold)
        headerStyle = workbook.createCellStyle();
        
        // Purple color (similar to UI theme #663399)
        headerStyle.setFillForegroundColor(IndexedColors.VIOLET.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        Font headerFont = workbook.createFont();
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // Title style (larger, bold)
        titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);
        
        // Data style (borders, wrap text)
        dataStyle = workbook.createCellStyle();
        dataStyle.setBorderBottom(BorderStyle.THIN);
        dataStyle.setBorderTop(BorderStyle.THIN);
        dataStyle.setBorderLeft(BorderStyle.THIN);
        dataStyle.setBorderRight(BorderStyle.THIN);
        dataStyle.setWrapText(true);
        dataStyle.setVerticalAlignment(VerticalAlignment.TOP);
    }
    
    /**
     * Create summary sheet.
     */
    private void createSummarySheet(AnalysisResult result) {
        Sheet sheet = workbook.createSheet("Summary");
        int rowNum = 0;
        
        // Title
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("RaksAnalyzer - Project Analysis Report");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));
        rowNum++;
        
        // Project Information
        ProjectInfo projectInfo = result.getProjectInfo();
        if (projectInfo != null) {
            addLabelValueRow(sheet, rowNum++, "Project Name", projectInfo.getProjectName());
            addLabelValueRow(sheet, rowNum++, "Project Type", projectInfo.getProjectType().getDisplayName());
            addLabelValueRow(sheet, rowNum++, "Project Path", projectInfo.getProjectPath());
            if (projectInfo.getVersion() != null) {
                addLabelValueRow(sheet, rowNum++, "Version", projectInfo.getVersion());
            }
            if (projectInfo.getMuleVersion() != null) {
                addLabelValueRow(sheet, rowNum++, "Mule Version", projectInfo.getMuleVersion());
            }
            if (projectInfo.getTibcoVersion() != null) {
                addLabelValueRow(sheet, rowNum++, "Tibco Version", projectInfo.getTibcoVersion());
            }
            rowNum++;
        }
        
        // Analysis Information
        addLabelValueRow(sheet, rowNum++, "Analysis ID", result.getAnalysisId());
        
        // Format date with timezone
        java.time.ZonedDateTime zonedDateTime = result.getStartTime().atZone(java.time.ZoneId.systemDefault());
        String formattedDate = zonedDateTime.format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
        );
        addLabelValueRow(sheet, rowNum++, "Analysis Date", formattedDate);
        
        addLabelValueRow(sheet, rowNum++, "Duration (ms)", String.valueOf(result.getDurationMillis()));
        addLabelValueRow(sheet, rowNum++, "Status", result.isSuccess() ? "SUCCESS" : "FAILED");
        rowNum++;
        
        // Statistics
        Row statsHeaderRow = sheet.createRow(rowNum++);
        Cell statsHeaderCell = statsHeaderRow.createCell(0);
        statsHeaderCell.setCellValue("Statistics");
        statsHeaderCell.setCellStyle(titleStyle);
        
        addLabelValueRow(sheet, rowNum++, "Total Flows/Processes", String.valueOf(result.getFlows().size()));
        addLabelValueRow(sheet, rowNum++, "Total Components", String.valueOf(countTotalComponents(result)));
        addLabelValueRow(sheet, rowNum++, "Total Properties", String.valueOf(result.getProperties().size()));
        
        // Auto-size columns
        for (int i = 0; i < 4; i++) {
            sheet.autoSizeColumn(i);
        }
    }
    
    /**
     * Create flows sheet.
     */
    private void createFlowsSheet(AnalysisResult result) {
        Sheet sheet = workbook.createSheet("Flows");
        int rowNum = 0;
        
        // Header
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Flow Name", "Type", "File Name", "Description", "Component Count"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Data rows
        for (FlowInfo flow : result.getFlows()) {
            Row row = sheet.createRow(rowNum++);
            
            createDataCell(row, 0, flow.getName());
            createDataCell(row, 1, flow.getType());
            createDataCell(row, 2, flow.getFileName());
            createDataCell(row, 3, flow.getDescription());
            createDataCell(row, 4, String.valueOf(flow.getComponents().size()));
        }
        
        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        
        // Add AutoFilter to the header row
        if (rowNum > 0) {
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, headers.length - 1));
        }
    }
    
    /**
     * Create components sheet.
     */
    private void createComponentsSheet(AnalysisResult result) {
        Sheet sheet = workbook.createSheet("Components");
        int rowNum = 0;
        
        // Header
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Flow Name", "Component Name", "Type", "Category", "Config Ref", "Attributes"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Data rows
        for (FlowInfo flow : result.getFlows()) {
            for (ComponentInfo component : flow.getComponents()) {
                Row row = sheet.createRow(rowNum++);
                
                createDataCell(row, 0, flow.getName());
                createDataCell(row, 1, component.getName());
                createDataCell(row, 2, component.getType());
                createDataCell(row, 3, component.getCategory());
                createDataCell(row, 4, component.getConfigRef());
                createDataCell(row, 5, formatAttributes(component));
            }
        }
        
        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        
        // Add AutoFilter to the header row
        if (rowNum > 0) {
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, headers.length - 1));
        }
    }
    
    /**
     * Create properties sheet with one column per properties file.
     * Column A: PROPERTY_NAME
     * Column B+: PROPERTY_VALUE (relative/path/to/file.properties)
     */
    private void createPropertiesSheet(AnalysisResult result) {
        Sheet sheet = workbook.createSheet("Properties");
        int rowNum = 0;
        
        // Get list of property files
        List<String> propertyFiles = result.getPropertyFiles();
        
        logger.info("=== Creating Properties Sheet ===");
        logger.info("Total properties: {}", result.getProperties().size());
        logger.info("Property files: {}", propertyFiles);
        
        // Header row
        Row headerRow = sheet.createRow(rowNum++);
        int colNum = 0;
        
        // Column A: Property Name
        createHeaderCell(headerRow, colNum++, "PROPERTY_NAME");
        
        // Columns B+: One column per properties file
        for (String filePath : propertyFiles) {
            // Header format: PROPERTY_VALUE (src\main\resources\mule-app.properties)
            String header = "PROPERTY_VALUE (" + filePath + ")";
            createHeaderCell(headerRow, colNum++, header);
        }
        
        // Data rows
        for (PropertyInfo property : result.getProperties()) {
            Row row = sheet.createRow(rowNum++);
            colNum = 0;
            
            // Column A: Property name
            createDataCell(row, colNum++, property.getKey());
            
            // Columns B+: Property value from each file
            for (String filePath : propertyFiles) {
                String value = property.getEnvironmentValues().get(filePath);
                if (value == null || value.trim().isEmpty()) {
                    value = "PROPERTY_NOT_FOUND";
                }
                createDataCell(row, colNum++, value);
            }
        }
        
        // Auto-size columns with max width of 50
        for (int i = 0; i < colNum; i++) {
            sheet.autoSizeColumn(i);
            
            // Limit column width to 50 characters (50 * 256)
            int currentWidth = sheet.getColumnWidth(i);
            int maxWidth = 50 * 256;
            if (currentWidth > maxWidth) {
                sheet.setColumnWidth(i, maxWidth);
            }
        }
        
        // Add AutoFilter to the header row
        if (rowNum > 0) {
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, colNum - 1));
        }
        
        logger.info("Properties sheet created with {} rows and {} columns", rowNum, colNum);
    }
    
    /**
     * Add label-value row to sheet.
     */
    private void addLabelValueRow(Sheet sheet, int rowNum, String label, String value) {
        Row row = sheet.createRow(rowNum);
        
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        CellStyle boldStyle = workbook.createCellStyle();
        boldStyle.setFont(boldFont);
        labelCell.setCellStyle(boldStyle);
        
        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value != null ? value : "");
    }
    
    /**
     * Create header cell with style.
     */
    private void createHeaderCell(Row row, int colNum, String value) {
        Cell cell = row.createCell(colNum);
        cell.setCellValue(value);
        cell.setCellStyle(headerStyle);
    }
    
    /**
     * Create data cell with style.
     */
    private void createDataCell(Row row, int colNum, String value) {
        Cell cell = row.createCell(colNum);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(dataStyle);
    }
    
    /**
     * Format component attributes as string.
     */
    private String formatAttributes(ComponentInfo component) {
        if (component.getAttributes().isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        component.getAttributes().forEach((key, value) -> {
            if (sb.length() > 0) sb.append("; ");
            sb.append(key).append("=").append(value);
        });
        
        return sb.toString();
    }
    
    /**
     * Count total components across all flows.
     */
    private int countTotalComponents(AnalysisResult result) {
        return result.getFlows().stream()
            .mapToInt(flow -> flow.getComponents().size())
            .sum();
    }
    
    /**
     * Get output path for Excel file.
     */
    private Path getOutputPath(AnalysisResult result) {
        // Use output directory from result (set based on input source type)
        String outputDir = result.getOutputDirectory();
        if (outputDir == null || outputDir.isEmpty()) {
            // Fallback to config if not set
            outputDir = config.getProperty("framework.output.directory", "./output");
        }
        
        // Get project name from result
        String projectName = "analysis";
        if (result.getProjectInfo() != null && result.getProjectInfo().getProjectName() != null) {
            projectName = result.getProjectInfo().getProjectName()
                .replaceAll("[^a-zA-Z0-9-_]", "_"); // Sanitize filename
        }
        
        // Format timestamp as YYYYMMDD_HHmmss
        String timestamp = result.getStartTime()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        
        String fileName = String.format("%s_%s.xlsx", projectName, timestamp);
        return Paths.get(outputDir, fileName);
    }
}
