package com.raks.apiurlcomparison;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class HtmlReportGenerator {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static File generateJsonReport(List<ComparisonResult> results, String outputPath) throws IOException {
        if (results == null) {
            throw new IllegalArgumentException("Results list cannot be null for JSON report generation.");
        }

        File outputFile = new File(outputPath);
        // If the provided path is a directory, append a default filename.
        if (outputFile.isDirectory()) {
            outputFile = new File(outputFile, "results.json");
        }

        // Ensure the parent directory exists.
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        mapper.enable(SerializationFeature.INDENT_OUTPUT); // Pretty print JSON
        mapper.writeValue(outputFile, results);
        return outputFile;
    }

    public static File generateHtmlReport(List<ComparisonResult> results, String outputPath) throws IOException {
        File baseOutputFile = new File(outputPath);
        // If the provided path is a directory, append a default filename to determine
        // the base name.
        if (baseOutputFile.isDirectory()) {
            baseOutputFile = new File(baseOutputFile, "results.json");
        }

        // Construct the HTML file path based on the resolved base file path.
        String htmlFileName = baseOutputFile.getName().replaceFirst("[.][^.]+$", "") + ".html";
        File htmlFile = new File(baseOutputFile.getParent(), htmlFileName);

        try (PrintWriter writer = new PrintWriter(new FileWriter(htmlFile))) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html lang=\"en\">");
            writer.println("<head>");
            writer.println("<meta charset=\"UTF-8\">");
            writer.println("<title>API Response Comparison Tool - APITestingGuard</title>");
            writer.println(getStyles());
            writer.println("</head>");
            writer.println("<body>");
            writer.println("<div class=\"container\">");
            writer.println("<h1>API Response Comparison Tool - APITestingGuard</h1>");

            // --- Summaries ---
            String generationTimestamp = ZonedDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
            generateSummary(writer, results, generationTimestamp);

            // --- Details Table ---
            writer.println("<h2>Iteration Details</h2>");
            writer.println("<table class=\"details-table\">");
            writer.println(
                    "<thead><tr><th>#</th><th>Operation</th><th>Status</th><th>Tokens</th><th>Details</th></tr></thead>");
            writer.println("<tbody>");

            int iteration = 1;
            for (ComparisonResult result : results) {
                writer.println("<tr class=\"summary-row " + result.getStatus().toLowerCase() + "\">");
                writer.println("<td>" + iteration + "</td>");
                writer.println("<td>" + escapeHtml(result.getOperationName()) + "</td>");
                writer.println("<td><span class=\"status\">" + result.getStatus() + "</span></td>");
                writer.println("<td>" + escapeHtml(result.getIterationTokens().toString()) + "</td>");
                writer.println("<td><button onclick=\"toggleDetails('details-" + iteration
                        + "')\">Toggle Details</button></td>");
                writer.println("</tr>");

                // Collapsible details row
                writer.println("<tr id=\"details-" + iteration + "\" class=\"details-row\" style=\"display:none;\">");
                writer.println("<td colspan=\"5\">");
                writer.println("<div class=\"details-content\">");

                // Show baseline metadata if present
                if (result.getBaselineServiceName() != null) {
                    writer.println("<div class=\"baseline-info\">");
                    writer.println("<h4>📊 Baseline Information</h4>");
                    writer.println(
                            "<p><strong>Service:</strong> " + escapeHtml(result.getBaselineServiceName()) + "</p>");
                    writer.println("<p><strong>Date:</strong> " + escapeHtml(result.getBaselineDate()) + "</p>");
                    writer.println("<p><strong>Run ID:</strong> " + escapeHtml(result.getBaselineRunId()) + "</p>");
                    if (result.getBaselineDescription() != null) {
                        writer.println("<p><strong>Description:</strong> " + escapeHtml(result.getBaselineDescription())
                                + "</p>");
                    }
                    if (result.getBaselineTags() != null && !result.getBaselineTags().isEmpty()) {
                        writer.println("<p><strong>Tags:</strong> "
                                + escapeHtml(String.join(", ", result.getBaselineTags())) + "</p>");
                    }
                    writer.println("<p><strong>Captured:</strong> " + escapeHtml(result.getBaselineCaptureTimestamp())
                            + "</p>");
                    writer.println("<p class=\"comparison-note\"><em>API1 = Current Live API | API2 = Baseline from "
                            + escapeHtml(result.getBaselineDate()) + "/" + escapeHtml(result.getBaselineRunId())
                            + "</em></p>");
                    writer.println("</div>");
                    writer.println("<hr>");
                }

                if ("ERROR".equals(result.getStatus())) {
                    writer.println("<p class=\"error-message\"><strong>Error:</strong> "
                            + escapeHtml(result.getErrorMessage()) + "</p>");
                }
                if (result.getDifferences() != null && !result.getDifferences().isEmpty()) {
                    writer.println("<div><strong>Differences:</strong><ul>");
                    result.getDifferences().forEach(d -> writer.println("<li>" + escapeHtml(d) + "</li>"));
                    writer.println("</ul></div>");
                }

                writer.println("<div class=\"api-details\">");

                // Determine if this is a baseline comparison
                boolean isBaselineComparison = result.getBaselineServiceName() != null;

                // Format API labels based on mode
                String api1Label = isBaselineComparison ? "API Current" : "API 1";
                String api2Label = isBaselineComparison ? "API Baseline" : "API 2";

                writer.println(formatApiCallResult(api1Label, result.getApi1()));
                writer.println(formatApiCallResult(api2Label, result.getApi2()));
                writer.println("</div>"); // end api-details

                writer.println("</div></td></tr>");
                iteration++;
            }

            writer.println("</tbody></table>");
            writer.println("</div>"); // Close container
            writer.println(getScript());
            writer.println("</body>");
            writer.println("</html>");
        }
        return htmlFile;
    }

    private static String formatApiCallResult(String apiName, ApiCallResult callResult) {
        if (callResult == null) {
            return "<div class=\"api-column\"><h3>" + apiName + "</h3><p>No call made.</p></div>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"api-column\">");
        sb.append("<h3>").append(apiName).append("</h3>");
        sb.append("<p><strong>URL:</strong> ").append(escapeHtml(callResult.getUrl())).append("</p>");
        sb.append("<p><strong>Method:</strong> ").append(escapeHtml(callResult.getMethod())).append("</p>");
        sb.append("<p><strong>Status Code:</strong> ").append(callResult.getStatusCode()).append("</p>");
        sb.append("<p><strong>Duration:</strong> ").append(callResult.getDuration()).append("ms</p>");
        sb.append("<h4>Request Payload</h4>");
        sb.append("<pre><code>").append(prettyPrintAndEscape(callResult.getRequestPayload())).append("</code></pre>");
        sb.append("<h4>Response Payload</h4>");
        sb.append("<pre><code>").append(prettyPrintAndEscape(callResult.getResponsePayload())).append("</code></pre>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String prettyPrintAndEscape(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "[No Content]";
        }
        // Try to pretty-print if it's JSON
        try {
            Object json = mapper.readValue(content, Object.class);
            String prettyJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            return escapeHtml(prettyJson);
        } catch (JsonProcessingException e) {
            // Not JSON, just escape it
            return escapeHtml(content);
        }
    }

    private static String escapeHtml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void generateSummary(PrintWriter writer, List<ComparisonResult> results,
            String generationTimestamp) {
        long matches = results.stream().filter(r -> "MATCH".equals(r.getStatus())).count();
        long mismatches = results.stream().filter(r -> "MISMATCH".equals(r.getStatus())).count();
        long errors = results.stream().filter(r -> "ERROR".equals(r.getStatus())).count();
        long totalDuration = results.stream()
                .mapToLong(r -> (r.getApi1() != null ? r.getApi1().getDuration() : 0)
                        + (r.getApi2() != null ? r.getApi2().getDuration() : 0))
                .sum();

        // Check if this is a baseline operation
        String baselinePath = null;
        String baselineOperation = null;
        if (!results.isEmpty() && results.get(0).getBaselinePath() != null) {
            baselinePath = results.get(0).getBaselinePath();
            // Determine if this is capture or compare based on whether api2 exists
            baselineOperation = results.get(0).getApi2() != null ? "Baseline Used" : "Baseline Captured";
        }

        writer.println("<div class=\"summary-container\">");

        // Build execution summary content
        StringBuilder execSummary = new StringBuilder();
        execSummary.append("<div class=\"summary-box\"><h2>Execution Summary</h2>")
                .append("<p><strong>Total Iterations:</strong> ").append(results.size()).append("</p>")
                .append("<p><strong>Total API Call Duration:</strong> ").append(totalDuration).append(" ms</p>");

        // Add baseline path if present
        if (baselinePath != null) {
            execSummary.append("<p><strong>").append(baselineOperation).append(":</strong> ")
                    .append(escapeHtml(baselinePath)).append("</p>");
        }

        execSummary.append("<p><strong>Report Generated At:</strong> ").append(generationTimestamp)
                .append("</p></div>");
        writer.println(execSummary.toString());

        writer.println("<div class=\"summary-box\"><h2>Comparison Summary</h2>"
                + "<p><strong>Matches:</strong> <span class=\"status-count match\">" + matches + "</span></p>"
                + "<p><strong>Mismatches:</strong> <span class=\"status-count mismatch\">" + mismatches + "</span></p>"
                + "<p><strong>Errors:</strong> <span class=\"status-count error\">" + errors + "</span></p></div>");
        writer.println("</div>");
    }

    private static String getScript() {
        return "<script>"
                + "function toggleDetails(id) {"
                + "  var x = document.getElementById(id);"
                + "  if (x.style.display === 'none') {"
                + "    x.style.display = 'table-row';"
                + "  } else {"
                + "    x.style.display = 'none';"
                + "  }"
                + "}"
                + "</script>";
    }

    private static String getStyles() {
        return "<style>"
                + "body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #E3F2FD; color: #333333; }"
                + ".container { max-width: 95%; margin: 0 auto; background-color: #F5F0FA; border: 8px solid #2196F3; border-radius: 12px; padding: 20px; box-shadow: 0 4px 12px rgba(33, 150, 243, 0.3); }"
                + "h1 { background-color: #5E278B; color: #FFFFFF; padding: 15px; border-radius: 8px; }"
                + "h2 { color: #5E278B; border-bottom: 2px solid #D1C4E9; padding-bottom: 5px; }"
                + ".summary-container { display: flex; gap: 20px; margin-bottom: 20px; border-radius: 8px; }"
                + ".summary-box { flex: 1; background-color: #fff; padding: 15px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); border: 1px solid #D1C4E9; }"
                + ".status-count { font-weight: bold; padding: 3px 8px; border-radius: 5px; color: #155724; }"
                + ".status-count.match { background-color: #D4EDDA; }"
                + ".status-count.mismatch { background-color: #F8D7DA; color: #721C24;}"
                + ".status-count.error { background-color: #F8D7DA; color: #721C24; }"
                + ".details-table { width: 100%; border-collapse: collapse; background-color: #fff; box-shadow: 0 2px 4px rgba(0,0,0,0.1); border-radius: 8px; overflow: hidden; margin-top: 20px; }"
                + ".details-table th, .details-table td { border: 1px solid #D1C4E9; padding: 8px; text-align: left; }"
                + ".details-table th { background-color: #EDE7F6; color: #5E278B; }"
                + ".summary-row.match { background-color: #D4EDDA; }"
                + ".summary-row.mismatch { background-color: #F8D7DA; }"
                + ".summary-row.error { background-color: #F8D7DA; }"
                + ".status { font-weight: bold; padding: 3px 8px; border-radius: 5px; color: #fff; display: inline-block; }"
                + ".summary-row.match .status { background-color: #155724; }"
                + ".summary-row.mismatch .status { background-color: #721C24; }"
                + ".summary-row.error .status { background-color: #721C24; }"
                + ".details-row { background-color: #F5F0FA; }"
                + ".details-content { padding: 15px; border: 1px solid #D1C4E9; border-radius: 8px; margin-top: 10px; background-color: #fff; }"
                + ".error-message { color: #721C24; font-weight: bold; }"
                + ".api-details { display: flex; justify-content: space-between; gap: 20px; margin-top: 15px; }"
                + ".api-column { flex: 1; background-color: #fff; padding: 15px; border: 1px solid #D1C4E9; border-radius: 8px; }"
                + "pre { background-color: #EDE7F6; padding: 10px; border-radius: 5px; white-space: pre-wrap; word-wrap: break-word; }"
                + "code { font-family: 'Courier New', Courier, monospace; }"
                + "ul { padding-left: 20px; }"
                + "button { background-color: #5E278B; color: white; border: none; padding: 5px 10px; border-radius: 5px; cursor: pointer; }"
                + "button:hover { background-color: #4A1F6E; }"
                + "</style>";
    }
}