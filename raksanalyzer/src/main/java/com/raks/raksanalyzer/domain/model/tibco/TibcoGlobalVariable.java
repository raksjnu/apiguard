package com.raks.raksanalyzer.domain.model.tibco;

/**
 * Represents a Tibco global variable.
 * Global variables are stored in .substvar files in the defaultVars folder.
 */
public class TibcoGlobalVariable {
    private String groupName; // Parent folder name
    private String name;
    private String value;
    private String dataType; // String, Integer, Boolean, Password, etc.
    private boolean deploymentSettable;
    private boolean serviceSettable;
    private String description;
    private boolean isPassword;
    private String decryptedValue; // Only populated if decryption is enabled
    
    public TibcoGlobalVariable() {}
    
    public TibcoGlobalVariable(String groupName, String name, String value) {
        this.groupName = groupName;
        this.name = name;
        this.value = value;
    }
    
    public TibcoGlobalVariable(String name, String value, String dataType, String groupName) {
        this.name = name;
        this.value = value;
        this.dataType = dataType;
        this.groupName = groupName;
    }
    
    // Getters and Setters
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    
    // Alias for compatibility
    public String getType() { return dataType; }
    public void setType(String type) { this.dataType = type; }
    
    public boolean isDeploymentSettable() { return deploymentSettable; }
    public void setDeploymentSettable(boolean deploymentSettable) { this.deploymentSettable = deploymentSettable; }
    
    public boolean isServiceSettable() { return serviceSettable; }
    public void setServiceSettable(boolean serviceSettable) { this.serviceSettable = serviceSettable; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public boolean isPassword() { return isPassword; }
    public void setPassword(boolean password) { isPassword = password; }
    
    public String getDecryptedValue() { return decryptedValue; }
    public void setDecryptedValue(String decryptedValue) { this.decryptedValue = decryptedValue; }
    
    /**
     * Get full qualified name (GroupName/VariableName)
     */
    public String getFullName() {
        return (groupName != null && !groupName.isEmpty() ? groupName + "/" : "") + name;
    }
    
    /**
     * Get display value (masked if password)
     */
    public String getDisplayValue() {
        if (isPassword && decryptedValue == null) {
            return "********";
        }
        return value;
    }
    
    @Override
    public String toString() {
        return "TibcoGlobalVariable{" +
                "groupName='" + groupName + '\'' +
                ", name='" + name + '\'' +
                ", dataType='" + dataType + '\'' +
                ", isPassword=" + isPassword +
                '}';
    }
}
