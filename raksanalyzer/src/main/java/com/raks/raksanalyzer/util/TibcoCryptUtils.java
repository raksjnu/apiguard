package com.raks.raksanalyzer.util;
public class TibcoCryptUtils {
    public static String decrypt(String encryptedValue) {
        return encryptedValue;
    }
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith("#!");
    }
}
