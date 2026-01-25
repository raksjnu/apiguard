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
        
        try (CloseableHttpClient client = HttpClients.custom()
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
}
