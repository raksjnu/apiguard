package com.raks.raksanalyzer;
import com.raks.raksanalyzer.ApplicationArguments;
public class ReproArgs {
    public static void main(String[] args) {
        System.out.println("Testing Argument Parsing...");
        testArgs(new String[]{"--config", "testdata/1.xml"}, "testdata/1.xml");
        testArgs(new String[]{"--config=testdata/1.xml"}, "testdata/1.xml");
        testArgs(new String[]{"--config=\"testdata/1.xml\""}, "testdata/1.xml");
        testArgs(new String[]{"--config='testdata/1.xml'"}, "testdata/1.xml");
    }
    private static void testArgs(String[] args, String expected) {
        ApplicationArguments appArgs = ApplicationArguments.parse(args);
        String actual = appArgs.getCustomConfigPath().orElse("NULL");
        System.out.print("Args: [");
        for(String s : args) System.out.print(s + " ");
        System.out.println("]");
        if (expected.equals(actual)) {
            System.out.println("  PASS: Got " + actual);
        } else {
            System.out.println("  FAIL: Expected " + expected + ", Got " + actual);
        }
    }
}
