package com.raks.raksanalyzer.domain.enums;
public enum EnvironmentScope {
    ALL("All Environments", "all"),
    CUSTOM("Select Specific Environments", "custom");
    private final String displayName;
    private final String code;
    EnvironmentScope(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }
    public String getDisplayName() {
        return displayName;
    }
    public String getCode() {
        return code;
    }
    public static EnvironmentScope fromCode(String code) {
        for (EnvironmentScope scope : values()) {
            if (scope.code.equalsIgnoreCase(code)) {
                return scope;
            }
        }
        throw new IllegalArgumentException("Unknown environment scope code: " + code);
    }
}
