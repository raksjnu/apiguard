# Changelog

All notable changes to this project will be documented in this file.

## [1.0.0] - 2026-01-16
### Added
- Integrated Aegis (MuleGuard) core engine.
- Added `mule-app.properties` with externalized secrets.
- Added `CHANGELOG.md`.

### Changed
- Removed `src/test` directory for enterprise distribution.
- Updated `ZipExtractor` to use SLF4J logging.
- Externalized sensitive email and token configurations.

### Fixed
- Fixed `ResourceNotFoundException` by restoring `mule-app.properties`.
