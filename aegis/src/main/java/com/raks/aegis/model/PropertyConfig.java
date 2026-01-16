package com.raks.aegis.model;
import java.util.List;
public class PropertyConfig {
    private String name;
    private List<String> values;
    private Boolean caseSensitiveName; 
    private Boolean caseSensitiveValue; 
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
