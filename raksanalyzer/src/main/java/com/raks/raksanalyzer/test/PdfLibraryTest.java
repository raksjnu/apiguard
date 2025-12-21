package com.raks.raksanalyzer.test;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.vandeseer.easytable.RepeatedHeaderTableDrawer;
import org.vandeseer.easytable.settings.HorizontalAlignment;
import org.vandeseer.easytable.structure.Row;
import org.vandeseer.easytable.structure.Table;
import org.vandeseer.easytable.structure.cell.TextCell;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class PdfLibraryTest {

    public static void main(String[] args) {
        System.out.println("Starting PDF Library Test...");
        
        String inputPath = "testdata/test.properties";
        String outputPath = "test_library_pagination.pdf";
        
        try {
            // 1. Read Properties
            List<String[]> properties = new ArrayList<>();
            File file = new File(inputPath);
            if (!file.exists()) {
                System.err.println("File not found: " + file.getAbsolutePath());
                // Try absolute path fallback if running from different cwd
                file = new File("C:/raks/apiguard/raksanalyzer/testdata/test.properties");
            }
            
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                int count = 1;
                while ((line = br.readLine()) != null) {
                    if (line.contains("=")) {
                        String[] parts = line.split("=", 2);
                        properties.add(new String[]{parts[0], parts.length > 1 ? parts[1] : ""});
                    }
                    count++;
                }
            }
            System.out.println("Loaded " + properties.size() + " properties.");

            // 2. Setup PDF
            PDDocument document = new PDDocument();
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            
            // 3. Build Table
            Table.TableBuilder tableBuilder = Table.builder()
                    .addColumnsOfWidth(150, 350) // 500pt total
                    .fontSize(10)
                    .font(font)
                    .borderColor(Color.BLACK);

            // Header
            tableBuilder.addRow(Row.builder()
                    .add(TextCell.builder().text("Key").font(fontBold).borderWidth(1).build())
                    .add(TextCell.builder().text("Value").font(fontBold).borderWidth(1).build())
                    .build());

            // Rows
            for (String[] prop : properties) {
                tableBuilder.addRow(Row.builder()
                        .add(TextCell.builder().text(prop[0]).borderWidth(1).build())
                        .add(TextCell.builder()
                                .text(prop[1])
                                .borderWidth(1)
                                .wordBreak(true) // Enable text wrapping
                                .build())
                        .build());
            }
            
            Table table = tableBuilder.build();

            // 4. Draw using RepeatedHeaderTableDrawer (Standard Library Logic)
            RepeatedHeaderTableDrawer.builder()
                    .table(table)
                    .startX(50)
                    .startY(PDRectangle.A4.getHeight() - 50)
                    .endY(50) // Stop at bottom margin
                    .build()
                    .draw(() -> document, () -> new PDPage(PDRectangle.A4), 50f);

            // 5. Save
            document.save(outputPath);
            document.close();
            
            System.out.println("PDF generated successfully: " + outputPath);
            
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("PDF Generation FAILED: " + e.getMessage());
        }
    }
}
