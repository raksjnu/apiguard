package com.raks.aegis.util;

public class GenerateRuleGuide {
    public static void main(String[] args) {
        String outputDir = args.length \u003e 0 ? args[0] : "temp";
        System.out.println("Generating rule_guide.html to: " + outputDir);
        com.raks.aegis.engine.ReportGenerator.generateRuleGuide(outputDir);
        System.out.println("Rule guide generated successfully!");
    }
}
