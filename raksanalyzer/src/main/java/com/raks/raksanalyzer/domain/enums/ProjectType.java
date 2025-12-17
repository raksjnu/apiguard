package com.raks.raksanalyzer.domain.enums;

/**
 * Supported project technology types.
 */
public enum ProjectType {
    MULE("Mule 4.x Application", "mule"),
    TIBCO_BW5("Tibco BusinessWorks 5.x", "tibco-bw5"),
    TIBCO_BW6("Tibco BusinessWorks 6.x", "tibco-bw6"),
    SPRING_BOOT("Spring Boot Application", "spring-boot");
    
    private final String displayName;
    private final String code;
    
    ProjectType(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getCode() {
        return code;
    }
    
    public static ProjectType fromCode(String code) {
        for (ProjectType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown project type code: " + code);
    }
}
