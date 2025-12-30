package com.tibcodocgen;
import java.io.IOException;
import java.util.regex.Matcher;
public class FileSeparator {
	public static String FileSeperatorMethod(String directoryPath,String reportPath) throws IOException {
		String separator = System.getProperty("file.separator");
		System.out.println(separator);
		directoryPath = directoryPath.replaceAll("/", Matcher.quoteReplacement(separator));
		reportPath = reportPath.replaceAll("/", Matcher.quoteReplacement(separator));
		return directoryPath.toString()+","+reportPath.toString();
	}
}
