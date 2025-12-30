package com.raks.raksanalyzer.domain.model.tibco;
public class TibcoArchive {
    private String name;
    private String type; 
    private String filePath;
    private long fileSize;
    private String creationDate;
    private String author;
    private String version;
    private String description;
    private java.util.List<String> processesIncluded = new java.util.ArrayList<>();
    private java.util.List<String> sharedResourcesIncluded = new java.util.ArrayList<>();
    private java.util.List<TibcoArchive> children = new java.util.ArrayList<>();
    public java.util.List<TibcoArchive> getChildren() { return children; }
    public void addChild(TibcoArchive child) { this.children.add(child); }
    public TibcoArchive() {}
    public TibcoArchive(String name, String type) {
        this.name = name;
        this.type = type;
    }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getCreationDate() { return creationDate; }
    public void setCreationDate(String creationDate) { this.creationDate = creationDate; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public java.util.List<String> getProcessesIncluded() { return processesIncluded; }
    public void setProcessesIncluded(java.util.List<String> processesIncluded) { this.processesIncluded = processesIncluded; }
    public void addProcess(String processName) { this.processesIncluded.add(processName); }
    public java.util.List<String> getSharedResourcesIncluded() { return sharedResourcesIncluded; }
    public void setSharedResourcesIncluded(java.util.List<String> sharedResourcesIncluded) { this.sharedResourcesIncluded = sharedResourcesIncluded; }
    public void addSharedResource(String resourceName) { this.sharedResourcesIncluded.add(resourceName); }
    private java.util.List<String> filesIncluded = new java.util.ArrayList<>();
    public java.util.List<String> getFilesIncluded() { return filesIncluded; }
    public void setFilesIncluded(java.util.List<String> filesIncluded) { this.filesIncluded = filesIncluded; }
    public void addFile(String file) { this.filesIncluded.add(file); }
    private java.util.List<String> jarsIncluded = new java.util.ArrayList<>();
    public java.util.List<String> getJarsIncluded() { return jarsIncluded; }
    public void setJarsIncluded(java.util.List<String> jarsIncluded) { this.jarsIncluded = jarsIncluded; }
    public void addJar(String jar) { this.jarsIncluded.add(jar); }
    @Override
    public String toString() {
        return "TibcoArchive{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", fileSize=" + fileSize +
                '}';
    }
}
