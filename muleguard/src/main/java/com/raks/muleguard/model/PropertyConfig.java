package com.raks.muleguard.model;

import java.util.List;

/**
 * Configuration class for property validation with optional case sensitivity
 * overrides.
 * Used by property validation checks to define expected property names and
 * values.
 * 
 * @author Rakesh Kumar (raksjnu@gmail.com)
 */
public class PropertyConfig {
    private String name;
    private List<String> values;
    private Boolean caseSensitiveName; // Optional per-property override
    private Boolean caseSensitiveValue; // Optional per-property override

    public PropertyConfig() {
    }

    public PropertyConfig(String name, List<String> values) {
        this.name = name;
        this.values = values;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }

    public Boolean getCaseSensitiveName() {
        return caseSensitiveName;
    }

    public void setCaseSensitiveName(Boolean caseSensitiveName) {
        this.caseSensitiveName = caseSensitiveName;
    }

    public Boolean getCaseSensitiveValue() {
        return caseSensitiveValue;
    }

    public void setCaseSensitiveValue(Boolean caseSensitiveValue) {
        this.caseSensitiveValue = caseSensitiveValue;
    }

    @Override
    public String toString() {
        return "PropertyConfig{" +
                "name='" + name + '\'' +
                ", values=" + values +
                ", caseSensitiveName=" + caseSensitiveName +
                ", caseSensitiveValue=" + caseSensitiveValue +
                '}';
    }
}
