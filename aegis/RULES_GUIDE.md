# Aegis Rules Guide

This guide details the validation rules enforced by Aegis. Aegis is a multi-technology analysis tool, and rules are categorized by the technology stack they validate.

## Table of Contents
1.  [Java / Spring Boot](#java--spring-boot)
2.  [Tibco](#tibco)
3.  [Python](#python)
4.  [MuleSoft](#mulesoft)
5.  [Generic Config](#generic-config)

---

## <a id="java--spring-boot"></a>Java / Spring Boot

*Coming Soon* - Standard rules for Java 17+ and Spring Boot 3.x best practices.

## <a id="tibco"></a>Tibco

*Coming Soon* - Rules for Tibco BW5/BW6 project validation.

## <a id="python"></a>Python

*Coming Soon* - PEP8 compliance and security checks for Python projects.

---

## <a id="mulesoft"></a>MuleSoft

Detailed validation rules for Mule 4.x applications, covering code quality, security, and migration readiness.

### Code Rules (000-099)

#### RULE-000: Generic Code Check
**Description**: Scans project files for specific forbidden tokens or patterns.
- **Goal**: Prevent usage of `System.out.println` and `printStackTrace`.
- **Failure Condition**: If any of these tokens are found in Java or XML files.

#### RULE-001: Validate Parent POM
**Description**: Ensures the project `pom.xml` inherits from the standard parent POM.
- **Requirement**: `groupId: com.raks.eapi`, `artifactId: MuleParentPom`, `version: LATEST`.

#### RULE-002: Validate Maven Plugin Version
**Description**: Ensures `mule.maven.plugin.version` is set to the required version.
- **Requirement**: `4.9.0`.

#### RULE-003: Forbidden Plugins
**Description**: Removes unnecessary or harmful Maven plugins.
- **Forbidden**: `maven-clean-plugin`, `maven-surefire-plugin` (version 2.22.0).

#### RULE-004: Forbidden Dependencies
**Description**: Removes dependencies irrelevant to the runtime or security standards.
- **Forbidden**: `spring-security-ldap`, `spring-security-web`, `db2jcc_license_cu`, `mule-core-ee`.

#### RULE-005: IBM MQ Cipher Suite
**Description**: Validates that the IBM MQ Connector uses the approved Cipher Suite.
- **Requirement**: `cipherSuite="TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"`.
- **Note**: This rule only runs if the IBM MQ connector is present in the project.

#### RULE-006: Required Dependencies
**Description**: Ensures `apimuleutilities` dependency is present.
- **Goal**: Standardize utility functions.

#### RULE-007: Mule Artifact Validation
**Description**:  Validates `mule-artifact.json` for compliance.
- **Requirements**:
    - `minMuleVersion`: "4.9.0"
    - `javaSpecificationVersions`: "17" (Supports string "17" or array ["17"])
    - `secureProperties`: Must be explicitly defined.

#### RULE-008: API Autodiscovery
**Description**: Verifies that API Autodiscovery is enabled.
- **Requirements**: The `<api-gateway:autodiscovery>` element must exist in **AT LEAST ONE** configuration file (ANY match).

#### RULE-009: Unsupported Error Expressions
**Description**: Removes legacy error handling expressions.
- **Forbidden**: `error.errorType`, `error.muleMessage`, `error.exception`.

#### RULE-010: Legacy Attributes
**Description**: Checks for deprecated connector attributes.
- **Forbidden**: `fromApplicationCode`, `toApplicationCode`.

#### RULE-011: DLP Properties
**Description**: Prevents usage of DLP flags in code.
- **Forbidden**: `north.bound.dlp.flag`, `south.bound.dlp.flag`.

#### RULE-012: Legacy JCE Encryption
**Description**: Detects legacy PBE algorithms.
- **Forbidden**: `PBEWithHmacSHA256AndAES_256`.

#### RULE-013: Crypto Config Type
**Description**: Ensures `crypto:jce-config` has a `type` attribute.
- **Note**: Only runs if the Crypto module is used.

#### RULE-014: Forbidden Functions
**Description**: Prevents usage of `toBase64`.
- **Forbidden**: `toBase64`, `tobase64`.

#### RULE-015: HTTP Listener Path Standards
**Description**: Enforces meaningful HTTP path names.
- **Forbidden Paths**: `/test`, `/temp`, `/todo`.

#### RULE-016: Hardcoded Connector Versions
**Description**: prevents hardcoded versions in `pom.xml`.
- **Requirement**: Connector versions must use properties (e.g., `${http.connector.version}`).
- **Failure**: If version matches a hardcoded number (e.g., `1.5.0`).

#### RULE-017: Externalized Configuration (Best Practice)
**Description**: Critical configurations must be externalized.
- **Scope**: DB (Host, Port, User, Pass), HTTP (Host, Port), JMS (Broker URL, User, Pass).
- **Requirement**: Values must be `${property.name}` placeholders.

#### RULE-018: Timeout Configuration (Best Practice)
**Description**:  Timeouts must be explicitly configured and externalized.
- **Scope**: HTTP Request (`responseTimeout`).

---

## <a id="generic-config"></a>Generic Config

Rules applicable to environment configurations across all technologies using Property/YAML files.

#### RULE-100: Generic Config Check
- **Goal**: Ensure `apiId` token exists in property files.

#### RULE-101: Log Format
- **Goal**: `LogJsonFormat` must be `true` or `false`.

#### RULE-102: Policy Protocols
- **Goal**: `http.protocols` must be `HTTPS` or `HTTP`.

#### RULE-103: Policy Versions
- **Goal**: `ratelimit.policy.version` must be a specific version (e.g., `1.4.2`).

#### RULE-104: Secure Properties Pattern
- **Goal**: Properties in `secure.properties` must match `secure::key=value`.
- **Goal**: Client ID mappings in policy files must match `...clientIDmap...`.

#### RULE-107: Forbidden Config Tokens
- **Forbidden**: `hardcoded.password`, `TODO`.

#### RULE-108: Forbidden Policy Keys
- **Forbidden**: `old.version`.

#### RULE-109: No Hardcoded IPs
- **Goal**: Property values must not be hardcoded IP addresses (use DNS/Hostnames).
