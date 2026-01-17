package com.raks.aegis.engine;
import com.raks.aegis.model.ValidationReport;
import com.raks.aegis.model.ValidationReport.RuleResult;
import com.raks.aegis.AegisMain.ApiResult;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReportGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ReportGenerator.class);
    private static final String[] RULE_DOCS = {
            "GLOBAL_CONFIGURATION.md",
            "PROJECT_IDENTIFICATION.md",
            "CONFIG_GENERIC_PROPERTY_FILE_CHECK.md",
            "CONFIG_MANDATORY_PROPERTY_VALUE_CHECK.md",
            "CONFIG_MANDATORY_SUBSTRING_CHECK.md",
            "CONFIG_OPTIONAL_PROPERTY_VALUE_CHECK.md",
            "CODE_GENERIC_TOKEN_SEARCH.md",
            "CODE_GENERIC_TOKEN_SEARCH_FORBIDDEN.md",
            "CODE_GENERIC_TOKEN_SEARCH_REQUIRED.md",
            "CODE_JSON_VALIDATION_FORBIDDEN.md",
            "CODE_JSON_VALIDATION_REQUIRED.md",
            "CODE_POM_VALIDATION_FORBIDDEN.md",
            "CODE_POM_VALIDATION_REQUIRED.md",
            "CODE_XML_ATTRIBUTE_EXISTS.md",
            "CODE_XML_ATTRIBUTE_NOT_EXISTS.md",
            "CODE_XML_ELEMENT_CONTENT_FORBIDDEN.md",
            "CODE_XML_ELEMENT_CONTENT_REQUIRED.md",
            "CODE_XML_XPATH_EXISTS.md",
            "CODE_XML_XPATH_NOT_EXISTS.md",
            "CONDITIONAL_CHECK.md",
            "CODE_PROJECT_CONTEXT.md",
            "CODE_FILE_EXISTS.md"
    };
    public static void generateIndividualReports(ValidationReport report, Path outputDir) {
        try {
            Files.createDirectories(outputDir);
            generateHtml(report, outputDir.resolve("report.html"));
            generateExcel(report, outputDir.resolve("report.xlsx"));
            try (InputStream logoStream = ReportGenerator.class.getResourceAsStream("/logo.svg")) {
                if (logoStream != null) {
                    Path logoPath = outputDir.resolve("logo.svg");
                    Files.copy(logoStream, logoPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Logo copied to {}", outputDir.getFileName());
                } else {
                    logger.warn("Logo not found in resources for {}", outputDir.getFileName());
                }
            } catch (Exception logoEx) {
                logger.warn("Failed to copy logo to {}: {}", outputDir.getFileName(), logoEx.getMessage());
            }
        } catch (Exception e) {
            logger.error("Failed to generate individual reports: {}", e.getMessage());
        }
    }
    public static void generateHtml(ValidationReport report, Path outputPath) {
        try {
            Files.createDirectories(outputPath.getParent());
            



            List<RuleResult> allResults = new java.util.ArrayList<>();
            allResults.addAll(report.passed);
            allResults.addAll(report.failed);
            

            allResults.sort((r1, r2) -> {
                if (r1.passed != r2.passed) {
                    return r1.passed ? 1 : -1; // FAIL (passed=false) comes first
                }
                return r1.id.compareTo(r2.id);
            });
            



            int seq = 1;
            for (RuleResult r : allResults) {

                r.displayId = String.format("%03d", seq++);
            }


            StringBuilder rows = new StringBuilder();
            



            Map<String, String> labels = report.labels != null ? report.labels : new java.util.HashMap<>();
            String passLabel = labels.getOrDefault("PASS", "PASS");
            String failLabel = labels.getOrDefault("FAIL", "FAIL");

            for (RuleResult r : allResults) {
                String configRow = (r.ruleConfig != null && !r.ruleConfig.isEmpty())
                        ? "<div style='margin-top:5px; padding-top:5px; border-top:1px dashed #ccc; font-size:0.85rem; color:#666;'><strong>Rule Config:</strong> " + escape(r.ruleConfig) + "</div>"
                        : "";
                String scope = r.scope != null ? r.scope : "GLOBAL";
                
                if (r.passed) {
                    String message = r.checks.isEmpty() ? "All checks passed" : r.checks.get(0).message;
                    rows.append(String.format(
                            "<tr style='background-color:#e8f5e9'><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td><strong style='color:green'>%s</strong></td><td><div>%s</div>%s</td></tr>",
                            escape(r.displayId), escape(r.name), escape(r.severity), escape(scope), escape(passLabel), escape(message), configRow));
                } else {
                    List<String> messages = r.checks.stream()
                            .filter(c -> !c.passed)
                            .map(c -> escape(c.message))
                            .toList();
                    String details = messages.stream()
                            .collect(Collectors.groupingBy(
                                    s -> s.contains(" not found in file: ") ? s.substring(0, s.lastIndexOf(":") + 1) : s,
                                    Collectors.mapping(
                                            s -> s.contains(" not found in file: ")
                                                    ? s.substring(s.lastIndexOf(":") + 1).trim()
                                                    : "",
                                            Collectors.joining(", "))))
                            .entrySet().stream()
                            .map(entry -> "• " + entry.getKey() + entry.getValue())
                            .collect(Collectors.joining("<br>"));
                    rows.append(String.format(
                            "<tr style='background-color:#ffebee'><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td><strong style='color:red'>%s</strong></td><td><div>%s</div>%s</td></tr>",
                            escape(r.displayId), escape(r.name), escape(r.severity), escape(scope), escape(failLabel), details, configRow));
                }
            } // End loop

            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <title>Aegis Report - %s</title>
                        <style>
                            :root {
                                --raks-purple: #663399;
                                --raks-purple-light: #7d4fb2;
                                --text-white: #FFFFFF;
                            }
                            body {font-family: Arial, sans-serif; margin: 0; background-color: #f5f5f5;}
                            .report-container { border: 5px solid var(--raks-purple); padding: 20px 40px; margin: 20px; border-radius: 8px; background-color: white; position: relative; }
                            h1 {color: var(--raks-purple);}
                            .summary {background: #e3f2fd; padding: 20px; border-radius: 8px; margin-bottom: 20px;}
                            .search-box { margin-bottom: 20px; width: 100%%; }
                            #searchInput { width: 100%%; padding: 12px; border: 2px solid var(--raks-purple); border-radius: 5px; font-size: 16px; box-sizing: border-box; }
                            table {width: 100%%; border-collapse: collapse; box-shadow: 0 4px 12px rgba(0,0,0,0.1);}
                            th, td {border: 1px solid #ddd; padding: 12px; text-align: left;}
                            th {background-color: var(--raks-purple); color: var(--text-white); cursor: pointer; position: sticky; top: 0; z-index: 10; }
                            th:hover { background-color: var(--raks-purple-light); }
                            th::after { content: ' ↕'; font-size: 0.8em; }
                            .top-nav-container { position: absolute; top: 20px; right: 40px; display: flex; gap: 10px; }
                            .top-nav-button {
                                background-color: var(--raks-purple); color: var(--text-white);
                                border: none; padding: 8px 16px; text-align: center; text-decoration: none;
                                display: inline-block; font-size: 14px; font-weight: bold;
                                cursor: pointer; border-radius: 5px; transition: background-color 0.3s ease;
                                box-shadow: 0 2px 4px rgba(0,0,0,0.2);
                            }
                            .top-nav-button:hover { background-color: var(--raks-purple-light); }
                        </style>
                    </head>
                    <body>
                        <div class="report-container">
                                <div class="top-nav-container">
                                <a href="../CONSOLIDATED-REPORT.html" class="top-nav-button" title="Return to main dashboard">← Dashboard</a>
                                <a href="report.xlsx" class="top-nav-button" style="background-color: #217346;">Excel Report</a>
                                <a href="../checklist.html" class="top-nav-button">Checklist</a>
                            </div>
                            <div style="display: flex; align-items: center; margin-bottom: 20px;">
                                <img src="logo.svg" alt="Aegis Logo" style="height: 40px; margin-right: 15px;">
                                <h1 style="margin: 0;">Aegis Report</h1>
                            </div>
                            <div class="summary">
                                <strong>Project:</strong> %s<br>
                                <strong>Generated:</strong> %s<br>
                                <strong>Total Rules:</strong> %d | <strong style="color:green">%s:</strong> %d | <strong style="color:red">%s:</strong> %d
                            </div>
                            <div class="search-box">
                                <input type="text" id="searchInput" onkeyup="filterTable()" placeholder="Search by name, status, or details...">
                            </div>
                            <table id="resultsTable">
                                <thead>
                                    <tr>
                                        <th onclick="sortTable(0)">Rule #</th>
                                        <th onclick="sortTable(1)">Name</th>
                                        <th onclick="sortTable(2)">Severity</th>
                                        <th onclick="sortTable(3)">Scope</th>
                                        <th onclick="sortTable(4)">Status</th>
                                        <th onclick="sortTable(5)">Details</th>
                                    </tr>
                                </thead>
                                <tbody id="tableBody">
                                    %s
                                </tbody>
                            </table>
                        </div>
                        <script>
                            function filterTable() {
                                var input, filter, table, tr, td, i, txtValue;
                                input = document.getElementById("searchInput");
                                filter = input.value.toUpperCase();
                                table = document.getElementById("resultsTable");
                                tr = table.getElementsByTagName("tr");
                                for (i = 1; i < tr.length; i++) {
                                    var found = false;
                                    var tds = tr[i].getElementsByTagName("td");
                                    for (var j = 0; j < tds.length; j++) {
                                        if (tds[j]) {
                                            txtValue = tds[j].textContent || tds[j].innerText;
                                            if (txtValue.toUpperCase().indexOf(filter) > -1) {
                                                found = true;
                                                break;
                                            }
                                        }
                                    }
                                    tr[i].style.display = found ? "" : "none";
                                }
                            }

                            function sortTable(n) {
                                var table, rows, switching, i, x, y, shouldSwitch, dir, switchcount = 0;
                                table = document.getElementById("resultsTable");
                                switching = true;
                                dir = "asc";
                                while (switching) {
                                    switching = false;
                                    rows = table.rows;
                                    for (i = 1; i < (rows.length - 1); i++) {
                                        shouldSwitch = false;
                                        x = rows[i].getElementsByTagName("TD")[n];
                                        y = rows[i + 1].getElementsByTagName("TD")[n];
                                        if (dir == "asc") {
                                            if (x.innerHTML.toLowerCase() > y.innerHTML.toLowerCase()) {
                                                shouldSwitch = true;
                                                break;
                                            }
                                        } else if (dir == "desc") {
                                            if (x.innerHTML.toLowerCase() < y.innerHTML.toLowerCase()) {
                                                shouldSwitch = true;
                                                break;
                                            }
                                        }
                                    }
                                    if (shouldSwitch) {
                                        rows[i].parentNode.insertBefore(rows[i + 1], rows[i]);
                                        switching = true;
                                        switchcount++;
                                    } else {
                                        if (switchcount == 0 && dir == "asc") {
                                            dir = "desc";
                                            switching = true;
                                        }
                                    }
                                }
                            }
                        </script>
                    </body>
                    </html>
                    """;
            
            String finalHtml = String.format(html,
                    escape(report.projectPath),
                    escape(report.projectPath),
                    ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")),
                    report.passed.size() + report.failed.size(),
                    escape(passLabel), report.passed.size(),
                    escape(failLabel), report.failed.size(),
                    rows.toString());
            Files.writeString(outputPath, finalHtml, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Failed to generate HTML: {}", e.getMessage());
        }
    }
    public static void generateExcel(ValidationReport report, Path outputPath) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Validation Results");
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle passStyle = workbook.createCellStyle();
            passStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            passStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle failStyle = workbook.createCellStyle();
            failStyle.setFillForegroundColor(IndexedColors.CORAL.getIndex());
            failStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Row header = sheet.createRow(0);
            String[] columns = { "Rule ID", "Name", "Severity", "Scope", "Status", "Details" };
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }
            
            Map<String, String> labels = report.labels != null ? report.labels : new java.util.HashMap<>();
            String passLabel = labels.getOrDefault("PASS", "PASS");
            String failLabel = labels.getOrDefault("FAIL", "FAIL");

            int rowNum = 1;
            for (RuleResult r : report.passed) {
                String message = r.checks.isEmpty() ? "All checks passed" : r.checks.get(0).message;
                String scope = r.scope != null ? r.scope : "GLOBAL";
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(r.id);
                row.createCell(1).setCellValue(r.name);
                row.createCell(2).setCellValue(r.severity);
                row.createCell(3).setCellValue(scope);
                row.createCell(4).setCellValue(passLabel);
                row.createCell(5).setCellValue(message);
                for (int i = 0; i < 6; i++)
                    row.getCell(i).setCellStyle(passStyle);
            }
            for (RuleResult r : report.failed) {
                String details = r.checks.stream()
                        .filter(c -> !c.passed)
                        .map(c -> "• " + c.message)
                        .collect(Collectors.joining("\n"));
                String scope = r.scope != null ? r.scope : "GLOBAL";
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(r.id);
                row.createCell(1).setCellValue(r.name);
                row.createCell(2).setCellValue(r.severity);
                row.createCell(3).setCellValue(scope);
                row.createCell(4).setCellValue(failLabel);
                row.createCell(5).setCellValue(details.isEmpty() ? "Failed" : details);
                for (int i = 0; i < 6; i++)
                    row.getCell(i).setCellStyle(failStyle);
            }
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }
            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                workbook.write(fos);
            }
        } catch (Exception e) {
            logger.error("Failed to generate Excel: {}", e.getMessage());
        }
    }
    public static void generateConsolidatedReport(List<ApiResult> results, Path outputPath) {
        try {
            if (results == null || results.isEmpty()) {
                logger.warn("No results to generate consolidated report");
                return;
            }
            Files.createDirectories(outputPath.getParent());
            StringBuilder tableRows = new StringBuilder();
            int totalApis = results.size();
            int totalPassed = 0;
            int totalFailed = 0;
            for (ApiResult r : results) {
                if (r == null || r.name == null || r.reportDir == null)
                    continue;
                totalPassed += r.passed;
                totalFailed += r.failed;
                String status = r.failed == 0 ? "PASS" : "FAIL";
                String color = r.failed == 0 ? "#e8f5e9" : "#ffebee";
                Path target = null;
                try {
                    target = r.reportDir.resolve("report.html");
                    if (!Files.exists(target)) {
                        logger.warn("Report not found: {}", target);
                    }
                } catch (Exception e) {
                    logger.warn("Invalid report path for API: {}", r.name);
                    continue;
                }
                String relativeLink;
                try {
                    relativeLink = outputPath.relativize(target)
                            .toString().replace("\\", "/");
                } catch (Exception e) {
                    relativeLink = "report.html"; 
                }
                tableRows.append("<tr style='background-color:").append(color).append("'>")
                        .append("<td>").append(escape(r.repository)).append("</td>")
                        .append("<td>").append(escape(r.name)).append("</td>")
                        .append("<td>").append(r.passed + r.failed).append("</td>")
                        .append("<td>").append(r.passed).append("</td>")
                        .append("<td>").append(r.failed).append("</td>")
                        .append("<td><strong style='color:").append(r.failed == 0 ? "green" : "red").append("'>")
                        .append(status).append("</strong></td>")
                        .append("<td><a href='").append(escape(relativeLink)).append("'>View Report</a></td>")
                        .append("</tr>\n");
            }
            int totalRules = totalPassed + totalFailed;
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <title>Aegis - Consolidated Report</title>
                        <style>
                            :root {
                                --raks-purple: #663399;
                                --raks-purple-light: #7d4fb2;
                                --text-white: #FFFFFF;
                            }
                            body {font-family: Arial, sans-serif; margin: 0; background-color: #f0f0f0;}
                            .report-container { border: 5px solid var(--raks-purple); padding: 20px 40px; margin: 20px; border-radius: 8px; background-color: white; }
                            h1 {color: var(--raks-purple);}
                            .card {background: white; padding: 20px; border-radius: 10px; box-shadow: 0 4px 20px rgba(0,0,0,0.1); margin-bottom: 20px;}
                            .search-box { margin-bottom: 20px; width: 100%%; }
                            #searchInput { width: 100%%; padding: 12px; border: 2px solid var(--raks-purple); border-radius: 5px; font-size: 16px; box-sizing: border-box; }
                            table {width: 100%%; border-collapse: collapse;}
                            th, td {border: 1px solid #ddd; padding: 12px; text-align: left;}
                            th {background: var(--raks-purple); color: var(--text-white); cursor: pointer; position: sticky; top: 0; z-index: 10; }
                            th:hover { background-color: var(--raks-purple-light); }
                            th::after { content: ' ↕'; font-size: 0.8em; }
                            .top-nav-container {
                                position: absolute;
                                top: 20px;
                                right: 40px;
                                display: flex;
                                gap: 10px;
                            }
                            .top-nav-button {
                                background-color: var(--raks-purple);
                                color: var(--text-white);
                                border: none;
                                padding: 8px 16px;
                                text-align: center;
                                text-decoration: none;
                                display: inline-block;
                                font-size: 14px;
                                font-weight: bold;
                                cursor: pointer;
                                border-radius: 5px;
                                transition: background-color 0.3s ease;
                                box-shadow: 0 2px 4px rgba(0,0,0,0.2);
                            }
                            .top-nav-button:hover { background-color: var(--raks-purple-light); }
                        </style>
                    </head>
                    <body>
                        <div class="report-container">
                                <div class="top-nav-container">
                                <a href="CONSOLIDATED-REPORT.xlsx" class="top-nav-button" style="background-color: #217346;">Excel Report</a>
                                <a href="checklist.html" class="top-nav-button">Checklist</a>
                            </div>
                            <div style="display: flex; align-items: center; margin-bottom: 20px;">
                                <img src="logo.svg" alt="Aegis Logo" style="height: 40px; margin-right: 15px;">
                                <h1 style="margin: 0;">Aegis Consolidated Report</h1>
                            </div>
                            <div style="border: 1px solid #ccc; padding: 10px 20px; margin-top: 15px; margin-bottom: 20px; background-color: #fbfbfbff; border-radius: 5px;">
                            <h4 style="margin-top: 0; color: #333;">Scan Summary:</h4>
                                <strong>Generated:</strong> %s<br>
                                <strong>Total APIs Scanned:</strong> %d<br>
                                <strong>Total Rules:</strong> %d | <strong style="color:green">Passed:</strong> %d | <strong style="color:red">Failed:</strong> %d
                            </div>
                            %s 
                            <div class="search-box">
                                <input type="text" id="searchInput" onkeyup="filterTable()" placeholder="Search by API name, status, or counts...">
                            </div>
                            <table id="resultsTable">
                                <thead>
                                    <tr>
                                        <th onclick="sortTable(0)">Repository</th>
                                        <th onclick="sortTable(1)">API Name</th>
                                        <th onclick="sortTable(2)">Total Rules</th>
                                        <th onclick="sortTable(3)">Passed</th>
                                        <th onclick="sortTable(4)">Failed</th>
                                        <th onclick="sortTable(5)">Status</th>
                                        <th>Report</th>
                                    </tr>
                                </thead>
                                <tbody id="tableBody">
                                    %s
                                </tbody>
                            </table>
                        </div>
                        <script>
                            function filterTable() {
                                var input, filter, table, tr, td, i, txtValue;
                                input = document.getElementById("searchInput");
                                filter = input.value.toUpperCase();
                                table = document.getElementById("resultsTable");
                                tr = table.getElementsByTagName("tr");
                                for (i = 1; i < tr.length; i++) {
                                    var found = false;
                                    var tds = tr[i].getElementsByTagName("td");
                                    for (var j = 0; j < tds.length; j++) {
                                        if (tds[j]) {
                                            txtValue = tds[j].textContent || tds[j].innerText;
                                            if (txtValue.toUpperCase().indexOf(filter) > -1) {
                                                found = true;
                                                break;
                                            }
                                        }
                                    }
                                    tr[i].style.display = found ? "" : "none";
                                }
                            }

                            function sortTable(n) {
                                var table, rows, switching, i, x, y, shouldSwitch, dir, switchcount = 0;
                                table = document.getElementById("resultsTable");
                                switching = true;
                                dir = "asc";
                                while (switching) {
                                    switching = false;
                                    rows = table.rows;
                                    for (i = 1; i < (rows.length - 1); i++) {
                                        shouldSwitch = false;
                                        x = rows[i].getElementsByTagName("TD")[n];
                                        y = rows[i + 1].getElementsByTagName("TD")[n];
                                        if (dir == "asc") {
                                            if (x.innerHTML.toLowerCase() > y.innerHTML.toLowerCase()) {
                                                shouldSwitch = true;
                                                break;
                                            }
                                        } else if (dir == "desc") {
                                            if (x.innerHTML.toLowerCase() < y.innerHTML.toLowerCase()) {
                                                shouldSwitch = true;
                                                break;
                                            }
                                        }
                                    }
                                    if (shouldSwitch) {
                                        rows[i].parentNode.insertBefore(rows[i + 1], rows[i]);
                                        switching = true;
                                        switchcount++;
                                    } else {
                                        if (switchcount == 0 && dir == "asc") {
                                            dir = "desc";
                                            switching = true;
                                        }
                                    }
                                }
                            }
                        </script>
                    </body>
                    </html>
                    """;
            

            // Check for git warnings
            String warningHtml = "";
            try {
                Path warningPath = outputPath.getParent().resolve(".Aegis_git_warnings");
                if (Files.exists(warningPath)) {
                    List<String> warnings = Files.readAllLines(warningPath);
                    if (!warnings.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("<div style=\"border: 1px solid #ffcccc; padding: 10px 20px; margin-bottom: 20px; background-color: #fff5f5; border-radius: 5px; color: #d9534f;\">");
                        sb.append("<h4 style=\"margin-top: 0; margin-bottom: 10px; color: #d9534f;\">⚠️ Git Clone Restrictions Applied:</h4>");
                        sb.append("<ul style=\"margin: 0; padding-left: 20px;\">");
                        for (String w : warnings) {
                            sb.append("<li>").append(escape(w)).append("</li>");
                        }
                        sb.append("</ul></div>");
                        warningHtml = sb.toString();
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to read git warnings: " + e.getMessage());
            }

            String finalHtml = String.format(html,
                            ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")),
                            totalApis,
                            totalRules,
                            totalPassed,
                            totalFailed,
                            warningHtml,
                            tableRows);
            Path htmlPath = outputPath.resolve("CONSOLIDATED-REPORT.html");
            Files.writeString(htmlPath, finalHtml, java.nio.charset.StandardCharsets.UTF_8);
            logger.debug("Consolidated report generated: {}", htmlPath.toAbsolutePath());
            copyHelpFile(outputPath);
            generateConsolidatedExcel(results, outputPath);
            generateChecklistReport(outputPath); 
            generateRuleGuide(outputPath); 
        } catch (Throwable t) {
            logger.error("Failed to generate consolidated report");
            logger.error("Error type: {}", t.getClass().getName());
            logger.error("Error message: {}", (t.getMessage() != null ? t.getMessage() : "null"), t); 
        }
    }
    private static void generateChecklistReport(Path outputDir) {
        try {
            StringBuilder rows = new StringBuilder();
            try (InputStream is = ReportGenerator.class.getResourceAsStream("/rulemapping.csv");
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(Objects.requireNonNull(is, "Cannot find rulemapping.csv")))) {
                reader.readLine();
                int srNo = 1;
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(";", 3);
                    if (parts.length == 3) {
                        rows.append(String.format("<tr><td>%d</td><td>%s</td><td>%s</td><td>%s</td></tr>", srNo++,
                                escape(parts[0]), escape(parts[1]), escape(parts[2])));
                    }
                }
            }
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <title>Aegis - Validation Checklist</title>
                        <style>
                            :root {
                                --raks-purple: #663399;
                                --raks-purple-light: #7d4fb2;
                                --text-white: #FFFFFF;
                            }
                            body {font-family: Arial, sans-serif; margin: 0; background-color: #f0f0f0;}
                            .report-container { border: 5px solid var(--raks-purple); padding: 20px 40px; margin: 20px; border-radius: 8px; background-color: white; position: relative; }
                            h1 {color: var(--raks-purple);}
                            table {width: 100%%; border-collapse: collapse; box-shadow: 0 4px 12px rgba(0,0,0,0.1);}
                            th, td {border: 1px solid #ddd; padding: 12px; text-align: left;}
                            th {background-color: var(--raks-purple); color: var(--text-white);}
                            .contact-button {
                                background-color: var(--raks-purple);
                                color: var(--text-white);
                                border: none;
                                padding: 12px 24px;
                                text-align: center;
                                text-decoration: none;
                                display: inline-block;
                                font-size: 16px;
                                font-weight: bold;
                                margin-top: 25px;
                                cursor: pointer;
                                border-radius: 5px;
                                transition: background-color 0.3s ease;
                                box-shadow: 0 2px 4px rgba(0,0,0,0.2);
                            }
                            .contact-button:hover { background-color: var(--raks-purple-light); }
                            .top-nav-container {
                                position: absolute;
                                top: 20px;
                                right: 40px;
                                display: flex;
                                gap: 10px;
                            }
                            .top-nav-button {
                                background-color: var(--raks-purple);
                                color: var(--text-white);
                                border: none;
                                padding: 8px 16px;
                                text-align: center;
                                text-decoration: none;
                                display: inline-block;
                                font-size: 14px;
                                font-weight: bold;
                                cursor: pointer;
                                border-radius: 5px;
                                transition: background-color 0.3s ease;
                                box-shadow: 0 2px 4px rgba(0,0,0,0.2);
                            }
                            .top-nav-button:hover { background-color: var(--raks-purple-light); }
                        </style>
                    </head>
                    <body>
                        <div class="report-container">
                                <div class="top-nav-container">
                                <a href="CONSOLIDATED-REPORT.html" id="dashboardBtn" class="top-nav-button" title="Return to main dashboard">← Dashboard</a>
                                <a href="/Aegis/main" id="mainPageBtn" class="top-nav-button" title="Go to Main Page" style="display: none; background-color: #0078d4;">🏠 Main Page</a>
                            </div>
                            <div style="display: flex; align-items: center; margin-bottom: 20px;">
                                <img src="logo.svg" alt="Aegis Logo" style="height: 40px; margin-right: 15px;">
                                <h1 style="margin: 0;">Aegis Checklist</h1>
                            </div>
                            <p>This page lists all the individual checks performed by the Aegis tool.</p>
                            <table>
                                <tr><th>Sr.#</th><th>ChecklistItem</th><th>ChecklistType</th><th>RuleId</th></tr>
                                %s
                            </table>
                        </div>
                            <script>
                                document.addEventListener('DOMContentLoaded', function() {
                                    var path = window.location.pathname;
                                    var isMuleStatic = path.includes('/apiguard/Aegis/web/') || path.includes('/Aegis/web/');
                                    var isMuleReport = path.includes('/apiguard/Aegis/reports/') || path.includes('/Aegis/reports/');
                                    var dashboardBtn = document.getElementById('dashboardBtn');
                                    var mainPageBtn = document.getElementById('mainPageBtn');
                                    

                                    var isInMuleWrapper = path.includes('/apiguard/');
                                    var basePath = isInMuleWrapper ? '/apiguard/Aegis' : '/Aegis';
                                    
                                    var sessionId = new URLSearchParams(window.location.search).get('session');
                                    if (!sessionId && isMuleReport) {
                                        var parts = path.split('/Aegis/reports/');
                                        if (parts.length > 1) {
                                            var subparts = parts[1].split('/');
                                            if (subparts.length > 0) sessionId = subparts[0];
                                        }
                                    }
                                    if (isMuleStatic || isMuleReport) {
                                        if (mainPageBtn) {
                                            mainPageBtn.style.display = 'inline-block';
                                            var mainPageUrl = basePath + '/main';
                                            if (sessionId) mainPageUrl += '?session=' + sessionId;
                                            mainPageBtn.href = mainPageUrl;
                                            if (isMuleStatic) {
                                                mainPageBtn.style.right = '40px';
                                                if (dashboardBtn) dashboardBtn.style.display = 'none'; 
                                            } else {
                                                mainPageBtn.style.right = '180px';
                                            }
                                        }
                                    } else {
                                        if (dashboardBtn) dashboardBtn.style.display = 'inline-block';
                                    }
                                });
                            </script>
                    </body>
                    </html>
                    """;
            String finalHtml = String.format(html, rows.toString());
            Path checklistPath = outputDir.resolve("checklist.html");
            Files.writeString(checklistPath, finalHtml, java.nio.charset.StandardCharsets.UTF_8);
            logger.debug("Checklist generated");
        } catch (Exception e) {
            logger.error("Failed to generate checklist report: {}", e.getMessage());
        }
    }
    public static void generateRuleGuide(String outputDir) {
        generateRuleGuide(java.nio.file.Paths.get(outputDir));
    }

    public static void generateRuleGuide(Path outputDir) {
        try {

            StringBuilder sidebar = new StringBuilder();
            StringBuilder content = new StringBuilder();
            int index = 0;
            for (String docFile : RULE_DOCS) {
                String ruleName = docFile.replace(".md", "").replace("CODE_", "").replace("CONFIG_", "");
                String mdContent = readResource("/docs/" + docFile);
                if (mdContent == null) {
                    mdContent = "# " + ruleName + "\n\nDocumentation not found for " + ruleName;
                }


                StringBuilder subNav = new StringBuilder();
                java.util.regex.Pattern headerPattern = java.util.regex.Pattern.compile("(?m)^## (.*)$");
                java.util.regex.Matcher matcher = headerPattern.matcher(mdContent);
                
                if (matcher.find()) {
                    subNav.append("<ul class=\"sub-nav\" id=\"sub-").append(ruleName).append("\">");
                    matcher.reset();
                    while (matcher.find()) {
                        String headerTitle = matcher.group(1).trim();

                        String sectionId = ruleName + "_" + headerTitle.replaceAll("[^a-zA-Z0-9]", "");
                        
                        subNav.append(String.format("<li><a href=\"#\" onclick=\"showRule(event, '%s', '%s')\">%s</a></li>", 
                                ruleName, sectionId, headerTitle));
                        

                        mdContent = mdContent.replace("## " + headerTitle, "<h2 id=\"" + sectionId + "\">" + headerTitle + "</h2>");
                    }
                    subNav.append("</ul>");
                }


                boolean hasSubNav = subNav.length() > 0;
                sidebar.append("<li>");
                if (hasSubNav) {
                    sidebar.append("<div class=\"rule-header\">");
                    sidebar.append(String.format("<span class=\"toggle-icon\" onclick=\"toggleSubNav(event, '%s')\">&#9654;</span>", ruleName));
                    sidebar.append(String.format("<a href=\"#\" onclick=\"showRule(event, '%s')\">%s</a>", ruleName, ruleName));
                    sidebar.append("</div>");
                    sidebar.append(subNav.toString());
                } else {
                     sidebar.append(String.format("<a href=\"#\" onclick=\"showRule(event, '%s')\">%s</a>", ruleName, ruleName));
                }
                sidebar.append("</li>");

                String htmlContent = convertMdToHtml(mdContent);
                


                content.append(String.format("<div id=\"%s\" class=\"rule-content\" style=\"display: %s;\">%s</div>", 
                        ruleName, (index == 0 ? "block" : "none"), htmlContent));
                index++;
            }
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <title>Aegis - Standard Rule Guide</title>
                        <style>
                            :root {
                                --raks-purple: #663399;
                                --raks-purple-light: #7d4fb2;
                                --text-white: #FFFFFF;
                                --sidebar-width: 300px;
                            }
                            html { height: 100%%; margin: 0; padding: 0; }
                            body { font-family: Arial, sans-serif; margin: 0; background-color: #f0f0f0; display: flex; height: 100%%; overflow: hidden; width: 100%%; }
                            .sidebar {
                                width: var(--sidebar-width);
                                background: white;
                                border-right: 1px solid #ddd;
                                display: flex;
                                flex-direction: column;
                                height: 100%%;
                                overflow: hidden; 
                                min-width: 200px;
                                max-width: 600px;
                                flex-shrink: 0;
                            }
                            .sidebar-header {
                                padding: 20px;
                                background: var(--raks-purple);
                                color: white;
                            }
                            .sidebar-header h2 { margin: 0; font-size: 1.2rem; color: white; }
                            .sidebar-nav { list-style-type: none; padding: 0; overflow-y: auto; flex-grow: 1; }
                            .sidebar-nav li { border-bottom: 1px solid #eee; }
                            .sidebar-nav a { display: block; color: #333; padding: 15px 20px; text-decoration: none; font-size: 14px; display: flex; justify-content: space-between; align-items: center; }
                            .sidebar-nav a:hover { background-color: #f5f5f5; color: #000; }
                            .sidebar-nav a.active { background-color: var(--raks-purple); color: white; border-left: 4px solid #7d4fb2; }
                            
                            
                            .rule-header { display: flex; align-items: center; width: 100%%; cursor: pointer; }
                            .rule-header:hover { background-color: #f5f5f5; }
                            .rule-header a { flex-grow: 1; padding: 15px 10px; border: none; }
                            .toggle-icon { padding: 15px 10px; color: #666; cursor: pointer; font-size: 10px; width: 20px; text-align: center; }
                            .toggle-icon:hover { color: #000; }
                            .sub-nav { list-style-type: none; padding: 0; display: none; background-color: #f9f9f9; border-top: 1px solid #eee; }
                            .sub-nav.expanded { display: block; }
                            .sub-nav li { border-bottom: 1px solid #eee; }
                            .sub-nav a { padding: 8px 10px 8px 45px; font-size: 13px; color: #555; }
                            .sub-nav a:hover { color: #000; background-color: #eee; }
                            .sub-nav a.active { background-color: var(--raks-purple); color: white; font-weight: bold; }

                            .main-content { flex: 1; padding: 40px; overflow-y: auto; background-color: white; position: relative; }
                            .report-container {
                                background: white;
                                padding: 40px;
                                border-radius: 8px;
                                box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                                max-width: 1400px;
                                margin: 0 auto;
                                border: 5px solid var(--raks-purple);
                                position: relative;
                            }
                            .top-nav-container {
                                position: absolute;
                                top: 20px;
                                right: 40px;
                                display: flex;
                                gap: 10px;
                                z-index: 100;
                            }
                            .top-nav-button {
                                background-color: var(--raks-purple);
                                color: var(--text-white);
                                border: none;
                                padding: 8px 16px;
                                text-align: center;
                                text-decoration: none;
                                display: inline-block;
                                font-size: 14px;
                                font-weight: bold;
                                cursor: pointer;
                                border-radius: 5px;
                                transition: background-color 0.3s ease;
                                box-shadow: 0 2px 4px rgba(0,0,0,0.2);
                            }
                            .top-nav-button:hover { background-color: var(--raks-purple-light); }
                            h1 { color: var(--raks-purple) !important; border-bottom: 2px solid #eee; padding-bottom: 10px; margin-top: 0; }  
                            h2 { color: var(--raks-purple) !important; margin-top: 30px; }
                            h3 { color: var(--raks-purple-light) !important; }
                            h4 { color: var(--raks-purple-light) !important; font-size: 1.1em; margin-top: 20px; }
                            .rule-content { color: #333 !important; line-height: 1.6; }
                            pre { background: #f4f4f4; padding: 15px; border-radius: 5px; overflow-x: auto; border: 1px solid #ddd; }
                            code { font-family: Consolas, monospace; color: #0078d4 !important; }
                            code.inline-token { color: #0078d4 !important; font-weight: bold; }
                            pre code { color: #333 !important; } /* Code blocks should be black text */
                            table { border-collapse: collapse; width: 100%%; margin: 20px 0; color: #333 !important; }
                            th, td { border: 1px solid #ddd; padding: 10px; text-align: left; }
                            th { background: #f1f1f1; color: #333 !important; }
                            blockquote { border-left: 4px solid var(--raks-purple); margin: 0; padding-left: 20px; color: #555; }
                            .search-highlight { background-color: #ffeb3b; color: black; font-weight: bold; border-radius: 2px; }
                            ::highlight(search-results) { background-color: #ffeb3b; color: black; }
                            .match-count { background: #663399; color: white; border-radius: 10px; padding: 2px 8px; font-size: 0.8em; margin-left: 10px; font-weight: bold; white-space: nowrap; }
                            .resizer {
                                width: 5px;
                                background-color: #ddd;
                                cursor: col-resize;
                                transition: background-color 0.2s;
                                z-index: 10;
                            }
                            .resizer:hover, .resizer.resizing {
                                background-color: var(--raks-purple);
                            }
                        </style>
                        <script>
                            var currentFilter = "";
                            var searchTimeout = null;
                            var searchIndex = []; // { id: ruleId, content: upperCaseText }

                            function buildIndex() {
                                var rules = document.querySelectorAll('.rule-content');
                                rules.forEach(function(el) {
                                    var id = el.id;
                                    var text = (el.textContent || el.innerText).toUpperCase();

                                    searchIndex.push({ id: id, content: text });
                                });
                                console.log("Search index built: " + searchIndex.length + " items.");
                            }

                            function showRule(event, ruleId, sectionId) {
                                if (event) event.preventDefault();
                                document.querySelectorAll('.rule-content').forEach(el => el.style.display = 'none');
                                var contentDiv = document.getElementById(ruleId);
                                if (contentDiv) {
                                    contentDiv.style.display = 'block';
                                    

                                    expandRuleTree(ruleId);


                                    if (sectionId) {
                                        var section = document.getElementById(sectionId);
                                        if (section) section.scrollIntoView({block: 'start'});
                                    } else {

                                        document.querySelector('.main-content').scrollTop = 0;
                                    }
                                    
                                    setTimeout(function(){
                                        applyHighlight(contentDiv, currentFilter, false);
                                    }, 0);
                                }
                                

                                document.querySelectorAll('.sidebar-nav a').forEach(el => {
                                    el.classList.remove('active');
                                    var onclick = el.getAttribute('onclick');

                                    if (onclick && onclick.includes("'" + ruleId + "'")) {

                                        if (!sectionId || (sectionId && onclick.includes("'" + sectionId + "'"))) {
                                             el.classList.add('active');
                                        }
                                    }
                                });
                            }
                            
                            function toggleSubNav(event, ruleId) {
                                if(event) event.stopPropagation();
                                var subNav = document.getElementById('sub-' + ruleId);
                                var icon = event.target;
                                if (subNav) {
                                    if (subNav.classList.contains('expanded')) {
                                        subNav.classList.remove('expanded');
                                        icon.innerHTML = '&#9654;'; // Right Arrow
                                    } else {
                                        subNav.classList.add('expanded');
                                        icon.innerHTML = '&#9660;'; // Down Arrow
                                    }
                                }
                            }
                            
                            function expandRuleTree(ruleId) {
                                var subNav = document.getElementById('sub-' + ruleId);
                                if (subNav && !subNav.classList.contains('expanded')) {
                                    subNav.classList.add('expanded');

                                    var header = subNav.previousElementSibling;
                                    if (header) {
                                        var icon = header.querySelector('.toggle-icon');
                                        if (icon) icon.innerHTML = '&#9660;';
                                    }
                                }
                            }
                            
                            function escapeRegExp(string) {
                                return string.replace(/[.*+?^${}()|[\\]\\\\]/g, '\\\\$&'); 
                            }

                            function applyHighlight(container, term, shouldScroll) {
                                if (!container) return;
                                

                                if (window.CSS && CSS.highlights) {
                                    CSS.highlights.clear();
                                }
                                
                                if (!term || term.length < 2) return;

                                var ranges = [];
                                var termUpper = term.toUpperCase();
                                var walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, null, false);
                                var node;
                                var matchCount = 0;
                                var maxMatches = 100;

                                while (node = walker.nextNode()) {
                                    if (matchCount >= maxMatches) break;
                                    var text = node.nodeValue;
                                    var valUpper = text.toUpperCase();
                                    var idx = valUpper.indexOf(termUpper);
                                    
                                    while (idx >= 0 && matchCount < maxMatches) {
                                        var range = new Range();
                                        range.setStart(node, idx);
                                        range.setEnd(node, idx + term.length);
                                        ranges.push(range);
                                        matchCount++;
                                        idx = valUpper.indexOf(termUpper, idx + term.length);
                                    }
                                }


                                if (window.CSS && CSS.highlights) {
                                    var highlight = new Highlight(...ranges);
                                    CSS.highlights.set("search-results", highlight);
                                }


                                if (shouldScroll && ranges.length > 0) {
                                    var firstRange = ranges[0];
                                    if (firstRange) {
                                        var startNode = firstRange.startContainer;
                                        var element = startNode.nodeType === 3 ? startNode.parentElement : startNode;
                                        if (element) {
                                            element.scrollIntoView({block: "center"});
                                        }
                                    }
                                }
                            }

                            function onSearchInput() {
                                if (searchTimeout) clearTimeout(searchTimeout);

                                searchTimeout = setTimeout(filterSidebar, 300);
                            }

                            function filterSidebar() {
                                var input = document.getElementById("filterInput");
                                currentFilter = input.value.trim().toUpperCase();
                                


                                
                                var matchedIds = new Set();
                                var matchesMap = {}; // id -> count

                                if (currentFilter.length < 2) {

                                    var lis = document.getElementById("sidebarNav").getElementsByTagName("li");
                                    for (var i = 0; i < lis.length; i++) {
                                        lis[i].style.display = "";
                                        var a = lis[i].getElementsByTagName("a")[0];
                                        var oldBadge = a.querySelector('.match-count');
                                        if (oldBadge) oldBadge.remove();
                                    }
                                    return;
                                }


                                for (var i = 0; i < searchIndex.length; i++) {
                                    var item = searchIndex[i];
                                    var count = 0;
                                    

                                    if (item.id.toUpperCase().indexOf(currentFilter) > -1) {
                                        count++; 
                                    } 
                                    

                                    var pos = item.content.indexOf(currentFilter);
                                    while (pos !== -1 && count < 101) {
                                        count++;
                                        pos = item.content.indexOf(currentFilter, pos + currentFilter.length);
                                    }
                                    
                                    if (count > 0) {
                                        matchedIds.add(item.id);
                                        matchesMap[item.id] = count;
                                    }
                                }


                                var ul = document.getElementById("sidebarNav");
                                var lis = ul.getElementsByTagName("li");

                                requestAnimationFrame(() => {
                                    for (var i = 0; i < lis.length; i++) {
                                        var a = lis[i].getElementsByTagName("a")[0];




                                        var onclickStr = a.getAttribute("onclick"); 
                                        var ruleId = onclickStr.match(/'([^']+)'/)[1]; 

                                        var oldBadge = a.querySelector('.match-count');
                                        if (oldBadge) oldBadge.remove();

                                        if (matchedIds.has(ruleId) || currentFilter === "") {
                                            lis[i].style.display = "";
                                            var count = matchesMap[ruleId] || 0;
                                            if (count > 0) {
                                                var badge = document.createElement("span");
                                                badge.className = "match-count";
                                                badge.innerText = count > 100 ? "100+" : count;
                                                a.appendChild(badge);
                                            }
                                        } else {
                                            lis[i].style.display = "none";
                                        }
                                    }
                                    

                                    var visible = document.querySelector('.rule-content[style*="block"]');
                                    if (visible) {
                                        applyHighlight(visible, currentFilter, false); 
                                    }
                                });
                            }

                            document.addEventListener('DOMContentLoaded', function() {

                                setTimeout(buildIndex, 100);


                                var hash = window.location.hash;
                                if (hash && hash.length > 1) {
                                    var ruleId = hash.substring(1); // Remove #

                                    if (document.getElementById(ruleId)) {
                                        showRule(null, ruleId);
                                    }
                                }

                                const sidebar = document.getElementById('sidebar');
                                const resizer = document.getElementById('resizer');
                                if (!sidebar || !resizer) return; 
                                let isResizing = false;
                                
                                resizer.addEventListener('mousedown', function(e) {
                                    e.preventDefault(); // Critical: Prevents text selection during drag
                                    isResizing = true;
                                    resizer.classList.add('resizing');
                                    document.body.style.cursor = 'col-resize'; 
                                });
                                
                                document.addEventListener('mousemove', function(e) {
                                    if (!isResizing) return;
                                    const newWidth = e.clientX;
                                    if (newWidth > 200 && newWidth < 800) {
                                        sidebar.style.width = newWidth + 'px';
                                    }
                                });
                                
                                document.addEventListener('mouseup', function(e) {
                                    if (isResizing) {
                                        isResizing = false;
                                        resizer.classList.remove('resizing');
                                        document.body.style.cursor = '';
                                    }
                                });
                            });
                        </script>
                    </head>
                    <body>
                        <div class="sidebar" id="sidebar">
                            <div class="sidebar-header">
                                <div style="background: white; padding: 10px; border-radius: 8px; display: inline-block; margin-bottom: 10px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                                    <img src="logo.svg" alt="Aegis" style="height: 30px; display: block;">
                                </div>
                                <h2>Aegis Rules</h2>
                                <input type="text" id="filterInput" onkeyup="onSearchInput()" placeholder="Search rules..." 
                                       style="width: 100%%; padding: 8px; margin-top: 10px; box-sizing: border-box; border: 1px solid #ccc; border-radius: 4px;">
                            </div>
                            <ul class="sidebar-nav" id="sidebarNav">
                                %s
                            </ul>
                        </div>
                        <div class="resizer" id="resizer"></div>
                        <div class="main-content">
                            <div class="top-nav-container">
                                <a href="CONSOLIDATED-REPORT.html" id="dashboardBtn" class="top-nav-button" title="Return to main dashboard">← Dashboard</a>
                                <a href="#" id="mainPageBtn" class="top-nav-button" title="Go to Home" style="display: none; background-color: #0078d4;">🏠 Home</a>
                            </div>
                            <div class="report-container">
                                %s
                            </div>
                        </div>
                            <script>
                                document.addEventListener('DOMContentLoaded', function() {
                                    var path = window.location.pathname;
                                    var isReport = path.includes('/reports/');
                                    var isStandalone = path.endsWith('/rule_guide.html');
                                    var dashboardBtn = document.getElementById('dashboardBtn');
                                    var mainPageBtn = document.getElementById('mainPageBtn');
                                    

                                    var isInMuleWrapper = path.includes('/apiguard/');
                                    var basePath = isInMuleWrapper ? '/apiguard/Aegis' : '';
                                    
                                    var sessionId = new URLSearchParams(window.location.search).get('session');
                                    if (!sessionId && isReport) {
                                        var parts = path.split('/reports/');
                                        if (parts.length > 1) {
                                            var subparts = parts[1].split('/');
                                            if (subparts.length > 0) sessionId = subparts[0];
                                        }
                                    }
                                    
                                    if (dashboardBtn) {
                                        if (isStandalone) {
                                            dashboardBtn.innerText = "Home";
                                            dashboardBtn.href = basePath + "/";
                                            dashboardBtn.title = "Go to Main Page";
                                            dashboardBtn.style.display = 'inline-block';
                                            if (mainPageBtn) mainPageBtn.style.display = 'none';
                                        } else {
                                            dashboardBtn.style.display = 'inline-block';
                                        }
                                    }
                                    
                                    if (isReport && mainPageBtn) {
                                        mainPageBtn.style.display = 'inline-block';
                                        var mainPageUrl = basePath + '/'; 
                                        if (!isInMuleWrapper) mainPageUrl = '/';
                                        if (sessionId) mainPageUrl += '?session=' + sessionId;
                                        mainPageBtn.href = mainPageUrl;
                                    }
                                });
                            </script>
                    </body>
                    </html>
                    """;
            String finalHtml = String.format(html, sidebar.toString(), content.toString());
            Path ruleGuidePath = outputDir.resolve("rule_guide.html");
            java.util.Optional.ofNullable(ruleGuidePath.getParent()).ifPresent(p -> {
                try {
                    java.nio.file.Files.createDirectories(p);
                } catch (Exception e) {}
            });
            Files.writeString(ruleGuidePath, finalHtml, java.nio.charset.StandardCharsets.UTF_8);
            logger.debug("Rule guide generated (dynamic)");
        } catch (Exception e) {
            logger.error("Failed to generate rule guide: {}", e.getMessage(), e);
        }
    }
    private static String convertMdToHtml(String md) {
        // 1. Extract code blocks to prevent processing them as markdown
        java.util.List<String> codeBlocks = new java.util.ArrayList<>();
        java.util.regex.Matcher codeBlockMatcher = java.util.regex.Pattern.compile("(?s)```(.*?)\\n([\\s\\S]*?)```").matcher(md);
        StringBuffer sb = new StringBuffer();
        while (codeBlockMatcher.find()) {
            String language = codeBlockMatcher.group(1).trim();
            String code = codeBlockMatcher.group(2);
            // Escape HTML in code blocks
            code = escape(code);
            String placeholder = "___CODE_BLOCK_" + codeBlocks.size() + "___";
            codeBlocks.add("<pre><code class='language-" + (language.isEmpty() ? "text" : language) + "'>" + code + "</code></pre>");
            codeBlockMatcher.appendReplacement(sb, placeholder);
        }
        codeBlockMatcher.appendTail(sb);
        String html = sb.toString();

        // 2. Extract inline code
        java.util.List<String> inlineCode = new java.util.ArrayList<>();
        java.util.regex.Matcher inlineMatcher = java.util.regex.Pattern.compile("`([^`]+)`").matcher(html);
        sb = new StringBuffer();
        while (inlineMatcher.find()) {
            String code = inlineMatcher.group(1);
            code = escape(code);
            String placeholder = "___INLINE_CODE_" + inlineCode.size() + "___";
            inlineCode.add("<code class='inline-token'>" + code + "</code>");
            inlineMatcher.appendReplacement(sb, placeholder);
        }
        inlineMatcher.appendTail(sb);
        html = sb.toString();

        // 3. Standard Markdown Processing
        html = html.replaceAll("(?i)(?m)^#{1,6}\\s*Version History[\\s\\S]*$", "");
        html = html.replaceAll("\\[([^\\]]+)\\]\\((?:CODE_|CONFIG_)?([^)]+)\\.md\\)", "<a href=\"#\" onclick=\"showRule(event, '$2')\">$1</a>");
        html = html.replaceAll("(?m)^# (.*)$", "<h1>$1</h1>");
        html = html.replaceAll("(?m)^## (.*)$", "<h2>$1</h2>");
        html = html.replaceAll("(?m)^### (.*)$", "<h3>$1</h3>");
        html = html.replaceAll("(?m)^#### (.*)$", "<h4>$1</h4>");
        html = html.replaceAll("\\*\\*(.*?)\\*\\*", "<strong>$1</strong>");
        html = html.replaceAll("(?m)^- (.*)$", "<li>$1</li>");
        html = html.replaceAll("(?m)^\\|(.+)\\|$", "<tr><td>$1</td></tr>");
        html = html.replaceAll("\\|", "</td><td>");
        html = html.replaceAll("<tr><td></td><td>", "<tr><td>");
        html = html.replaceAll("</td><td></td></tr>", "</td></tr>");
        // Improved separator line removal (handles colons :---)
        html = html.replaceAll("(?m)^<tr><td>[\\s\\-:]+</td>(?:<td>[\\s\\-:]+</td>)*</tr>$", "");
        
        // Wrap consecutive <tr> blocks in <table> tags
        html = html.replaceAll("(?s)(<tr>.*?</tr>)", "<table>$1</table>");
        html = html.replaceAll("</table>\\s*<table>", "");

        html = html.replaceAll("(?m)^$", "<br>");

        // 4. Restore placeholders
        for (int i = 0; i < inlineCode.size(); i++) {
            html = html.replace("___INLINE_CODE_" + i + "___", inlineCode.get(i));
        }
        for (int i = 0; i < codeBlocks.size(); i++) {
            html = html.replace("___CODE_BLOCK_" + i + "___", codeBlocks.get(i));
        }

        return html;
    }
    private static void generateConsolidatedExcel(List<ApiResult> results, Path outputDir) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Aegis Summary");
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle passStyle = workbook.createCellStyle();
            passStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            passStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle failStyle = workbook.createCellStyle();
            failStyle.setFillForegroundColor(IndexedColors.CORAL.getIndex());
            failStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Row header = sheet.createRow(0);
            String[] columns = { "API Name", "Total Rules", "Passed", "Failed", "Status", "Report Path" };
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }
            int rowNum = 1;
            int totalRules = 0, totalPassed = 0, totalFailed = 0;
            for (ApiResult r : results) {
                if (r == null || r.name == null)
                    continue;
                totalRules += r.passed + r.failed;
                totalPassed += r.passed;
                totalFailed += r.failed;
                Row row = sheet.createRow(rowNum++);
                CellStyle rowStyle = (r.failed == 0) ? passStyle : failStyle;
                row.createCell(0).setCellValue(r.name);
                row.createCell(1).setCellValue(r.passed + r.failed);
                row.createCell(2).setCellValue(r.passed);
                row.createCell(3).setCellValue(r.failed);
                row.createCell(4).setCellValue(r.failed == 0 ? "PASS" : "FAIL");
                row.createCell(5).setCellValue(r.reportDir.resolve("report.html").toString());
                for (int i = 0; i < 6; i++) {
                    row.getCell(i).setCellStyle(rowStyle);
                }
            }
            Row summary = sheet.createRow(rowNum++);
            summary.createCell(0).setCellValue("TOTAL");
            summary.createCell(1).setCellValue(totalRules);
            summary.createCell(2).setCellValue(totalPassed);
            summary.createCell(3).setCellValue(totalFailed);
            summary.createCell(4).setCellValue(totalFailed == 0 ? "ALL PASS" : "SOME FAILURES");
            CellStyle bold = workbook.createCellStyle();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            bold.setFont(boldFont);
            summary.getCell(0).setCellStyle(bold);
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }
            Path excelPath = outputDir.resolve("CONSOLIDATED-REPORT.xlsx");
            try (FileOutputStream fos = new FileOutputStream(excelPath.toFile())) {
                workbook.write(fos);
            }
            logger.debug("Consolidated Excel report generated");
        } catch (Exception e) {
            logger.error("Failed to generate consolidated Excel report: {}", e.getMessage(), e);
        }
    }
    private static String escape(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
    private static String readResource(String path) {
        try (InputStream is = ReportGenerator.class.getResourceAsStream(path)) {
            if (is == null) return null;
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Failed to read resource {}: {}", path, e.getMessage());
            return null;
        }
    }

    private static void copyHelpFile(Path outputDir) {
        try {
            InputStream helpStream = ReportGenerator.class.getResourceAsStream("/help.html");
            if (helpStream == null) {
                logger.warn("Help file not found in resources");
            } else {
                Path helpPath = outputDir.resolve("help.html");
                Files.copy(helpStream, helpPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                helpStream.close();
                logger.debug("Help file copied");
            }
            InputStream logoStream = ReportGenerator.class.getResourceAsStream("/logo.svg");
            if (logoStream == null) {
                logger.warn("Logo not found in resources");
            } else {
                Path logoPath = outputDir.resolve("logo.svg");
                Files.copy(logoStream, logoPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                logoStream.close();
                logger.debug("Logo copied");
            }
        } catch (Exception e) {
            logger.warn("Failed to copy help files: {}", e.getMessage());
        }
    }
}
