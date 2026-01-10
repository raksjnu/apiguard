# ApiGuard Enterprise Project Cleanup Guide

**Purpose**: This document serves as the standard reference for cleaning up any project in the ApiGuard suite for enterprise production readiness.

**Applies To**: All projects in the ApiGuard ecosystem (muleguard, raksanalyzer, apidiscovery, apiurlcomparison, gitanalyzer, filesync, and their wrappers)

**Last Updated**: 2026-01-10  
**Version**: 1.0

---

## Table of Contents
1. [Cleanup Checklist](#cleanup-checklist)
2. [Trial License Removal](#trial-license-removal)
3. [Logging Standards](#logging-standards)
4. [Credential Management](#credential-management)
5. [Code Quality Standards](#code-quality-standards)
6. [Cross-Platform Compatibility](#cross-platform-compatibility)
7. [Dependency Management](#dependency-management)
8. [Testing Requirements](#testing-requirements)
9. [Documentation Standards](#documentation-standards)
10. [Security Best Practices](#security-best-practices)
11. [Deployment Considerations](#deployment-considerations)

---

## Cleanup Checklist

Use this checklist for every project cleanup:

### Phase 1: Analysis
- [ ] Identify all trial/license-related code
- [ ] Search for hardcoded credentials/secrets
- [ ] Identify all `System.out/err` usage
- [ ] Review test files and dependencies
- [ ] Check for TODO/FIXME comments
- [ ] Analyze dependency vulnerabilities

### Phase 2: Trial License Removal
- [ ] Delete trial license manager classes
- [ ] Remove trial validation logic from main classes
- [ ] Delete trial-related documentation
- [ ] Remove hardcoded contact emails
- [ ] Update README to remove trial references

### Phase 3: Logging Implementation
- [ ] Add SLF4J API dependency
- [ ] Add Logback Classic dependency
- [ ] Create `logback.xml` configuration
- [ ] Replace all `System.out.println()` with `logger.info()`
- [ ] Replace all `System.err.println()` with `logger.error()` or `logger.warn()`
- [ ] Replace `printStackTrace()` with `logger.error("message", exception)`
- [ ] Use parameterized logging: `logger.info("Value: {}", value)`

### Phase 4: Test Cleanup
- [ ] Remove JUnit dependencies if not needed for enterprise
- [ ] Delete `src/test` directory if no tests
- [ ] Remove test scripts (unless demo/sample data)
- [ ] Keep sample data if useful for validation

### Phase 5: Code Quality & Cross-Platform
- [ ] Add input validation to public methods
- [ ] Review exception handling (avoid generic catches)
- [ ] Ensure proper resource management (try-with-resources)
- [ ] Remove unused imports and dead code
- [ ] Fix any compiler warnings
- [ ] **Remove all comments from Java files** (comments allowed only in `.properties`, `.yaml`, `.xml` config files)
- [ ] **Use `Paths.get()` for all file path operations**
- [ ] **No hardcoded `\` or `/` in path strings**
- [ ] **Use `System.lineSeparator()` for line endings**
- [ ] **No hardcoded drive letters or absolute paths**
- [ ] **Test on both Windows and Unix-like systems**

### Phase 6: Documentation
- [ ] Create/update `CHANGELOG.md`
- [ ] Update `README.md` (remove trial references)
- [ ] Update `SECURITY_COMPLIANCE_REPORT.md`
- [ ] Ensure `ENTERPRISE_ONBOARDING.md` exists

### Phase 7: Verification
- [ ] Build JAR successfully
- [ ] Test with wrapper (if applicable)
- [ ] Verify backward compatibility
- [ ] Check JAR size (should be reasonable)
- [ ] Run security scan

---

## Trial License Removal

### What to Remove

#### 1. License Manager Classes
```java
// DELETE files like:
src/main/java/com/*/license/TrialLicenseManager.java
src/main/java/com/*/license/LicenseValidator.java
```

#### 2. Trial Validation Code
```java
// REMOVE blocks like this from main classes:
try {
    TrialLicenseManager licenseManager = new TrialLicenseManager();
    licenseManager.validateTrial();
} catch (LicenseException e) {
    System.err.println("LICENSE ERROR");
    System.exit(1);
}
```

#### 3. Trial Documentation
```bash
# DELETE files:
TRIAL_VERSION_README.md
TRIAL_QUICK_REFERENCE.md
LICENSE_GUIDE.md (if trial-specific)
```

#### 4. Hardcoded Contact Information
```java
// REMOVE hardcoded emails like:
"Please contact raksjnu@gmail.com for a full license."
```

### Verification
After removal, search for:
```bash
grep -r "trial" --include="*.java" --include="*.md"
grep -r "license" --include="*.java" --include="*.md"
grep -r "raksjnu@gmail.com" --include="*.java"
```

---

## Logging Standards

### Required Dependencies (Maven)
```xml
<!-- SLF4J API for structured logging -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>2.0.9</version>
</dependency>

<!-- Logback Classic (SLF4J implementation) -->
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.4.14</version>
</dependency>
```

### Standard Logback Configuration
Create `src/main/resources/logback.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Console Appender -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- File Appender with Rolling -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/${project.artifactId}.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/${project.artifactId}.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
            <totalSizeCap>1GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Root logger -->
    <root level="INFO">
        <appender-ref ref="CONSOLE" />
        <appender-ref ref="FILE" />
    </root>

    <!-- Project-specific logger -->
    <logger name="com.raks" level="INFO" additivity="false">
        <appender-ref ref="CONSOLE" />
        <appender-ref ref="FILE" />
    </logger>
</configuration>
```

### Code Migration Pattern

#### Before (❌ Bad):
```java
public class MyClass {
    public void doSomething() {
        System.out.println("Starting process...");
        try {
            // code
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

#### After (✅ Good):
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyClass {
    private static final Logger logger = LoggerFactory.getLogger(MyClass.class);
    
    public void doSomething() {
        logger.info("Starting process...");
        try {
            // code
        } catch (Exception e) {
            logger.error("Error occurred: {}", e.getMessage(), e);
        }
    }
}
```

### Logging Levels Guide
- `logger.trace()` - Very detailed diagnostic info (rarely used)
- `logger.debug()` - Debugging information for developers
- `logger.info()` - General informational messages (default)
- `logger.warn()` - Warning messages (potential issues)
- `logger.error()` - Error messages (failures)

### Best Practices
1. **Use parameterized logging**: `logger.info("User {} logged in", username)` NOT `logger.info("User " + username + " logged in")`
2. **Log exceptions properly**: `logger.error("Failed to process", exception)` NOT `exception.printStackTrace()`
3. **Avoid logging in loops**: Can cause performance issues
4. **Don't log sensitive data**: Passwords, tokens, PII
5. **Use appropriate levels**: Don't use `error` for informational messages

---

## Credential Management

### Golden Rule
**NEVER** commit credentials to source code or configuration files.

### What NOT to Do (❌)
```java
// NEVER hardcode credentials
String apiKey = "sk-1234567890abcdef";
String password = "mypassword123";
String token = "ghp_abc123xyz";

// NEVER in properties files
api.key=sk-1234567890abcdef
database.password=secretpassword
```

### What TO Do (✅)

#### 1. Environment Variables
```java
String apiKey = System.getenv("API_KEY");
String password = System.getenv("DB_PASSWORD");
```

```properties
# In properties file
api.key=${env:API_KEY}
database.password=${env:DB_PASSWORD}
```

#### 2. External Configuration Files (Not in Git)
```properties
# Add to .gitignore
secrets.properties
credentials.yaml
*.key
*.pem
```

#### 3. Mule Secure Properties (for Mule apps)
```xml
<secure-properties:config name="Secure_Properties" 
    file="secure.yaml" 
    key="${secure.key}"/>
```

#### 4. CloudHub Properties (for CloudHub deployment)
- Use CloudHub Properties tab
- Mark as "Secure" in UI
- Reference as `${secure::property.name}`

### Verification
Search for potential credentials:
```bash
grep -r "password\s*=" --include="*.java" --include="*.properties"
grep -r "api[._-]key" --include="*.java" --include="*.properties"
grep -r "secret" --include="*.java" --include="*.properties"
grep -r "token\s*=" --include="*.java" --include="*.properties"
```

---

## Code Quality Standards

### Input Validation
```java
// ✅ Good: Validate inputs
public Map<String, Object> processProject(String projectPath, String configPath) {
    if (projectPath == null || projectPath.trim().isEmpty()) {
        throw new IllegalArgumentException("Project path cannot be null or empty");
    }
    
    Path path = Paths.get(projectPath);
    if (!Files.exists(path)) {
        throw new IllegalArgumentException("Project path does not exist: " + projectPath);
    }
    
    // Continue processing...
}
```

### Exception Handling
```java
// ❌ Bad: Generic catch
try {
    // code
} catch (Exception e) {
    e.printStackTrace();
}

// ✅ Good: Specific exceptions
try {
    // code
} catch (IOException e) {
    logger.error("Failed to read file: {}", filePath, e);
    throw new RuntimeException("File operation failed", e);
} catch (IllegalArgumentException e) {
    logger.warn("Invalid argument: {}", e.getMessage());
    return Collections.emptyMap();
}
```

### Resource Management
```java
// ❌ Bad: Manual close
InputStream input = null;
try {
    input = new FileInputStream(file);
    // use input
} finally {
    if (input != null) {
        input.close();
    }
}

// ✅ Good: Try-with-resources
try (InputStream input = new FileInputStream(file)) {
    // use input
} catch (IOException e) {
    logger.error("Failed to read file", e);
}
```

### Null Safety
```java
// ✅ Good: Check for null
public String processValue(String value) {
    if (value == null) {
        return null; // or throw exception, or return default
    }
    return value.trim().toLowerCase();
}

// ✅ Good: Use Optional for return values
public Optional<User> findUser(String id) {
    User user = database.findById(id);
    return Optional.ofNullable(user);
}
```

---

## Cross-Platform Compatibility

### Critical Rule
**ALWAYS** use platform-independent code to ensure applications work on Windows, Linux, and macOS.

### File Path Handling

#### ❌ NEVER Do This:
```java
// WRONG: Hardcoded Windows paths
String path = "C:\\Users\\rakesh\\project\\file.txt";
String reportPath = projectPath + "\\reports\\output.html";

// WRONG: Hardcoded Unix paths  
String path = "/home/rakesh/project/file.txt";
String reportPath = projectPath + "/reports/output.html";

// WRONG: Manual path concatenation
String fullPath = baseDir + "\\" + subDir + "\\" + fileName;
```

#### ✅ ALWAYS Do This:
```java
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.File;

// ✅ CORRECT: Use Path API (Java 7+, preferred)
Path projectPath = Paths.get(baseDir, subDir, fileName);
Path reportPath = projectPath.resolve("reports").resolve("output.html");
String pathString = projectPath.toString(); // Platform-appropriate separators

// ✅ CORRECT: Use File.separator
String fullPath = baseDir + File.separator + subDir + File.separator + fileName;

// ✅ CORRECT: Use Paths.get for multiple segments
Path fullPath = Paths.get(baseDir, subDir, fileName);
```

### Path Separator Handling

```java
// ❌ WRONG: Hardcoded separators
String[] parts = path.split("\\\\");  // Only works on Windows
String[] parts = path.split("/");     // Only works on Unix

// ✅ CORRECT: Use File.separator or Path API
String[] parts = path.split(Pattern.quote(File.separator));

// ✅ BETTER: Use Path API
Path p = Paths.get(path);
int nameCount = p.getNameCount();
Path fileName = p.getFileName();
```

### Line Ending Handling

```java
// ❌ WRONG: Hardcoded line endings
String output = "Line 1\n" + "Line 2\n";      // Unix only
String output = "Line 1\r\n" + "Line 2\r\n";  // Windows only

// ✅ CORRECT: Use System.lineSeparator()
String output = "Line 1" + System.lineSeparator() + 
                "Line 2" + System.lineSeparator();

// ✅ CORRECT: Use %n in format strings
String output = String.format("Line 1%nLine 2%n");

// ✅ CORRECT: For logging (SLF4J handles this)
logger.info("Line 1\nLine 2");  // OK in logs
```

### File Operations

```java
// ✅ CORRECT: Platform-independent file operations
import java.nio.file.*;

// Create directories
Path dir = Paths.get("reports", "output");
Files.createDirectories(dir);  // Creates parent dirs if needed

// Write files
Path file = dir.resolve("report.html");
Files.write(file, content.getBytes(StandardCharsets.UTF_8));

// Read files
String content = Files.readString(file, StandardCharsets.UTF_8);

// Check existence
if (Files.exists(file)) {
    // file exists
}

// Delete files
Files.deleteIfExists(file);
```

### Temporary Files and Directories

```java
// ❌ WRONG: Hardcoded temp paths
String tempDir = "C:\\Temp\\myapp";
String tempDir = "/tmp/myapp";

// ✅ CORRECT: Use system temp directory
Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "myapp");
Files.createDirectories(tempDir);

// ✅ CORRECT: Use Files.createTempFile/createTempDirectory
Path tempFile = Files.createTempFile("muleguard-", ".zip");
Path tempDir = Files.createTempDirectory("muleguard-work-");
```

### Environment-Specific Code

```java
// ✅ CORRECT: Detect OS when necessary
String os = System.getProperty("os.name").toLowerCase();
boolean isWindows = os.contains("win");
boolean isMac = os.contains("mac");
boolean isUnix = os.contains("nix") || os.contains("nux") || os.contains("aix");

// Use only when platform-specific behavior is required
if (isWindows) {
    // Windows-specific code
} else {
    // Unix/Mac code
}
```

### URL Path Handling (Web Applications)

```javascript
// ❌ WRONG: Hardcoded absolute paths in JavaScript
var url = "/muleguard/main";  // Breaks in wrapper context

// ✅ CORRECT: Dynamic base path detection
var isInMuleWrapper = path.includes('/apiguard/');
var basePath = isInMuleWrapper ? '/apiguard/muleguard' : '/muleguard';
var url = basePath + '/main';
```

### Common Pitfalls to Avoid

#### 1. String Replacement for Paths
```java
// ❌ WRONG
String unixPath = windowsPath.replace("\\", "/");

// ✅ CORRECT: Use Path API
Path path = Paths.get(windowsPath);
String normalizedPath = path.toString();  // Platform-appropriate
```

#### 2. Hardcoded Drive Letters
```java
// ❌ WRONG
if (path.startsWith("C:")) { ... }

// ✅ CORRECT: Check if path is absolute
Path p = Paths.get(path);
if (p.isAbsolute()) { ... }
```

#### 3. Case Sensitivity
```java
// ❌ RISKY: Case-sensitive comparison (fails on Windows)
if (fileName.equals("Report.html")) { ... }

// ✅ BETTER: Case-insensitive for file names
if (fileName.equalsIgnoreCase("Report.html")) { ... }

// ✅ BEST: Use Path comparison
Path p1 = Paths.get("Report.html");
Path p2 = Paths.get("report.html");
// On Windows: p1.equals(p2) returns true
// On Unix: p1.equals(p2) returns false
```

### Testing Cross-Platform Code

```bash
# Test on multiple platforms
# Windows
mvn clean package
java -jar target/myapp.jar

# Linux/Mac
mvn clean package
java -jar target/myapp.jar

# Verify paths in logs
grep "Path:" logs/myapp.log
```

### Checklist for Cross-Platform Code

- [ ] No hardcoded `\` or `/` in path strings
- [ ] Use `Paths.get()` or `File.separator` for all path operations
- [ ] Use `System.lineSeparator()` or `%n` for line endings
- [ ] Use `System.getProperty("java.io.tmpdir")` for temp files
- [ ] No hardcoded drive letters (C:, D:, etc.)
- [ ] Use `Files.createDirectories()` instead of `mkdir()`
- [ ] Use `Path.resolve()` for path concatenation
- [ ] Test on both Windows and Unix-like systems
- [ ] Use case-insensitive comparisons for file names when appropriate
- [ ] Avoid platform-specific APIs unless absolutely necessary

### Real-World Example: MuleGuard Path Fix

```java
// ❌ BEFORE (Platform-specific)
String reportPath = projectPath + "\\muleguard-reports\\CONSOLIDATED-REPORT.html";

// ✅ AFTER (Cross-platform)
Path reportPath = Paths.get(projectPath)
    .resolve("muleguard-reports")
    .resolve("CONSOLIDATED-REPORT.html");
String reportPathString = reportPath.toString();
```

---

## Dependency Management


### Security Scanning
```bash
# Run Maven dependency check
mvn dependency-check:check

# View dependency tree
mvn dependency:tree
```

### Update Strategy
1. **Critical Security Updates**: Apply immediately
2. **High Priority**: Within 1 week
3. **Medium Priority**: Within 1 month
4. **Low Priority**: Next release cycle

### Recommended Versions (as of 2026-01-10)
```xml
<!-- Logging -->
<slf4j.version>2.0.9</slf4j.version>
<logback.version>1.4.14</logback.version>

<!-- JSON/YAML -->
<jackson.version>2.18.2</jackson.version>
<snakeyaml.version>2.2</snakeyaml.version>

<!-- Apache Commons -->
<commons-io.version>2.18.0</commons-io.version>
<commons-lang3.version>3.18.0</commons-lang3.version>
<commons-text.version>1.10.0</commons-text.version>

<!-- Excel -->
<poi.version>5.4.0</poi.version>

<!-- XML -->
<dom4j.version>2.1.4</dom4j.version>

<!-- Log4j (if needed) -->
<log4j.version>2.24.3</log4j.version>
```

### Exclude Vulnerable Dependencies
```xml
<dependency>
    <groupId>some.group</groupId>
    <artifactId>some-artifact</artifactId>
    <version>1.0.0</version>
    <exclusions>
        <exclusion>
            <groupId>vulnerable.group</groupId>
            <artifactId>vulnerable-artifact</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

---

## Testing Requirements

### Unit Tests for Enterprise
**Decision Point**: Do we need unit tests in the distributed JAR?

#### Keep Tests If:
- Tests are part of CI/CD pipeline
- Customers may run tests for validation
- Tests serve as documentation

#### Remove Tests If:
- Tests are only for development
- JAR size is a concern
- No customer-facing test requirements

### Removing Tests
```bash
# Delete test directory
rm -rf src/test

# Remove test dependencies from pom.xml
# Delete <scope>test</scope> dependencies like:
# - junit
# - mockito
# - hamcrest
```

### Sample Data vs Tests
**Keep**: Sample/demo data in `testData/` for validation
**Remove**: JUnit test classes in `src/test/`

---

## Documentation Standards

### Required Files

#### 1. README.md
Must include:
- Project description
- Features
- Prerequisites
- Installation instructions
- Usage examples (CLI and GUI if applicable)
- Configuration guide
- Build instructions

#### 2. CHANGELOG.md
Format:
```markdown
# Changelog

## [Version] - YYYY-MM-DD

### Added
- New features

### Changed
- Modifications to existing features

### Removed
- Deleted features

### Fixed
- Bug fixes

### Security
- Security improvements
```

#### 3. ENTERPRISE_ONBOARDING.md
- Architecture overview
- Deployment guide
- Configuration reference
- Troubleshooting
- Support contacts

#### 4. SECURITY_COMPLIANCE_REPORT.md
- Dependency versions
- Known vulnerabilities (none expected)
- Last security audit date
- Compliance status

### Remove Trial Documentation
Delete:
- `TRIAL_VERSION_README.md`
- `TRIAL_QUICK_REFERENCE.md`
- Any trial-specific guides

---

## Security Best Practices

### Code Security

#### 1. No Hardcoded Secrets
```bash
# Search for potential secrets
grep -r "password\s*=" .
grep -r "api.*key" .
grep -r "secret" .
```

#### 2. Input Validation
- Validate all user inputs
- Sanitize file paths
- Check for path traversal attacks

#### 3. Dependency Scanning
```bash
mvn dependency-check:check
```

### Deployment Security

#### Standalone JAR
```bash
# Set restrictive permissions
chmod 750 myapp.jar

# Run as non-root user
sudo -u appuser java -jar myapp.jar
```

#### CloudHub (Mule Apps)
- Use secure properties
- Enable VPC if needed
- Restrict API access
- Use HTTPS only

### Log Security
- Don't log passwords or tokens
- Restrict log file permissions: `chmod 640 logs/*.log`
- Implement log rotation
- Monitor logs for suspicious activity

---

## Deployment Considerations

### JAR Size Optimization
- Remove trial code: ~0.3-0.5 MB savings
- Exclude test dependencies
- Use `maven-shade-plugin` or `maven-assembly-plugin`
- Target size: < 30 MB for most projects

### Backward Compatibility
When cleaning up projects used by wrappers:

#### Critical Rules:
1. **Don't change public API method signatures**
2. **Don't change return types**
3. **Don't remove public methods**
4. **Don't change package names**

#### Safe Changes:
1. ✅ Internal implementation changes
2. ✅ Logging improvements
3. ✅ Exception handling improvements
4. ✅ Adding new methods (backward compatible)

### Verification Checklist
```bash
# 1. Build the cleaned project
mvn clean package

# 2. Copy JAR to wrapper
cp target/myproject-1.0.0.jar ../wrapper/lib/

# 3. Build wrapper
cd ../wrapper
mvn clean compile

# 4. If wrapper builds successfully, backward compatibility maintained ✅
```

---

## Git Workflow

### Branch Strategy
```bash
# Create feature branch
git checkout -b feature/enterprise-cleanup

# Make changes, commit incrementally
git add -A
git commit -m "Remove trial license code"
git commit -m "Implement SLF4J logging"
git commit -m "Add enterprise documentation"

# Merge to main branch (after testing)
git checkout ibmct  # or master
git merge feature/enterprise-cleanup
```

### Commit Message Format
```
<type>: <short description>

<detailed description>

<list of changes>
```

Example:
```
Enterprise cleanup: Remove trial license and implement SLF4J logging

- Removed TrialLicenseManager.java and trial documentation
- Added SLF4J API and Logback dependencies
- Replaced all System.out/err with structured logging
- JAR size reduced from 26.29MB to 25.96MB
- Maintained backward compatibility with wrapper
```

---

## Project-Specific Notes

### MuleGuard
- ✅ Completed 2026-01-10
- Trial license removed
- SLF4J logging implemented
- JAR: 25.96 MB

### RaksAnalyzer
- Status: Pending cleanup
- Has trial code: TBD
- Logging: Needs SLF4J migration

### ApiDiscovery
- Status: Pending cleanup
- Has trial code: TBD
- Logging: Needs SLF4J migration

### ApiUrlComparison
- Status: Pending cleanup
- Has trial code: TBD
- Logging: Needs SLF4J migration

### GitAnalyzer
- Status: Pending cleanup
- Has trial code: TBD
- Logging: Needs SLF4J migration

### FileSync
- Status: Pending cleanup
- Has trial code: TBD
- Logging: Needs SLF4J migration

---

## Quick Reference Commands

### Search for Issues
```bash
# Find trial code
grep -r "trial" --include="*.java" --include="*.md"

# Find System.out/err
grep -r "System.out.print\|System.err.print" --include="*.java"

# Find hardcoded credentials
grep -r "password\s*=" --include="*.java" --include="*.properties"

# Find TODO/FIXME
grep -r "TODO\|FIXME" --include="*.java"
```

### Build and Test
```bash
# Clean build
mvn clean compile

# Build JAR
mvn clean package

# Run dependency check
mvn dependency-check:check

# Check JAR size
ls -lh target/*.jar
```

### Git Operations
```bash
# Create branch
git checkout -b feature/enterprise-cleanup

# Stage all changes
git add -A

# Commit
git commit -m "Description"

# View log
git log --oneline -5
```

---

## Success Criteria

A project is considered "enterprise-ready" when:

- [ ] ✅ No trial license code remains
- [ ] ✅ All logging uses SLF4J framework
- [ ] ✅ No hardcoded credentials or secrets
- [ ] ✅ No `System.out/err` usage
- [ ] ✅ All dependencies are up-to-date and secure
- [ ] ✅ Wrapper builds successfully (if applicable)
- [ ] ✅ All tests pass (if tests exist)
- [ ] ✅ Documentation updated (README, CHANGELOG)
- [ ] ✅ Backward compatibility verified
- [ ] ✅ JAR size is reasonable (< 30MB typically)
- [ ] ✅ Security scan passes
- [ ] ✅ Code quality standards met

---

## Support and Questions

For questions about this cleanup process:
- **Email**: raksjnu@gmail.com
- **Document Location**: `C:\raks\apiguard\ENTERPRISE_CLEANUP_GUIDE.md`
- **Last Updated**: 2026-01-10

---

**Remember**: This is a living document. Update it as we learn from each project cleanup!
