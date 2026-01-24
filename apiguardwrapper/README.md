# ApiGuard Wrapper

- **Aegis**: Universal Applications Code & Configuration Validation Tool
  - **Multiple Input Modes**: ZIP upload, JAR upload, Git Integration
  - **Download Reports**: Download validation reports as ZIP file
  - **Email Notifications**: Receive reports via email
  - **Git Integration**: Connect to GitLab/GitHub. Supports Public/Private repos & Discovery mode.
  - **Rule Engine**: Policy-driven validation using YAML rules.
- **RaksAnalyzer**: Universal document generation framework for Mule, TIBCO, and other projects
- **API Forge**: Universal API response comparison and strategic baseline testing engine
- **GitAnalyzer**: Semantic code analysis tool for validating migrations and configuration changes
- **CSV FileSync**: **[NEW]** Configuration-driven CSV transformation tool with Visual Mapping & Multi-Source Merge support

## Documentation

For detailed enterprise onboarding and architecture, see:
[ENTERPRISE_ONBOARDING.md](ENTERPRISE_ONBOARDING.md)

## Deployment


### Standalone Deployment

**Recommended Build Method**:
Use the provided build scripts to ensure all dependencies (like Aegis) are correctly built and synchronized.

1. **Build with Aegis Integration**:
   ```bash
   # Windows
   build-apiguardwrapper-withaegis.bat

   # Linux/Mac
   ./build-apiguardwrapper-withaegis.sh
   ```
   *Note: This script automatically builds Aegis, synchronizes the latest `rules.yaml` and web resources, and then builds the wrapper.*

2. **Manual Build (Alternative)**:
   ```bash
   mvn clean package
   ```

2. Deploy to Mule runtime:
   ```bash
   copy target\apiguardwrapper-1.0.0-mule-application.jar %MULE_HOME%\apps\apiguardwrapper.jar
   ```

3. Access the application:
   - Portal: `http://localhost:8081/apiguard`
   - RaksAnalyzer: `http://localhost:8081/apiguard/raksanalyzer`

### CloudHub Deployment

When deploying to CloudHub, **no special configuration is required**. The application automatically uses `${mule.home}` for temporary file storage, which is compatible with both standalone and CloudHub environments.

The working directory is configured via the `raksanalyzer.work.dir` property in `mule-app.properties`, which uses `${mule.home}/apps/apiguardwrapper/working/raks` for standalone and will automatically resolve to the appropriate CloudHub path.

### Automatic Cleanup

The application includes scheduled cleanup for temporary files:
- **Schedule**: Runs daily at 2 AM (configurable via `workspace.cleanup.schedule`)
- **Retention**: Deletes files older than 1 day (configurable via `workspace.cleanup.retention.days`)
- **Enable/Disable**: Set `workspace.cleanup.enabled=true/false` in `mule-app.properties`

## Configuration

Key configuration files:
- `mule-app.properties`: Application-wide settings
- `apiguard-common.xml`: Shared configurations (HTTP, File, Email)
- `apiguard-portal.xml`: Portal flows
- `aegis-wrapper.xml`: Aegis integration
- `raksanalyzer-wrapper.xml`: RaksAnalyzer integration

## Important Notes

### RaksAnalyzer - Single Project Limitation

**Current Version**: RaksAnalyzer processes **one project per upload** (ZIP/EAR/JAR/Git).
- For ZIP/EAR uploads containing multiple projects, only the first detected project will be analyzed
- For local folder analysis, each project is analyzed separately
- **Future Enhancement**: Multi-project support is planned for a future release

### Cross-Platform Compatibility

The application is designed to work across platforms:
- ✅ **Windows**: Fully supported (local and CloudHub)
- ✅ **Linux**: Fully supported (local and CloudHub)
- ✅ **CloudHub**: Fully supported (all file paths use `${mule.home}` for portability)

## System Requirements

- Mule Runtime 4.6.0+
- Java 17
- Maven 3.6+
