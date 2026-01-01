package com.raks.raksanalyzer;

import com.raks.raksanalyzer.generator.tibco.TibcoDiagramGenerator;
import com.raks.raksanalyzer.core.config.ConfigurationManager;
import com.raks.raksanalyzer.core.utils.XmlUtils;
import org.w3c.dom.Document;
import java.io.File;
import java.nio.file.Paths;

public class ReproductionTest {
    public static void main(String[] args) {
        try {
            System.out.println("Running ReproductionTest...");
            File f = new File("testdata/TibcoApp1/BusinessProcess/connector.process");
            if (!f.exists()) { 
                f = new File("c:\\raks\\apiguard\\raksanalyzer\\testdata\\TibcoApp1\\BusinessProcess\\connector.process");
            }
            if (!f.exists()) {
                System.err.println("Test file not found!");
                System.exit(1);
            }

            Document doc = XmlUtils.parseXmlFile(f.toPath());
            TibcoDiagramGenerator gen = new TibcoDiagramGenerator();
            String puml = gen.generateFlowPuml(doc, "Test");

            System.out.println("--- Generated PUML Content (Snippet) ---");
            if (puml.length() > 500) System.out.println(puml.substring(0, 500) + "...");
            else System.out.println(puml);
            System.out.println("----------------------------------------");

            boolean hasJavaCode = puml.contains("Java Code");
            boolean hasLog = puml.contains("Log");
            boolean hasWriteFile = puml.contains("Write File");

            System.out.println("Contains 'Java Code': " + hasJavaCode);
            System.out.println("Contains 'Log': " + hasLog);
            System.out.println("Contains 'Write File': " + hasWriteFile);

            if (hasJavaCode && hasLog && hasWriteFile) {
                System.out.println("SUCCESS: All nodes found.");
            } else {
                System.out.println("FAILURE: Missing nodes in group.");
                System.exit(1);
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
