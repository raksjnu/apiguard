package com.raks.raksanalyzer.model;
public class OutputFormatConfig {
    private boolean pdfEnabled;
    private boolean wordEnabled;
    private boolean excelEnabled;
    public OutputFormatConfig() {
        this.pdfEnabled = true;
        this.wordEnabled = false;
        this.excelEnabled = false;
    }
    public boolean isPdfEnabled() {
        return pdfEnabled;
    }
    public void setPdfEnabled(boolean pdfEnabled) {
        this.pdfEnabled = pdfEnabled;
    }
    public boolean isWordEnabled() {
        return wordEnabled;
    }
    public void setWordEnabled(boolean wordEnabled) {
        this.wordEnabled = wordEnabled;
    }
    public boolean isExcelEnabled() {
        return excelEnabled;
    }
    public void setExcelEnabled(boolean excelEnabled) {
        this.excelEnabled = excelEnabled;
    }
    public boolean hasAnyFormatEnabled() {
        return pdfEnabled || wordEnabled || excelEnabled;
    }
    @Override
    public String toString() {
        return "OutputFormatConfig{" +
                "pdf=" + pdfEnabled +
                ", word=" + wordEnabled +
                ", excel=" + excelEnabled +
                '}';
    }
}
