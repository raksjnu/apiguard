package com.raks.raksanalyzer.domain.model.tibco;

/**
 * Represents a Tibco shared resource.
 * Includes shared variables, data formats, alias libraries, etc.
 */
public class TibcoSharedResource {
    private String name;
    private String type; // SharedVariable, JobSharedVariable, DataFormat, AliasLibrary, etc.
    private String filePath;
    private String dataType; // For variables
    private String defaultValue; // For variables
    private String scope; // For job shared variables: Job, Process, etc.
    private String formatType; // For data formats: Delimited, Fixed, etc.
    private String delimiter; // For delimited formats
    private java.util.List<String> usedByProcesses = new java.util.ArrayList<>();
    private java.util.Map<String, String> properties = new java.util.HashMap<>();
    
    public TibcoSharedResource() {}
    
    public TibcoSharedResource(String name, String type) {
        this.name = name;
        this.type = type;
    }
    
    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    
    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
    
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    
    public String getFormatType() { return formatType; }
    public void setFormatType(String formatType) { this.formatType = formatType; }
    
    public String getDelimiter() { return delimiter; }
    public void setDelimiter(String delimiter) { this.delimiter = delimiter; }
    
    public java.util.List<String> getUsedByProcesses() { return usedByProcesses; }
    public void setUsedByProcesses(java.util.List<String> usedByProcesses) { this.usedByProcesses = usedByProcesses; }
    public void addUsedByProcess(String processName) { this.usedByProcesses.add(processName); }
    
    public java.util.Map<String, String> getProperties() { return properties; }
    public void setProperties(java.util.Map<String, String> properties) { this.properties = properties; }
    public void addProperty(String key, String value) { this.properties.put(key, value); }
    
    @Override
    public String toString() {
        return "TibcoSharedResource{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", filePath='" + filePath + '\'' +
                '}';
    }
}
