package com.raks.muleguard.engine;
import com.raks.muleguard.model.ValidationReport;
import com.raks.muleguard.model.ValidationReport.RuleResult;
import com.raks.muleguard.MuleGuardMain.ApiResult;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
public class ReportGenerator {
    private static final String[] RULE_DOCS = {
            "CONFIG_CLIENTIDMAP_VALIDATOR.md",
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
            "CODE_XML_XPATH_NOT_EXISTS.md"
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
                    System.out.println("   → logo.svg copied to " + outputDir.getFileName());
                } else {
                    System.err.println("Warning: logo.svg not found in resources for " + outputDir.getFileName());
                }
            } catch (Exception logoEx) {
                System.err.println(
                        "Warning: Failed to copy logo.svg to " + outputDir.getFileName() + ": " + logoEx.getMessage());
            }
        } catch (Exception e) {
            System.err.println("Failed to generate individual reports: " + e.getMessage());
        }
    }
    public static void generateHtml(ValidationReport report, Path outputPath) {
        try {
            Files.createDirectories(outputPath.getParent());
            StringBuilder rows = new StringBuilder();
            for (RuleResult r : report.passed) {
                String message = r.checks.isEmpty() ? "All checks passed" : r.checks.get(0).message;
                rows.append(String.format(
                        "<tr style='background-color:#e8f5e9'><td>%s</td><td>%s</td><td>%s</td><td><strong style='color:green'>PASS</strong></td><td>%s</td></tr>",
                        escape(r.id), escape(r.name), escape(r.severity), escape(message)));
            }
            for (RuleResult r : report.failed) {
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
                        "<tr style='background-color:#ffebee'><td>%s</td><td>%s</td><td>%s</td><td><strong style='color:red'>FAIL</strong></td><td>%s</td></tr>",
                        escape(r.id), escape(r.name), escape(r.severity), details));
            }
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <title>MuleGuard Report - %s</title>
                        <style>
                            :root {
                                --truist-purple: #663399;
                                --truist-purple-light: #7d4fb2;
                                --text-white: #FFFFFF;
                            }
                            body {font-family: Arial, sans-serif; margin: 0; background-color: #f5f5f5;}
                            .report-container { border: 5px solid var(--truist-purple); padding: 20px 40px; margin: 20px; border-radius: 8px; background-color: white; position: relative; }
                            h1 {color: var(--truist-purple);}
                            .summary {background: #e3f2fd; padding: 20px; border-radius: 8px; margin-bottom: 20px;}
                            table {width: 100%%; border-collapse: collapse; box-shadow: 0 4px 12px rgba(0,0,0,0.1);}
                            th, td {border: 1px solid #ddd; padding: 12px; text-align: left;}
                            th {background-color: var(--truist-purple); color: var(--text-white);}
                            .contact-button {
                                background-color: var(--truist-purple);
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
                            .contact-button:hover { background-color: var(--truist-purple-light); }
                            .top-nav-container {
                                position: absolute;
                                top: 20px;
                                right: 40px;
                                display: flex;
                                gap: 10px;
                            }
                            .top-nav-button {
                                background-color: var(--truist-purple);
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
                            .top-nav-button:hover { background-color: var(--truist-purple-light); }
                        </style>
                    </head>
                    <body>
                        <div class="report-container">
                            <div class="top-nav-container">
                                <a href="../CONSOLIDATED-REPORT.html" class="top-nav-button" title="Return to main dashboard">← Dashboard</a>
                                <a href="../checklist.html" class="top-nav-button">Checklist</a>
                                <a href="../rule_guide.html" class="top-nav-button">Rule Guide</a>
                                <a href="../help.html" class="top-nav-button">Help</a>
                            </div>
                            <div style="display: flex; align-items: center; margin-bottom: 20px;">
                                <img src="logo.svg" alt="MuleGuard Logo" style="height: 40px; margin-right: 15px;">
                                <h1 style="margin: 0;">MuleGuard - Mulesoft Application Review & Validation</h1>
                            </div>
                            <div class="summary">
                                <strong>Project:</strong> %s<br>
                                <strong>Generated:</strong> %s<br>
                                <strong>Total Rules:</strong> %d | <strong style="color:green">Passed:</strong> %d | <strong style="color:red">Failed:</strong> %d
                            </div>
                            <table><tr><th>Rule ID</th><th>Name</th><th>Severity</th><th>Status</th><th>Details</th></tr>%s</table>
                        </div>
                    </body>
                    </html>
                    """
                    .formatted(
                            escape(report.projectPath),
                            escape(report.projectPath),
                            LocalDateTime.now(),
                            report.passed.size() + report.failed.size(),
                            report.passed.size(),
                            report.failed.size(),
                            rows.toString());
            Files.writeString(outputPath, html, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Failed to generate HTML: " + e.getMessage());
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
            String[] columns = { "Rule ID", "Name", "Severity", "Status", "Details" };
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }
            int rowNum = 1;
            for (RuleResult r : report.passed) {
                String message = r.checks.isEmpty() ? "All checks passed" : r.checks.get(0).message;
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(r.id);
                row.createCell(1).setCellValue(r.name);
                row.createCell(2).setCellValue(r.severity);
                row.createCell(3).setCellValue("PASS");
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
                row.createCell(3).setCellValue("FAIL");
                row.createCell(4).setCellValue(details.isEmpty() ? "Failed" : details);
                for (int i = 0; i < 5; i++)
                    row.getCell(i).setCellStyle(failStyle);
            }
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }
            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                workbook.write(fos);
            }
        } catch (Exception e) {
            System.err.println("Failed to generate Excel: " + e.getMessage());
        }
    }
    public static void generateConsolidatedReport(List<ApiResult> results, Path outputPath) {
        try {
            if (results == null || results.isEmpty()) {
                System.err.println("No results to generate consolidated report.");
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
                        System.err.println("Warning: Report not found: " + target);
                    }
                } catch (Exception e) {
                    System.err.println("Invalid report path for API: " + r.name);
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
                        <title>MuleGuard - Consolidated Report</title>
                        <style>
                            :root {
                                --truist-purple: #663399;
                                --truist-purple-light: #7d4fb2;
                                --text-white: #FFFFFF;
                            }
                            body {font-family: Arial, sans-serif; margin: 0; background-color: #f0f0f0;}
                            .report-container { border: 5px solid var(--truist-purple); padding: 20px 40px; margin: 20px; border-radius: 8px; background-color: white; }
                            h1 {color: var(--truist-purple);}
                            .card {background: white; padding: 20px; border-radius: 10px; box-shadow: 0 4px 20px rgba(0,0,0,0.1); margin-bottom: 20px;}
                            table {width: 100%%; border-collapse: collapse;}
                            th, td {border: 1px solid #ddd; padding: 12px; text-align: left;}
                            th {background: var(--truist-purple); color: var(--text-white);}
                            .top-nav-container {
                                position: absolute;
                                top: 20px;
                                right: 40px;
                                display: flex;
                                gap: 10px;
                            }
                            .top-nav-button {
                                background-color: var(--truist-purple);
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
                            .top-nav-button:hover { background-color: var(--truist-purple-light); }
                        </style>
                    </head>
                    <body>
                        <div class="report-container">
                            <div class="top-nav-container">
                                <a href="checklist.html" class="top-nav-button">Checklist</a>
                                <a href="rule_guide.html" class="top-nav-button">Rule Guide</a>
                                <a href="help.html" class="top-nav-button">Help</a>
                            </div>
                            <div style="display: flex; align-items: center; margin-bottom: 20px;">
                                <img src="logo.svg" alt="MuleGuard Logo" style="height: 40px; margin-right: 15px;">
                                <h1 style="margin: 0;">MuleGuard - Mulesoft Application Review & Validation</h1>
                            </div>
                            <div style="border: 1px solid #ccc; padding: 10px 20px; margin-top: 15px; margin-bottom: 20px; background-color: #fbfbfbff; border-radius: 5px;">
                            <h4 style="margin-top: 0; color: #333;">Report Details:</h4>
                                <strong>Generated:</strong> %s<br>
                                <strong>Total APIs Scanned:</strong> %d<br>
                                <strong>Total Rules:</strong> %d | <strong style="color:green">Passed:</strong> %d | <strong style="color:red">Failed:</strong> %d
                            </div>
                            <table><tr><th>API Name</th><th>Total Rules</th><th>Passed</th><th>Failed</th><th>Status</th><th>Report</th></tr>%s</table>
                            </div>
                    </body>
                    </html>
                    """
                    .formatted(
                            LocalDateTime.now(),
                            totalApis,
                            totalRules,
                            totalPassed,
                            totalFailed,
                            tableRows);
            Path htmlPath = outputPath.resolve("CONSOLIDATED-REPORT.html");
            Files.writeString(htmlPath, html, java.nio.charset.StandardCharsets.UTF_8);
            System.out.println("CONSOLIDATED REPORT GENERATED:");
            System.out.println("   → " + htmlPath.toAbsolutePath());
            copyHelpFile(outputPath);
            generateConsolidatedExcel(results, outputPath);
            generateChecklistReport(outputPath); 
            generateRuleGuide(outputPath); 
        } catch (Throwable t) {
            System.err.println("FAILED TO GENERATE CONSOLIDATED REPORT!");
            System.err.println("Error type: " + t.getClass().getName());
            System.err.println("Message: " + (t.getMessage() != null ? t.getMessage().replace('%', '％') : "null"));
            t.printStackTrace(System.err); 
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
                    String[] parts = line.split(",", 3);
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
                        <title>MuleGuard - Validation Checklist</title>
                        <style>
                            :root {
                                --truist-purple: #663399;
                                --truist-purple-light: #7d4fb2;
                                --text-white: #FFFFFF;
                            }
                            body {font-family: Arial, sans-serif; margin: 0; background-color: #f0f0f0;}
                            .report-container { border: 5px solid var(--truist-purple); padding: 20px 40px; margin: 20px; border-radius: 8px; background-color: white; position: relative; }
                            h1 {color: var(--truist-purple);}
                            table {width: 100%%; border-collapse: collapse; box-shadow: 0 4px 12px rgba(0,0,0,0.1);}
                            th, td {border: 1px solid #ddd; padding: 12px; text-align: left;}
                            th {background-color: var(--truist-purple); color: var(--text-white);}
                            .contact-button {
                                background-color: var(--truist-purple);
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
                            .contact-button:hover { background-color: var(--truist-purple-light); }
                            .top-nav-container {
                                position: absolute;
                                top: 20px;
                                right: 40px;
                                display: flex;
                                gap: 10px;
                            }
                            .top-nav-button {
                                background-color: var(--truist-purple);
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
                            .top-nav-button:hover { background-color: var(--truist-purple-light); }
                        </style>
                    </head>
                    <body>
                        <div class="report-container">
                            <div class="top-nav-container">
                                <a href="CONSOLIDATED-REPORT.html" id="dashboardBtn" class="top-nav-button" title="Return to main dashboard">← Dashboard</a>
                                <a href="/muleguard/main" id="mainPageBtn" class="top-nav-button" title="Go to Main Page" style="display: none; background-color: #0078d4;">🏠 Main Page</a>
                                <a href="help.html" class="top-nav-button">Help</a>
                            </div>
                            <div style="display: flex; align-items: center; margin-bottom: 20px;">
                                <img src="logo.svg" alt="MuleGuard Logo" style="height: 40px; margin-right: 15px;">
                                <h1 style="margin: 0;">MuleGuard - Mulesoft Application Review & Validation</h1>
                            </div>
                            <p>This page lists all the individual checks performed by the MuleGuard tool.</p>
                            <table>
                                <tr><th>Sr.#</th><th>ChecklistItem</th><th>ChecklistType</th><th>RuleId</th></tr>
                                %s
                            </table>
                        </div>
                            <script>
                                document.addEventListener('DOMContentLoaded', function() {
                                    var path = window.location.pathname;
                                    var isMuleStatic = path.includes('/apiguard/muleguard/web/') || path.includes('/muleguard/web/');
                                    var isMuleReport = path.includes('/apiguard/muleguard/reports/') || path.includes('/muleguard/reports/');
                                    var dashboardBtn = document.getElementById('dashboardBtn');
                                    var mainPageBtn = document.getElementById('mainPageBtn');
                                    
                                    // Detect if we're in Mule wrapper (has /apiguard prefix) or standalone Java
                                    var isInMuleWrapper = path.includes('/apiguard/');
                                    var basePath = isInMuleWrapper ? '/apiguard/muleguard' : '/muleguard';
                                    
                                    var sessionId = new URLSearchParams(window.location.search).get('session');
                                    if (!sessionId && isMuleReport) {
                                        var parts = path.split('/muleguard/reports/');
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
                    """
                    .formatted(rows.toString());
            Path checklistPath = outputDir.resolve("checklist.html");
            Files.writeString(checklistPath, html, java.nio.charset.StandardCharsets.UTF_8);
            System.out.println("   → checklist.html generated");
        } catch (Exception e) {
            System.err.println("Failed to generate checklist report: " + e.getMessage());
        }
    }
    private static void generateRuleGuide(Path outputDir) {
        try {
            StringBuilder sidebar = new StringBuilder();
            StringBuilder content = new StringBuilder();
            int index = 0;
            for (String fileName : RULE_DOCS) {
                String ruleName = fileName.replace(".md", "");
                String contentHtml = "";
                try (InputStream is = ReportGenerator.class.getResourceAsStream("/docs/" + fileName)) {
                    if (is != null) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
                        String md = reader.lines().collect(Collectors.joining("\n"));
                        contentHtml = convertMdToHtml(md);
                    } else {
                        contentHtml = "<p>Documentation not found for " + ruleName + "</p>";
                    }
                } catch (Exception e) {
                    contentHtml = "<p>Error loading documentation: " + e.getMessage() + "</p>";
                }
                String activeClass = (index == 0) ? "active" : "";
                String displayStyle = (index == 0) ? "block" : "none";
                sidebar.append(String.format("<li><a href='#' class='%s' onclick=\"showRule(event, '%s')\">%s</a></li>",
                        activeClass, ruleName, ruleName));
                content.append(String.format("<div id='%s' class='rule-content' style='display: %s;'>%s</div>",
                        ruleName, displayStyle, contentHtml));
                index++;
            }
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <title>MuleGuard - Rule Guide</title>
                        <style>
                            :root {
                                --truist-purple: #663399;
                                --truist-purple-light: #7d4fb2;
                                --text-white: #FFFFFF;
                                --sidebar-width: 300px;
                            }
                            body {font-family: Arial, sans-serif; margin: 0; background-color: #f0f0f0; display: flex; height: 100vh; overflow: hidden;}
                            .sidebar {
                                width: var(--sidebar-width);
                                background: white;
                                border-right: 1px solid #ddd;
                                display: flex;
                                flex-direction: column;
                                height: 100%%;
                                overflow: auto;
                                min-width: 200px;
                                max-width: 600px;
                                flex-shrink: 0;
                            }
                            .sidebar-header {
                                padding: 20px;
                                background: var(--truist-purple);
                                color: white;
                            }
                            .sidebar-header h2 { margin: 0; font-size: 1.2rem; color: white; }
                            .sidebar-nav {
                                flex: 1;
                                overflow-y: auto;
                                list-style: none;
                                padding: 0;
                                margin: 0;
                            }
                            .sidebar-nav li a {
                                display: block;
                                padding: 15px 20px;
                                color: #333;
                                text-decoration: none;
                                border-bottom: 1px solid #eee;
                                transition: background 0.2s;
                            }
                            .sidebar-nav li a:hover { background: #f5f5f5; }
                            .sidebar-nav li a.active {
                                background: var(--truist-purple-light);
                                color: white;
                            }
                            .resizer {
                                width: 5px;
                                background-color: #ddd;
                                cursor: col-resize;
                                height: 100%%;
                                flex-shrink: 0;
                                transition: background-color 0.2s;
                            }
                            .resizer:hover, .resizer.resizing {
                                background-color: var(--truist-purple);
                            }
                            .main-content {
                                flex: 1;
                                padding: 40px;
                                overflow-y: auto;
                                background: #f9f9f9;
                                position: relative;  
                            }
                            .report-container {
                                background: white;
                                padding: 40px;
                                border-radius: 8px;
                                box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                                max-width: 1400px;
                                margin: 0 auto;
                                border: 5px solid var(--truist-purple);
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
                                background-color: var(--truist-purple);
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
                            .top-nav-button:hover { background-color: var(--truist-purple-light); }
                            h1 { color: var(--truist-purple); border-bottom: 2px solid #eee; padding-bottom: 10px; margin-top: 0; }  
                            h2 { color: #444; margin-top: 30px; }
                            h3 { color: #666; }
                            pre { background: #f4f4f4; padding: 15px; border-radius: 5px; overflow-x: auto; border: 1px solid #ddd; }
                            code { font-family: Consolas, monospace; color: #d63384; }
                            pre code { color: #333; }
                            table { border-collapse: collapse; width: 100%%; margin: 20px 0; }
                            th, td { border: 1px solid #ddd; padding: 10px; text-align: left; }
                            th { background: #f1f1f1; }
                            blockquote { border-left: 4px solid var(--truist-purple); margin: 0; padding-left: 20px; color: #555; }
                        </style>
                        <script>
                            function showRule(event, ruleId) {
                                event.preventDefault();
                                document.querySelectorAll('.rule-content').forEach(el => el.style.display = 'none');
                                document.getElementById(ruleId).style.display = 'block';
                                document.querySelectorAll('.sidebar-nav a').forEach(el => el.classList.remove('active'));
                                event.target.classList.add('active');
                            }
                            document.addEventListener('DOMContentLoaded', function() {
                                const sidebar = document.getElementById('sidebar');
                                const resizer = document.getElementById('resizer');
                                if (!sidebar || !resizer) return; 
                                let isResizing = false;
                                resizer.addEventListener('mousedown', function(e) {
                                    isResizing = true;
                                    resizer.classList.add('resizing');
                                    document.body.style.cursor = 'col-resize'; 
                                    document.body.style.userSelect = 'none'; 
                                });
                                document.addEventListener('mousemove', function(e) {
                                    if (!isResizing) return;
                                    const newWidth = e.clientX;
                                    if (newWidth > 200 && newWidth < 600) {
                                        sidebar.style.width = newWidth + 'px';
                                    }
                                });
                                document.addEventListener('mouseup', function(e) {
                                    if (isResizing) {
                                        isResizing = false;
                                        resizer.classList.remove('resizing');
                                        document.body.style.cursor = '';
                                        document.body.style.userSelect = '';
                                    }
                                });
                            });
                        </script>
                    </head>
                    <body>
                        <div class="sidebar" id="sidebar">
                            <div class="sidebar-header">
                                <h2>MuleGuard Rules</h2>
                            </div>
                            <ul class="sidebar-nav">
                                %s
                            </ul>
                        </div>
                        <div class="resizer" id="resizer"></div>
                        <div class="main-content">
                            <div class="top-nav-container">
                                <a href="CONSOLIDATED-REPORT.html" id="dashboardBtn" class="top-nav-button" title="Return to main dashboard">← Dashboard</a>
                                <a href="/muleguard/main" id="mainPageBtn" class="top-nav-button" title="Go to Main Page" style="display: none; background-color: #0078d4;">🏠 Main Page</a>
                            </div>
                            <div class="report-container">
                                %s
                            </div>
                        </div>
                            <script>
                                document.addEventListener('DOMContentLoaded', function() {
                                    var path = window.location.pathname;
                                    var isMuleStatic = path.includes('/apiguard/muleguard/web/') || path.includes('/muleguard/web/');
                                    var isMuleReport = path.includes('/apiguard/muleguard/reports/') || path.includes('/muleguard/reports/');
                                    var dashboardBtn = document.getElementById('dashboardBtn');
                                    var mainPageBtn = document.getElementById('mainPageBtn');
                                    
                                    // Detect if we're in Mule wrapper (has /apiguard prefix) or standalone Java
                                    var isInMuleWrapper = path.includes('/apiguard/');
                                    var basePath = isInMuleWrapper ? '/apiguard/muleguard' : '/muleguard';
                                    
                                    var sessionId = new URLSearchParams(window.location.search).get('session');
                                    if (!sessionId && isMuleReport) {
                                        var parts = path.split('/muleguard/reports/');
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
                    """
                    .formatted(sidebar.toString(), content.toString());
            Path ruleGuidePath = outputDir.resolve("rule_guide.html");
            Files.writeString(ruleGuidePath, html, java.nio.charset.StandardCharsets.UTF_8);
            System.out.println("   → rule_guide.html generated");
        } catch (Exception e) {
            System.err.println("Failed to generate Rule Guide: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private static String convertMdToHtml(String md) {
        String html = md;
        html = html.replaceAll("(?m)^# (.*)$", "<h1>$1</h1>");
        html = html.replaceAll("(?m)^## (.*)$", "<h2>$1</h2>");
        html = html.replaceAll("(?m)^### (.*)$", "<h3>$1</h3>");
        html = html.replaceAll("(?m)^#### (.*)$", "<h4>$1</h4>");
        html = html.replaceAll("(?s)```yaml(.*?)```", "<pre><code class='language-yaml'>$1</code></pre>");
        html = html.replaceAll("(?s)```xml(.*?)```", "<pre><code class='language-xml'>$1</code></pre>");
        html = html.replaceAll("(?s)```(.*?)```", "<pre><code>$1</code></pre>");
        html = html.replaceAll("`([^`]+)`", "<code>$1</code>");
        html = html.replaceAll("\\*\\*(.*?)\\*\\*", "<strong>$1</strong>");
        html = html.replaceAll("(?m)^- (.*)$", "<li>$1</li>");
        html = html.replaceAll("(?m)^\\|(.+)\\|$", "<tr><td>$1</td></tr>");
        html = html.replaceAll("\\|", "</td><td>");
        html = html.replaceAll("<tr><td></td><td>", "<tr><td>");
        html = html.replaceAll("</td><td></td></tr>", "</td></tr>");
        html = html.replaceAll("(?m)^<tr><td>\\s*-+\\s*</td>.*</tr>$", "");
        html = html.replaceAll("(?m)^$", "<br>");
        return html;
    }
    private static void generateConsolidatedExcel(List<ApiResult> results, Path outputDir) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("MuleGuard Summary");
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
            System.out.println("   → CONSOLIDATED-REPORT.xlsx generated");
        } catch (Exception e) {
            System.err.println("Failed to generate consolidated Excel report: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private static String escape(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
    private static void copyHelpFile(Path outputDir) {
        try {
            InputStream helpStream = ReportGenerator.class.getResourceAsStream("/help.html");
            if (helpStream == null) {
                System.err.println("Warning: help.html not found in resources");
            } else {
                Path helpPath = outputDir.resolve("help.html");
                Files.copy(helpStream, helpPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                helpStream.close();
                System.out.println("   → help.html copied");
            }
            InputStream logoStream = ReportGenerator.class.getResourceAsStream("/logo.svg");
            if (logoStream == null) {
                System.err.println("Warning: logo.svg not found in resources");
            } else {
                Path logoPath = outputDir.resolve("logo.svg");
                Files.copy(logoStream, logoPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                logoStream.close();
                System.out.println("   → logo.svg copied");
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to copy help files: " + e.getMessage());
        }
    }
}