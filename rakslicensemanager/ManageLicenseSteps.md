# How to Create a New License

Follow these simple steps whenever you need to generate a license for a new client.

## Prerequisites
-   Ensure you have the `private.key` file in this directory. **DO NOT LOSE THIS FILE.**
-   If you lose `private.key`, you must generate a new key pair (Option 1 in the tool), which will **INVALIDATE ALL PREVIOUS LICENSES** (they will stop working).

## Step-by-Step Guide

1.  **Open Terminal / Command Prompt** inside this folder:
    ```cmd
    cd c:\raks\apiguard\rakslicensemanager
    ```

2.  **Run the License Manager Tool**:
    ```cmd
    java -jar target/rakslicensemanager-1.0.0.jar
    ```

3.  **Select "Generate License Key"**:
    -   Enter `2` when prompted for choice.

4.  **Enter Client Details**:
    -   **Client Name**: Enter the name of the client (e.g., `Raks Org`).
    -   **Expiry Date**: Enter the date in `YYYY-MM-DD` format (e.g., `2026-12-31`).

5.  **Get the License**:
    -   The tool will print a long text string starting with `eyJ...`.
    -   **Copy this entire string.**
    -   It is also saved to a file named `generated.license` in the same folder.

6.  **Distribute and Activate**:
    -   Provide the license string to the client.
    -   They can activate the application using any of the options below.

## Application Activation Options

Once a client has the license string, they can use it in **any** of these three ways:

### Option A: Local File (Easiest for Users)
Create a file named `license.key` in the same folder as the application JAR and paste the license string inside. The application will detect it automatically on startup.

### Option B: System Property
Provide the key as a Java system property when launching:
```cmd
java -Draks.license.key=YOUR_LICENSE_STRING -jar aegis-1.0.0.jar
```

### Option C: Environment Variable (Mule Wrapper)
For the MuleSoft wrapper, the key (license.key) is typically configured in the `mule-app.properties` file or as an environment variable named `LICENSE_KEY`.
