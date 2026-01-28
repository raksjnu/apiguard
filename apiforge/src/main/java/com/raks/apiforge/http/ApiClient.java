package com.raks.apiforge.http;

import com.raks.apiforge.Authentication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class ApiClient {
    private static final Logger logger = LoggerFactory.getLogger(ApiClient.class);
    private final Authentication authentication;
    private String accessToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApiClient(Authentication authentication) {
        this.authentication = authentication;
    }

    public String getAccessToken() {
        return accessToken;
    }

    private void obtainAccessToken() throws IOException {
        if (authentication == null || authentication.getTokenUrl() == null) {
            return; 
        }
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(authentication.getTokenUrl());
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("grant_type", "client_credentials"));
            if (authentication.getClientId() != null) {
                params.add(new BasicNameValuePair("client_id", authentication.getClientId()));
            }
            if (authentication.getClientSecret() != null) {
                params.add(new BasicNameValuePair("client_secret", authentication.getClientSecret()));
            }
            post.setEntity(new UrlEncodedFormEntity(params));
            logger.info("Requesting new access token from {}", authentication.getTokenUrl());
            try (CloseableHttpResponse response = client.execute(post)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                if (response.getStatusLine().getStatusCode() == 200) {
                    JsonNode tokenResponse = objectMapper.readTree(responseBody);
                    if (tokenResponse.has("access_token")) {
                        this.accessToken = tokenResponse.get("access_token").asText();
                        logger.info("Successfully obtained new access token.");
                    } else {
                        throw new IOException("Token response missing access_token field: " + responseBody);
                    }
                } else {
                    throw new IOException("Failed to obtain access token. Status: " + response.getStatusLine()
                            + ", Body: " + responseBody);
                }
            }
        }
    }

    public HttpResponse sendRequest(String url, String method, Map<String, String> headers, String body) throws IOException {
        if (accessToken == null && authentication != null && authentication.getTokenUrl() != null) {
            obtainAccessToken();
        }
        
        RequestBuilder requestBuilder = RequestBuilder.create(method.toUpperCase()).setUri(url);
        if (headers != null) {
            headers.forEach(requestBuilder::addHeader);
        }
        if (accessToken != null) {
            requestBuilder.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        else if (authentication != null && authentication.getClientId() != null) {
            String clientSecret = authentication.getClientSecret() != null ? authentication.getClientSecret() : "";
            String auth = authentication.getClientId() + ":" + clientSecret;
            byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
            String authHeader = "Basic " + new String(encodedAuth);
            requestBuilder.addHeader(HttpHeaders.AUTHORIZATION, authHeader);
        }
        
        if (body != null && !body.isEmpty()) {
            String contentType = headers != null ? headers.getOrDefault(HttpHeaders.CONTENT_TYPE, headers.getOrDefault("Content-Type", "text/plain")) : "text/plain";
            org.apache.http.entity.ContentType type = org.apache.http.entity.ContentType.parse(contentType);
            requestBuilder.setEntity(new StringEntity(body, type));
        }

        final Map<String, String> finalRequestHeaders = new java.util.HashMap<>();
        
        HttpClientBuilder clientBuilder = HttpClients.custom();
        
        if (authentication != null && (authentication.getPfxPath() != null || authentication.getClientCertPath() != null || authentication.getCaCertPath() != null)) {
            try {
                SSLContext sslContext = createSslContext(authentication);
                SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
                        sslContext,
                        new String[] {"TLSv1.2", "TLSv1.3"},
                        null,
                        NoopHostnameVerifier.INSTANCE);
                clientBuilder.setSSLSocketFactory(sslsf);
            } catch (Exception e) {
                logger.error("Failed to create secure SSL context for mTLS", e);
                throw new IOException("SSL Configuration Error: " + e.getMessage(), e);
            }
        }

        try (CloseableHttpClient client = clientBuilder
                .addInterceptorFirst((org.apache.http.HttpRequestInterceptor) (request, context) -> {
                    for (org.apache.http.Header header : request.getAllHeaders()) {
                        finalRequestHeaders.put(header.getName(), header.getValue());
                    }
                })
                .build()) {
            
            HttpUriRequest request = requestBuilder.build();
            logger.debug("Executing request: {}", request);
            try (CloseableHttpResponse response = client.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                Map<String, String> respHeaders = new java.util.HashMap<>();
                for (org.apache.http.Header header : response.getAllHeaders()) {
                    respHeaders.put(header.getName(), header.getValue());
                }
                return new HttpResponse(statusCode, responseBody, respHeaders, finalRequestHeaders);
            }
        }
    }

    private SSLContext createSslContext(Authentication auth) throws Exception {
        SSLContextBuilder sslContextBuilder = SSLContexts.custom();

        // 1. Handle Trust Store (CA Certificates)
        if (auth.getCaCertPath() != null && !auth.getCaCertPath().isEmpty()) {
            File caFile = resolveCertFile(auth.getCaCertPath());
            if (caFile.exists()) {
                KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                trustStore.load(null, null);
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                try (InputStream is = new FileInputStream(caFile)) {
                    java.security.cert.Certificate cert = cf.generateCertificate(is);
                    trustStore.setCertificateEntry("ca", cert);
                }
                sslContextBuilder.loadTrustMaterial(trustStore, null);
                logger.info("Loaded CA certificate from {}", caFile.getAbsolutePath());
            }
        } else {
            // Default: Trust all if no CA provided? Or trust standard Java trust store.
            // For apiforge, let's trust all if user is doing testing, but better to be safe.
            sslContextBuilder.loadTrustMaterial(new org.apache.http.conn.ssl.TrustSelfSignedStrategy());
        }

        // 2. Handle Key Store (Client Certificate & Key)
        char[] password = (auth.getPassphrase() != null) ? auth.getPassphrase().toCharArray() : null;

        if (auth.getPfxPath() != null && !auth.getPfxPath().isEmpty()) {
            File pfxFile = resolveCertFile(auth.getPfxPath());
            if (pfxFile.exists()) {
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                if (auth.getPfxPath().toLowerCase().endsWith(".jks")) {
                    keyStore = KeyStore.getInstance("JKS");
                }
                try (InputStream is = new FileInputStream(pfxFile)) {
                    keyStore.load(is, password);
                }
                sslContextBuilder.loadKeyMaterial(keyStore, password);
                logger.info("Loaded Client KeyStore from {}", pfxFile.getAbsolutePath());
            }
        } else if (auth.getClientCertPath() != null && !auth.getClientCertPath().isEmpty() &&
               auth.getClientKeyPath() != null && !auth.getClientKeyPath().isEmpty()) {
        
            File certFile = resolveCertFile(auth.getClientCertPath());
            File keyFile = resolveCertFile(auth.getClientKeyPath());
            
            if (certFile.exists() && keyFile.exists()) {
                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(null, null);
                
                Certificate cert = loadCertificate(certFile);
                PrivateKey key = loadPrivateKey(keyFile);
                
                keyStore.setKeyEntry("client", key, password, new Certificate[]{cert});
                sslContextBuilder.loadKeyMaterial(keyStore, password);
                logger.info("Loaded Client Identity from PEM files: {} and {}", certFile.getName(), keyFile.getName());
            }
        }

        return sslContextBuilder.build();
    }

    private PrivateKey loadPrivateKey(File file) throws Exception {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        String b64 = content.replace("-----BEGIN PRIVATE KEY-----", "")
                            .replace("-----END PRIVATE KEY-----", "")
                            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                            .replace("-----END RSA PRIVATE KEY-----", "")
                            .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(b64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        try {
            return kf.generatePrivate(spec);
        } catch (Exception e) {
            // Try EC if RSA fails
            return KeyFactory.getInstance("EC").generatePrivate(spec);
        }
    }

    private Certificate loadCertificate(File file) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (InputStream is = new FileInputStream(file)) {
            return cf.generateCertificate(is);
        }
    }

    private File resolveCertFile(String path) {
        File file = new File(path);
        if (file.isAbsolute()) return file;
        
        // Try relative to Working Directory if provided (implied by tool design)
        // Since ApiClient doesn't know about WorkingDir directly, we might need to pass it
        // Or assume CWD if absolute fails.
        return file;
    }
}
