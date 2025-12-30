package com.raks.raksanalyzer.domain.enums;
public enum ExecutionMode {
    FULL("Full (Analyze + Generate Documents)", "full"),
    ANALYZE_ONLY("Analyze Only (Excel Report)", "analyze"),
    GENERATE_ONLY("Generate Only (Word Document)", "generate");
    private final String displayName;
    private final String code;
    ExecutionMode(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }
    public String getDisplayName() {
        return displayName;
    }
    public String getCode() {
        return code;
    }
    public static ExecutionMode fromCode(String code) {
        for (ExecutionMode mode : values()) {
            if (mode.code.equalsIgnoreCase(code)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown execution mode code: " + code);
    }
}
