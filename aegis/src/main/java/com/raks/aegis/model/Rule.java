package com.raks.aegis.model;

import java.util.List;

public class Rule {
    private String id;
    private String name;
    private String description;
    private boolean enabled = true;
    private String severity;
    private List<Check> checks;

    private String useCase;
    private String rationale;
    private String exampleGood;
    private String exampleBad;
    private String scope = "GLOBAL";
    private String successMessage;
    private String errorMessage;
    private String docLink;
    private List<String> appliesTo;  // NEW: Project types this rule applies to (e.g., ["CODE", "CONFIG"])

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getSuccessMessage() { return successMessage; }
    public void setSuccessMessage(String successMessage) { this.successMessage = successMessage; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public List<Check> getChecks() { return checks; }
    public void setChecks(List<Check> checks) { this.checks = checks; }

    public String getUseCase() { return useCase; }
    public void setUseCase(String useCase) { this.useCase = useCase; }

    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }

    public String getExampleGood() { return exampleGood; }
    public void setExampleGood(String exampleGood) { this.exampleGood = exampleGood; }

    public String getExampleBad() { return exampleBad; }
    public void setExampleBad(String exampleBad) { this.exampleBad = exampleBad; }

    public String getDocLink() { return docLink; }
    public void setDocLink(String docLink) { this.docLink = docLink; }

    public List<String> getAppliesTo() { return appliesTo; }
    public void setAppliesTo(List<String> appliesTo) { this.appliesTo = appliesTo; }
}
