package com.raks.raksanalyzer;

import com.raks.raksanalyzer.generator.tibco.TibcoDiagramGenerator;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;

public class VerifyOrderService {
    @Test
    public void verifyOrderServiceFile() throws Exception {
        TibcoDiagramGenerator generator = new TibcoDiagramGenerator();
        File processFile = new File("testdata/TibcoApp1/Service/orderServiceFILE.process");
        
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        Document doc = dbFactory.newDocumentBuilder().parse(processFile);
        
        String puml = generator.generateFlowPuml(doc, "orderServiceFILE");
        
        // File debugOut = new File("target/debug_orderservice.puml");
        // java.nio.file.Files.writeString(debugOut.toPath(), puml);
        // System.out.println("Written PUML to " + debugOut.getAbsolutePath());
        
        // Inspect "1=2" surroundings
        int index = puml.indexOf("1=2");
        StringBuilder sb = new StringBuilder();
        if (index != -1) {
            sb.append("Context around 1=2:\n");
            String context = puml.substring(Math.max(0, index - 10), Math.min(puml.length(), index + 20));
            sb.append("'").append(context).append("'\n");
            for (char c : context.toCharArray()) {
                sb.append("Char: ").append(c).append(" (").append((int)c).append(")\n");
            }
        } else {
            sb.append("Label '1=2' not found in PUML!\n");
        }
        // File debugChars = new File("target/debug_chars.txt");
        // java.nio.file.Files.writeString(debugChars.toPath(), sb.toString());
        // System.out.println("Written chars to " + debugChars.getAbsolutePath());
    }
}
