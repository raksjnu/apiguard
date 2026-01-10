package com.raks.filesync;

import com.raks.filesync.cli.CliInterface;
import com.raks.filesync.gui.MainWindow;

/**
 * Main entry point for FileSync Tool
 */
public class FileSyncTool {
    
    public static void main(String[] args) {
        System.out.println("FileSync Tool v1.0");
        System.out.println("==================");
        System.out.println();
        
        // If no arguments or "gui" argument, launch GUI
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("gui"))) {
            System.out.println("Launching GUI...");
            MainWindow.launch();
        } else {
            // Run CLI
            CliInterface cli = new CliInterface();
            cli.run(args);
        }
    }
}
