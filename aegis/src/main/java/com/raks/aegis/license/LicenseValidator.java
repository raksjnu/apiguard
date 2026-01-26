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
    private static final String PUBLIC_KEY_BASE64 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAy+h+v5C7R/8T27W/V7Q5Q" +
            "J3VFRZE1+2RU4Fe46EKWeoBq/hjpDZS5fWHWoV/l8bVgsXL5dWf4NidzEc90IvwFDjQIDAQABdyvyd1k1LdYgyLMJlL+hnLyguNIVcyh9kWosW7tZlyFDwDWQ/JNMmfVVo5+HMvqS/GNEIB0D6sfJptAFCNGCf3nJ"; 

    public static void validate(String licenseKey) {
        // Check for license protection marker
        if (!new java.io.File("LICENSE_MODE_ENABLED").exists()) {
            return; // Bypass validation if marker is missing
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
