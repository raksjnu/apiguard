package com.org.propertyutil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.*;
public class GlobalVariableFileStyle {
	public static void gvfilestyling(String File,Integer PNumberOfEnv) {
		try {
			InputStream inp = new FileInputStream(File);
			XSSFWorkbook resultWorkbook = new XSSFWorkbook(inp);
			XSSFSheet resultSheet = resultWorkbook.getSheet("GlobalVariables");
			XSSFCellStyle style = resultWorkbook.createCellStyle();
            Font font = resultWorkbook.createFont();
            font.setColor(IndexedColors.WHITE.getIndex());
            font.setBold(true);
            style.setFont(font);
            style.setFillForegroundColor(IndexedColors.GREEN.getIndex());
			style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			for (int k = 0; k < PNumberOfEnv + 1; k++) {
				resultSheet.getRow(0).getCell(k).setCellStyle(style);
				}
			OutputStream outputStream = new FileOutputStream(File);
	        resultWorkbook.write(outputStream);
	        resultWorkbook.close();
	        System.out.println("Report header styling done");
	        inp.close();
	        outputStream.close();
		} 
		 catch (Exception e) {
				e.printStackTrace();
			}
	}
}
