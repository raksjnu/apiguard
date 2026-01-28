package com.raks.apiforge;
import java.util.List;
import java.util.Map;
public class Operation {
    private String name;
    private String path;
    private List<String> methods;
    private Map<String, String> headers;
    private String payloadTemplatePath;
    @com.fasterxml.jackson.annotation.JsonProperty("queryParams")
    private Map<String, String> queryParams;

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getPath() {
        return path;
    }
    public void setPath(String path) {
        this.path = path;
    }
    public List<String> getMethods() {
        return methods;
    }
    public void setMethods(List<String> methods) {
        this.methods = methods;
    }
    public Map<String, String> getHeaders() {
        return headers;
    }
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }
    public String getPayloadTemplatePath() {
        return payloadTemplatePath;
    }
    public void setPayloadTemplatePath(String payloadTemplatePath) {
        this.payloadTemplatePath = payloadTemplatePath;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(Map<String, String> queryParams) {
        this.queryParams = queryParams;
    }
}
