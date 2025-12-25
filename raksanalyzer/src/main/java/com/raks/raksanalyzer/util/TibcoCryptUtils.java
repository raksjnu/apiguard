package com.raks.raksanalyzer.util;

/**
 * Utility for handling TIBCO BW 5.x obfuscated passwords (#!...).
 * Note: Actual decryption requires TIBCO runtime libraries and is disabled by default.
 */
public class TibcoCryptUtils {

    /**
     * Check if value is encrypted (starts with #!).
     * 
     * @param encryptedValue The value to check
     * @return Original value (decryption is disabled)
     */
    public static String decrypt(String encryptedValue) {
        // Decryption is disabled - return original value
        return encryptedValue;
    }
    
    /**
     * Check if value is encrypted.
     */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith("#!");
    }
}
