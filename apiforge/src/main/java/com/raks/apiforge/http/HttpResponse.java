package com.raks.apiforge.http;

import java.util.Collections;
import java.util.Map;

public class HttpResponse {
    private final int statusCode;
    private final String body;
    private final Map<String, String> headers;
    private final Map<String, String> requestHeaders;
    
    public HttpResponse(int statusCode, String body, Map<String, String> headers, Map<String, String> requestHeaders) {
        this.statusCode = statusCode;
        this.body = body;
        this.headers = headers != null ? headers : Collections.emptyMap();
        this.requestHeaders = requestHeaders != null ? requestHeaders : Collections.emptyMap();
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public String getBody() {
        return body;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }
}
