package com.raks.gitanalyzer.core;

import freemarker.template.Configuration;
import freemarker.template.Template;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

public class ReportGenerator {
    
    public void generateReport(Map<String, Object> dataModel, String outputDirParam) {
        try {
            Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
            cfg.setClassForTemplateLoading(this.getClass(), "/");
            cfg.setDefaultEncoding("UTF-8");

            Template template = cfg.getTemplate("report_template.html");
            
            // Resolve directory using ConfigManager logic (ensures app.home/temp if null)
            File outputDir = ConfigManager.resolveOutputDir(outputDirParam, "app.output.dir");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            String filename = "MigrationReport_" + ConfigManager.getCurrentTimestamp() + ".html";
            // Sanitize filename for windows (remove spaces/colons in timestamp if any, though standard format is safe-ish, 'z' might have space/colon)
            filename = filename.replace(" ", "_").replace(":", "-");
            
            File reportFile = new File(outputDir, filename);
            Writer fileWriter = new FileWriter(reportFile);
            
            template.process(dataModel, fileWriter);
            
            System.out.println("Report generated successfully: " + reportFile.getAbsolutePath());
            
            // Open in browser?
            // java.awt.Desktop.getDesktop().browse(reportFile.toURI());
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
