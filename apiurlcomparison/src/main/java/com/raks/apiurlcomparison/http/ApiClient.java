package com.raks.apiurlcomparison.http;

import com.raks.apiurlcomparison.Authentication;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    private void obtainAccessToken() throws IOException {
        if (authentication == null || authentication.getTokenUrl() == null) {
            return; // Not OAuth or no token URL
        }

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(authentication.getTokenUrl());
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("grant_type", "client_credentials"));
            // Some OAuth providers might require client_id/secret in body
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

    public String sendRequest(String url, String method, Map<String, String> headers, String body) throws IOException {
        // Try to get OAuth token if configured
        if (accessToken == null && authentication != null && authentication.getTokenUrl() != null) {
            obtainAccessToken();
        }

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            RequestBuilder requestBuilder = RequestBuilder.create(method.toUpperCase()).setUri(url);

            headers.forEach(requestBuilder::addHeader);

            // OAuth Token
            if (accessToken != null) {
                requestBuilder.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
            }
            // Basic Auth Fallback (if no token URL but client credentials exist)
            else if (authentication != null && authentication.getClientId() != null
                    && authentication.getClientSecret() != null) {
                String auth = authentication.getClientId() + ":" + authentication.getClientSecret();
                byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
                String authHeader = "Basic " + new String(encodedAuth);
                requestBuilder.addHeader(HttpHeaders.AUTHORIZATION, authHeader);
            }

            if (body != null && !body.isEmpty()) {
                requestBuilder.setEntity(new StringEntity(body, "UTF-8"));
            }

            HttpUriRequest request = requestBuilder.build();
            logger.debug("Executing request: {}", request);

            try (CloseableHttpResponse response = client.execute(request)) {
                return EntityUtils.toString(response.getEntity());
            }
        }
    }
}