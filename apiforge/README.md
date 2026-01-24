# API Forge - Universal Semantic Compliance Engine

**API Forge** is a strategic analysis engine designed for **Universal API Compliance**. It allows engineering and ops teams to validate migrations, perform massive structural testing, and manage post-deployment health using deep semantic inspection.

## Key Features

- **Semantic A/B Comparison**: Deep structural comparison of JSON and XML responses.
- **Strategic Baseline Guard**: Capture "Golden States" and verify future releases against saved truth.
- **Massive Matrix Testing**: Use **Iteration Tokens** to verify hundreds of permutations in a single run.
- **JWT Auth Studio**: Built-in inspector for decoding JWT claims and verifying security headers.
- **Data Foundry**: Utility suite for Minify, Pretty-print, and Base64 transformations.
- **Response Header Guard**: Automated comparison of critical HTTP headers.
- **Smart Cleanup**: Tool starts clean and empty every time, ensuring no stale test data is used.

## Strategic Enterprise Scenarios

API Forge provides unique capabilities that go beyond traditional professional tools:

### 1. 🚀 Zero-Day Release Guard (Ops/SRE)
Capture a stable "Golden Baseline" of your services before a production release. Immediately after deployment, run a comparison against the baseline to detect semantic shifts before users report issues. Transition from **Reactive** to **Proactive** incident management.

### 2. 🧪 Universal Compliance (Dev/QA)
Verify semantic consistency across massive data permutations. Use **Iteration Tokens** to run matrix tests that ensure your API behaves correctly across all business rules, preventing regression in complex logic.

### 3. 🏗️ Bit-Perfect Core Parity (Architecture)
During cloud migrations or legacy modernizations, use API Forge as the ultimate source of truth. Compare legacy output against the new cloud-native service to ensure bit-perfect semantic parity during critical transitions.

## Installation & Setup

**Quick Start Scripts:**
- **Windows**: Double-click `start-mock-server.bat` or run from command prompt
- **macOS/Linux**: Run `./start-mock-server.sh` from terminal (make executable first: `chmod +x start-mock-server.sh`)

The mock server starts on ports `9091-9094` (REST API 1, REST API 2, SOAP API 1, SOAP API 2) and provides test endpoints for development and validation.

## Configuration

Create a `config.yaml` file to define your API comparison settings:

```yaml
testType: REST  # or SOAP

# Iteration strategy
iterationController: ONE_BY_ONE  # or ALL_COMBINATIONS
maxIterations: 100

# Configuration block for REST APIs
rest:
  api1:
    baseUrl: "http://localhost:9091"
    authentication:
      clientId: "user1"
      clientSecret: "pass1"
    operations:
      - name: "createResource"
        methods: ["POST"]
        path: "/api/resource"
        payloadTemplatePath: "testData/api1/payload.json"
        headers:
          Content-Type: "application/json"
  
  api2:
    baseUrl: "http://localhost:9092"
    authentication:
      clientId: "user2"
      clientSecret: "pass2"
    operations:
      - name: "createResource"
        methods: ["POST"]
        path: "/api/resource"
        payloadTemplatePath: "testData/api2/payload.json"
        headers:
          Content-Type: "application/json"

# Configuration block for SOAP APIs
soap:
  api1:
    baseUrl: "http://localhost:9093/ws/AccountService"
    authentication:
      clientId: "user1"
      clientSecret: "pass1"
    operations:
      - name: "getAccountDetails"
        methods: ["POST"]
        headers:
          Content-Type: "text/xml;charset=UTF-8"
          SOAPAction: "getAccountDetails"
        payloadTemplatePath: "testData/api1/payload.xml"

  api2:
    baseUrl: "http://localhost:9094/ws/AccountService"
    authentication:
      clientId: "user2"
      clientSecret: "pass2"
    operations:
      - name: "getAccountDetails"
        methods: ["POST"]
        headers:
          Content-Type: "text/xml;charset=UTF-8"
          SOAPAction: "getAccountDetails"
        payloadTemplatePath: "testData/api2/payload.xml"

# Token definitions for iteration testing
tokens:
  account:
    - "999"
    - "1000"
    - "1001"
  uuid:
    - "id1"
    - "id2"
```

### Payload Templates

Create JSON or XML payload templates with token placeholders:

**JSON Example:**
```json
{
  "account": "1479",
  "myaccountvalue": 987,
  "name": "Rakesh",
  "uuid": "testid"
}
```

The tool uses **field name matching** for token replacement. If a field name contains a token name (case-insensitive), it will be replaced during iterations.

## Iteration Logic

### Original Input Payload (Iteration #1)
The tool **always** executes the original payload first without any token replacements. This ensures you have a baseline execution with your exact input data.

### ONE_BY_ONE Strategy
After the baseline, tests each token value individually:
- **Iteration 1**: Original payload (no replacements)
- **Iteration 2**: First token, first value
- **Iteration 3**: First token, second value
- **Iteration 4**: Second token, first value
- **Iteration 5**: Second token, second value

**Total Iterations**: 1 (baseline) + sum of all token values

### ALL_COMBINATIONS Strategy
After the baseline, tests all possible combinations:
- **Iteration 1**: Original payload (no replacements)
- **Iterations 2+**: All combinations of token values

**Total Iterations**: 1 (baseline) + (product of all token value counts)

## Reports

### CLI HTML Report
- **Header**: "API Response Comparison Tool - APITestingGuard"
- **Execution Summary**: Total iterations, duration, timestamp
- **Comparison Summary**: Matches, mismatches, errors
- **Iteration Details**: Expandable sections with full request/response data
- **Differences**: Highlighted mismatches with field-level details

### GUI Dashboard
- **Real-time Results**: Live updates as comparisons execute
- **Visual Status Indicators**: Color-coded match/mismatch/error badges
- **Expandable Iterations**: Click to view detailed request/response data
- **Professional Purple Theme**: Subtle borders with hover effects for modern look
- **XML Pretty-Printing**: Formatted XML/SOAP payloads with proper indentation
- **Horizontal Scrolling**: View complete long payloads without truncation
- **Timestamp**: Report generation time with timezone
- **Full-Width Layout**: Utilizes 95% of screen width for better visibility

### JSON Report
Machine-readable format for automation and integration:
```json
{
  "status": "MATCH",
  "operationName": "createResource (Original Input Payload)",
  "iterationTokens": {},
  "timestamp": "2025-12-08 13:00:00",
  "api1": { ... },
  "api2": { ... },
  "differences": []
}
```

## Baseline Testing

The tool supports **baseline testing** to capture API responses as a baseline and compare future API responses against that baseline. This is useful for:
- **Regression testing**: Ensure new code changes don't break existing API behavior
- **Upgrade validation**: Compare API responses before and after system upgrades
- **Version comparison**: Track API response changes across different versions

### How Baseline Testing Works

1. **Capture Mode**: Run your API tests and save all request/response data as a "baseline"
2. **Compare Mode**: Run the same tests later and compare against the saved baseline
3. **Results**: Get detailed comparison reports showing any differences

### Baseline Folder Structure

Baselines are organized in a hierarchical folder structure:

```
baselines/
└── {serviceName}/           # e.g., "AccountService"
    └── {date}/              # e.g., "20251208" (YYYYMMDD)
        └── {run-id}/        # e.g., "run-001"
            ├── metadata.json       # Run metadata (description, tags, timestamp)
            ├── summary.json        # Summary of results
            └── iteration-{N}/      # One folder per iteration
                ├── request.xml
                ├── request-headers.json
                ├── request-metadata.json
                ├── response.xml
                ├── response-headers.json
                └── response-metadata.json
```

### Using Baseline Testing (GUI)

The web GUI provides an intuitive interface for baseline testing:

1. **Start the GUI**:
   ```bash
   # Windows
   start-gui.bat
   
   # Linux/Mac
   ./start-gui.sh
   ```

2. **Switch to Baseline Mode**:
   - In the GUI, change the **Mode** dropdown from "Live (API1 vs API2)" to "Baseline Testing"
   - The URL2 field will automatically hide (only one API endpoint needed)

3. **Capture a Baseline**:
   - Select **Operation**: "Capture Baseline"
   - Fill in:
     - **Service Name**: e.g., "AccountService"
     - **Description**: e.g., "Pre-upgrade baseline v1.0.0"
     - **Tags**: e.g., "v1.0.0, pre-upgrade"
   - Configure your API endpoint, operation, and test parameters
   - Click **"Capture Baseline"**
   - Results are saved to: `baselines/{serviceName}/{date}/{run-id}/`

4. **Compare Against Baseline**:
   - Select **Operation**: "Compare with Baseline"
   - Choose from dropdowns:
     - **Service**: Select the service (e.g., "AccountService")
     - **Date**: Select the date when baseline was captured
     - **Run**: Select the specific run to compare against
   - Configure your current API endpoint and test parameters
   - Click **"Compare with Baseline"**
   - View comparison results showing matches and mismatches

**Working Directory**: The GUI includes a "Working Directory" field to specify a custom location for baseline storage. Leave empty to use the default location, or enter a custom path to browse/save baselines in different locations.

### Using Baseline Testing (CLI)

#### Capture a Baseline

Update `config.yaml`:

```yaml
# Comparison mode: "LIVE" or "BASELINE"
comparisonMode: "BASELINE"

baseline:
  # Operation: "CAPTURE" or "COMPARE"
  operation: "CAPTURE"
  
  # Directory where baselines are stored
  storageDir: "baselines"
  
  # Service name for organization
  serviceName: "AccountService"
  
  # Description for this baseline
  description: "Pre-upgrade baseline v1.0.0"
  
  # Tags for identification
  tags:
    - "v1.0.0"
    - "pre-upgrade"
```

Run the comparison:

```bash
java -jar target/apiforge-1.0.0-shaded.jar
```

#### Compare Against Baseline

Update `config.yaml`:

```yaml
comparisonMode: "BASELINE"

baseline:
  operation: "COMPARE"
  storageDir: "baselines"
  serviceName: "AccountService"
  
  # Specify which baseline to compare against
  compareDate: "20251208"      # Date folder (YYYYMMDD)
  compareRunId: "run-001"      # Run ID to compare against
```

Run the comparison:

```bash
java -jar target/apiforge-1.0.0-shaded.jar
```

### Baseline Testing Example Workflow

**Scenario**: You're upgrading your API from v1.0 to v2.0 and want to ensure no breaking changes.

1. **Before Upgrade - Capture Baseline**:
   ```yaml
   comparisonMode: "BASELINE"
   baseline:
     operation: "CAPTURE"
     serviceName: "AccountService"
     description: "Pre-upgrade baseline v1.0.0"
     tags: ["v1.0.0", "pre-upgrade"]
   
   url1: "http://localhost:8080/api/v1/account"
   ```
   
   This creates: `baselines/AccountService/20251208/run-001/`

2. **After Upgrade - Compare**:
   ```yaml
   comparisonMode: "BASELINE"
   baseline:
     operation: "COMPARE"
     serviceName: "AccountService"
     compareDate: "20251208"
     compareRunId: "run-001"
   
   url1: "http://localhost:8080/api/v2/account"
   ```
   
   This compares v2.0 responses against the v1.0 baseline.

3. **Review Results**:
   - **MATCH**: API behavior unchanged ✓
   - **MISMATCH**: Review differences to ensure they're intentional
   - HTML report shows detailed comparison with highlighted differences

### Baseline Metadata

Each baseline run includes metadata for easy identification:

**metadata.json**:
```json
{
  "serviceName": "AccountService",
  "description": "Pre-upgrade baseline v1.0.0",
  "tags": ["v1.0.0", "pre-upgrade"],
  "timestamp": "2025-12-08T17:40:23.456Z",
  "totalIterations": 5
}
```

This metadata is displayed in:
- GUI dropdown menus (when selecting a baseline to compare)
- HTML comparison reports
- CLI output

## Examples

### Basic REST API Comparison
```bash
# 1. Start mock server (optional, for testing)
mvn compile exec:java -Dexec.mainClass="com.raks.apiforge.MockApiServer"

# 2. Run comparison
java -jar target/apiforge-1.0.0.jar --config config.yaml --output ./reports

# 3. View results
open reports/results.html
```

### Using the GUI
```bash
# 1. Launch GUI
java -jar target/apiforge-1.0.0.jar

# 2. Configure in browser:
#    - Select Type (REST/SOAP) and Mode (Live/Baseline)
#    - Enter endpoint URLs
#    - Check "Enable Authentication" if your API is secured (Basic Auth)
#    - Enter "Ignored Fields" to skip comparison for dynamic values (e.g., timestamp)
#    - Add payload template and define tokens
#    - Click "Run Comparison"

# 3. View results in the Execution Dashboard
```

## CloudHub vs Standalone Deployment
Choose the deployment model that best fits your testing needs:

### 1. Standalone Java Tool (Local)
**Best for:**
- **Local Development:** Testing APIs running on localhost or accessible from your machine.
- **Baseline Management:** Creating and storing baseline files persistently on your local disk (e.g., `C:\Baselines`).
- **File Access:** Fully supports interacting with your local filesystem (Windows/Mac/Linux).

**How to Run:**
`java -jar apiforge-1.0.0.jar`

### 2. CloudHub Deployment (Mule Application)
**Best for:**
- **Integration Testing:** Running comparisons from within the MuleSoft environment.
- **Shared Access:** Exposing the tool to the team via a central URL.
- **Ephemeral Testing:** Quick validation runs where persistent baselines are not required or are managed externally.

**Limitations:**
- **No Local File Access:** The CloudHub application runs on a remote server. It **cannot access** files on your local computer (e.g., `C:\users`). Do not try to save baselines to local paths.
- **Ephemeral Storage:** Files saved to the CloudHub worker (e.g., `/tmp`) will be lost if the application restarts.


## Troubleshooting

### Port Already in Use (GUI)
The GUI automatically finds an available port if 4567 is taken.

### 404 Errors
- Verify `baseUrl` and `path` in config.yaml
- Check that endpoints are accessible
- Ensure mock server is running if testing locally

### Token Replacement Not Working
- Verify field names in payload contain token names (case-insensitive)
- Check that tokens are defined in config.yaml
- Review the "Original Input Payload" iteration to see baseline behavior

### Build Errors
```bash
# Clean and rebuild
mvn clean package

# Skip tests if needed
mvn clean package -DskipTests
```

## Project Structure

```
apiforge/
├── src/main/java/com/myorg/apiforge/
│   ├── ApiForgeMain.java      # CLI entry point
│   ├── ApiForgeWeb.java       # GUI entry point
│   ├── ComparisonService.java         # Core comparison logic
│   ├── TestDataGenerator.java         # Iteration generation
│   ├── PayloadProcessor.java          # Token replacement
│   ├── ComparisonEngine.java          # Response comparison
│   ├── HtmlReportGenerator.java       # CLI report generation
│   ├── MockApiServer.java             # Mock server for testing
│   └── http/ApiClient.java            # HTTP client wrapper
├── src/main/resources/
│   └── public/                        # GUI assets
│       ├── index.html
│       ├── app.js
│       └── style.css
├── config.yaml                        # Configuration file
├── pom.xml                           # Maven configuration
└── README.md                         # This file
```

## Contributing

This tool is designed for API testing and validation. Feel free to extend it with additional features such as:
- OAuth 2.0 authentication support
- GraphQL API support
- Custom comparison rules
- Performance metrics
- Parallel execution

## License

[Specify your license here]

---
**For any further inquiries, reach out to:**

- **Author**: Rakesh Kumar
- **Email**: Rakesh.Kumar@ibm.com
- **Role**: Application Architect
---

