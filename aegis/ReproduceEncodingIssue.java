
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import com.raks.aegis.AegisMain;

public class ReproduceEncodingIssue {
    public static void main(String[] args) {
        try {
            // Create a YAML file with Windows-1252 encoding containing a "smart quote"
            // byte 0x92 is right single quotation mark in Windows-1252
            // byte 0x92 is NOT valid in UTF-8
            String filename = "test_windows1252.yaml";
            
            // "name: 'Test’s Rule'" where ’ is 0x92
            byte[] content = new byte[] {
                'r', 'u', 'l', 'e', 's', ':', '\n',
                ' ', ' ', '-', ' ', 'i', 'd', ':', ' ', '"', 'T', 'E', 'S', 'T', '-', '0', '0', '1', '"', '\n',
                ' ', ' ', ' ', ' ', 'n', 'a', 'm', 'e', ':', ' ', '"', 'T', 'e', 's', 't', (byte)0x92, 's', ' ', 'R', 'u', 'l', 'e', '"', '\n', // 0x92 here
                ' ', ' ', ' ', ' ', 'e', 'n', 'a', 'b', 'l', 'e', 'd', ':', ' ', 't', 'r', 'u', 'e', '\n'
            };

            try (FileOutputStream fos = new FileOutputStream(filename)) {
                fos.write(content);
            }
            
            System.out.println("Created " + filename + " with Windows-1252 specific char (0x92)");

            System.out.println("\n--- SCENARIO 1: BEFORE FIX (Simulating old code) ---");
            try {
                // This mimics the old code: simply trying to read as UTF-8
                java.nio.file.Files.newBufferedReader(java.nio.file.Paths.get(filename), java.nio.charset.StandardCharsets.UTF_8).read();
                System.out.println("FAILURE: Old code unexpectedly succeeded? This shouldn't happen for 0x92 byte.");
            } catch (Exception e) {
                System.out.println("SUCCESS: Old code crashed as expected!");
                System.out.println("Exception: " + e); // Should be MalformedInputException
            }

            System.out.println("\n--- SCENARIO 2: AFTER FIX (New AegisMain logic) ---");
            // Now run AegisMain which has the retry logic
            try {
                System.setProperty("aegis.test.mode", "true"); 
                
                String[] argsAegis = {"-p", ".", "-c", filename};
                int result = AegisMain.execute(argsAegis);
                
                System.out.println("Aegis execution returned code: " + result);
                // Even if it returns 1 due to missing 'config' section, getting here means it parsed the YAML!

            } catch (Exception e) {
                e.printStackTrace();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
