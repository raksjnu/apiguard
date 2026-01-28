package com.raks.apiforge;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
@JsonIgnoreProperties(ignoreUnknown = true)
public class Authentication {
    @JsonProperty("tokenUrl")
    private String tokenUrl;
    @JsonProperty("clientId")
    private String clientId;
    @JsonProperty("clientSecret")
    private String clientSecret;
    @JsonProperty("caCertPath")
    private String caCertPath;
    @JsonProperty("clientCertPath")
    private String clientCertPath;
    @JsonProperty("clientKeyPath")
    private String clientKeyPath;
    @JsonProperty("pfxPath")
    private String pfxPath;
    @JsonProperty("pfxAlias")
    private String pfxAlias;
    @JsonProperty("passphrase")
    private String passphrase;

    public String getTokenUrl() {
        return tokenUrl;
    }
    public void setTokenUrl(String tokenUrl) {
        this.tokenUrl = tokenUrl;
    }
    public String getClientId() {
        return clientId;
    }
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    public String getClientSecret() {
        return clientSecret;
    }
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
    public String getCaCertPath() {
        return caCertPath;
    }
    public void setCaCertPath(String caCertPath) {
        this.caCertPath = caCertPath;
    }
    public String getClientCertPath() {
        return clientCertPath;
    }
    public void setClientCertPath(String clientCertPath) {
        this.clientCertPath = clientCertPath;
    }
    public String getClientKeyPath() {
        return clientKeyPath;
    }
    public void setClientKeyPath(String clientKeyPath) {
        this.clientKeyPath = clientKeyPath;
    }
    public String getPfxPath() {
        return pfxPath;
    }
    public void setPfxPath(String pfxPath) {
        this.pfxPath = pfxPath;
    }
    public String getPfxAlias() {
        return pfxAlias;
    }
    public void setPfxAlias(String pfxAlias) {
        this.pfxAlias = pfxAlias;
    }
    public String getPassphrase() {
        return passphrase;
    }
    public void setPassphrase(String passphrase) {
        this.passphrase = passphrase;
    }
}
