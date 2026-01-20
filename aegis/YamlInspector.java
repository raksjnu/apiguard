
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

public class YamlInspector {
    public static void main(String[] args) {
        String filePath = "C:\\raks\\temp\\truist\\truist_aegis_RK_v1.0.yaml";
        if (args.length > 0) filePath = args[0];

        System.out.println("Inspecting: " + filePath);
        try {
            // Read raw bytes to detect encoding issues
            // But for inspection, let's read as UTF-8 first to find chars
            byte[] bytes = Files.readAllBytes(Paths.get(filePath));
            String content = new String(bytes, StandardCharsets.UTF_8); // We assume it's meant to be UTF-8 for analysis

            String[] lines = content.split("\r?\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                int lineNum = i + 1;

                // 1. Check for Tabs
                if (line.contains("\t")) {
                    System.out.println("[Line " + lineNum + "] WARNING: Tab character detected. YAML forbids tabs for indentation.");
                }

                // 2. Check for NBSP (0xA0) or other weird spaces
                for (char c : line.toCharArray()) {
                    if (c == '\u00A0') {
                        System.out.println("[Line " + lineNum + "] CRITICAL: Non-Breaking Space (NBSP, 0xA0) detected. This breaks YAML parsing.");
                    }
                }

                // 3. Check for "Files Checked:" (The error reported by user)
                if (line.contains("Files Checked:")) {
                    System.out.println("[Line " + lineNum + "] INFO: Found 'Files Checked:' string. Context: " + line.trim());
                    // Check if it looks like an unquoted colon issue
                    // If proper YAML key/value, fine. If inside a multiline string without quotes, fine?
                    // "Files Checked: {CHECKED_FILES}" inside a flow scalar might be an issue.
                }

                // 4. Check for Smart Quotes
                if (line.contains("\u2018") || line.contains("\u2019") || line.contains("\u201C") || line.contains("\u201D")) {
                     System.out.println("[Line " + lineNum + "] WARNING: Smart quotes detected. Ensure encoding handles them or replace with straight quotes.");
                }
            }
            System.out.println("Inspection Complete.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
