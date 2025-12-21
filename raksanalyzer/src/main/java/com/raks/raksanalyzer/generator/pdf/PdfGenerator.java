package com.raks.raksanalyzer.generator.pdf;

import com.raks.raksanalyzer.core.config.ConfigurationManager;
import com.raks.raksanalyzer.domain.model.AnalysisResult;
import com.raks.raksanalyzer.domain.model.ComponentInfo;
import com.raks.raksanalyzer.domain.model.ConnectorConfig;
import com.raks.raksanalyzer.domain.model.FlowInfo;
import com.raks.raksanalyzer.domain.model.PomInfo;
import com.raks.raksanalyzer.domain.model.ProjectInfo;
import com.raks.raksanalyzer.domain.model.ProjectNode;
import com.raks.raksanalyzer.domain.model.ProjectNode.NodeType;
import com.raks.raksanalyzer.domain.model.PropertyInfo;
// import com.raks.raksanalyzer.domain.FlowWithSource; // Removed incorrect import
import com.raks.raksanalyzer.generator.DiagramGenerator;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.imageio.ImageIO;

/**
 * Generates PDF documentation from analysis results with full content
 */
public class PdfGenerator {
    private static final Logger logger = LoggerFactory.getLogger(PdfGenerator.class);
    
    private final ConfigurationManager config;
    private PDDocument document;
    private PDFont fontRegular;
    private PDFont fontBold;
    private PDFont fontMono;
    private PDFont fontItalic;
    private float currentY;
    private PDPage currentPage;
    private PDPageContentStream contentStream;
    private PDDocumentOutline documentOutline;
    private PDOutlineItem rootOutline;
    
    // TOC Support
    private List<TOCEntry> tocEntries = new ArrayList<>();
    
    // Safety Net: Track all created streams to ensure closure
    private List<PDPageContentStream> trackedStreams = new ArrayList<>();
    
    private static class TOCEntry {
        String title;
        PDPage page;
        int level;
        
        TOCEntry(String title, PDPage page, int level) {
            this.title = title;
            this.page = page;
            this.level = level;
        }
    }
    
    private static final float MARGIN = 50;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float CONTENT_WIDTH = PAGE_WIDTH - (2 * MARGIN);
    private static final float LINE_HEIGHT = 15;
    
    public PdfGenerator(ConfigurationManager config) {
        this.config = config;
    }
    
    public Path generate(AnalysisResult result) throws IOException {
        logger.info("Starting PDF generation for project: {}", result.getProjectInfo().getProjectName());
        
        try {
            document = new PDDocument();
            initializeFonts();
            initializeOutline();
            
            // Generate document sections
            // Generate document sections
            createCoverPage(result.getProjectInfo());
            
            // 1. Project Information & 1.2 POM Details
            org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem projectInfoMark = 
                createProjectInformation(result.getProjectInfo());
            createPomDetails(result, projectInfoMark);
            
            // 2. Flow Diagrams
            createFlowDiagramSection(result);
            
            // 3. Global Configuration
            createGlobalConfiguration(result);
            
            // 4. Connector Configs
            createConnectorConfigurations(result);
            
            // 5. Mule Flows
            createMuleFlows(result);
            
            // 6. Sub Flows
            createMuleSubFlows(result);
            
            // 7. DataWeave
            createDataWeaveFiles(result);
            
            // 8. Other Resources
            createOtherResources(result);
            createReferences(result);
            
            closeContentStream();
            
            // Generate TOC at the very end to have correct page numbers and shift pages
            generateTableOfContents();
            closeContentStream(); // Ensure TOC didn't leave anything open
            
            // Add footers (text + page numbers)
            addFooters();
            closeContentStream(); // Ensure Footers didn't leave anything open
            
            // Double-check: forcefully close any lingering streams
            if (contentStream != null) {
                try {
                    contentStream.close();
                } catch (Exception e) {
                    // Ignore - stream might already be closed
                }
                contentStream = null;
            }
            
            // FINAL SAFETY NET: Close all tracked streams
            for (PDPageContentStream stream : trackedStreams) {
                try {
                    stream.close();
                } catch (Exception ignored) {
                    // Ignore already closed
                }
            }
            trackedStreams.clear();
            
            // Save document
            String fileName = generateFileName(result.getProjectInfo());
            Path outputPath = Path.of(config.getProperty("framework.output.directory", "./output"), fileName);
            document.save(outputPath.toFile());
            document.close();
            
            logger.info("PDF generated successfully: {}", outputPath);
            return outputPath;
            
        } catch (Exception e) {
            logger.error("Error generating PDF", e);
            if (document != null) {
                document.close();
            }
            throw new IOException("Failed to generate PDF", e);
        }
    }
    
    private void initializeFonts() {
        fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        fontItalic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
        fontMono = new PDType1Font(Standard14Fonts.FontName.COURIER);
    }
    
    private void initializeOutline() {
        documentOutline = new PDDocumentOutline();
        document.getDocumentCatalog().setDocumentOutline(documentOutline);
        rootOutline = new PDOutlineItem();
        rootOutline.setTitle("RaksAnalyzer Report");
        documentOutline.addLast(rootOutline);
    }
    
    private void createCoverPage(ProjectInfo projectInfo) throws IOException {
        newPage();
        
        currentY = PAGE_HEIGHT - 200;
        drawCenteredText(config.getProperty("mule.common.cover.title", "Project Analysis Report"), 
                        fontBold, 24, currentY);
        
        currentY -= 60;
        drawCenteredText(projectInfo.getProjectName(), fontBold, 18, currentY);
        
        String subtitle = config.getProperty("mule.common.cover.subtitle", "");
        if (!subtitle.isEmpty()) {
            currentY -= 40;
            drawCenteredText(subtitle, fontRegular, 14, currentY);
        }
        
        currentY -= 60;
        String description = config.getProperty("mule.common.cover.description", 
                "Comprehensive analysis and documentation");
        drawCenteredText(description, fontRegular, 12, currentY);
        
        currentY -= 100;
        ZonedDateTime now = ZonedDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy HH:mm:ss z"));
        drawCenteredText("Generated: " + timestamp, fontRegular, 10, currentY);
        
        drawCenteredText("RaksAnalyzer ApiGuard Tool", fontRegular, 8, 50);
        
        closeContentStream();
    }
    
    private void generateTableOfContents() throws IOException {
        // Create TOC Page
        PDPage tocPage = new PDPage(PDRectangle.A4);
        
        // Insert at index 1 (after cover page)
        // Ensure we have at least 2 pages (Cover + Content), otherwise append
        if (document.getNumberOfPages() > 1) {
            document.getPages().insertBefore(tocPage, document.getPage(1));
        } else {
            document.addPage(tocPage);
        }
        
        // Initialize manual stream management for multi-page support
        
        // Re-implementing with manual stream management for multi-page support
        PDPage currentTocPage = tocPage;
        PDPageContentStream currentTocStream = createContentStream(document, currentTocPage);
        float tocY = PAGE_HEIGHT - 80;
        
        try {
             // Draw Title on first page
             currentTocStream.beginText();
             currentTocStream.setFont(fontBold, 18);
             currentTocStream.newLineAtOffset(MARGIN, PAGE_HEIGHT - 50);
             currentTocStream.showText("Table of Contents");
             currentTocStream.endText();
             
             for (TOCEntry entry : tocEntries) {
                 if (tocY < MARGIN + 20) {
                     currentTocStream.close();
                     PDPage nextTocPage = new PDPage(PDRectangle.A4);
                     document.getPages().insertAfter(nextTocPage, currentTocPage);
                     currentTocPage = nextTocPage;
                     currentTocStream = createContentStream(document, currentTocPage);
                     tocY = PAGE_HEIGHT - MARGIN;
                 }
                 
                 // Indentation based on level
                 float xindent = (entry.level - 1) * 20f;
                 int validPageNum = document.getPages().indexOf(entry.page) + 1;
                 
                 // Draw clickable link text
                 currentTocStream.beginText();
                 currentTocStream.setFont(fontRegular, 11);
                 currentTocStream.newLineAtOffset(MARGIN + xindent, tocY);
                 currentTocStream.showText(entry.title);
                 currentTocStream.endText();
                 
                 // Draw dots
                 float titleWidth = fontRegular.getStringWidth(entry.title) / 1000 * 11;
                 float dotsStart = MARGIN + xindent + titleWidth + 5;
                 float dotsEnd = PAGE_WIDTH - MARGIN - 70; // Increased buffer from 40 to 70
                 if (dotsEnd > dotsStart) {
                     currentTocStream.beginText();
                     currentTocStream.newLineAtOffset(dotsStart, tocY);
                     float dotComboWidth = fontRegular.getStringWidth(" .") / 1000 * 11;
                     int repeatCount = (int)((dotsEnd - dotsStart) / dotComboWidth);
                     if (repeatCount > 0) {
                         String dots = " .".repeat(repeatCount);
                         currentTocStream.showText(dots);
                     }
                     currentTocStream.endText();
                 }
                 
                 // Draw Page Number
                 String pageNumStr = String.valueOf(validPageNum);
                 float pageNumWidth = fontRegular.getStringWidth(pageNumStr) / 1000 * 11;
                 currentTocStream.beginText();
                 currentTocStream.newLineAtOffset(PAGE_WIDTH - MARGIN - pageNumWidth, tocY);
                 currentTocStream.showText(pageNumStr);
                 currentTocStream.endText();
                 
                 // Add Link Annotation
                 org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink link = 
                     new org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink();
                 
                 // Remove border (box) around the link
                 org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary borderStyle = 
                     new org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary();
                 borderStyle.setWidth(0);
                 link.setBorderStyle(borderStyle);
                 
                 org.apache.pdfbox.pdmodel.common.PDRectangle rect = new org.apache.pdfbox.pdmodel.common.PDRectangle();
                 rect.setLowerLeftX(MARGIN + xindent);
                 rect.setLowerLeftY(tocY - 2);
                 rect.setUpperRightX(PAGE_WIDTH - MARGIN);
                 rect.setUpperRightY(tocY + 10);
                 link.setRectangle(rect);
                 
                 // Destination - Use XYZ to keep current zoom level
                
                 // Destination - Use XYZ to keep current zoom level
                 org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination dest = 
                     new org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination();
                 dest.setPage(entry.page);
                 dest.setTop((int)PAGE_HEIGHT);
                 dest.setLeft(0);
                 dest.setZoom(-1); // Inherit current zoom level
                 
                 org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo action = 
                     new org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo();
                 action.setDestination(dest);
                 link.setAction(action);
                 
                 currentTocPage.getAnnotations().add(link);
                 
                 tocY -= 20;
             }
         } finally {
             // CRITICAL: Ensure the TOC stream is closed before returning!
             if (currentTocStream != null) {
                 try {
                     currentTocStream.close();
                 } catch (Exception e) {
                     logger.warn("Failed to close TOC stream", e);
                 }
                 currentTocStream = null;
             }
         }
    }
    
    private PDOutlineItem createProjectInformation(ProjectInfo projectInfo) throws IOException {
        newPage();
        PDOutlineItem bookmark = addBookmark("1. Project Information", rootOutline, currentPage);
        
        currentY = PAGE_HEIGHT - 100;
        drawSectionHeader("1. Project Information");
        
        // 1.1 Project Details
        addBookmark("1.1 Project Details", bookmark, currentPage);
        drawSubsectionHeader("1.1 Project Details");
        
        String[][] data = {
            {"Project Name", projectInfo.getProjectName()},
            {"Version", projectInfo.getVersion()},
            {"Project Path", projectInfo.getProjectPath()}
        };
        
        drawTable(data, new String[]{"Property", "Value"}, new float[]{0.3f, 0.7f});
        
        closeContentStream();
        return bookmark;
    }
    
    // Section 2: Flow Structure and Diagrams
    private void createFlowDiagramSection(AnalysisResult result) throws IOException {
        boolean genStructure = Boolean.parseBoolean(config.getProperty("mule.generate.flow.structure", "true"));
        boolean genDiagrams = Boolean.parseBoolean(config.getProperty("mule.generate.flow.diagrams", "true"));
        
        if (!genStructure && !genDiagrams) return;

        newPage();
        PDOutlineItem section = addBookmark("2. Flow Diagrams", rootOutline, currentPage);
        currentY = PAGE_HEIGHT - 100;
        drawSectionHeader("2. Flow Diagrams");
        
        // Collect all flows and sub-flows
        List<FlowWithSource> allFlows = new ArrayList<>();
        collectFlowsByType(result.getProjectStructure(), "flow", allFlows);
        collectFlowsByType(result.getProjectStructure(), "sub-flow", allFlows);
        
        // Sort for consistent order
        allFlows.sort(Comparator.comparing(f -> f.flow.getName()));

        if (allFlows.isEmpty()) {
            drawText("No flows found in the project.", fontRegular, 11);
            closeContentStream();
            return;
        }

        // 2.1 Integration Diagrams (config-ref only)
        if (genStructure) {
            drawSubSectionHeader("2.1 Mule Project Flow Integration", section, 2);
            closeContentStream();

            int flowNum = 1;
            for (FlowWithSource item : allFlows) {
                 checkPageSpace(150); // Need substantial space for name + image
                 
                 // Flow Name with hierarchical numbering
                 String integrationTitle = "2.1." + flowNum + " " + item.flow.getName();
                 PDOutlineItem flowBookmark = addBookmark(integrationTitle, section, currentPage, 3);
                 
                 try (PDPageContentStream cs = createContentStream(document, document.getPage(document.getNumberOfPages() - 1), PDPageContentStream.AppendMode.APPEND, true, true)) {
                     this.contentStream = cs;
                     drawText(integrationTitle, fontBold, 11);
                     currentY -= 15;
                     
                     // Add Path
                     if (item.sourceFile != null) {
                         cs.setFont(fontItalic, 9);
                         cs.setNonStrokingColor(java.awt.Color.GRAY);
                         cs.beginText();
                         cs.newLineAtOffset(MARGIN, currentY);
                         cs.showText("Path: " + item.sourceFile.getRelativePath());
                         cs.endText();
                         cs.setNonStrokingColor(java.awt.Color.BLACK); // Reset
                         currentY -= 15;
                     }
                 }
                 this.contentStream = null;
                 
                 // Generate integration diagram (config-ref only, full names)
                 int maxDepth = Integer.parseInt(config.getProperty("pdf.diagram.nested.component.max.depth", "0"));
                 byte[] imageBytes = DiagramGenerator.generatePlantUmlImage(item.flow, maxDepth, true, true);
                 if (imageBytes != null && imageBytes.length > 0) {
                     // Convert to BufferedImage
                     java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
                     // Create PDF Image
                     PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);
                     
                     // Get available width
                     float availableWidth = PAGE_WIDTH - (2 * MARGIN);
                     // Get available height on a FULL page (max possible height for an image)
                     float maxPageHeight = PAGE_HEIGHT - (2 * MARGIN) - 50; // buffer for header/footer
                     
                     float imgWidth = pdImage.getWidth();
                     float imgHeight = pdImage.getHeight();
                     
                     float scale = 1.0f;
                     
                     // 1. Scale to fit Width
                     if (imgWidth > availableWidth) {
                         scale = availableWidth / imgWidth;
                     }
                     
                     // 2. Check if scaled height fits in Max Page Height
                     float scaledHeight = imgHeight * scale;
                     if (scaledHeight > maxPageHeight) {
                         // If still too tall, scale down based on Height
                         scale = scale * (maxPageHeight / scaledHeight);
                     }
                     
                     float finalWidth = imgWidth * scale;
                     float finalHeight = imgHeight * scale;
                     
                     // Calculate available space on current page
                     float availableSpaceOnCurrentPage = currentY - MARGIN;
                     
                     // Check if image fits on current page
                     // If not, move to new page BEFORE drawing
                     if (finalHeight > availableSpaceOnCurrentPage) {
                         // Image doesn't fit on current page, move to new page
                         newPage();
                         currentY = PAGE_HEIGHT - MARGIN;
                         
                         // Double-check: if it still doesn't fit on a fresh page, it means the image is taller than maxPageHeight
                         // This shouldn't happen because we already scaled it, but just in case
                         float availableSpaceOnNewPage = currentY - MARGIN;
                         if (finalHeight > availableSpaceOnNewPage) {
                             // Image is too tall even for a fresh page, scale it down further
                             float additionalScale = availableSpaceOnNewPage / finalHeight;
                             finalWidth *= additionalScale;
                             finalHeight *= additionalScale;
                         }
                     }
                     
                     // Draw Image
                     try (PDPageContentStream cs = createContentStream(document, document.getPage(document.getNumberOfPages() - 1), PDPageContentStream.AppendMode.APPEND, true, true)) {
                         this.contentStream = cs;
                         cs.drawImage(pdImage, MARGIN, currentY - finalHeight, finalWidth, finalHeight);
                         currentY -= (finalHeight + 20);
                     }
                     this.contentStream = null;
                     
                     // Add note if scaled significantly
                     if (scale < 0.5f) {
                         try (PDPageContentStream cs = createContentStream(document, document.getPage(document.getNumberOfPages() - 1), PDPageContentStream.AppendMode.APPEND, true, true)) {
                             this.contentStream = cs;
                             cs.setFont(fontItalic, 8);
                             cs.setNonStrokingColor(java.awt.Color.GRAY);
                             cs.beginText();
                             cs.newLineAtOffset(MARGIN, currentY);
                             cs.showText("(Diagram scaled to fit page)");
                             cs.endText();
                             cs.setNonStrokingColor(java.awt.Color.BLACK);
                             currentY -= 10;
                         }
                     }
                 } else {
                     try (PDPageContentStream cs = createContentStream(document, document.getPage(document.getNumberOfPages() - 1), PDPageContentStream.AppendMode.APPEND, true, true)) {
                         this.contentStream = cs;
                         drawText("(No integration points available)", fontRegular, 9);
                         currentY -= 20;
                     } 
                     this.contentStream = null;
                 }
                 
                 flowNum++;
            }
        }

        // 2.2 Visual Diagrams
        if (genDiagrams) {
            // Ensure we start on new page if previous section took space? 
            // Better to check space, but Diagrams are large.
            if (genStructure) { 
                newPage();
                closeContentStream();
                currentY = PAGE_HEIGHT - MARGIN;
            }
            
            drawSubSectionHeader("2.2 Mule Project Flow Diagram", section, 2);
            closeContentStream();

            int flowNum = 1;
            for (FlowWithSource item : allFlows) {
                 checkPageSpace(150); // Need substantial space for name + image
                 
                 // Flow Name with hierarchical numbering
                 String diagramTitle = "2.2." + flowNum + " " + item.flow.getName();
                 PDOutlineItem flowBookmark = addBookmark(diagramTitle, section, currentPage, 3);
                 
                 try (PDPageContentStream cs = createContentStream(document, document.getPage(document.getNumberOfPages() - 1), PDPageContentStream.AppendMode.APPEND, true, true)) {
                     this.contentStream = cs;
                     drawText(diagramTitle, fontBold, 11);
                     currentY -= 15;
                     
                     // Add Path
                     if (item.sourceFile != null) {
                         cs.setFont(fontItalic, 9);
                         cs.setNonStrokingColor(java.awt.Color.GRAY);
                         cs.beginText();
                         cs.newLineAtOffset(MARGIN, currentY);
                         cs.showText("Path: " + item.sourceFile.getRelativePath());
                         cs.endText();
                         cs.setNonStrokingColor(java.awt.Color.BLACK); // Reset
                         currentY -= 15;
                     }
                 }
                 this.contentStream = null;
                 
                 // Ensure stream is properly closed before image creation
                 closeContentStream(); // Double check logic

                 // Generate Image
                 int maxDepth = Integer.parseInt(config.getProperty("pdf.diagram.nested.component.max.depth", "0"));
                 boolean useFullNames = Boolean.parseBoolean(config.getProperty("pdf.diagram.element.fullname", "true"));
                 byte[] imageBytes = DiagramGenerator.generatePlantUmlImage(item.flow, maxDepth, useFullNames, false);
                 if (imageBytes != null && imageBytes.length > 0) {
                     // Convert to BufferedImage
                     java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
                     // Create PDF Image
                     PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);
                     
                     // Get available width
                     float availableWidth = PAGE_WIDTH - (2 * MARGIN);
                     // Get available height on a FULL page (max possible height for an image)
                     float maxPageHeight = PAGE_HEIGHT - (2 * MARGIN) - 50; // buffer for header/footer
                     
                     float imgWidth = pdImage.getWidth();
                     float imgHeight = pdImage.getHeight();
                     
                     float scale = 1.0f;
                     
                     // 1. Scale to fit Width
                     if (imgWidth > availableWidth) {
                         scale = availableWidth / imgWidth;
                     }
                     
                     // 2. Check if scaled height fits in Max Page Height
                     float scaledHeight = imgHeight * scale;
                     if (scaledHeight > maxPageHeight) {
                         // If still too tall, scale down based on Height
                         scale = scale * (maxPageHeight / scaledHeight);
                     }
                     
                     float finalWidth = imgWidth * scale;
                     float finalHeight = imgHeight * scale;
                     
                     // Calculate available space on current page
                     float availableSpaceOnCurrentPage = currentY - MARGIN;
                     
                     // Check if image fits on current page
                     // If not, move to new page BEFORE drawing
                     if (finalHeight > availableSpaceOnCurrentPage) {
                         // Image doesn't fit on current page, move to new page
                         newPage();
                         currentY = PAGE_HEIGHT - MARGIN;
                         
                         // Double-check: if it still doesn't fit on a fresh page, it means the image is taller than maxPageHeight
                         // This shouldn't happen because we already scaled it, but just in case
                         float availableSpaceOnNewPage = currentY - MARGIN;
                         if (finalHeight > availableSpaceOnNewPage) {
                             // Image is too tall even for a fresh page, scale it down further
                             float additionalScale = availableSpaceOnNewPage / finalHeight;
                             finalWidth *= additionalScale;
                             finalHeight *= additionalScale;
                         }
                     }
                     
                     // Draw Image
                     try (PDPageContentStream cs = createContentStream(document, document.getPage(document.getNumberOfPages() - 1), PDPageContentStream.AppendMode.APPEND, true, true)) {
                         this.contentStream = cs;
                         cs.drawImage(pdImage, MARGIN, currentY - finalHeight, finalWidth, finalHeight);
                         currentY -= (finalHeight + 20);
                     }
                     this.contentStream = null;
                     
                     // Add note if scaled significantly
                     if (scale < 0.5f) {
                         try (PDPageContentStream cs = createContentStream(document, document.getPage(document.getNumberOfPages() - 1), PDPageContentStream.AppendMode.APPEND, true, true)) {
                             this.contentStream = cs;
                             drawText("(Diagram scaled to fit page)", fontRegular, 8);
                         }
                         this.contentStream = null;
                         currentY -= 10;
                     }
                 } else {
                     try (PDPageContentStream cs = createContentStream(document, document.getPage(document.getNumberOfPages() - 1), PDPageContentStream.AppendMode.APPEND, true, true)) {
                         this.contentStream = cs;
                         drawText("(No visual representation available)", fontRegular, 9);
                         currentY -= 20;
                     } 
                     this.contentStream = null;
                 }
                 
                 flowNum++;
            }
        }
    }
    
    private void drawSubSectionHeader(String title, PDOutlineItem parent, int level) throws IOException {
        checkPageSpace(50);
        try (PDPageContentStream cs = createContentStream(document, document.getPage(document.getNumberOfPages() - 1), PDPageContentStream.AppendMode.APPEND, true, true)) {
             this.contentStream = cs;
             // Add bookmark
             addBookmark(title, parent, document.getPage(document.getNumberOfPages() - 1), level);
             
             // Draw Header
             cs.setFont(fontBold, 14);
             cs.setNonStrokingColor(getColorConfig("pdf.header.text.color", java.awt.Color.DARK_GRAY)); // Reusing header color or default
             cs.beginText();
             cs.newLineAtOffset(MARGIN, currentY);
             cs.showText(title);
             cs.endText();
             currentY -= 25;
             
             // Reset color
             cs.setNonStrokingColor(java.awt.Color.BLACK);
        }
        this.contentStream = null;
    }
    
    // Placeholder for Flow Diagrams (Section 2)
    private void createFlowDiagramsPlaceholder() throws IOException {
        newPage();
        addBookmark("2. Flow Diagrams", rootOutline, currentPage);
        
        currentY = PAGE_HEIGHT - 100;
        drawSectionHeader("2. Flow Diagrams");
        
        drawText("This section is reserved for flow diagrams showing the execution flow of Mule", fontRegular, 11);
        currentY -= 15;
        drawText("applications, including listeners, processors, connectors, and parallel execution paths.", fontRegular, 11);
        
        closeContentStream();
    }
    
    private void createGlobalConfiguration(AnalysisResult result) throws IOException {
        newPage();
        PDOutlineItem section = addBookmark("3. Global Configuration", rootOutline, currentPage);
        
        currentY = PAGE_HEIGHT - 100;
        drawSectionHeader("3. Global Configuration");
        
        List<PropertyInfo> properties = result.getProperties();
        List<String> propFiles = result.getPropertyFiles();
        
        // Show property files count
        if (propFiles != null && !propFiles.isEmpty()) {
            drawText("Property Files Found: " + propFiles.size(), fontBold, 12);
            currentY -= 10;
            
            // List property files
            for (String propFile : propFiles) {
                checkPageSpace(20);
                drawText("• " + propFile, fontRegular, 10);
                currentY -= 15;
            }
            
            currentY -= 10;
        }
        
        // Show total properties count
        if (properties != null && !properties.isEmpty()) {
            drawText("Total Properties: " + properties.size(), fontBold, 12);
            currentY -= 25;
            
            // Group properties by file
            Map<String, List<PropertyInfo>> propsByFile = new LinkedHashMap<>();
            
            // Group properties by their actual source file
            for (PropertyInfo prop : properties) {
                String sourceFile = prop.getSourcePath();
                if (sourceFile != null && !sourceFile.isEmpty()) {
                    propsByFile.computeIfAbsent(sourceFile, k -> new ArrayList<>()).add(prop);
                } else {
                    // Fallback for properties without source path
                    propsByFile.computeIfAbsent("Application Properties", k -> new ArrayList<>()).add(prop);
                }
            }
            
            // Close the current content stream BEFORE drawing complex tables
            // as RepeatedHeaderTableDrawer manages its own streams.
            closeContentStream();
            
            // Display properties by file
            int propFileIndex = 1;
            for (Map.Entry<String, List<PropertyInfo>> entry : propsByFile.entrySet()) {
                // Add sub-bookmark for this file
                addBookmark("3." + propFileIndex + " " + entry.getKey(), section, currentPage);
                // Build table data
                List<String[]> propData = new ArrayList<>();
                for (PropertyInfo prop : entry.getValue()) {
                    String value = prop.getDefaultValue();
                    if (value == null || value.isEmpty()) {
                        if (prop.getEnvironmentValues() != null && !prop.getEnvironmentValues().isEmpty()) {
                            value = prop.getEnvironmentValues().values().iterator().next();
                        }
                    }
                    propData.add(new String[]{
                        prop.getKey() != null ? prop.getKey() : "",
                        value != null ? value : ""
                    });
                }
                
                if (!propData.isEmpty()) {
                    // Get column widths
                    float keyWidth = Float.parseFloat(config.getProperty("pdf.table.properties.column.key.width", "0.3"));
                    float valueWidth = Float.parseFloat(config.getProperty("pdf.table.properties.column.value.width", "0.7"));
                    float tableWidth = CONTENT_WIDTH;
                    float[] colWidthsInPoints = new float[]{tableWidth * keyWidth, tableWidth * valueWidth};
                    
                    org.vandeseer.easytable.structure.Table.TableBuilder tableBuilder = 
                        org.vandeseer.easytable.structure.Table.builder()
                            .addColumnsOfWidth(colWidthsInPoints)
                            .fontSize(9)
                            .font(fontRegular)
                            .borderColor(java.awt.Color.BLACK);
                            
                    // Header Row 1: File Label
                    tableBuilder.addRow(
                        org.vandeseer.easytable.structure.Row.builder()
                            .add(org.vandeseer.easytable.structure.cell.TextCell.builder()
                                .text("3." + (propFileIndex++) + " " + entry.getKey() + " (" + entry.getValue().size() + " properties)")
                                .colSpan(2)
                                .font(fontBold)
                                .fontSize(10)
                                .padding(5f)
                                .borderWidth(0f)
                                .build())
                            .build()
                    );
                    
                    // Header Row 2: Column Headers
                    org.vandeseer.easytable.structure.Row.RowBuilder headerRowBuilder = 
                        org.vandeseer.easytable.structure.Row.builder();
                    
                    java.awt.Color headerBgColor = getColorConfig("pdf.table.header.background.color", new java.awt.Color(230, 230, 250)); // Old Purple: 102, 51, 153
                    java.awt.Color headerTextColor = getColorConfig("pdf.table.header.text.color", java.awt.Color.BLACK);
                    
                    headerRowBuilder.add(org.vandeseer.easytable.structure.cell.TextCell.builder()
                        .text("Key")
                        .backgroundColor(headerBgColor)
                        .textColor(headerTextColor)
                        .font(fontBold)
                        .fontSize(10)
                        .horizontalAlignment(org.vandeseer.easytable.settings.HorizontalAlignment.LEFT)
                        .borderWidth(0.5f)
                        .build());
                    headerRowBuilder.add(org.vandeseer.easytable.structure.cell.TextCell.builder()
                        .text("Value")
                        .backgroundColor(headerBgColor)
                        .textColor(headerTextColor)
                        .font(fontBold)
                        .fontSize(10)
                        .horizontalAlignment(org.vandeseer.easytable.settings.HorizontalAlignment.LEFT)
                        .borderWidth(0.5f)
                        .build());
                    tableBuilder.addRow(headerRowBuilder.build());
                    
                    // Data Rows
                    for (String[] row : propData) {
                         String key = row[0];
                         String val = row[1];
                         if (key == null) key = "";
                         if (val == null) val = "";
                         if (val.length() > 2000) val = val.substring(0, 2000) + "... [TRUNCATED]";
                         
                         tableBuilder.addRow(org.vandeseer.easytable.structure.Row.builder()
                            .add(org.vandeseer.easytable.structure.cell.TextCell.builder()
                                .text(key).borderWidth(0.5f).wordBreak(true).build())
                            .add(org.vandeseer.easytable.structure.cell.TextCell.builder()
                                .text(val).borderWidth(0.5f).wordBreak(true).build())
                            .build());
                    }
                    
                    // Check if we need a new page for the START of this table
                    // Since context stream is closed, we just check currentY
                    if (currentY < 150) {
                         newPage(); // This re-opens stream and resets Y
                         closeContentStream(); // Close it again immediately for the drawer
                         currentY = PAGE_HEIGHT - MARGIN;
                    }

                    // Draw
                    org.vandeseer.easytable.RepeatedHeaderTableDrawer.builder()
                        .table(tableBuilder.build())
                        .startX(MARGIN)
                        .startY(currentY)
                        .endY(MARGIN)
                        .numberOfRowsToRepeat(2)
                        .build()
                        .draw(() -> document, () -> new PDPage(PDRectangle.A4), MARGIN + 20f);
                        
                    // Post-draw cleanup
                    // Only new page if there are more files to process
                    if (propFileIndex <= propsByFile.size()) { // propFileIndex was incremented at start of loop, so it's effectively 'next index'
                        newPage(); 
                        closeContentStream();
                        currentY = PAGE_HEIGHT - MARGIN;
                    } else {
                         // Last item: just close stream, next section will handle new page
                         closeContentStream();
                    }
                }
            }
        } else {
             drawText("No properties found", fontRegular, 12);
             closeContentStream();
        }
    }
    
    private void createConnectorConfigurations(AnalysisResult result) throws IOException {
        newPage();
        PDOutlineItem section = addBookmark("4. Connector Configurations", rootOutline, currentPage);
        
        currentY = PAGE_HEIGHT - 100;
        drawSectionHeader("4. Connector Configurations");
        
        // Configurable Limit and Depth
        int textLimit = Integer.parseInt(config.getProperty("pdf.table.cell.text.limit", "2000"));
        int maxDepth = Integer.parseInt(config.getProperty("pdf.nested.component.max.depth", "3"));
        
        // Stream safety
        closeContentStream();
        
        // 1. Collect all connector configs
        List<ConnectorConfigWithSource> allConfigs = new ArrayList<>();
        collectAllConnectorConfigs(result.getProjectStructure(), allConfigs);
        
        // Sort by name
        allConfigs.sort(Comparator.comparing(c -> c.config.getName() != null ? c.config.getName() : ""));
        
        if (!allConfigs.isEmpty()) {
            // Draw Count
            try (PDPageContentStream cs = createContentStream(document, document.getPage(document.getNumberOfPages() - 1), PDPageContentStream.AppendMode.APPEND, true, true)) {
                this.contentStream = cs;
                drawText("Total Configurations: " + allConfigs.size(), fontBold, 12);
                currentY -= 20;
            } // Close immediately to keep state clean for loop
             this.contentStream = null;

            int configIndex = 1;
            for (ConnectorConfigWithSource item : allConfigs) {
                // Ensure we start on a safe area for a new connector (Header + Table)
                if (currentY < 60) {
                    newPage();
                    closeContentStream(); // Close global stream to prevent leak when local cs is created
                    currentY = PAGE_HEIGHT - MARGIN;
                } 
                
                PDOutlineItem configBookmark = null;
                // Draw Connector Header with numbering
                try (PDPageContentStream cs = createContentStream(document, document.getPage(document.getNumberOfPages() - 1), PDPageContentStream.AppendMode.APPEND, true, true)) {
                    this.contentStream = cs;
                    String headerText = "4." + configIndex + ". " + (item.config.getName() != null ? item.config.getName() : "Unnamed") 
                                      + " (" + (item.config.getType() != null ? item.config.getType() : "Unknown Type") + ")";
                     configBookmark = addBookmark(headerText, section, document.getPage(document.getNumberOfPages() - 1), 2);
                     drawText(headerText, fontBold, 11);
                     currentY -= 15;
                     drawText("  Path: " + (item.sourceFile != null ? item.sourceFile : "Unknown"), fontRegular, 9);
                     currentY -= 10; // Reduced spacing before table
                }
                this.contentStream = null;

                // Draw Attributes Table
                if (item.config.getAttributes() != null && !item.config.getAttributes().isEmpty()) {
                     drawAttributesTable(item.config.getAttributes(), textLimit);
                }
                
                // Draw Nested Components Recursively with hierarchical numbering
                if (item.config.getNestedComponents() != null && !item.config.getNestedComponents().isEmpty()) {
                     renderComponentsHierarchically(item.config.getNestedComponents(), 0, maxDepth, textLimit, "4." + configIndex, configBookmark, 2);
                }
                
                // Gap between configurations - just a small space
                currentY -= 10;
                configIndex++;
            }
        } else {
             try (PDPageContentStream cs = createContentStream(document, document.getPage(document.getNumberOfPages() - 1), PDPageContentStream.AppendMode.APPEND, true, true)) {
                this.contentStream = cs;
                drawText("No connector configurations found", fontRegular, 12);
            }
            this.contentStream = null;
        }
        
        // Ensure stream is null at exit
        this.contentStream = null;
    }
    
    // Helper Class for Sorting
    private static class ConnectorConfigWithSource {
        ConnectorConfig config;
        String sourceFile;
        ConnectorConfigWithSource(ConnectorConfig config, String sourceFile) {
            this.config = config;
            this.sourceFile = sourceFile;
        }
    }
    
    // Helper class for flows with source information
    private static class FlowWithSource {
        FlowInfo flow;
        ProjectNode sourceFile;
        FlowWithSource(FlowInfo flow, ProjectNode sourceFile) {
            this.flow = flow;
            this.sourceFile = sourceFile;
        }
    }

    // Helper to collect configs from project structure (ProjectNode)
    private void collectAllConnectorConfigs(ProjectNode node, List<ConnectorConfigWithSource> accumulated) {
        if (node == null) return;
        
        // Metadata key for connector configs - MATCHING WordGenerator logic ("muleConfigs")
        if (node.getType() == NodeType.FILE) {
             Object configs = node.getMetadata("muleConfigs");
             if (configs instanceof List) {
                 for (Object o : (List<?>)configs) {
                     if (o instanceof ConnectorConfig) {
                         accumulated.add(new ConnectorConfigWithSource((ConnectorConfig)o, node.getRelativePath()));
                     }
                 }
             }
        }
        
        // Recurse children
        if (node.getChildren() != null) {
             for (ProjectNode child : node.getChildren()) {
                 collectAllConnectorConfigs(child, accumulated);
             }
        }
    }
    
    // Helper to collect flows by type from project structure
    @SuppressWarnings("unchecked")
    private void collectFlowsByType(ProjectNode node, String flowType, List<FlowWithSource> result) {
        if (node == null) return;
        
        if (node.getType() == NodeType.FILE && node.getName().endsWith(".xml")) {
            List<FlowInfo> flows = (List<FlowInfo>) node.getMetadata("muleFlows");
            if (flows != null) {
                for (FlowInfo flow : flows) {
                    if (flowType.equalsIgnoreCase(flow.getType())) {
                        result.add(new FlowWithSource(flow, node));
                    }
                }
            }
        } else if (node.getType() == NodeType.DIRECTORY && node.getChildren() != null) {
            for (ProjectNode child : node.getChildren()) {
                collectFlowsByType(child, flowType, result);
            }
        }
    }
    
    // Recursive Component Renderer with hierarchical numbering
    private void renderComponentsHierarchically(List<ComponentInfo> components, int startDepth, int maxDepth, int textLimit, String numberPrefix, PDOutlineItem parentBookmark, int parentLevel) throws IOException {
        if (components == null) return;
        
        // Track counters per depth level
        Map<Integer, Integer> depthCounters = new HashMap<>();
        int lastDepth = startDepth;
        
        for (ComponentInfo comp : components) {
             // Determine depth from attribute if available
             int depth = startDepth;
             if (comp.getAttributes() != null && comp.getAttributes().containsKey("_depth")) {
                 try {
                     depth = Integer.parseInt(comp.getAttributes().get("_depth"));
                 } catch (NumberFormatException ignored) {}
             }
             
             // Check Max Depth
             if (depth > maxDepth) continue;

             // Reset counters for deeper levels when we go back to a shallower level
             if (depth < lastDepth) {
                 for (int d = depth + 1; d <= lastDepth; d++) {
                     depthCounters.remove(d);
                 }
             }
             lastDepth = depth;

             // Get or initialize counter for this depth
             int counter = depthCounters.getOrDefault(depth, 0) + 1;
             depthCounters.put(depth, counter);

             // Build hierarchical number based on depth
             String currentNumber;
             if (depth == 0) {
                 currentNumber = numberPrefix + "." + counter;
             } else {
                 StringBuilder sb = new StringBuilder(numberPrefix);
                 for (int i = 0; i <= depth; i++) {
                     sb.append(".").append(depthCounters.getOrDefault(i, 0));
                 }
                 currentNumber = sb.toString();
             }

             // Check space for component header
             if (currentY < 40) {
                 newPage();
                 closeContentStream(); // Close global stream to prevent leak when local cs is created
                 currentY = PAGE_HEIGHT - MARGIN;
             }
             
             // Draw Header with hierarchical numbering
              try (PDPageContentStream cs = createContentStream(document, document.getPage(document.getNumberOfPages() - 1), PDPageContentStream.AppendMode.APPEND, true, true)) {
                 this.contentStream = cs;
                 
                 // Draw component type with hierarchical number and indentation
                 float indentPerLevel = 15f;
                 float xOffset = depth * indentPerLevel;
                 String componentText = currentNumber + " " + (comp.getType() != null ? comp.getType() : "Unknown");
                 
                 // Add bookmark and TOC entry for this component
                 addBookmark(componentText, parentBookmark, document.getPage(document.getNumberOfPages() - 1), parentLevel + 1 + depth);
                 
                 drawText(componentText, fontBold, 10, xOffset);
                 currentY -= 15;
              }
              this.contentStream = null;

             // Draw Attributes
             if (comp.getAttributes() != null && !comp.getAttributes().isEmpty()) {
                 drawAttributesTable(comp.getAttributes(), textLimit);
             }
        }
    }
    
    // Reusable Table Drawer
    private void drawAttributesTable(Map<String, String> attributes, int textLimit) throws IOException {
         // Build Data - Filter out internal attributes like _depth
         List<String[]> rows = new ArrayList<>();
         
         // Extract content first
         String content = attributes.get("_content");
         
         for (Map.Entry<String, String> entry : attributes.entrySet()) {
             String key = entry.getKey() != null ? entry.getKey() : "";
             
             // Skip internal attributes that start with underscore
             if (key.startsWith("_")) {
                 continue;
             }
             
             String val = entry.getValue() != null ? entry.getValue() : "";
             val = sanitizeForPdf(val, textLimit);
             rows.add(new String[]{key, val});
         }
         
         // Add content row if exists (matching Word generator behavior)
         if (content != null) {
             // Strip CDATA wrapper if present to just show the inner content
             if (content.trim().startsWith("<![CDATA[") && content.trim().endsWith("]]>")) {
                 content = content.trim();
                 content = content.substring(9, content.length() - 3);
                 
                 // Also strip Mule expression wrapper #[ ... ] if present inside CDATA
                 if (content.trim().startsWith("#[") && content.trim().endsWith("]")) {
                     content = content.trim();
                     content = content.substring(2, content.length() - 1);
                 }
             }
             
             content = sanitizeForPdf(content, textLimit);
             String cdataLabel = config.getProperty("pdf.cdata.label", "<![CDATA[#[...]]]>");
             rows.add(new String[]{cdataLabel, content});
         }
         
         if (rows.isEmpty()) return;
         
         // Config
         float tableWidth = CONTENT_WIDTH;
         float[] colWidths = new float[]{tableWidth * 0.3f, tableWidth * 0.7f};
         
         org.vandeseer.easytable.structure.Table.TableBuilder tableBuilder = 
            org.vandeseer.easytable.structure.Table.builder()
                .addColumnsOfWidth(colWidths)
                .fontSize(9)
                .font(fontRegular)
                .borderColor(java.awt.Color.BLACK);
                
         // Columns Header
         java.awt.Color headerBgColor = getColorConfig("pdf.table.header.background.color", new java.awt.Color(230, 230, 250)); // Old Purple: 102, 51, 153
         java.awt.Color headerTextColor = getColorConfig("pdf.table.header.text.color", java.awt.Color.BLACK);
         
         org.vandeseer.easytable.structure.Row.RowBuilder header = org.vandeseer.easytable.structure.Row.builder();
         header.add(org.vandeseer.easytable.structure.cell.TextCell.builder().text("Attribute").font(fontBold).textColor(headerTextColor).backgroundColor(headerBgColor).borderWidth(0.5f).build());
         header.add(org.vandeseer.easytable.structure.cell.TextCell.builder().text("Value").font(fontBold).textColor(headerTextColor).backgroundColor(headerBgColor).borderWidth(0.5f).build());
         tableBuilder.addRow(header.build());
         
         for (String[] r : rows) {
             tableBuilder.addRow(org.vandeseer.easytable.structure.Row.builder()
                .add(org.vandeseer.easytable.structure.cell.TextCell.builder().text(r[0]).borderWidth(0.5f).wordBreak(true).build())
                .add(org.vandeseer.easytable.structure.cell.TextCell.builder().text(r[1]).borderWidth(0.5f).wordBreak(true).build())
                .build());
         }
         
          
          // Build the table
          org.vandeseer.easytable.structure.Table table = tableBuilder.build();
          
          // Check if we have enough space for the table (estimate)
          float estimatedTableHeight = table.getHeight();
          if (currentY - estimatedTableHeight < MARGIN + 50) {
               // Not enough space, create new page
               newPage();
               closeContentStream();
               currentY = PAGE_HEIGHT - MARGIN;
          }
          
          // Open a content stream for drawing
          if (contentStream == null) {
              contentStream = createContentStream(document, document.getPage(document.getNumberOfPages() - 1), PDPageContentStream.AppendMode.APPEND, true, true);
          }
          
          // Draw the table using TableDrawer (not RepeatedHeaderTableDrawer)
          // This allows multiple tables on the same page
          org.vandeseer.easytable.TableDrawer.builder()
             .contentStream(contentStream)
             .table(table)
             .startX(MARGIN)
             .startY(currentY)
             .build()
             .draw();
             
          // Update currentY to position after the table
          currentY -= table.getHeight() + 20; // Reduced gap to match Properties section
          
          // Close the content stream to keep state clean
          closeContentStream();
    }
    
    // Page Space Check that opens/closes stream
    private void checkPageSpaceForHeader(float needed) throws IOException {
        if (currentY < needed) {
            newPage();
            currentY = PAGE_HEIGHT - MARGIN;
        }
    }
    
    
    private void createMuleFlows(AnalysisResult result) throws IOException {
        newPage();
        PDOutlineItem section = addBookmark("5. Mule Flows", rootOutline, currentPage);
        
        currentY = PAGE_HEIGHT - 100;
        drawSectionHeader("5. Mule Flows");
        
        // Collect all flows from project structure (matching Word generator)
        List<FlowWithSource> allFlows = new ArrayList<>();
        collectFlowsByType(result.getProjectStructure(), "flow", allFlows);
        
        if (!allFlows.isEmpty()) {
            // Open stream for drawing
            if (contentStream == null) {
                contentStream = createContentStream(document, currentPage, PDPageContentStream.AppendMode.APPEND, true, true);
            }
            
            drawText("Total Flows: " + allFlows.size(), fontBold, 12);
            currentY -= 20;
            
            int flowNum = 1;
            for (FlowWithSource item : allFlows) {
                checkPageSpace(80);
                
                // Ensure stream is open for this flow
                if (contentStream == null) {
                    contentStream = createContentStream(document, currentPage, PDPageContentStream.AppendMode.APPEND, true, true);
                }
                
                // Flow header
                String flowTitle = "5." + flowNum + ". " + item.flow.getName();
                PDOutlineItem flowBookmark = addBookmark("5." + flowNum + " " + item.flow.getName(), section, currentPage, 2);
                drawText(flowTitle, fontBold, 11);
                currentY -= 15;
                
                // Source file path
                drawText("Path: " + item.sourceFile.getRelativePath(), fontRegular, 9);
                currentY -= 15;
                
                // Flow type
                drawText("Type: " + (item.flow.getType() != null ? item.flow.getType() : "flow"), fontRegular, 10);
                currentY -= 15;
                
                // Components count only (not rendering details for now)
                if (item.flow.getComponents() != null && !item.flow.getComponents().isEmpty()) {
                    drawText("Components: " + item.flow.getComponents().size(), fontRegular, 10);
                    currentY -= 15;
                    
                    // Close stream before delegating to hierarchical renderer
                    closeContentStream();
                    
                    int textLimit = Integer.parseInt(config.getProperty("pdf.table.cell.text.limit", "2000"));
                    int maxDepth = Integer.parseInt(config.getProperty("pdf.nested.component.max.depth", "3"));
                    
                    renderComponentsHierarchically(item.flow.getComponents(), 0, maxDepth, textLimit, "5." + flowNum, flowBookmark, 2);
                    
                    // Re-open stream for next iteration if needed (though start of loop handles it)
                    // Just leave it closed, next loop iteration will check if (contentStream == null)
                } else {
                    drawText("No components", fontRegular, 10);
                    currentY -= 20;
                }
                
                flowNum++;
            }
        } else {
            // Open stream for drawing
            if (contentStream == null) {
                contentStream = createContentStream(document, currentPage, PDPageContentStream.AppendMode.APPEND, true, true);
            }
            drawText("No flows found", fontRegular, 12);
        }
        
        // Ensure stream is closed before exiting
        closeContentStream();
    }
    
    private void createMuleSubFlows(AnalysisResult result) throws IOException {
        newPage();
        PDOutlineItem section = addBookmark("6. Mule Sub-Flows", rootOutline, currentPage);
        
        currentY = PAGE_HEIGHT - 100;
        drawSectionHeader("6. Mule Sub-Flows");
        
        // Collect all sub-flows from project structure (matching Word generator)
        List<FlowWithSource> allSubFlows = new ArrayList<>();
        collectFlowsByType(result.getProjectStructure(), "sub-flow", allSubFlows);
        
        if (!allSubFlows.isEmpty()) {
            // Open stream for drawing
            if (contentStream == null) {
                contentStream = createContentStream(document, currentPage, PDPageContentStream.AppendMode.APPEND, true, true);
            }
            
            drawText("Total Sub-Flows: " + allSubFlows.size(), fontBold, 12);
            currentY -= 20;
            
            int subFlowNum = 1;
            for (FlowWithSource item : allSubFlows) {
                checkPageSpace(80);
                
                // Ensure stream is open for this sub-flow
                if (contentStream == null) {
                    contentStream = createContentStream(document, currentPage, PDPageContentStream.AppendMode.APPEND, true, true);
                }
                
                // Sub-flow header
                String subFlowTitle = "6." + subFlowNum + ". " + item.flow.getName();
                PDOutlineItem subFlowBookmark = addBookmark("6." + subFlowNum + " " + item.flow.getName(), section, currentPage, 2);
                drawText(subFlowTitle, fontBold, 11);
                currentY -= 15;
                
                // Source file path
                drawText("Path: " + item.sourceFile.getRelativePath(), fontRegular, 9);
                currentY -= 15;
                
                // Flow type
                drawText("Type: " + (item.flow.getType() != null ? item.flow.getType() : "sub-flow"), fontRegular, 10);
                currentY -= 15;
                
                // Components count only (not rendering details for now)
                if (item.flow.getComponents() != null && !item.flow.getComponents().isEmpty()) {
                    drawText("Components: " + item.flow.getComponents().size(), fontRegular, 10);
                    currentY -= 15;
                    
                    // Close stream before delegating to hierarchical renderer
                    closeContentStream();
                    
                    int textLimit = Integer.parseInt(config.getProperty("pdf.table.cell.text.limit", "2000"));
                    int maxDepth = Integer.parseInt(config.getProperty("pdf.nested.component.max.depth", "3"));
                    
                    renderComponentsHierarchically(item.flow.getComponents(), 0, maxDepth, textLimit, "6." + subFlowNum, subFlowBookmark, 2);
                    
                    // Re-open stream for next iteration if needed (though start of loop handles it)
                    // Just leave it closed, next loop iteration will check if (contentStream == null)
                } else {
                    drawText("No components", fontRegular, 10);
                    currentY -= 20;
                }
                
                subFlowNum++;
            }
        } else {
            // Open stream for drawing
            if (contentStream == null) {
                contentStream = createContentStream(document, currentPage, PDPageContentStream.AppendMode.APPEND, true, true);
            }
            drawText("No sub-flows found", fontRegular, 12);
        }
        
        // Ensure stream is closed before exiting
        closeContentStream();
    }
    
    private void createDataWeaveFiles(AnalysisResult result) throws IOException {
        newPage();
        PDOutlineItem section = addBookmark("7. DataWeave Files", rootOutline, currentPage);
        
        currentY = PAGE_HEIGHT - 100;
        drawSectionHeader("7. DataWeave Files");
        
        // Collect all .dwl files
        List<ProjectNode> dwlFiles = new ArrayList<>();
        collectFilesByExtension(result.getProjectStructure(), ".dwl", dwlFiles);
        
        // Sort by name
        dwlFiles.sort(Comparator.comparing(ProjectNode::getName));
        
        if (!dwlFiles.isEmpty()) {
            // Open stream for drawing if needed
            if (contentStream == null) {
                contentStream = createContentStream(document, currentPage, PDPageContentStream.AppendMode.APPEND, true, true);
            }
            
            drawText("Total DataWeave Files: " + dwlFiles.size(), fontBold, 12);
            currentY -= 25;
            
            int fileNum = 1;
            for (ProjectNode dwlFile : dwlFiles) {
                // Ensure we have space for header
                checkPageSpace(60);
                
                // Ensure stream is open
                if (contentStream == null) {
                    contentStream = createContentStream(document, currentPage, PDPageContentStream.AppendMode.APPEND, true, true);
                }
                
                // File header
                String fileTitle = "7." + fileNum + ". " + dwlFile.getName();
                addBookmark(fileTitle, section, currentPage);
                drawText(fileTitle, fontBold, 11);
                currentY -= 15;
                
                // File path
                drawText("Path: " + dwlFile.getRelativePath(), fontRegular, 9);
                currentY -= 20;
                
                // Read and display file content
                try {
                    String content = java.nio.file.Files.readString(
                        java.nio.file.Paths.get(dwlFile.getAbsolutePath()), 
                        java.nio.charset.StandardCharsets.UTF_8
                    );
                    
                    // Standardize newlines and replace tabs (PDFBox font issue)
                    content = content.replace("\r\n", "\n").replace("\t", "    ");
                    
                    // Truncate if too long (similar to Word generator)
                    if (content.length() > 50000) {
                        content = content.substring(0, 50000) + "\n... (Content truncated)";
                    }
                    
                    // Draw content in a box
                    drawFileContent(content);
                    
                } catch (Exception e) {
                    // Ensure stream is open to report error
                    if (contentStream == null) {
                        contentStream = createContentStream(document, currentPage, PDPageContentStream.AppendMode.APPEND, true, true);
                    }
                    drawText("Error reading/displaying file: " + e.getMessage(), fontRegular, 9);
                    currentY -= 15;
                }
                
                currentY -= 20; // Space between files
                fileNum++;
            }
        } else {
            // Open stream for drawing
            if (contentStream == null) {
                contentStream = createContentStream(document, currentPage, PDPageContentStream.AppendMode.APPEND, true, true);
            }
            drawText("No DataWeave files found", fontRegular, 12);
        }
        
        closeContentStream();
    }
    
    private void createOtherResources(AnalysisResult result) throws IOException {
        newPage();
        addBookmark("8. Other Resources", rootOutline, currentPage);
        
        currentY = PAGE_HEIGHT - 100;
        drawSectionHeader("8. Other Resources");
        
        // Collect all other files (excluding common Mule files)
        List<ProjectNode> otherFiles = new ArrayList<>();
        collectOtherResourceFiles(result.getProjectStructure(), otherFiles);
        
        if (!otherFiles.isEmpty()) {
            // Create table with File Name and Relative Path
            List<String[]> rows = new ArrayList<>();
            for (ProjectNode file : otherFiles) {
                rows.add(new String[]{file.getName(), file.getRelativePath()});
            }
            
            String[][] data = rows.toArray(new String[0][]);
            drawTableWithPageBreaks(data, new String[]{"File Name", "Relative Path"}, new float[]{0.4f, 0.6f});
        } else {
            // Open stream for drawing
            if (contentStream == null) {
                contentStream = createContentStream(document, currentPage, PDPageContentStream.AppendMode.APPEND, true, true);
            }
            drawText("No other resource files found", fontRegular, 12);
        }
        
        closeContentStream();
    }
    
    private void createPomDetails(AnalysisResult result, PDOutlineItem parentBookmark) throws IOException {
        // Continuous section (subsection of Project Info)
        checkPageSpace(60);
        
        // Ensure stream is open since previous section closed it
        if (contentStream == null) {
            contentStream = createContentStream(document, document.getPage(document.getNumberOfPages() - 1), PDPageContentStream.AppendMode.APPEND, true, true);
        }
        
        addBookmark("1.2 POM Details", parentBookmark, currentPage);
        
        // drawSubsectionHeader uses currentY, so we assume checkPageSpace ensured safety
        drawSubsectionHeader("1.2 POM Details");
        
        // Find pom.xml in project structure
        ProjectNode pomNode = findFileByName(result.getProjectStructure(), "pom.xml");
        
        if (pomNode != null) {
            // Draw source path
            drawText("Path: " + pomNode.getRelativePath(), fontRegular, 9);
            currentY -= 15;
            
            // Extract PomInfo from metadata
            PomInfo pomInfo = (PomInfo) pomNode.getMetadata("pomInfo");
            
            if (pomInfo != null) {
                // Basic Information
                drawSubsectionHeader("1.2.1 Basic Information");
                createPomBasicInfoTable(pomInfo);
                currentY -= 20;
                
                // Properties
                if (pomInfo.getProperties() != null && !pomInfo.getProperties().isEmpty()) {
                    checkPageSpace(100);
                    drawSubsectionHeader("1.2.2 Properties");
                    createPomPropertiesTable(pomInfo.getProperties());
                    currentY -= 20;
                }
                
                // Plugins
                if (pomInfo.getPlugins() != null && !pomInfo.getPlugins().isEmpty()) {
                    checkPageSpace(100);
                    drawSubsectionHeader("1.2.3 Plugins");
                    createPomPluginsTable(pomInfo.getPlugins());
                    currentY -= 20;
                }
                
                // Dependencies
                if (pomInfo.getDependencies() != null && !pomInfo.getDependencies().isEmpty()) {
                    checkPageSpace(100);
                    drawSubsectionHeader("1.2.4 Dependencies");
                    createPomDependenciesTable(pomInfo.getDependencies());
                }
            } else {
                drawText("POM information not available", fontRegular, 12);
            }
        } else {
            drawText("pom.xml not found in project", fontRegular, 12);
        }

        // Add Mule-artifact.json
        ProjectNode muleArtifact = findFileByName(result.getProjectStructure(), "mule-artifact.json");
        if (muleArtifact != null) {
            checkPageSpace(150);
            addBookmark("1.3 Mule-artifact.json", parentBookmark, currentPage);
            drawSubsectionHeader("1.3 Mule-artifact.json");
            drawText("Path: " + muleArtifact.getRelativePath(), fontRegular, 9);
            currentY -= 15;
            
            try {
                String content = java.nio.file.Files.readString(
                    java.nio.file.Path.of(muleArtifact.getAbsolutePath()),
                    java.nio.charset.StandardCharsets.UTF_8
                );
                
                // Standardize newlines and replace tabs
                content = content.replace("\r\n", "\n").replace("\t", "    ");
                // Truncate if too long
                if (content.length() > 50000) {
                     content = content.substring(0, 50000) + "\n... (Content truncated)";
                }
                drawFileContent(content);
            } catch (Exception e) {
                 drawText("Error reading file: " + e.getMessage(), fontRegular, 9);
                 currentY -= 15;
            }
        }
        
        // Add Release-info.json
        ProjectNode releaseInfo = findFileByName(result.getProjectStructure(), "releaseinfo.json");
        if (releaseInfo != null) {
            checkPageSpace(150);
            addBookmark("1.4 Release-info.json", parentBookmark, currentPage);
            drawSubsectionHeader("1.4 Release-info.json");
            drawText("Path: " + releaseInfo.getRelativePath(), fontRegular, 9);
            currentY -= 15;
            
            try {
                String content = java.nio.file.Files.readString(
                    java.nio.file.Path.of(releaseInfo.getAbsolutePath()),
                    java.nio.charset.StandardCharsets.UTF_8
                );
                
                // Standardize newlines and replace tabs
                content = content.replace("\r\n", "\n").replace("\t", "    ");
                // Truncate if too long
                if (content.length() > 50000) {
                     content = content.substring(0, 50000) + "\n... (Content truncated)";
                }
                drawFileContent(content);
            } catch (Exception e) {
                 drawText("Error reading file: " + e.getMessage(), fontRegular, 9);
                 currentY -= 15;
            }
        }
        
        closeContentStream();
    }
    
    private void createReferences(AnalysisResult result) throws IOException {
        newPage();
        addBookmark("9. References", rootOutline, currentPage);
        
        currentY = PAGE_HEIGHT - 100;
        drawSectionHeader("9. References");
        
        drawParagraph("References section is placeholder for any reference details team requires. It can have team playbook reference details or any other information for project.");
        
        closeContentStream();
    }
    
    // Helper methods
    
    private PDPageContentStream createContentStream(PDDocument doc, PDPage page) throws IOException {
        PDPageContentStream cs = new PDPageContentStream(doc, page);
        trackedStreams.add(cs);
        return cs;
    }

    private PDPageContentStream createContentStream(PDDocument doc, PDPage page, PDPageContentStream.AppendMode mode, boolean compress, boolean reset) throws IOException {
        PDPageContentStream cs = new PDPageContentStream(doc, page, mode, compress, reset);
        trackedStreams.add(cs);
        return cs;
    }

    private void newPage() throws IOException {
        if (contentStream != null) {
            closeContentStream();
        }
        currentPage = new PDPage(PDRectangle.A4);
        document.addPage(currentPage);
        contentStream = createContentStream(document, currentPage);
        currentY = PAGE_HEIGHT - MARGIN;
    }
    
    private void closeContentStream() throws IOException {
        if (contentStream != null) {
            contentStream.close();
            contentStream = null;
        }
    }
    
    private void checkPageSpace(float requiredSpace) throws IOException {
        if (currentY - requiredSpace < MARGIN) {
            newPage();
            currentY = PAGE_HEIGHT - MARGIN;
        }
    }
    
    private String sanitizeForPdf(String text, int limit) {
        if (text == null) return "";
        
        // Replace tabs with spaces
        String sanitized = text.replace("\t", "    ");
        
        // Remove characters outside the Basic Multilingual Plane (BMP) - e.g. Emojis
        // PDFBox standard fonts usually choke on these
        sanitized = sanitized.replaceAll("[^\\u0000-\\uFFFF]", "?");
        
        // Remove Variation Selectors (U+FE00 - U+FE0F) which cause issues with standard fonts
        // Especially U+FE0F which is common in emoji sequences
        sanitized = sanitized.replaceAll("[\\uFE00-\\uFE0F]", "");
        
        // Check text limit
        if (sanitized.length() > limit) {
            sanitized = sanitized.substring(0, limit) + "... [TRUNCATED]";
        }
        
        return sanitized;
    }
    
    // ... other methods ...

    // Helper to parse color from properties "R, G, B"
    private java.awt.Color getColorConfig(String key, java.awt.Color defaultColor) {
        String val = config.getProperty(key);
        if (val == null || val.trim().isEmpty()) {
            return defaultColor;
        }
        try {
            String[] parts = val.split(",");
            if (parts.length == 3) {
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());
                return new java.awt.Color(r, g, b);
            }
        } catch (Exception e) {
            logger.warn("Invalid color format for key {}: {}. using default.", key, val);
        }
        return defaultColor;
    }
    
    private void drawText(String text, PDFont font, float fontSize) throws IOException {
        drawText(text, font, fontSize, 0);
    }
    
    private void drawText(String text, PDFont font, float fontSize, float xOffset) throws IOException {
        if (text == null) text = "";
        
        contentStream.beginText();
        contentStream.setFont(font, fontSize);
        contentStream.newLineAtOffset(MARGIN + xOffset, currentY);
        contentStream.showText(text);
        contentStream.endText();
        currentY -= fontSize + 5;
    }

    private void drawParagraph(String text) throws IOException {
        if (text == null || text.isEmpty()) return;
        
        float tableWidth = CONTENT_WIDTH;
        
        // Use EasyTable for automatic wrapping, similar to drawFileContent but without borders/background
        org.vandeseer.easytable.structure.Table.TableBuilder tableBuilder = 
            org.vandeseer.easytable.structure.Table.builder()
                .addColumnsOfWidth(tableWidth)
                .fontSize(12)
                .font(fontRegular)
                .borderColor(Color.WHITE) // Invisible border
                .wordBreak(true);
        
        tableBuilder.addRow(org.vandeseer.easytable.structure.Row.builder()
            .add(org.vandeseer.easytable.structure.cell.TextCell.builder()
                .text(text)
                .borderWidth(0)
                .padding(0)
                .build())
            .build());
            
        org.vandeseer.easytable.structure.Table table = tableBuilder.build();
        
        // Close current stream before using RepeatedHeaderTableDrawer
        closeContentStream();
        
        // Use RepeatedHeaderTableDrawer to handle pagination automatically
        org.vandeseer.easytable.RepeatedHeaderTableDrawer.builder()
            .table(table)
            .startX(MARGIN)
            .startY(currentY)
            .endY(MARGIN)
            .build()
            .draw(() -> document, () -> new PDPage(PDRectangle.A4), MARGIN);
            
        // Calculate new Y position
        float totalHeight = table.getHeight();
        float availableOnPage = currentY - MARGIN;
        
        if (totalHeight <= availableOnPage) {
            currentY -= totalHeight;
        } else {
             float remaining = totalHeight - availableOnPage;
             float pageContentHeight = PAGE_HEIGHT - (2 * MARGIN);
             
             float heightOnLastPage = remaining % pageContentHeight;
             if (heightOnLastPage == 0) heightOnLastPage = pageContentHeight;
             
             currentY = (PAGE_HEIGHT - MARGIN) - heightOnLastPage;
        }
        
        // Re-open stream on the last page
        currentPage = document.getPage(document.getNumberOfPages() - 1);
        contentStream = createContentStream(document, currentPage, PDPageContentStream.AppendMode.APPEND, true, true);
        
        currentY -= 15; // Space after paragraph
    }

    
    private void drawTreeBranch(int depth) throws IOException {
        if (depth <= 0) return;
        
        float indentPerLevel = 15f;
        float baseX = MARGIN + (depth - 1) * indentPerLevel;
        float branchLength = 10f;
        float verticalOffset = 5f;
        
        // Draw L-shaped branch: vertical line down, then horizontal line right
        contentStream.setLineWidth(0.5f);
        contentStream.moveTo(baseX, currentY + verticalOffset + 3);
        contentStream.lineTo(baseX, currentY + verticalOffset);
        contentStream.lineTo(baseX + branchLength, currentY + verticalOffset);
        contentStream.stroke();
    }
    
    private void drawCenteredText(String text, PDFont font, float fontSize, float y) throws IOException {
        if (text == null) text = "";
        
        float textWidth = font.getStringWidth(text) / 1000 * fontSize;
        float x = (PAGE_WIDTH - textWidth) / 2;
        
        contentStream.beginText();
        contentStream.setFont(font, fontSize);
        contentStream.newLineAtOffset(x, y);
        contentStream.showText(text);
        contentStream.endText();
    }
    
    private void drawSectionHeader(String text) throws IOException {
        contentStream.setNonStrokingColor(new Color(102, 51, 153));
        drawText(text, fontBold, 16);
        contentStream.setNonStrokingColor(Color.BLACK);
        currentY -= 10;
    }
    
    private void drawFileContent(String content) throws IOException {
        // Use EasyTable to simulate a text box with background
        
        float tableWidth = CONTENT_WIDTH;
        
        org.vandeseer.easytable.structure.Table.TableBuilder tableBuilder = 
            org.vandeseer.easytable.structure.Table.builder()
                .addColumnsOfWidth(tableWidth)
                .fontSize(9)
                .font(fontMono) // Use Courier for code
                .borderColor(java.awt.Color.LIGHT_GRAY)
                .backgroundColor(new java.awt.Color(250, 250, 250)) // Light gray background
                .wordBreak(true);
        
        tableBuilder.addRow(org.vandeseer.easytable.structure.Row.builder()
            .add(org.vandeseer.easytable.structure.cell.TextCell.builder()
                .text(content)
                .padding(Float.parseFloat(config.getProperty("pdf.table.cell.padding", "10.0")))
                .borderWidth(0.5f)
                .build())
            .build());
            
        org.vandeseer.easytable.structure.Table table = tableBuilder.build();
        
        // Close current stream before using RepeatedHeaderTableDrawer
        closeContentStream();
        
        // Use RepeatedHeaderTableDrawer to handle long content across pages
        org.vandeseer.easytable.RepeatedHeaderTableDrawer.builder()
            .table(table)
            .startX(MARGIN)
            .startY(currentY)
            .endY(MARGIN)
            .build()
            .draw(() -> document, () -> new PDPage(PDRectangle.A4), MARGIN);
            
        // Calculate new Y position to allow continuous writing
        float totalHeight = table.getHeight();
        float availableOnPage = currentY - MARGIN;
        
        if (totalHeight <= availableOnPage) {
            currentY -= totalHeight;
        } else {
             // Calculate height on the last page
             float remaining = totalHeight - availableOnPage;
             float pageContentHeight = PAGE_HEIGHT - (2 * MARGIN);
             
             // Since no repeated headers (content box is one cell)
             float heightOnLastPage = remaining % pageContentHeight;
             // Handle exact page fit
             if (heightOnLastPage == 0) heightOnLastPage = pageContentHeight;
             
             currentY = (PAGE_HEIGHT - MARGIN) - heightOnLastPage;
        }
        
        // Add small padding after box
        currentY -= 10;
        
        // Re-open stream on the last page (RepeatedHeaderTableDrawer closes it)
        currentPage = document.getPage(document.getNumberOfPages() - 1);
        contentStream = createContentStream(document, currentPage, PDPageContentStream.AppendMode.APPEND, true, true);
    }

    // For small tables that fit on one page (e.g., POM Details)
    private void drawTable(String[][] data, String[] headers, float[] columnWidths) throws IOException {
        // Use easytable library for automatic text wrapping
        float tableWidth = CONTENT_WIDTH;
        
        // Calculate column widths in points
        float[] colWidthsInPoints = new float[columnWidths.length];
        for (int i = 0; i < columnWidths.length; i++) {
            colWidthsInPoints[i] = tableWidth * columnWidths[i];
        }
        
        // Create table builder
        org.vandeseer.easytable.structure.Table.TableBuilder tableBuilder = 
            org.vandeseer.easytable.structure.Table.builder()
                .addColumnsOfWidth(colWidthsInPoints)
                .fontSize(9)
                .font(fontRegular)
                .borderColor(java.awt.Color.BLACK);
        
        // Add header row
        org.vandeseer.easytable.structure.Row.RowBuilder headerRowBuilder = 
            org.vandeseer.easytable.structure.Row.builder();
            
        java.awt.Color headerBgColor = getColorConfig("pdf.table.header.background.color", new java.awt.Color(230, 230, 250)); // Old Purple: 102, 51, 153
        java.awt.Color headerTextColor = getColorConfig("pdf.table.header.text.color", java.awt.Color.BLACK);
        
        for (String header : headers) {
            headerRowBuilder.add(org.vandeseer.easytable.structure.cell.TextCell.builder()
                .text(header)
                .backgroundColor(headerBgColor)
                .textColor(headerTextColor)
                .font(fontBold)
                .fontSize(10)
                .horizontalAlignment(org.vandeseer.easytable.settings.HorizontalAlignment.LEFT)
                .borderWidth(0.5f)
                .build());
        }
        tableBuilder.addRow(headerRowBuilder.build());
        
        // Add data rows - text wraps automatically!
        for (String[] row : data) {
            org.vandeseer.easytable.structure.Row.RowBuilder rowBuilder = 
                org.vandeseer.easytable.structure.Row.builder();
            
            for (int i = 0; i < row.length && i < headers.length; i++) {
                String cellText = row[i] != null ? row[i] : "";
                rowBuilder.add(org.vandeseer.easytable.structure.cell.TextCell.builder()
                    .text(cellText)  // NO TRUNCATION - automatic wrapping!
                    .horizontalAlignment(org.vandeseer.easytable.settings.HorizontalAlignment.LEFT)
                    .borderWidth(0.5f)
                    .build());
            }
            tableBuilder.addRow(rowBuilder.build());
        }
        
        // Build table
        org.vandeseer.easytable.structure.Table table = tableBuilder.build();
        
        // Check if we need a new page
        checkPageSpace(table.getHeight() + 20);
        
        // Draw table using regular TableDrawer (stays on same page)
        org.vandeseer.easytable.TableDrawer.builder()
            .contentStream(contentStream)
            .table(table)
            .startX(MARGIN)
            .startY(currentY)
            .build()
            .draw();
        
        // Update currentY
        currentY -= table.getHeight() + 10;
    }
    
    // For large tables that may span multiple pages (e.g., DataWeave Files, Other Resources)
    private void drawTableWithPageBreaks(String[][] data, String[] headers, float[] columnWidths) throws IOException {
        // Use easytable library for automatic text wrapping and page breaks
        float tableWidth = CONTENT_WIDTH;
        
        // Calculate column widths in points
        float[] colWidthsInPoints = new float[columnWidths.length];
        for (int i = 0; i < columnWidths.length; i++) {
            colWidthsInPoints[i] = tableWidth * columnWidths[i];
        }
        
        // Create table builder
        org.vandeseer.easytable.structure.Table.TableBuilder tableBuilder = 
            org.vandeseer.easytable.structure.Table.builder()
                .addColumnsOfWidth(colWidthsInPoints)
                .fontSize(9)
                .font(fontRegular)
                .borderColor(java.awt.Color.BLACK);
        
        // Add header row
        org.vandeseer.easytable.structure.Row.RowBuilder headerRowBuilder = 
            org.vandeseer.easytable.structure.Row.builder();
            
        java.awt.Color headerBgColor = getColorConfig("pdf.table.header.background.color", new java.awt.Color(230, 230, 250)); // Old Purple: 102, 51, 153
        java.awt.Color headerTextColor = getColorConfig("pdf.table.header.text.color", java.awt.Color.BLACK);
        
        for (String header : headers) {
            headerRowBuilder.add(org.vandeseer.easytable.structure.cell.TextCell.builder()
                .text(header)
                .backgroundColor(headerBgColor)
                .textColor(headerTextColor)
                .font(fontBold)
                .fontSize(10)
                .horizontalAlignment(org.vandeseer.easytable.settings.HorizontalAlignment.LEFT)
                .borderWidth(0.5f)
                .build());
        }
        tableBuilder.addRow(headerRowBuilder.build());
        
        // Add data rows - text wraps automatically!
        for (String[] row : data) {
            org.vandeseer.easytable.structure.Row.RowBuilder rowBuilder = 
                org.vandeseer.easytable.structure.Row.builder();
            
            for (int i = 0; i < row.length && i < headers.length; i++) {
                String cellText = row[i] != null ? row[i] : "";
                rowBuilder.add(org.vandeseer.easytable.structure.cell.TextCell.builder()
                    .text(cellText)  // NO TRUNCATION - automatic wrapping!
                    .horizontalAlignment(org.vandeseer.easytable.settings.HorizontalAlignment.LEFT)
                    .borderWidth(0.5f)
                    .build());
            }
            tableBuilder.addRow(rowBuilder.build());
        }
        
        // Build table
        org.vandeseer.easytable.structure.Table table = tableBuilder.build();
        
        // Close current stream before using RepeatedHeaderTableDrawer
        closeContentStream();
        
        // Use RepeatedHeaderTableDrawer for automatic page breaks!
        // This will automatically create new pages and repeat headers
        org.vandeseer.easytable.RepeatedHeaderTableDrawer.builder()
            .table(table)
            .startX(MARGIN)
            .startY(currentY)
            .endY(MARGIN)  // Bottom margin
            .build()
            .draw(() -> document, () -> new PDPage(PDRectangle.A4), MARGIN);
        
        // RepeatedHeaderTableDrawer handles page breaks autonomously and leaves the stream closed usually
        // We do NOT want to force a new page here because the NEXT section will likely start with newPage().
        // If we force one here, and the next section forces one, we get a blank page.
        
        // Just ensure stream is closed and let the next component decide.
        if (contentStream != null) {
            closeContentStream();
        }
        
        // Reset currentY to indicate we are done with this page (or unknown state)
        // But better yet, just leave it. The next update will likely be a new section requiring new page.
    }
    
    private PDOutlineItem addBookmark(String title, PDOutlineItem parent, PDPage page, int level) {
        PDOutlineItem bookmark = new PDOutlineItem();
        bookmark.setTitle(title);
        
        PDPageFitWidthDestination dest = new PDPageFitWidthDestination();
        dest.setPage(page);
        dest.setTop((int) PAGE_HEIGHT);
        bookmark.setDestination(dest);
        
        parent.addLast(bookmark);
        // bookmark.openNode(); // Removed to avoid cluttering outline expanded state
        
        // Add to TOC entries
        tocEntries.add(new TOCEntry(title, page, level));
        
        return bookmark;
    }
    
    // Legacy overload for compatibility if needed (defaults to old logic)
    private PDOutlineItem addBookmark(String title, PDOutlineItem parent, PDPage page) {
        int level = (parent == rootOutline) ? 1 : 2;
        return addBookmark(title, parent, page, level);
    }
    
    private String generateFileName(ProjectInfo projectInfo) {
        String timestamp = ZonedDateTime.now().format(
            DateTimeFormatter.ofPattern(config.getProperty("document.output.timestamp.format", "yyyyMMdd_HHmmss"))
        );
        return projectInfo.getProjectName() + "_Analysis_" + timestamp + ".pdf";
    }
    
    // Helper method to find a file by name in project structure
    private ProjectNode findFileByName(ProjectNode root, String fileName) {
        if (root == null) return null;
        if (root.getType() == NodeType.FILE && root.getName().equals(fileName)) {
            return root;
        }
        if (root.getType() == NodeType.DIRECTORY && root.getChildren() != null) {
            for (ProjectNode child : root.getChildren()) {
                ProjectNode found = findFileByName(child, fileName);
                if (found != null) return found;
            }
        }
        return null;
    }
    
    // Helper method to draw subsection headers
    private void drawSubsectionHeader(String text) throws IOException {
        checkPageSpace(30);
        drawText(text, fontBold, 11);
        currentY -= 15;
    }
    
    // Create POM Basic Information table
    private void createPomBasicInfoTable(PomInfo pomInfo) throws IOException {
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        
        if (pomInfo.getModelVersion() != null) {
            rows.add(new String[]{"Model Version", pomInfo.getModelVersion()});
        }
        if (pomInfo.getGroupId() != null) {
            rows.add(new String[]{"Group ID", pomInfo.getGroupId()});
        }
        if (pomInfo.getArtifactId() != null) {
            rows.add(new String[]{"Artifact ID", pomInfo.getArtifactId()});
        }
        if (pomInfo.getVersion() != null) {
            rows.add(new String[]{"Version", pomInfo.getVersion()});
        }
        if (pomInfo.getPackaging() != null) {
            rows.add(new String[]{"Packaging", pomInfo.getPackaging()});
        }
        if (pomInfo.getName() != null) {
            rows.add(new String[]{"Name", pomInfo.getName()});
        }
        if (pomInfo.getDescription() != null) {
            rows.add(new String[]{"Description", pomInfo.getDescription()});
        }
        
        // Parent information
        if (pomInfo.getParent() != null) {
            PomInfo.ParentInfo parent = pomInfo.getParent();
            rows.add(new String[]{"Parent Group ID", parent.getGroupId()});
            rows.add(new String[]{"Parent Artifact ID", parent.getArtifactId()});
            rows.add(new String[]{"Parent Version", parent.getVersion()});
            if (parent.getRelativePath() != null) {
                rows.add(new String[]{"Parent Relative Path", parent.getRelativePath()});
            }
        }
        
        if (!rows.isEmpty()) {
            String[][] data = rows.toArray(new String[0][]);
            drawTable(data, new String[]{"Property", "Value"}, new float[]{0.3f, 0.7f});
        }
    }
    
    // Create POM Properties table
    private void createPomPropertiesTable(java.util.Map<String, String> properties) throws IOException {
        if (properties == null || properties.isEmpty()) return;
        
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, String> entry : properties.entrySet()) {
            rows.add(new String[]{entry.getKey(), entry.getValue()});
        }
        
        String[][] data = rows.toArray(new String[0][]);
        drawTable(data, new String[]{"Key", "Value"}, new float[]{0.4f, 0.6f});
    }
    
    // Create POM Plugins table
    private void createPomPluginsTable(java.util.List<PomInfo.PluginInfo> plugins) throws IOException {
        if (plugins == null || plugins.isEmpty()) return;
        
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        for (PomInfo.PluginInfo plugin : plugins) {
            rows.add(new String[]{
                plugin.getGroupId() != null ? plugin.getGroupId() : "",
                plugin.getArtifactId() != null ? plugin.getArtifactId() : "",
                plugin.getVersion() != null ? plugin.getVersion() : ""
            });
        }
        
        String[][] data = rows.toArray(new String[0][]);
        drawTable(data, new String[]{"Group ID", "Artifact ID", "Version"}, new float[]{0.4f, 0.4f, 0.2f});
    }
    
    // Create POM Dependencies table
    private void createPomDependenciesTable(java.util.List<PomInfo.DependencyInfo> dependencies) throws IOException {
        if (dependencies == null || dependencies.isEmpty()) return;
        
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        for (PomInfo.DependencyInfo dep : dependencies) {
            rows.add(new String[]{
                dep.getGroupId() != null ? dep.getGroupId() : "",
                dep.getArtifactId() != null ? dep.getArtifactId() : "",
                dep.getVersion() != null ? dep.getVersion() : ""
            });
        }
        
        String[][] data = rows.toArray(new String[0][]);
        drawTable(data, new String[]{"Group ID", "Artifact ID", "Version"}, new float[]{0.4f, 0.4f, 0.2f});
    }
    
    // Helper method to collect files by extension
    private void collectFilesByExtension(ProjectNode node, String extension, List<ProjectNode> result) {
        if (node == null) return;
        
        if (node.getType() == NodeType.FILE && node.getName().endsWith(extension)) {
            result.add(node);
        }
        
        if (node.getType() == NodeType.DIRECTORY && node.getChildren() != null) {
            for (ProjectNode child : node.getChildren()) {
                collectFilesByExtension(child, extension, result);
            }
        }
    }
    
    // Helper method to collect other resource files (excluding common Mule files)
    private void collectOtherResourceFiles(ProjectNode node, List<ProjectNode> result) {
        if (node == null) return;
        
        if (node.getType() == NodeType.FILE) {
            String name = node.getName();
            // Exclude common Mule project files
            if (!name.equals("pom.xml") && 
                !name.endsWith(".xml") && 
                !name.endsWith(".dwl") && 
                !name.endsWith(".yaml") && 
                !name.endsWith(".yml") && 
                !name.endsWith(".properties") && 
                !name.endsWith(".json") &&
                !name.startsWith(".")) {
                result.add(node);
            }
        }
        
        if (node.getType() == NodeType.DIRECTORY && node.getChildren() != null) {
            for (ProjectNode child : node.getChildren()) {
                collectOtherResourceFiles(child, result);
            }
        }
    }
    private void addFooters() throws IOException {
        String footerText = config.getProperty("document.footer.text", "RaksAnalyzer ApiGuard Tool");
        int totalPages = document.getNumberOfPages();
        
        // Include cover page (start from index 0)
        for (int i = 0; i < totalPages; i++) {
            PDPage page = document.getPage(i);
            
            try (PDPageContentStream cs = createContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                // Settings
                float footerY = 20;
                cs.setNonStrokingColor(java.awt.Color.GRAY);
                
                // 1. Draw Footer Text (Centered)
                float textWidth = fontRegular.getStringWidth(footerText) / 1000 * 9;
                float textX = (page.getMediaBox().getWidth() - textWidth) / 2;
                
                cs.beginText();
                cs.setFont(fontRegular, 9);
                cs.newLineAtOffset(textX, footerY);
                cs.showText(footerText);
                cs.endText();
                
                // 2. Draw Page Number (Right): "Page X of Y"
                String pageNumText = "Page " + (i + 1) + " of " + totalPages;
                float numWidth = fontRegular.getStringWidth(pageNumText) / 1000 * 9;
                float numX = page.getMediaBox().getWidth() - 30 - numWidth; // 30 margin
                
                cs.beginText();
                cs.newLineAtOffset(numX, footerY);
                cs.showText(pageNumText);
                cs.endText();
            }
        }
    }
}
