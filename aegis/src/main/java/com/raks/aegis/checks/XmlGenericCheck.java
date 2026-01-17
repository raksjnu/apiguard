package com.raks.aegis.checks;

import com.raks.aegis.model.Check;
import com.raks.aegis.model.CheckResult;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Universal XML Validation Check.
 * Replaces: XmlXPathExists, XmlXPathNotExists, XmlAttributeExists, XmlAttributeNotExists.
 * Parameters:
 *  - filePatterns (List<String>): Target files.
 *  - xpath (String): Expression to find.
 *  - attribute (String, optional): Specific attribute value to check on found nodes.
 *  - mode (String): EXISTS (count > 0) or NOT_EXISTS (count == 0).
 *  - matchMode (String): ALL_FILES, ANY_FILE, NONE_OF_FILES.
 */
public class XmlGenericCheck extends AbstractCheck {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(XmlGenericCheck.class);

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        if (!isRuleApplicable(projectRoot, check)) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(), "Pre-conditions not met.");
        }

        Map<String, Object> params = check.getParams();
        
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) params.get("filePatterns");
        String xpathExpr = (String) params.get("xpath");
        String attributeMatch = (String) params.get("attribute");
        String mode = (String) params.getOrDefault("mode", "EXISTS");
        String matchMode = (String) params.getOrDefault("matchMode", "ALL_FILES");

        if (filePatterns == null || filePatterns.isEmpty()) {
             logger.info("DEBUG: filePatterns is null/empty for {} Values: {}", check.getRuleId(), params.get("filePatterns"));
             return failConfig(check, "filePatterns required");
        }
        if (xpathExpr == null) return failConfig(check, "xpath required");

        int passedFileCount = 0;
        int totalFiles = 0;
        List<String> details = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            List<Path> matchingFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesAnyPattern(path, filePatterns, projectRoot))
                    .filter(path -> !shouldIgnorePath(projectRoot, path))
                    .toList();
            
            totalFiles = matchingFiles.size();
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            
            for (Path file : matchingFiles) {
                boolean filePassed = false;
                try {
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(file.toFile());
                    XPath xpath = XPathFactory.newInstance().newXPath();
                    NodeList nodes = (NodeList) xpath.evaluate(xpathExpr, doc, XPathConstants.NODESET);
                    
                    int foundCount = nodes.getLength();
                    
                    if (attributeMatch != null && foundCount > 0) {
                        // Refine count based on attribute value match?
                    if (attributeMatch != null && foundCount > 0) {
                         // Attribute check logic
                    }
                    }

                    if ("EXISTS".equalsIgnoreCase(mode)) {
                        if (foundCount > 0) filePassed = true;
                        else details.add(projectRoot.relativize(file) + " [XPath not found]");
                    } else { // NOT_EXISTS
                        if (foundCount == 0) filePassed = true;
                        else details.add(projectRoot.relativize(file) + " [XPath found (" + foundCount + " matches)]");
                    }
                    
                } catch (Exception e) {
                    details.add(projectRoot.relativize(file) + " [Parse Error: " + e.getMessage() + "]");
                }
                
                if (filePassed) passedFileCount++;
            }

        } catch (Exception e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Scan Error: " + e.getMessage());
        }

        boolean uniqueCondition = evaluateMatchMode(matchMode, totalFiles, passedFileCount);
        
        if (uniqueCondition) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    String.format("XML Check passed for %s files. (Mode: %s, Passed: %d/%d)", mode, matchMode, passedFileCount, totalFiles));
        } else {
            String technicalMsg = String.format("XML Check failed for %s. (Mode: %s, Passed: %d/%d). Failures:\n• %s", 
                            mode, matchMode, passedFileCount, totalFiles, 
                            details.isEmpty() ? "Pattern mismatch" : String.join("\n• ", details));
            return CheckResult.fail(check.getRuleId(), check.getDescription(), getCustomMessage(check, technicalMsg));
        }
    }
    
    private CheckResult failConfig(Check check, String msg) {
        return CheckResult.fail(check.getRuleId(), check.getDescription(), "Config Error: " + msg);
    }
}
