package com.raks.raksanalyzer.manual;
import com.raks.raksanalyzer.generator.tibco.TibcoDiagramGenerator;
import org.junit.jupiter.api.Test;
import java.io.File;
import static org.junit.jupiter.api.Assertions.assertTrue;
public class VerifyTibcoDiagramsTest {
    @Test
    public void testTibcoIntegrationDiagramRecursion() {
        File projectRoot = new File("c:/raks/apiguard/raksanalyzer/testdata/TibcoApp1");
        TibcoDiagramGenerator generator = new TibcoDiagramGenerator();
        String pumlStartup = generator.generateIntegrationPuml("onstartup", "Service/Initialization_onstartup.process", projectRoot, null);
        System.out.println("--- Generated PUML (Startup) ---");
        System.out.println(pumlStartup);
        // Updated to match activity diagram syntax
        assertTrue(pumlStartup.contains("@startuml"), "Should be valid PlantUML");
        assertTrue(pumlStartup.contains("start"), "Should contain start node");
        assertTrue(pumlStartup.contains("OnStartup") || pumlStartup.contains("onstartup"), "Should reference the starter");
        
        String pumlJms = generator.generateIntegrationPuml("jmsService", "Service/orderServiceJMS.process", projectRoot, null);
        System.out.println("--- Generated PUML (JMS) ---");
        System.out.println(pumlJms);
        // Check for partition (subprocess call) and connectors
        assertTrue(pumlJms.contains("partition") || pumlJms.contains("CallProcess") || pumlJms.contains("createOrder"), "Should contain reference to subprocess");
        assertTrue(pumlJms.contains("JDBC") || pumlJms.contains("Query"), "Should contain database connector");
        assertTrue(pumlJms.contains("JMS") || pumlJms.contains("jms"), "Should contain JMS reference");
    }
}
