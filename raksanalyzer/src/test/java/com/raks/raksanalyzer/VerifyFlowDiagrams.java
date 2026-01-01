package com.raks.raksanalyzer;

import com.raks.raksanalyzer.generator.tibco.TibcoDiagramGenerator;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VerifyFlowDiagrams {
    @Test
    public void verifyLogAndErrorHandler() throws Exception {
        TibcoDiagramGenerator generator = new TibcoDiagramGenerator();
        File processFile = new File("testdata/TibcoApp1/BusinessProcess/LogandErrorHandler.process");
        
        System.out.println("Checking file: " + processFile.getAbsolutePath());
        assertTrue(processFile.exists(), "Test file must exist");
        
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        Document doc = dbFactory.newDocumentBuilder().parse(processFile);
        
        String puml = generator.generateFlowPuml(doc, "LogandErrorHandler");
        
        // File debugOut = new File("target/debug_loganderror.puml");
        // java.nio.file.Files.writeString(debugOut.toPath(), puml);
        // System.out.println("Written PUML to " + debugOut.getAbsolutePath());
        
        // Assertions for expected shape syntax
        // :<icon> **Name**\nType;
        assertTrue(puml.contains(":"), "Should contain activity start colon");
        assertTrue(puml.contains("LogDEBUG"), "Should contain LogDEBUG");
    }
}
