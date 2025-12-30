package com.raks.raksanalyzer.test;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.vandeseer.easytable.RepeatedHeaderTableDrawer;
import org.vandeseer.easytable.structure.Row;
import org.vandeseer.easytable.structure.Table;
import org.vandeseer.easytable.structure.cell.TextCell;
import java.awt.Color;
import java.io.IOException;
public class PdfIntegrationTest {
    public static void main(String[] args) {
        System.out.println("Starting PDF Integration Test (Text + Table)...");
        String outputPath = "test_integration.pdf";
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            float startY = PDRectangle.A4.getHeight() - 50;
            System.out.println("Step 1: Writing Header Text...");
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                contentStream.newLineAtOffset(50, startY);
                contentStream.showText("Global Configuration Header (Manual Text)");
                contentStream.endText();
            } 
            float tableStartY = startY - 30; 
            System.out.println("Step 2: Building Table...");
            Table.TableBuilder tableBuilder = Table.builder()
                    .addColumnsOfWidth(200, 200)
                    .fontSize(10)
                    .font(new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            tableBuilder.addRow(Row.builder()
                    .add(TextCell.builder().text("Header 1").borderWidth(1).build())
                    .add(TextCell.builder().text("Header 2").borderWidth(1).build())
                    .build());
            for (int i = 0; i < 50; i++) { 
                tableBuilder.addRow(Row.builder()
                        .add(TextCell.builder().text("Key " + i).borderWidth(1).build())
                        .add(TextCell.builder().text("Value " + i).borderWidth(1).build())
                        .build());
            }
            System.out.println("Step 3: Drawing Table...");
            RepeatedHeaderTableDrawer.builder()
                    .table(tableBuilder.build())
                    .startX(50)
                    .startY(tableStartY) 
                    .endY(50)
                    .build()
                    .draw(() -> document, () -> new PDPage(PDRectangle.A4), 50f);
            document.save(outputPath);
            System.out.println("PDF generated successfully: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Test FAILED: " + e.getMessage());
        }
    }
}
