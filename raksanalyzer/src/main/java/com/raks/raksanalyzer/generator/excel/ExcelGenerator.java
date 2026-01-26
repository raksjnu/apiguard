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
public class ExcelGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ExcelGenerator.class);
    private final ConfigurationManager config = ConfigurationManager.getInstance();
    private Workbook workbook;
    private CellStyle headerStyle;
    private CellStyle dataStyle;
    private CellStyle titleStyle;
    public Path generate(AnalysisResult result) throws IOException {
        logger.info("Generating Excel report for analysis: {}", result.getAnalysisId());
        workbook = new XSSFWorkbook();
        createStyles();
        createSummarySheet(result);
        createFlowsSheet(result);
        createComponentsSheet(result);
        createPropertiesSheet(result);
        Path outputPath = getOutputPath(result);
        if (outputPath.getParent() != null && !java.nio.file.Files.exists(outputPath.getParent())) {
            java.nio.file.Files.createDirectories(outputPath.getParent());
        }
        try (FileOutputStream out = new FileOutputStream(outputPath.toFile())) {
            workbook.write(out);
        }
        workbook.close();
        logger.info("Excel report generated: {}", outputPath);
        return outputPath;
    }
    private void createStyles() {
        headerStyle = workbook.createCellStyle();
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
        titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);
        dataStyle = workbook.createCellStyle();
        dataStyle.setBorderBottom(BorderStyle.THIN);
        dataStyle.setBorderTop(BorderStyle.THIN);
        dataStyle.setBorderLeft(BorderStyle.THIN);
        dataStyle.setBorderRight(BorderStyle.THIN);
        dataStyle.setWrapText(true);
        dataStyle.setVerticalAlignment(VerticalAlignment.TOP);
    }
    private void createSummarySheet(AnalysisResult result) {
        Sheet sheet = workbook.createSheet("Summary");
        int rowNum = 0;
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("RaksAnalyzer - Project Analysis Report");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));
        rowNum++;
        ProjectInfo projectInfo = result.getProjectInfo();
        if (projectInfo != null) {
            addLabelValueRow(sheet, rowNum++, "Project Name", projectInfo.getProjectName());
            addLabelValueRow(sheet, rowNum++, "Project Type", projectInfo.getProjectType().getDisplayName());
            String displayPath = projectInfo.getProjectPath();
            if (result.getSourceUrl() != null && !result.getSourceUrl().isEmpty()) {
                displayPath = result.getSourceUrl();
            }
            addLabelValueRow(sheet, rowNum++, "Project Path", displayPath);
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
        addLabelValueRow(sheet, rowNum++, "Analysis ID", result.getAnalysisId());
        java.time.ZonedDateTime zonedDateTime = result.getStartTime().atZone(java.time.ZoneId.systemDefault());
        String formattedDate = zonedDateTime.format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
        );
        addLabelValueRow(sheet, rowNum++, "Analysis Date", formattedDate);
        addLabelValueRow(sheet, rowNum++, "Duration (ms)", String.valueOf(result.getDurationMillis()));
        addLabelValueRow(sheet, rowNum++, "Status", result.isSuccess() ? "SUCCESS" : "FAILED");
        rowNum++;
        Row statsHeaderRow = sheet.createRow(rowNum++);
        Cell statsHeaderCell = statsHeaderRow.createCell(0);
        statsHeaderCell.setCellValue("Statistics");
        statsHeaderCell.setCellStyle(titleStyle);
        addLabelValueRow(sheet, rowNum++, "Total Flows/Processes", String.valueOf(result.getFlows().size()));
        addLabelValueRow(sheet, rowNum++, "Total Components", String.valueOf(countTotalComponents(result)));
        addLabelValueRow(sheet, rowNum++, "Total Properties", String.valueOf(result.getProperties().size()));
        for (int i = 0; i < 4; i++) {
            sheet.autoSizeColumn(i);
        }
    }
    private void createFlowsSheet(AnalysisResult result) {
        Sheet sheet = workbook.createSheet("Flows");
        int rowNum = 0;
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Flow Name", "Type", "File Name", "Description", "Component Count"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        for (FlowInfo flow : result.getFlows()) {
            Row row = sheet.createRow(rowNum++);
            createDataCell(row, 0, flow.getName());
            createDataCell(row, 1, flow.getType());
            createDataCell(row, 2, flow.getFileName());
            createDataCell(row, 3, flow.getDescription());
            createDataCell(row, 4, String.valueOf(flow.getComponents().size()));
        }
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        if (rowNum > 0) {
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, headers.length - 1));
        }
    }
    private void createComponentsSheet(AnalysisResult result) {
        Sheet sheet = workbook.createSheet("Components");
        int rowNum = 0;
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Flow Name", "Component Name", "Type", "Category", "Config Ref", "Attributes"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
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
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        if (rowNum > 0) {
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, headers.length - 1));
        }
    }
    private void createPropertiesSheet(AnalysisResult result) {
        Sheet sheet = workbook.createSheet("Properties");
        int rowNum = 0;
        List<String> propertyFiles = result.getPropertyFiles();
        logger.info("=== Creating Properties Sheet ===");
        logger.info("Total properties: {}", result.getProperties().size());
        logger.info("Property files: {}", propertyFiles);
        Row headerRow = sheet.createRow(rowNum++);
        int colNum = 0;
        createHeaderCell(headerRow, colNum++, "PROPERTY_NAME");
        for (String filePath : propertyFiles) {
            String header = "PROPERTY_VALUE (" + filePath + ")";
            createHeaderCell(headerRow, colNum++, header);
        }
        for (PropertyInfo property : result.getProperties()) {
            Row row = sheet.createRow(rowNum++);
            colNum = 0;
            createDataCell(row, colNum++, property.getKey());
            for (String filePath : propertyFiles) {
                String value = property.getEnvironmentValues().get(filePath);
                if (value == null || value.trim().isEmpty()) {
                    value = "PROPERTY_NOT_FOUND";
                }
                createDataCell(row, colNum++, value);
            }
        }
        for (int i = 0; i < colNum; i++) {
            sheet.autoSizeColumn(i);
            int currentWidth = sheet.getColumnWidth(i);
            int maxWidth = 50 * 256;
            if (currentWidth > maxWidth) {
                sheet.setColumnWidth(i, maxWidth);
            }
        }
        if (rowNum > 0) {
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, colNum - 1));
        }
        logger.info("Properties sheet created with {} rows and {} columns", rowNum, colNum);
    }
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
    private void createHeaderCell(Row row, int colNum, String value) {
        Cell cell = row.createCell(colNum);
        cell.setCellValue(value);
        cell.setCellStyle(headerStyle);
    }
    private void createDataCell(Row row, int colNum, String value) {
        Cell cell = row.createCell(colNum);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(dataStyle);
    }
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
    private int countTotalComponents(AnalysisResult result) {
        return result.getFlows().stream()
            .mapToInt(flow -> flow.getComponents().size())
            .sum();
    }
    private Path getOutputPath(AnalysisResult result) {
        String outputDir = result.getOutputDirectory();
        if (outputDir == null || outputDir.isEmpty()) {
            outputDir = config.getProperty("framework.output.directory", "./output");
        }
        String projectName = "analysis";
        if (result.getProjectInfo() != null && result.getProjectInfo().getProjectName() != null) {
            projectName = result.getProjectInfo().getProjectName()
                .replaceAll("[^a-zA-Z0-9-_]", "_"); 
        }
        String timestamp = result.getStartTime()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = String.format("%s_%s.xlsx", projectName, timestamp);
        return Paths.get(outputDir, fileName);
    }
}
