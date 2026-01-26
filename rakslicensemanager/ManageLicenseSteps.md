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
    -   **Client Name**: Enter the name of the client (e.g., `Truist Bank`).
    -   **Expiry Date**: Enter the date in `YYYY-MM-DD` format (e.g., `2026-12-31`).

5.  **Get the License**:
    -   The tool will print a long text string starting with `eyJ...`.
    -   **Copy this entire string.**
    -   It is also saved to a file named `generated.license` in the same folder.

6.  **Distribute**:
    -   Send this string to the client or configure it in the application environment variables as `LICENSE_KEY`.
