
import com.raks.aegis.engine.ReportGenerator;
import java.nio.file.Paths;

public class GenRules {
    public static void main(String[] args) {
        ReportGenerator.generateRuleGuide(Paths.get("target"));
        System.out.println("Generated rule guide in target");
    }
}
