package com.raks.aegis.engine;
import com.raks.aegis.model.ValidationReport;
import com.raks.aegis.model.ValidationReport.RuleResult;
import com.raks.aegis.AegisMain.ApiResult;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReportGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ReportGenerator.class);
    private static final String[] RULE_DOCS = {
            "QUICK_REFERENCE.md",
            "GLOBAL_CONFIGURATION.md",
            "PROJECT_IDENTIFICATION.md",
            "CROSS_PROJECT_RESOLUTION.md",
            "CODE_FILE_EXISTS.md",
            "CODE_GENERIC_TOKEN_SEARCH.md",
            "CODE_GENERIC_TOKEN_SEARCH_FORBIDDEN.md",
            "CODE_GENERIC_TOKEN_SEARCH_REQUIRED.md",
            "CODE_JSON_VALIDATION_FORBIDDEN.md",
            "CODE_JSON_VALIDATION_REQUIRED.md",
            "CODE_POM_VALIDATION_FORBIDDEN.md",
            "CODE_POM_VALIDATION_REQUIRED.md",
            "CODE_PROJECT_CONTEXT.md",
            "CODE_XML_ATTRIBUTE_EXISTS.md",
            "CODE_XML_ATTRIBUTE_NOT_EXISTS.md",
            "CODE_XML_ELEMENT_CONTENT_FORBIDDEN.md",
            "CODE_XML_ELEMENT_CONTENT_REQUIRED.md",
            "CODE_XML_XPATH_EXISTS.md",
            "CODE_XML_XPATH_NOT_EXISTS.md",
            "CONDITIONAL_CHECK.md",
            "CONFIG_GENERIC_PROPERTY_FILE_CHECK.md",
            "CONFIG_MANDATORY_PROPERTY_VALUE_CHECK.md",
            "CONFIG_MANDATORY_SUBSTRING_CHECK.md",
            "CONFIG_OPTIONAL_PROPERTY_VALUE_CHECK.md"
    };
    public static void generateIndividualReports(ValidationReport report, Path outputDir) {
        try {
            Files.createDirectories(outputDir);

            try (InputStream logoStream = ReportGenerator.class.getResourceAsStream("/logo.svg")) {
                if (logoStream != null) {
                    Path logoPath = outputDir.resolve("logo.svg");
                    Files.copy(logoStream, logoPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ignored) {}

            generateHtml(report, outputDir.resolve("report.html"));
            generateExcel(report, outputDir.resolve("report.xlsx"));

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
                    return r1.passed ? 1 : -1; 
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
                String configRow = "";
                if (r.ruleConfig != null && !r.ruleConfig.isEmpty()) {
                    String configId = "config-" + r.id + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
                    configRow = "<div style='margin-top:5px; padding-top:5px; border-top:1px dashed #ccc; font-size:0.85rem; color:#666;'>" +
                            "<a href='javascript:void(0)' id='" + configId + "-link' class='config-toggle' onclick=\"toggleConfig('" + configId + "')\" style='text-decoration:none; color:#663399; font-weight:bold;'>[+] Show Rule Config</a>" +
                            "<div id='" + configId + "' class='rule-config' style='display:none; margin-top:10px; margin-left:20px; padding:12px; background:#e3f2fd; border:1px solid #90caf9; border-left: 5px solid #2196f3; box-shadow: 0 4px 6px rgba(0,0,0,0.1); border-radius:4px; font-family: Consolas, monospace; white-space: pre-wrap; color: #0d47a1;'>" + 
                            escape(r.ruleConfig) + 
                            "</div></div>";
                }
                if (r.passed) {
                    String message = r.checks.isEmpty() ? "All checks passed" : r.checks.get(0).message;
                    rows.append(String.format(
                            "<tr style='background-color:#f0fff4; border-bottom: 4px solid white;'> " +
                            "<td style='border-left: 6px solid #28a745;'>%s</td>" + 
                            "<td>%s</td><td>%s</td><td><strong style='color:#155724'>%s</strong></td>" + 
                            "<td><div style='word-wrap: break-word; white-space: pre-wrap;'>%s</div>%s</td></tr>",
                            escape(r.displayId), escape(r.name), escape(r.severity), escape(passLabel), formatMessage(message), configRow));
                } else {
                    List<String> messages = r.checks.stream()
                            .filter(c -> !c.passed)
                            .map(c -> formatMessage(c.message))
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
                            .map(entry -> {
                                String msg = entry.getKey().replace("[Config]", "<span class='config-label'>CONFIG - </span>");
                                String files = entry.getValue().replace("[Config]", "<span class='config-label'>CONFIG - </span>");
                                if (files.isEmpty()) return "• " + msg;
                                
                                String[] fileArr = files.split(", ");
                                if (fileArr.length > 5) {
                                    return "• " + msg + " <details style='display:inline-block; margin-left:10px;'><summary style='cursor:pointer; color:#663399; font-weight:bold;'>[" + fileArr.length + " files]</summary><div style='padding:5px; background:#f9f9f9; border:1px solid #ddd; border-radius:3px; margin-top:5px; max-height:200px; overflow-y:auto;'>" + files + "</div></details>";
                                } else {
                                    return "• " + msg + " " + files;
                                }
                            })
                            .collect(Collectors.joining("<br>"));

                    rows.append(String.format(
                            "<tr style='background-color:#fff5f5; border-bottom: 4px solid white;'> " + 
                            "<td style='border-left: 6px solid #dc3545;'>%s</td>" + 
                            "<td>%s</td><td>%s</td><td><strong style='color:#721c24'>%s</strong></td>" + 
                            "<td><div style='word-wrap: break-word; white-space: pre-wrap;'>%s</div>%s</td></tr>",
                            escape(r.displayId), escape(r.name), escape(r.severity), escape(failLabel), details, configRow));
                }
            } 

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
                            table {width: 100%%; border-collapse: collapse; box-shadow: 0 4px 12px rgba(0,0,0,0.1); table-layout: fixed; }
                            th, td {border: 1px solid #ddd; padding: 12px; text-align: left; word-wrap: break-word; overflow-wrap: break-word; }
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
                            .config-label { color: #0047AB; font-weight: bold; }
                        </style>
                    </head>
                    <body>
                        <div class="report-container">
                                <div class="top-nav-container">
                                <a href="../CONSOLIDATED-REPORT.html" class="top-nav-button" title="Return to main dashboard">← Dashboard</a>
                                <a href="report.xlsx" class="top-nav-button" style="background-color: #217346;">Excel Report</a>
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

                            <div class="search-box" style="display:flex; gap:10px; align-items:center;">
                                <input type="text" id="searchInput" onkeyup="filterTable()" placeholder="Search by name, status, or details..." style="flex-grow:1;">
                                <button class="top-nav-button" onclick="toggleAllConfigs(true)" style="width:150px; font-size:12px; white-space:nowrap;">Expand All Configs</button>
                                <button class="top-nav-button" onclick="toggleAllConfigs(false)" style="width:120px; font-size:12px; background:#999; white-space:nowrap;">Collapse All</button>
                            </div>
                            <table id="resultsTable">
                                <thead>
                                    <tr>
                                        <th style="width: 5%%;" onclick="sortTable(0)">Rule #</th>
                                        <th style="width: 25%%;" onclick="sortTable(1)">Name</th>
                                        <th style="width: 5%%;" onclick="sortTable(2)">Severity</th>
                                        <th style="width: 5%%;" onclick="sortTable(3)">Status</th>
                                        <th style="width: 60%%;" onclick="sortTable(4)">Details</th>
                                    </tr>
                                </thead>
                                <tbody id="tableBody">
                                    %s
                                </tbody>
                            </table>
                        </div>
                        <script>
                            function toggleConfig(id) {
                                var x = document.getElementById(id);
                                var link = document.getElementById(id + '-link');
                                if (x.style.display === "none") {
                                    x.style.display = "block";
                                    if(link) link.innerHTML = "[-] Hide Rule Config";
                                } else {
                                    x.style.display = "none";
                                    if(link) link.innerHTML = "[+] Show Rule Config";
                                }
                            }

                            function toggleAllConfigs(expand) {
                                var configs = document.getElementsByClassName("rule-config");
                                for (var i = 0; i < configs.length; i++) {
                                    var div = configs[i];
                                    var id = div.id;
                                    var link = document.getElementById(id + '-link');
                                    if (expand) {
                                        div.style.display = "block";
                                        if (link) link.innerHTML = "[-] Hide Rule Config";
                                    } else {
                                        div.style.display = "none";
                                        if (link) link.innerHTML = "[+] Show Rule Config";
                                    }
                                }
                            }

                            function filterTable() {
                                var input, filter, table, tr, td, i, txtValue;
                                input = document.getElementById("searchInput");
                                filter = input.value.toUpperCase();
                                table = document.getElementById("resultsTable");
                                tr = table.getElementsByTagName("tr");

                                // Remove existing highlights first
                                removeHighlights();

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

                                // Apply highlighting if there is a filter
                                if (filter.length > 0) {
                                    highlightSearch(input.value);
                                }
                            }

                            function removeHighlights() {
                                var highlighted = document.querySelectorAll('span.search-highlight');
                                highlighted.forEach(function(span) {
                                    var parent = span.parentNode;
                                    parent.replaceChild(document.createTextNode(span.textContent), span);
                                    parent.normalize();
                                });
                            }

                            function highlightSearch(term) {
                                if (!term) return;
                                // Escape regex special characters in the term
                                // We use a simplified character set to avoid Java escaping hell
                                // [-.[]{}()...] -> escaped with double backslash for Java string literal
                                var escapedTerm = term.replace(/[-[\\]{}()*+?.,\\\\^$|#\\s]/g, '\\\\$&');
                                var regex;
                                try {
                                    regex = new RegExp("(" + escapedTerm + ")", "gi");
                                } catch (e) {
                                    return; 
                                }
                                var body = document.getElementById("tableBody");
                                recursiveHighlight(body, regex);
                            }

                            function recursiveHighlight(node, regex) {
                                if (node.nodeType === 3) { // Text node
                                     if (node.nodeValue.trim().length > 0 && regex.test(node.nodeValue)) {
                                         var span = document.createElement("span");
                                         span.innerHTML = node.nodeValue.replace(regex, "<span class='search-highlight' style='background-color: #ffeb3b; color: black; font-weight: bold; padding: 2px 4px; border-radius: 2px;'>$1</span>");
                                         node.parentNode.replaceChild(span, node);
                                     }
                                } else if (node.nodeType === 1 && node.nodeName !== "SCRIPT" && node.nodeName !== "STYLE" && !node.classList.contains("search-highlight")) {
                                     // Clone children to avoid iteration issues if we modify DOM
                                     var children = Array.from(node.childNodes);
                                     for (var i = 0; i < children.length; i++) {
                                         recursiveHighlight(children[i], regex);
                                     }
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

            // --- Styles ---
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            titleStyle.setFont(titleFont);
            
            CellStyle labelStyle = workbook.createCellStyle();
            Font labelFont = workbook.createFont();
            labelFont.setBold(true);
            labelStyle.setFont(labelFont);
            
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            
            CellStyle passStyle = workbook.createCellStyle();
            passStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            passStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            passStyle.setBorderBottom(BorderStyle.THIN);
            passStyle.setBorderTop(BorderStyle.THIN);
            passStyle.setBorderLeft(BorderStyle.THIN);
            passStyle.setBorderRight(BorderStyle.THIN);

            CellStyle failStyle = workbook.createCellStyle();
            failStyle.setFillForegroundColor(IndexedColors.CORAL.getIndex());
            failStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            failStyle.setBorderBottom(BorderStyle.THIN);
            failStyle.setBorderTop(BorderStyle.THIN);
            failStyle.setBorderLeft(BorderStyle.THIN);
            failStyle.setBorderRight(BorderStyle.THIN);

            // --- Metadata Header ---
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("AEGIS VALIDATION REPORT");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 4));
            
            Row projectRow = sheet.createRow(2);
            projectRow.createCell(0).setCellValue("Project Path:");
            projectRow.getCell(0).setCellStyle(labelStyle);
            projectRow.createCell(1).setCellValue(report.projectPath);
            
            Row dateRow = sheet.createRow(3);
            dateRow.createCell(0).setCellValue("Generated:");
            dateRow.getCell(0).setCellStyle(labelStyle);
            dateRow.createCell(1).setCellValue(ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")));
            
            int passedCount = report.passed.size();
            int failedCount = report.failed.size();
            int total = passedCount + failedCount;
            
            Row summaryRow = sheet.createRow(4);
            summaryRow.createCell(0).setCellValue("Summary:");
            summaryRow.getCell(0).setCellStyle(labelStyle);
            summaryRow.createCell(1).setCellValue("Total Rules: " + total + " | Passed: " + passedCount + " | Failed: " + failedCount);
            
            Row statusRow = sheet.createRow(5);
            statusRow.createCell(0).setCellValue("Overall Status:");
            statusRow.getCell(0).setCellStyle(labelStyle);
            Cell statusVal = statusRow.createCell(1);
            statusVal.setCellValue(failedCount == 0 ? "PASS" : "FAIL");
            CellStyle statusStyle = workbook.createCellStyle();
            Font statusFont = workbook.createFont();
            statusFont.setBold(true);
            statusFont.setColor(failedCount == 0 ? IndexedColors.GREEN.getIndex() : IndexedColors.RED.getIndex());
            statusStyle.setFont(statusFont);
            statusVal.setCellStyle(statusStyle);

            // --- Results Table ---
            int startRow = 7;
            Row header = sheet.createRow(startRow);
            String[] columns = { "Rule ID", "Name", "Severity", "Status", "Details" };
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            Map<String, String> labels = report.labels != null ? report.labels : new java.util.HashMap<>();
            String passLabel = labels.getOrDefault("PASS", "PASS");
            String failLabel = labels.getOrDefault("FAIL", "FAIL");

            int rowNum = startRow + 1;
            for (RuleResult r : report.passed) {
                String message = r.checks.isEmpty() ? "All checks passed" : r.checks.get(0).message;
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(r.id);
                row.createCell(1).setCellValue(r.name);
                row.createCell(2).setCellValue(r.severity);
                row.createCell(3).setCellValue(passLabel);
                row.createCell(4).setCellValue(message);
                for (int i = 0; i < 5; i++)
                    row.getCell(i).setCellStyle(passStyle);
            }
            for (RuleResult r : report.failed) {
                String details = r.checks.stream()
                        .filter(c -> !c.passed)
                        .map(c -> "• " + c.message)
                        .collect(Collectors.joining("\n"));
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(r.id);
                row.createCell(1).setCellValue(r.name);
                row.createCell(2).setCellValue(r.severity);
                row.createCell(3).setCellValue(failLabel);
                row.createCell(4).setCellValue(details.isEmpty() ? "Failed" : details);
                for (int i = 0; i < 5; i++)
                    row.getCell(i).setCellStyle(failStyle);
            }
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
                // Cap width to avoid extremely wide columns for long details
                if (sheet.getColumnWidth(i) > 20000) {
                    sheet.setColumnWidth(i, 20000);
                }
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
            Files.createDirectories(outputPath); 

            try (InputStream logoStream = ReportGenerator.class.getResourceAsStream("/logo.svg")) {
                if (logoStream != null) {
                    Path logoPath = outputPath.resolve("logo.svg");
                    Files.copy(logoStream, logoPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ignored) {

            }

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

                     relativeLink = r.reportDir.getFileName().toString() + "/report.html";
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

            try (java.io.InputStream logoStream = ReportGenerator.class.getResourceAsStream("/logo.svg")) {
                if (logoStream != null) {
                    Path logoPath = outputPath.resolve("logo.svg");
                    Files.copy(logoStream, logoPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception logoEx) {
                logger.warn("Failed to copy logo to consolidated report: {}", logoEx.getMessage());
            }
            generateConsolidatedExcel(results, outputPath);
        } catch (Throwable t) {
            logger.error("Failed to generate consolidated report");
            logger.error("Error type: {}", t.getClass().getName());
            logger.error("Error message: {}", (t.getMessage() != null ? t.getMessage() : "null"), t); 
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
                    sidebar.append(String.format("<a href=\"#\" onclick=\"showRule(event, '%s')\" data-rule-id=\"%s\">%s</a>", ruleName, ruleName, ruleName));
                    sidebar.append("</div>");
                    sidebar.append(subNav.toString());
                } else {
                     sidebar.append(String.format("<a href=\"#\" onclick=\"showRule(event, '%s')\" data-rule-id=\"%s\">%s</a>", ruleName, ruleName, ruleName));
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

                                        // Use data-rule-id for robustness
                                        var ruleId = a.getAttribute("data-rule-id");
                                        if (!ruleId) continue; 

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
                                <div style="display: flex; align-items: center; gap: 15px; margin-bottom: 15px;">
                                    <div style="background: white; padding: 10px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                                        <img src="logo.svg" alt="Aegis" style="height: 35px; display: block;">
                                    </div>
                                    <h1 style="margin: 0; font-size: 2rem; color: white !important; font-weight: bold; letter-spacing: 2px; border-bottom: none !important; padding-bottom: 0 !important;">AEGIS</h1>
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
                                    var basePath = isInMuleWrapper ? '/apiguard/aegis' : '';

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


            try {
                Path resourcesPath = java.nio.file.Paths.get("src/main/resources/web/aegis/rule_guide.html");
                if (java.nio.file.Files.exists(resourcesPath.getParent())) {
                    Files.writeString(resourcesPath, finalHtml, java.nio.charset.StandardCharsets.UTF_8);

                }
            } catch (Exception e) {
                logger.warn("Could not write rule_guide.html to resources folder: {}", e.getMessage());
            }

            logger.debug("Rule guide generated (dynamic)");
        } catch (Exception e) {
            logger.error("Failed to generate rule guide: {}", e.getMessage(), e);
        }
    }
    private static String convertMdToHtml(String md) {

        java.util.List<String> codeBlocks = new java.util.ArrayList<>();
        java.util.regex.Matcher codeBlockMatcher = java.util.regex.Pattern.compile("(?s)```(.*?)\\n([\\s\\S]*?)```").matcher(md);
        StringBuffer sb = new StringBuffer();
        while (codeBlockMatcher.find()) {
            String language = codeBlockMatcher.group(1).trim();
            String code = codeBlockMatcher.group(2);

            code = escape(code);
            String placeholder = "___CODE_BLOCK_" + codeBlocks.size() + "___";
            codeBlocks.add("<pre><code class='language-" + (language.isEmpty() ? "text" : language) + "'>" + code + "</code></pre>");
            codeBlockMatcher.appendReplacement(sb, placeholder);
        }
        codeBlockMatcher.appendTail(sb);
        String html = sb.toString();

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

        html = html.replaceAll("(?m)^<tr><td>[\\s\\-:]+</td>(?:<td>[\\s\\-:]+</td>)*</tr>$", "");

        html = html.replaceAll("(?s)(<tr>.*?</tr>)", "<table>$1</table>");
        html = html.replaceAll("</table>\\s*<table>", "");

        html = html.replaceAll("(?m)^$", "<br>");

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
            
            // --- Styles ---
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            titleStyle.setFont(titleFont);
            
            CellStyle labelStyle = workbook.createCellStyle();
            Font labelFont = workbook.createFont();
            labelFont.setBold(true);
            labelStyle.setFont(labelFont);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            
            CellStyle passStyle = workbook.createCellStyle();
            passStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            passStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            passStyle.setBorderBottom(BorderStyle.THIN);
            passStyle.setBorderTop(BorderStyle.THIN);
            passStyle.setBorderLeft(BorderStyle.THIN);
            passStyle.setBorderRight(BorderStyle.THIN);

            CellStyle failStyle = workbook.createCellStyle();
            failStyle.setFillForegroundColor(IndexedColors.CORAL.getIndex());
            failStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            failStyle.setBorderBottom(BorderStyle.THIN);
            failStyle.setBorderTop(BorderStyle.THIN);
            failStyle.setBorderLeft(BorderStyle.THIN);
            failStyle.setBorderRight(BorderStyle.THIN);

            // --- Stats Calculation ---
            int totalRules = 0, totalPassed = 0, totalFailed = 0;
            for (ApiResult r : results) {
                if (r == null || r.name == null) continue;
                totalRules += r.passed + r.failed;
                totalPassed += r.passed;
                totalFailed += r.failed;
            }

            // --- Metadata Header ---
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("AEGIS CONSOLIDATED REPORT");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 5));
            
            Row dateRow = sheet.createRow(2);
            dateRow.createCell(0).setCellValue("Generated:");
            dateRow.getCell(0).setCellStyle(labelStyle);
            dateRow.createCell(1).setCellValue(ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")));
            
            Row summaryRow = sheet.createRow(3);
            summaryRow.createCell(0).setCellValue("Summary:");
            summaryRow.getCell(0).setCellStyle(labelStyle);
            summaryRow.createCell(1).setCellValue("Total APIs: " + results.size() + " | Total Rules: " + totalRules);
            
            Row statsRow = sheet.createRow(4);
            statsRow.createCell(0).setCellValue("Statistics:");
            statsRow.getCell(0).setCellStyle(labelStyle);
            statsRow.createCell(1).setCellValue("Passed: " + totalPassed + " | Failed: " + totalFailed);
            
            Row statusRow = sheet.createRow(5);
            statusRow.createCell(0).setCellValue("Overall Status:");
            statusRow.getCell(0).setCellStyle(labelStyle);
            Cell statusVal = statusRow.createCell(1);
            statusVal.setCellValue(totalFailed == 0 ? "PASS" : "FAIL");
            CellStyle statusStyle = workbook.createCellStyle();
            Font statusFont = workbook.createFont();
            statusFont.setBold(true);
            statusFont.setColor(totalFailed == 0 ? IndexedColors.GREEN.getIndex() : IndexedColors.RED.getIndex());
            statusStyle.setFont(statusFont);
            statusVal.setCellStyle(statusStyle);

            // --- Results Table ---
            int startRow = 7;
            Row header = sheet.createRow(startRow);
            String[] columns = { "API Name", "Total Rules", "Passed", "Failed", "Status", "Report Path" };
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }
            
            int rowNum = startRow + 1;
            for (ApiResult r : results) {
                if (r == null || r.name == null)
                    continue;
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
            
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
                if (sheet.getColumnWidth(i) > 15000) {
                    sheet.setColumnWidth(i, 15000);
                }
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

    private static String formatMessage(String message) {
        String escaped = escape(message);
        return escaped.replace("Files Checked:", "<strong>Files Checked:</strong>")
                      .replace("Items Found:", "<strong>Items Found:</strong>")
                      .replace("Items Matched:", "<strong>Items Matched:</strong>")
                      .replace("Details:", "<strong>Details:</strong>")
                      .replace("Failures:", "<strong>Failures:</strong>") // Bold 'Failures:' for failed checks
                      .replace("Properties Resolved:", "<strong>Properties Resolved:</strong>")
                      .replace("[Config]", "<span class='config-label'>CONFIG - </span>");
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
