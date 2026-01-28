package com.raks.apiforge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

public class CertificateService {
    private static final Logger logger = LoggerFactory.getLogger(CertificateService.class);

    public Map<String, Object> uploadCertificate(String storageDir, String fileName, byte[] content) throws Exception {
        File certsDir = new File(storageDir, "certs");
        if (!certsDir.exists()) {
            boolean created = certsDir.mkdirs();
            if (!created && !certsDir.exists()) {
                throw new java.io.IOException("Could not create certificates directory: " + certsDir.getAbsolutePath());
            }
        }
        
        File targetFile = new File(certsDir, fileName);
        logger.info("Saving certificate to: {}", targetFile.getAbsolutePath());
        
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            fos.write(content);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("path", targetFile.getAbsolutePath().replace("\\", "/"));
        return result;
    }

    public Map<String, Object> validateCertificate(Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            String path = (String) params.get("path");
            String password = (String) params.get("passphrase");
            String type = (String) params.get("type"); // JKS, PFX, PEM, PEM_PAIR

            if (path == null || path.isEmpty()) {
                result.put("valid", false);
                result.put("error", "No path provided");
                return result;
            }

            File file = new File(path);
            if (!file.exists()) {
                result.put("valid", false);
                result.put("error", "File not found at: " + path);
                return result;
            }

            if ("JKS".equalsIgnoreCase(type) || "PFX".equalsIgnoreCase(type)) {
                return validateKeyStore(file, password, type);
            } else if ("PEM".equalsIgnoreCase(type)) {
                return validatePemCert(file);
            } else if ("PEM_PAIR".equalsIgnoreCase(type)) {
                return validatePemPair(file, (String) params.get("keyPath"));
            } else {
                result.put("valid", true);
                result.put("message", "File exists.");
                return result;
            }
        } catch (Exception e) {
            result.put("valid", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    private Map<String, Object> validateKeyStore(File file, String password, String type) {
        Map<String, Object> result = new HashMap<>();
        try {
            KeyStore ks = KeyStore.getInstance("JKS".equalsIgnoreCase(type) ? "JKS" : "PKCS12");
            try (FileInputStream fis = new FileInputStream(file)) {
                ks.load(fis, password != null ? password.toCharArray() : null);
            }
            result.put("valid", true);
            List<String> aliases = new ArrayList<>();
            Enumeration<String> enumeration = ks.aliases();
            while (enumeration.hasMoreElements()) {
                String alias = enumeration.nextElement();
                aliases.add(alias + (ks.isKeyEntry(alias) ? " (Key)" : " (Cert)"));
            }
            result.put("aliases", aliases);
            result.put("message", "Keystore loaded successfully with " + aliases.size() + " entries.");
        } catch (Exception e) {
            result.put("valid", false);
            result.put("error", "Failed to load keystore: " + e.getMessage());
        }
        return result;
    }

    private Map<String, Object> validatePemCert(File file) {
        Map<String, Object> result = new HashMap<>();
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            try (FileInputStream fis = new FileInputStream(file)) {
                X509Certificate cert = (X509Certificate) cf.generateCertificate(fis);
                result.put("valid", true);
                result.put("subject", cert.getSubjectX500Principal().getName());
                result.put("issuer", cert.getIssuerX500Principal().getName());
                result.put("expiry", cert.getNotAfter().toString());
                result.put("isExpired", cert.getNotAfter().before(new Date()));
                result.put("message", "Valid X.509 Certificate found.");
            }
        } catch (Exception e) {
            result.put("valid", false);
            result.put("error", "Invalid Certificate format: " + e.getMessage());
        }
        return result;
    }

    private Map<String, Object> validatePemPair(File certFile, String keyPath) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (keyPath == null || keyPath.isEmpty()) {
                result.put("valid", false);
                result.put("error", "Private Key path is missing for PEM pair.");
                return result;
            }
            File keyFile = new File(keyPath);
            if (!keyFile.exists()) {
                result.put("valid", false);
                result.put("error", "Private Key file not found at: " + keyPath);
                return result;
            }

            // Validate Cert
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert;
            try (FileInputStream fis = new FileInputStream(certFile)) {
                cert = (X509Certificate) cf.generateCertificate(fis);
            }

            // Basic validation that key is readable
            String keyContent = java.nio.file.Files.readString(keyFile.toPath());
            if (!keyContent.contains("PRIVATE KEY")) {
                throw new Exception("Key file does not appear to contain a valid PEM Private Key.");
            }

            result.put("valid", true);
            result.put("subject", cert.getSubjectX500Principal().getName());
            result.put("expiry", cert.getNotAfter().toString());
            result.put("isExpired", cert.getNotAfter().before(new Date()));
            result.put("message", "Valid PEM Certificate and Private Key found.");
        } catch (Exception e) {
            result.put("valid", false);
            result.put("error", "PEM Pair validation failed: " + e.getMessage());
        }
        return result;
    }
}
