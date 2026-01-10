# ApiGuard Security Best Practices

This document outlines security best practices for deploying and using **all ApiGuard projects** in enterprise environments.

**Applies to:**
- **MuleGuard**: MuleSoft static analysis tool
- **ApiGuardWrapper**: Mule 4 wrapper application (includes MuleGuard, RaksAnalyzer, ApiUrlComparison, GitAnalyzer)
- **RaksAnalyzer**: Universal documentation generator
- **ApiUrlComparison**: API response comparison tool
- **GitAnalyzer**: Semantic code analysis tool

---

## Table of Contents
1. [Credential Management](#credential-management)
2. [Deployment Security](#deployment-security)
3. [Log File Security](#log-file-security)
4. [Network Security](#network-security)
5. [Dependency Management](#dependency-management)
6. [Configuration File Security](#configuration-file-security)

---

## Credential Management

### Standalone Applications (MuleGuard, RaksAnalyzer)
✅ **Good News**: Standalone tools do **NOT** require or store any credentials. They perform static analysis and do not connect to external systems.

### When Using MuleGuard via Wrapper
If you're using MuleGuard through the `apiguardwrapper` Mule application:

#### Environment Variables (Recommended)
Store sensitive configuration in environment variables:
```properties
# In mule-app.properties
api.endpoint=${env:API_ENDPOINT}
```

#### Secure Properties (CloudHub)
Use Mule Secure Configuration Property Module:
```xml
<secure-properties:config name="Secure_Properties" 
    file="secure.yaml" 
    key="${secure.key}"/>
```

#### Never Hardcode
❌ **NEVER** hardcode credentials in:
- Source code
- Configuration files committed to Git
- Property files in the repository

---

## Deployment Security

### Standalone Deployment
When running MuleGuard as a standalone JAR:

1. **File System Permissions**
   ```bash
   # Restrict JAR file permissions
   chmod 750 muleguard-1.0.0-jar-with-raks.jar
   
   # Restrict log directory
   chmod 750 logs/
   ```

2. **User Permissions**
   - Run as a non-root user
   - Use dedicated service account
   - Limit file system access

3. **Working Directory**
   - Use dedicated directory for reports
   - Clean up temporary files after execution
   - Restrict access to report directories

### CloudHub Deployment (via Wrapper)
When deploying `apiguardwrapper` to CloudHub:

1. **Secure Properties**
   - Use CloudHub secure properties for sensitive data
   - Never expose credentials in application logs

2. **Network Policies**
   - Restrict inbound traffic to necessary endpoints only
   - Use VPC if processing sensitive code

3. **Monitoring**
   - Enable CloudHub logging
   - Monitor for unusual activity
   - Set up alerts for failures

---

## Log File Security

### Log Configuration
MuleGuard uses Logback for logging. Configuration in `src/main/resources/logback.xml`:

```xml
<appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/muleguard.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
        <fileNamePattern>logs/muleguard.%d{yyyy-MM-dd}.log</fileNamePattern>
        <maxHistory>30</maxHistory>
        <totalSizeCap>1GB</totalSizeCap>
    </rollingPolicy>
</appender>
```

### Best Practices

1. **Log File Permissions**
   ```bash
   # Restrict log file access
   chmod 640 logs/muleguard.log
   chown muleguard:muleguard logs/
   ```

2. **Sensitive Data**
   - MuleGuard logs do NOT contain credentials
   - Logs may contain file paths and project names
   - Review logs before sharing externally

3. **Log Retention**
   - Default: 30 days retention
   - Adjust `<maxHistory>` in `logback.xml` per compliance requirements
   - Implement log archival for audit trails

4. **Log Monitoring**
   - Monitor for ERROR level logs
   - Set up alerts for repeated failures
   - Review logs periodically for anomalies

---

## Network Security

### Standalone Mode
MuleGuard in standalone mode:
- ✅ **No network connections** required
- ✅ **No external API calls**
- ✅ **Operates entirely offline**

### Wrapper Mode (Mule Application)
When deployed as part of `apiguardwrapper`:

1. **HTTP Endpoints**
   - Secure all HTTP listeners with HTTPS
   - Use API Gateway policies
   - Implement rate limiting

2. **Firewall Rules**
   - Allow only necessary inbound ports
   - Restrict outbound connections
   - Use network segmentation

---

## Dependency Management

### Current Dependencies
MuleGuard uses the following major dependencies:

| Dependency | Version | Purpose | Security Notes |
|------------|---------|---------|----------------|
| SnakeYAML | 2.2 | YAML parsing | Updated for CVE fixes |
| Jackson | 2.18.2 | JSON processing | Latest secure version |
| Apache POI | 5.4.0 | Excel reports | Updated for CVE-2025-31672 |
| SLF4J | 2.0.9 | Logging API | Industry standard |
| Logback | 1.4.11 | Logging implementation | Latest stable |

### Security Updates

1. **Regular Scans**
   ```bash
   # Run dependency vulnerability scan
   mvn dependency-check:check
   ```

2. **Update Schedule**
   - Review dependencies **quarterly**
   - Apply security patches **immediately**
   - Test updates in non-production first

3. **Monitoring**
   - Subscribe to security advisories for dependencies
   - Use tools like Dependabot or Snyk
   - Monitor CVE databases

---

## Configuration File Security

### MuleGuard Configuration Files

MuleGuard validates environment-specific configuration files from folders containing `_config` in the name:

1. **Folder Structure**
   - Configuration files must be in a folder containing `_config` in the name
   - Examples: `muleapp_config`, `api_config`, `myproject_config`
   - Pattern in `rules.yaml`: `".*_config.*"` (matches any folder containing `_config`)
   - Supported extensions: `.properties`, `.policy`, `.deployment`
   - Format: `propertyName=propertyValue` (delimiter must be `=`)

2. **Security Considerations**
   ```bash
   # Restrict access to configuration folders
   chmod 750 muleapp_config/
   
   # Protect sensitive property files
   chmod 640 muleapp_config/*.properties
   ```

3. **Best Practices**
   - **Never commit** production credentials to Git
   - Use **environment variables** for sensitive values
   - Implement **property encryption** for passwords
   - **Audit** configuration file access regularly

### Wrapper Configuration (mule-app.properties)

When deploying ApiGuardWrapper:

1. **SMTP Credentials**
   ```properties
   # Use environment variables
   smtp.username=${env:SMTP_USERNAME}
   smtp.password=${env:SMTP_PASSWORD}
   ```

2. **Working Directories**
   - Use `${mule.home}` for CloudHub compatibility
   - Implement cleanup policies for temporary files
   - Restrict file system permissions

3. **API Endpoints**
   - Use HTTPS for all external connections
   - Implement API key rotation
   - Monitor for unauthorized access

---

## Report Security

### Generated Reports
MuleGuard generates HTML and Excel reports containing:
- Rule validation results
- File paths
- Configuration snippets
- Compliance status

### Best Practices

1. **Access Control**
   - Restrict report directory access
   - Use role-based access control (RBAC)
   - Implement audit logging for report access

2. **Data Classification**
   - Reports may contain **internal** information
   - Do NOT share reports publicly
   - Redact sensitive paths before external sharing

3. **Storage**
   - Store reports in secure locations
   - Encrypt reports at rest if required
   - Implement retention policies

---

## Compliance Considerations

### Data Privacy
- MuleGuard processes **source code** (not user data)
- No PII (Personally Identifiable Information) collected
- No data transmitted to external services

### Audit Trail
- All validations logged via SLF4J
- Log files provide audit trail
- Timestamps included in all log entries

### Regulatory Compliance
- **SOC 2**: Logging and monitoring capabilities support compliance
- **ISO 27001**: Security controls align with standard
- **GDPR**: No personal data processing

---

## Incident Response

### Security Issues
If you discover a security vulnerability:

1. **Do NOT** open a public GitHub issue
2. **Email**: rakesh.kumar@ibmcom with details
3. **Include**: 
   - Description of vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if available)

### Response Timeline
- **Acknowledgment**: Within 48 hours
- **Assessment**: Within 1 week
- **Fix**: Based on severity (critical: immediate, high: 1 week, medium: 1 month)

---

## Additional Resources

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [CWE/SANS Top 25](https://cwe.mitre.org/top25/)
- [NIST Cybersecurity Framework](https://www.nist.gov/cyberframework)

---

**Last Updated**: 2026-01-10  
**Version**: 1.0.0-enterprise
