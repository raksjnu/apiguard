package com.raks.license;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class LicenseManager {
    private static final String PRIVATE_KEY_FILE = "private.key";
    private static final String PUBLIC_KEY_FILE = "public.key";
    private static final int KEY_SIZE = 2048;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("==========================================");
        System.out.println("   Raks License Manager (Admin Tool)");
        System.out.println("==========================================");
        System.out.println("1. Generate New Key Pair (WARNING: Invalidates old licenses)");
        System.out.println("2. Generate License Key");
        System.out.print("Enter choice: ");

        String choice = scanner.nextLine();

        try {
            if ("1".equals(choice)) {
                generateKeys();
            } else if ("2".equals(choice)) {
                generateLicense(scanner);
            } else {
                System.out.println("Invalid choice.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void generateKeys() throws Exception {
        System.out.println("Generating new RSA Key Pair...");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(KEY_SIZE);
        KeyPair pair = generator.generateKeyPair();

        PrivateKey privateKey = pair.getPrivate();
        PublicKey publicKey = pair.getPublic();

        try (FileOutputStream fos = new FileOutputStream(PRIVATE_KEY_FILE)) {
            fos.write(privateKey.getEncoded());
        }

        try (FileOutputStream fos = new FileOutputStream(PUBLIC_KEY_FILE)) {
            fos.write(publicKey.getEncoded());
        }

        System.out.println("Keys generated successfully!");
        System.out.println("Private Key: " + PRIVATE_KEY_FILE + " (KEEP SECRET!)");
        System.out.println("Public Key:  " + PUBLIC_KEY_FILE + " (Embed in App)");
        
        System.out.println("\n--- PUBLIC KEY STRING (For embedding in Java Code) ---");
        System.out.println(Base64.getEncoder().encodeToString(publicKey.getEncoded()));
        System.out.println("------------------------------------------------------");
    }

    private static void generateLicense(Scanner scanner) throws Exception {
        if (!new File(PRIVATE_KEY_FILE).exists()) {
            System.err.println("Error: " + PRIVATE_KEY_FILE + " not found. Generate keys first.");
            return;
        }

        System.out.print("Enter Client Name: ");
        String clientName = scanner.nextLine();

        System.out.print("Enter Expiry Date (YYYY-MM-DD): ");
        String expiryDateStr = scanner.nextLine();
        LocalDate.parse(expiryDateStr); // Validate format

        // Load Private Key
        byte[] keyBytes = Files.readAllBytes(Paths.get(PRIVATE_KEY_FILE));
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = kf.generatePrivate(spec);

        // Create License Data
        Map<String, String> licenseData = new HashMap<>();
        licenseData.put("client", clientName);
        licenseData.put("expiry", expiryDateStr);
        
        ObjectMapper mapper = new ObjectMapper();
        String jsonPayload = mapper.writeValueAsString(licenseData);
        String payloadBase64 = Base64.getEncoder().encodeToString(jsonPayload.getBytes(StandardCharsets.UTF_8));

        // Sign
        Signature privateSignature = Signature.getInstance("SHA256withRSA");
        privateSignature.initSign(privateKey);
        privateSignature.update(payloadBase64.getBytes(StandardCharsets.UTF_8));
        byte[] signature = privateSignature.sign();
        String signatureBase64 = Base64.getEncoder().encodeToString(signature);

        // Final License String: Payload + "." + Signature
        String licenseKey = payloadBase64 + "." + signatureBase64;

        System.out.println("\n==========================================");
        System.out.println("LICENSE GENERATED FOR: " + clientName);
        System.out.println("EXPIRY: " + expiryDateStr);
        System.out.println("==========================================");
        System.out.println(licenseKey);
        System.out.println("==========================================");
        
        // Also save to file
        Files.write(Paths.get("generated.license"), licenseKey.getBytes(StandardCharsets.UTF_8));
        System.out.println("Saved to 'generated.license'");
    }
}
