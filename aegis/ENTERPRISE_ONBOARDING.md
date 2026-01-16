# Enterprise Onboarding Guide

## Overview
This document outlines the standard procedures for onboarding new projects and developers to the Aegis enterprise environment.

## 1. Project Setup
- **JDK Requirement**: Ensure JDK 17+ is installed and configured.
- **Maven**: Use Maven 3.8+ for building the project.
- **IDE Configuration**: IntelliJ IDEA or Eclipse with Maven support is recommended.

## 2. Configuration Standards
- **Logging**: All new code must use SLF4J/Logback. Do not use `System.out.println`.
- **File Paths**: Use `Paths.get()` for all file operations to ensure cross-platform compatibility.
- **Credentials**: Never hardcode secrets. Use environment variables or a secure vault.
- **Documentation**: Maintain `README.md` and keep strictly to the changelog.

## 3. Deployment
- **Build**: Run `mvn clean package` to generate the fat JAR.
- **Artifacts**: The build output is located in the `target/` directory.

## 4. Support
For issues, contact the Enterprise Platform Team or Rakesh Kumar (rakesh.kumar@ibm.com).
