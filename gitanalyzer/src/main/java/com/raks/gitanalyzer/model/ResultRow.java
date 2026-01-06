package com.raks.gitanalyzer.model;

public class ResultRow {
    private String location;
    private int line;
    private String content;

    public ResultRow(String location, int line, String content) {
        this.location = location;
        this.line = line;
        this.content = content;
    }

    // Getters and Setters needed for Jackson serialization
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    
    public int getLine() { return line; }
    public void setLine(int line) { this.line = line; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
