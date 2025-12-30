package com.raks.muleguard.license;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
public class TrialLicenseManager {
    private static final boolean TRIAL_MODE = false;
    private static final String LICENSE_FILE = ".muleguard-trial.dat";
    private static final String EXPIRY_DATE = "2026-02-01"; 
    private static final int MAX_EXECUTIONS = 10;
    private final Path licenseFilePath;
    private Properties licenseData;
    public TrialLicenseManager() {
        String userHome = System.getProperty("user.home");
        this.licenseFilePath = Paths.get(userHome, LICENSE_FILE);
        this.licenseData = new Properties();
        loadLicenseData();
    }
    public boolean validateTrial() throws LicenseException {
        if (!TRIAL_MODE) {
            return true;
        }
        LocalDate expiryDate = LocalDate.parse(EXPIRY_DATE, DateTimeFormatter.ISO_LOCAL_DATE);
        LocalDate today = LocalDate.now();
        if (today.isAfter(expiryDate)) {
            throw new LicenseException(
                "Trial period has expired on " + EXPIRY_DATE + ". " +
                "Please contact raksjnu@gmail.com for a full license."
            );
        }
        int executionCount = getExecutionCount();
        if (executionCount >= MAX_EXECUTIONS) {
            throw new LicenseException(
                "Trial execution limit (" + MAX_EXECUTIONS + " runs) has been reached. " +
                "Please contact raksjnu@gmail.com for a full license."
            );
        }
        incrementExecutionCount();
        long daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(today, expiryDate);
        int runsRemaining = MAX_EXECUTIONS - executionCount - 1;
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║           MuleGuard - TRIAL VERSION                        ║");
        System.out.println("╠════════════════════════════════════════════════════════════╣");
        System.out.println("║  Trial expires: " + EXPIRY_DATE + " (" + daysRemaining + " days remaining)        ║");
        System.out.println("║  Runs remaining: " + runsRemaining + " / " + MAX_EXECUTIONS + "                                  ║");
        System.out.println("║  Contact: raksjnu@gmail.com for full license              ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();
        return true;
    }
    private void loadLicenseData() {
        if (Files.exists(licenseFilePath)) {
            try (InputStream input = Files.newInputStream(licenseFilePath)) {
                licenseData.load(input);
            } catch (IOException e) {
                initializeLicenseData();
            }
        } else {
            initializeLicenseData();
        }
    }
    private void initializeLicenseData() {
        licenseData.setProperty("execution.count", "0");
        licenseData.setProperty("first.run", LocalDate.now().toString());
        saveLicenseData();
    }
    private void saveLicenseData() {
        try (OutputStream output = Files.newOutputStream(licenseFilePath)) {
            licenseData.store(output, "MuleGuard Trial License Data - DO NOT MODIFY");
        } catch (IOException e) {
            System.err.println("Warning: Could not save license data: " + e.getMessage());
        }
    }
    private int getExecutionCount() {
        String count = licenseData.getProperty("execution.count", "0");
        try {
            return Integer.parseInt(count);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    private void incrementExecutionCount() {
        int count = getExecutionCount();
        licenseData.setProperty("execution.count", String.valueOf(count + 1));
        licenseData.setProperty("last.run", LocalDate.now().toString());
        saveLicenseData();
    }
    public static class LicenseException extends Exception {
        public LicenseException(String message) {
            super(message);
        }
    }
}
