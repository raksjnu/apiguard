package com.raks.aegis.license;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LicenseValidator {

    // HARDCODED PUBLIC KEY
    private static final String PUBLIC_KEY_BASE64 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyOxwxoMcVkHOufzgt7Gf" +
            "BlZa3/c/TqOr08VafwNEhthZy9C5hcPzceAS6c57qKuQSlu6w6bEjN6OHguf6XWU" +
            "u0htVRLSEfvqifOWm80KdGDqV7tvUmkUCv988s4EdsNdbiXFmYk+JcZMyoWQaJQn" +
            "cLR8eEfsnXeaPqA8Mt70PQ/g1PXOi8YMpURBHmWEKioGyIFdpNVUA495vOnHPznT" +
            "LmhZSMu2KujFK5gm4oIyZO0F16JgrcTujrqkzPw8TPx7OGIYeC+3FVFi6Y1pSoU4" +
            "xmWf7EhPhgbbBO3j6tg6DMbFRRNjyjyuhFNT6wQ757/CEq+Wc0zpk1gdn7ms3VMk" +
            "/wIDAQAB"; 

    public static void validate(String licenseKey) {
        // Check for license protection marker (Robust check for Mule ClassLoaders)
        java.net.URL marker1 = LicenseValidator.class.getResource("/LICENSE_MODE_ENABLED");
        if (marker1 == null) marker1 = LicenseValidator.class.getClassLoader().getResource("LICENSE_MODE_ENABLED");
        
        java.net.URL marker2 = LicenseValidator.class.getResource("/PROTECTED_MODE_ENABLED");
        if (marker2 == null) marker2 = LicenseValidator.class.getClassLoader().getResource("PROTECTED_MODE_ENABLED");

        // System.err.println("[Aegis] Marker Check - LICENSE_MODE_ENABLED: " + marker1);
        // System.err.println("[Aegis] Marker Check - PROTECTED_MODE_ENABLED: " + marker2);

        if (marker1 == null && marker2 == null) {
            // System.err.println("[Aegis] No enforcement markers found. Bypassing validation.");
            return; // Bypass validation if both markers are missing from classpath
        }

        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            licenseKey = System.getProperty("raks.license.key");
        }

        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            licenseKey = loadFromLocalFile();
        }

        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            licenseKey = loadFromClasspath();
        }

        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            throw new SecurityException("License key is missing.");
        }

        try {
            String[] parts = licenseKey.split("\\.");
            if (parts.length != 2) throw new SecurityException("Invalid license format.");

            String payloadBase64 = parts[0];
            String signatureBase64 = parts[1];

            if (!verifySignature(payloadBase64, signatureBase64)) {
                throw new SecurityException("Invalid license signature. Tampering detected.");
            }

            String jsonPayload = new String(Base64.getDecoder().decode(payloadBase64), StandardCharsets.UTF_8);
            ObjectMapper mapper = new ObjectMapper();
            Map<String, String> data = mapper.readValue(jsonPayload, Map.class);

            String expiryDateStr = data.get("expiry");
            LocalDate expiryDate = LocalDate.parse(expiryDateStr);

            if (expiryDate.isBefore(LocalDate.now())) {
                String msg = "\n=======================================================\n" +
                             "   LICENSE EXPIRED\n" +
                             "=======================================================\n" +
                             "Your license for this application expired on: " + expiryDateStr + "\n\n" +
                             "To renew your access, please contact:\n" +
                             "   Rakesh Kumar\n" +
                             "   Email: rakesh.kumar@ibm.com\n" +
                             "          raksjnu@gmail.com\n" +
                             "=======================================================";
                throw new SecurityException(msg);
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("License validation error: " + e.getMessage());
        }
    }

    public static String loadFromLocalFile() {
        // 1. Check current working directory
        try {
            java.io.File localFile = new java.io.File("license.key");
            if (localFile.exists()) {
                // System.err.println("[Aegis] Found license.key in CWD");
                return new String(java.nio.file.Files.readAllBytes(localFile.toPath()), java.nio.charset.StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            // Silently ignore
        }

        // 2. Check directory containing the JAR
        try {
            java.security.CodeSource codeSource = LicenseValidator.class.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                java.net.URL location = codeSource.getLocation();
                java.io.File pathFile = new java.io.File(location.toURI());
                java.io.File jarDir = pathFile.getParentFile();
                if (jarDir != null) {
                    java.io.File licenseFile = new java.io.File(jarDir, "license.key");
                    if (licenseFile.exists()) {
                        // System.err.println("[Aegis] Found license.key next to JAR: " + licenseFile.getAbsolutePath());
                        return new String(java.nio.file.Files.readAllBytes(licenseFile.toPath()), java.nio.charset.StandardCharsets.UTF_8).trim();
                    }
                }
            }
        } catch (Exception e) {
            // Silently ignore
        }
        return null;
    }

    public static String loadFromClasspath() {
        try (java.io.InputStream is = LicenseValidator.class.getResourceAsStream("/license.key")) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            // Ignore error, return null
        }
        return null;
    }

    private static boolean verifySignature(String payloadBase64, String signatureBase64) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(PUBLIC_KEY_BASE64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PublicKey publicKey = kf.generatePublic(spec);
        Signature publicSignature = Signature.getInstance("SHA256withRSA");
        publicSignature.initVerify(publicKey);
        publicSignature.update(payloadBase64.getBytes(StandardCharsets.UTF_8));
        byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
        return publicSignature.verify(signatureBytes);
    }
}
