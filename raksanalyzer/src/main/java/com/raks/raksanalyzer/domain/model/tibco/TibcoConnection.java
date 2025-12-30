package com.raks.raksanalyzer.domain.model.tibco;
public class TibcoConnection {
    private String name;
    private String type; 
    private String filePath;
    private String host;
    private String port;
    private String database;
    private String username;
    private String connectionUrl;
    private String provider; 
    private String queueManager; 
    private String channel; 
    private boolean sslEnabled;
    private String authenticationType;
    private Integer minPoolSize;
    private Integer maxPoolSize;
    private Integer connectionTimeout;
    private java.util.Map<String, String> properties = new java.util.HashMap<>();
    private java.util.List<String> usedByProcesses = new java.util.ArrayList<>();
    public TibcoConnection() {}
    public TibcoConnection(String name, String type) {
        this.name = name;
        this.type = type;
    }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public String getPort() { return port; }
    public void setPort(String port) { this.port = port; }
    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getConnectionUrl() { return connectionUrl; }
    public void setConnectionUrl(String connectionUrl) { this.connectionUrl = connectionUrl; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getQueueManager() { return queueManager; }
    public void setQueueManager(String queueManager) { this.queueManager = queueManager; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public boolean isSslEnabled() { return sslEnabled; }
    public void setSslEnabled(boolean sslEnabled) { this.sslEnabled = sslEnabled; }
    public String getAuthenticationType() { return authenticationType; }
    public void setAuthenticationType(String authenticationType) { this.authenticationType = authenticationType; }
    public Integer getMinPoolSize() { return minPoolSize; }
    public void setMinPoolSize(Integer minPoolSize) { this.minPoolSize = minPoolSize; }
    public Integer getMaxPoolSize() { return maxPoolSize; }
    public void setMaxPoolSize(Integer maxPoolSize) { this.maxPoolSize = maxPoolSize; }
    public Integer getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(Integer connectionTimeout) { this.connectionTimeout = connectionTimeout; }
    public java.util.Map<String, String> getProperties() { return properties; }
    public void setProperties(java.util.Map<String, String> properties) { this.properties = properties; }
    public void addProperty(String key, String value) { this.properties.put(key, value); }
    public java.util.List<String> getUsedByProcesses() { return usedByProcesses; }
    public void setUsedByProcesses(java.util.List<String> usedByProcesses) { this.usedByProcesses = usedByProcesses; }
    public void addUsedByProcess(String processName) { this.usedByProcesses.add(processName); }
    @Override
    public String toString() {
        return "TibcoConnection{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", host='" + host + '\'' +
                ", port='" + port + '\'' +
                '}';
    }
}
