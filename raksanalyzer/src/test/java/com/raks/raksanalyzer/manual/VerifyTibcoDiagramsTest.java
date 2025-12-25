package com.raks.raksanalyzer.manual;

import com.raks.raksanalyzer.generator.tibco.TibcoDiagramGenerator;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class VerifyTibcoDiagramsTest {

    @Test
    public void testTibcoIntegrationDiagramRecursion() {
        File projectRoot = new File("c:/raks/apiguard/raksanalyzer/testdata/customerOrder");
        TibcoDiagramGenerator generator = new TibcoDiagramGenerator();
        
        // Test 1: Initialization_onstartup.process (Has pd:starter "onStartup")
        // Path relative to root: Service/Initialization_onstartup.process
        String pumlStartup = generator.generateIntegrationPuml("onstartup", "Service/Initialization_onstartup.process", projectRoot, null);
        
        System.out.println("--- Generated PUML (Startup) ---");
        System.out.println(pumlStartup);
        
        // Assertions for Startup
        assertTrue(pumlStartup.contains("rectangle \"onStartup\""), "Should contain visual node for onStartup starter");
        assertTrue(pumlStartup.contains("<<OnStartupEventSource>>") || pumlStartup.contains("<<OnStartup>>") || pumlStartup.contains("<<Connector>>"), "Should contain stereotype for starter");
        assertTrue(pumlStartup.contains("usecase \"End\""), "Should contain End node");
        // Log activity should be filtered out now as it is not an integration point!
        // assertTrue(pumlStartup.contains("rectangle \"Log\""), "Should contain Log activity"); 
        assertTrue(pumlStartup.contains("onStartup -->"), "Should have transition from starter");

        // Test 2: orderServiceJMS.process (Recursive Call)
        String pumlJms = generator.generateIntegrationPuml("jmsService", "Service/orderServiceJMS.process", projectRoot, null);
        System.out.println("--- Generated PUML (JMS) ---");
        System.out.println(pumlJms);
        
        assertTrue(pumlJms.contains("package \"createOrder\""), "Should contain package for recursive call");
        assertTrue(pumlJms.contains("JDBC Query"), "Should contain internal connector of sub-process");
        assertTrue(pumlJms.contains("<<JMS>>"), "Should contain JMS stereotype");
    }
}
