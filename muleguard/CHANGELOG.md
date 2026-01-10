# MuleGuard Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0-enterprise] - 2026-01-10

### Removed
- **Trial License System**: Completely removed trial license validation mechanism
  - Deleted `TrialLicenseManager.java` class
  - Removed trial validation from `MuleGuardMain.java`
  - Deleted `TRIAL_VERSION_README.md` and `TRIAL_QUICK_REFERENCE.md` documentation
  - Removed hardcoded contact email addresses

### Added
- **Enterprise Logging Framework**: Implemented SLF4J with Logback
  - Added SLF4J API 2.0.9 dependency
  - Added Logback Classic 1.4.14 dependency
  - Created `logback.xml` configuration with console and rolling file appenders
  - Configured daily log rotation with 30-day retention and 1GB size cap

### Changed
- **Logging Implementation**: Replaced all console output with structured logging
  - `MuleGuardMain.java`: Replaced 40+ `System.out/err` calls with logger
  - `PropertyResolver.java`: Replaced 2 `System.err` calls with `logger.warn`
  - `ProjectDiscovery.java`: Replaced 6 `System.out/err` calls with logger
  - All logging now uses parameterized messages for better performance
  - Error stack traces now logged via SLF4J instead of `printStackTrace()`

### Improved
- **JAR Size**: Reduced from 26.29MB to 25.96MB (~0.33MB savings)
- **Security Posture**: Removed trial-related code that could be bypassed
- **Enterprise Readiness**: Aligned with industry-standard logging practices
- **Maintainability**: Centralized logging configuration in `logback.xml`

### Compatibility
- ✅ **Backward Compatible**: All public API methods unchanged
- ✅ **Wrapper Compatible**: Successfully tested with `apiguardwrapper` Mule application
- ✅ **No Breaking Changes**: Drop-in replacement for previous JAR

### Technical Details
- **Build Tool**: Maven 3.x
- **Java Version**: 17
- **Dependencies Updated**: SLF4J, Logback added
- **Configuration**: `logback.xml` for log management

---

## [1.0.0] - Previous Releases

See git history for previous changes.
