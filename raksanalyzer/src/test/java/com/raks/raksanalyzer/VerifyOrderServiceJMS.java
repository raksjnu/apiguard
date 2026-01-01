package com.raks.raksanalyzer;

import com.raks.raksanalyzer.generator.tibco.TibcoDiagramGenerator;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;

public class VerifyOrderServiceJMS {
    @Test
    public void verifyOrderServiceJMS() throws Exception {
        TibcoDiagramGenerator generator = new TibcoDiagramGenerator();
        File processFile = new File("testdata/TibcoApp1/Service/orderServiceJMS.process");
        
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        Document doc = dbFactory.newDocumentBuilder().parse(processFile);
        
        String puml = generator.generateFlowPuml(doc, "orderServiceJMS");
        
        // File debugOut = new File("target/debug_jms.puml");
        // java.nio.file.Files.writeString(debugOut.toPath(), puml);
        // System.out.println("Written PUML to " + debugOut.getAbsolutePath());
    }
}
