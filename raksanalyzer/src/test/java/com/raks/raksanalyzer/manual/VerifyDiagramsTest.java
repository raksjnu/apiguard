package com.raks.raksanalyzer.manual;

import com.raks.raksanalyzer.analyzer.mule.MuleAnalyzer;
import com.raks.raksanalyzer.domain.model.AnalysisResult;
import com.raks.raksanalyzer.domain.model.FlowInfo;
import com.raks.raksanalyzer.domain.model.ProjectNode;
import com.raks.raksanalyzer.generator.DiagramGenerator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class VerifyDiagramsTest {

    @Test
    public void testIntegrationDiagramGeneration() throws Exception {
        Path projectPath = Paths.get("c:/raks/apiguard/raksanalyzer/testdata/testmule1");
        MuleAnalyzer analyzer = new MuleAnalyzer(projectPath, new ArrayList<>());
        AnalysisResult result = analyzer.analyze();

        assertTrue(result.isSuccess(), "Analysis should succeed");

        // Collect flows
        List<FlowInfo> flows = new ArrayList<>();
        collectFlows(result.getProjectStructure(), flows);
        
        Map<String, FlowInfo> allFlows = new HashMap<>();
        for (FlowInfo f : flows) {
            allFlows.put(f.getName(), f);
        }
        
        System.out.println("Found " + flows.size() + " flows.");

        // Use reflection to call private generatePlantUmlSource
        Method method = DiagramGenerator.class.getDeclaredMethod("generatePlantUmlSource", FlowInfo.class, int.class, boolean.class, boolean.class, Map.class);
        method.setAccessible(true);
        
        // Test Scheduler Flow
        FlowInfo schedulerFlow = allFlows.get("testmule1Flow2");
        assertNotNull(schedulerFlow, "testmule1Flow2 not found");
        String source = (String) method.invoke(null, schedulerFlow, 0, true, true, allFlows);
        System.out.println("--- Source for testmule1Flow2 ---");
        System.out.println(source);
        
        assertTrue(source.contains("scheduler"), "Should contain scheduler");
        
        // Test Recursion
        // testmule1Flow calls testmule1Flow1 which calls sockets:send
        FlowInfo mainFlow = allFlows.get("testmule1Flow");
        assertNotNull(mainFlow, "testmule1Flow not found");
        
        String mainSource = (String) method.invoke(null, mainFlow, 0, true, true, allFlows);
        System.out.println("--- Source for testmule1Flow ---");
        System.out.println(mainSource);
        
        // Check for content from the referenced flow (testmule1Flow1)
        // testmule1Flow1 has sockets:send with config-ref
        assertTrue(mainSource.contains("sockets:send"), "Should contain sockets:send from referenced flow");

    }

    @Test
    public void verifyFullFlowDiagram() throws Exception {
        Path projectPath = Paths.get("c:/raks/apiguard/raksanalyzer/testdata/testmule1");
        MuleAnalyzer analyzer = new MuleAnalyzer(projectPath, new ArrayList<>());
        AnalysisResult result = analyzer.analyze();
        
        // Collect flows
        List<FlowInfo> flows = new ArrayList<>();
        collectFlows(result.getProjectStructure(), flows);
        
        Map<String, FlowInfo> allFlows = new HashMap<>();
        for (FlowInfo f : flows) {
            allFlows.put(f.getName(), f);
        }

        // Find testmule1Flow1 which has the Async scope
        FlowInfo flow = allFlows.get("testmule1Flow1");
        assertNotNull(flow, "testmule1Flow1 not found");

        Method method = DiagramGenerator.class.getDeclaredMethod("generatePlantUmlSource", FlowInfo.class, int.class, boolean.class, boolean.class, Map.class);
        method.setAccessible(true);
        
        // Use maxDepth = 5 to ensure traverse
        // useFullNames = true (Default for Section 2.2 in WordGenerator)
        // configRefOnly = false (FULL DIAGRAM - Section 2.2)
        String source = (String) method.invoke(null, flow, 5, true, false, new HashMap<>());
        
        System.out.println("--- Source for testmule1Flow1 (Full With Recursion) ---");
        System.out.println(source);

        // Check for Async partition (ignoring icon path)
        assertTrue(source.contains("partition \"") && source.contains("Async\""), "Should contain Async partition in full diagram");
        // Check that content inside Async is visible
        assertTrue(source.contains("sockets:send"), "Should contain sockets:send inside Async");
        
        // Also verify recursion, although testmule1Flow1 might not have a flow-ref.
        // Let's check testmule1Flow which calls testmule1Flow1
        FlowInfo mainFlow = allFlows.get("testmule1Flow");
        String mainSource = (String) method.invoke(null, mainFlow, 5, true, false, allFlows); // Pass allFlows!
        System.out.println("--- Source for testmule1Flow (Full With Recursion) ---");
        System.out.println(mainSource);
        
        // Check for Ref partition (ignoring icon path)
        assertTrue(mainSource.contains("partition \"") && mainSource.contains("Ref: testmule1Flow1\""), "Should contain partition for referenced flow");
        assertTrue(mainSource.contains("http:listener"), "Should contain content from referenced flow");
        
        // Verify Property Resolution
        // testmule1Flow1 has http:listener with path="${path3}" where path3=/anotherpath345
        assertTrue(mainSource.contains("/anotherpath345"), "Should contain resolved property /anotherpath345");
        assertFalse(mainSource.contains("${path3}"), "Should NOT contain unresolved property ${path3}");
    }

    private void collectFlows(ProjectNode node, List<FlowInfo> flows) {
        if (node.getType() == ProjectNode.NodeType.FILE && node.getName().endsWith(".xml")) {
            Map<String, Object> metadata = node.getMetadata();
            if (metadata != null && metadata.containsKey("muleFlows")) {
                List<FlowInfo> explicitFlows = (List<FlowInfo>) metadata.get("muleFlows");
                flows.addAll(explicitFlows);
            }
        } else if (node.getType() == ProjectNode.NodeType.DIRECTORY) {
            for (ProjectNode child : node.getChildren()) {
                collectFlows(child, flows);
            }
        }
    }
}
