# ApiGuard Wrapper

Mule 4 wrapper application for ApiGuard portal, MuleGuard validator, and RaksAnalyzer document generator.

## Components

- **ApiGuard Portal**: User registration and tool access portal
- **MuleGuard**: Mule configuration validator and analyzer
- **RaksAnalyzer**: Universal document generation framework for Mule, TIBCO, and other projects
- **ApiUrlComparison**: API response comparison and baseline testing tool (featuring new Baseline Header Loading and Endpoint Display)

## Deployment

### Standalone Deployment

1. Build the project:
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
- `muleguard-wrapper.xml`: MuleGuard integration
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
