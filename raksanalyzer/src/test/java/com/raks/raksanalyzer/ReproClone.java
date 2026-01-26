package com.raks.raksanalyzer;

import com.raks.raksanalyzer.provider.GitHubProvider;
import java.io.File;

public class ReproClone {
    public static void main(String[] args) {
        try {
            System.out.println("Starting clone test...");
            GitHubProvider provider = new GitHubProvider("dummy-token-if-needed"); 
            // We use a public repo to avoid auth issues for this test, or prompt user.
            // Using the repo user mentioned: https://github.com/raksjnu/aegisapptest.git
            String repo = "https://github.com/raksjnu/aegisapptest.git";
            File dest = new File("temp_repro_clone");
            
            System.out.println("Cloning " + repo + " to " + dest.getAbsolutePath());
            if (dest.exists()) {
                deleteRecursively(dest);
            }
            
            provider.cloneRepository(repo, dest, "main");
            System.out.println("Clone SUCCESS!");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Clone FAILED: " + e.getMessage());
        }
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            for (File c : file.listFiles()) deleteRecursively(c);
        }
        file.delete();
    }
}
