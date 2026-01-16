# Changelog

All notable changes to this project will be documented in this file.

## [1.0.0] - 2026-01-16
### Added
- Enterprise-grade logging using SLF4J and Logback.
- `logback.xml` configuration for console and file logging.
- `ENTERPRISE_ONBOARDING.md` guide.
- `SECURITY_COMPLIANCE_REPORT.md`.

### Changed
- Removed all trial license restrictions and manager classes.
- Removed hardcoded personal email addresses.
- Replaced `System.out.println` with structured logging.
- Refactored `CheckFactory` to remove legacy check mappings.
- Cleaned up obsolete validation classes (`GenericPomValidationCheck`, etc.).

### Fixed
- Fixed cross-platform path handling issues.
- Resolved configuration errors in legacy rule mappings.
