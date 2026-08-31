# Contributing to LinkDeck

Thank you for contributing to LinkDeck! We welcome contributions that uphold our core values of **privacy-first design, strict security, clean architecture, and long-term maintainability**.

---

## Development Setup

- **Java Development Kit**: JDK 17
- **Android SDK**: `compileSdk = 36`, `targetSdk = 36`, `minSdk = 29`
- **Gradle**: Gradle 9.7.1 / AGP 8.7.3

### Build & Test Commands
```bash
# Run unit tests
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug

# Assemble release APK
./gradlew assembleRelease
```

---

## Architecture & Contribution Principles

1. **Privacy-First & Local-Only**:
   - LinkDeck must NEVER introduce analytics SDKs, telemetry, crash reporting, or cloud URL collection. All link routing, cleaning, and resolution must execute strictly on-device.
2. **Strict Security Boundaries**:
   - Any changes to networking or intent handling must strictly respect SSRF protections (`NetworkSafetyChecker`), scheme restrictions (HTTP/HTTPS only), and package visibility limitations (no `QUERY_ALL_PACKAGES`).
3. **No WebViews**:
   - LinkDeck does not render remote HTML or run JavaScript.
4. **Code Quality & Comments**:
   - Code should be clean, focused, and idiomatic Kotlin.
   - Keep file sizes modular (~200 lines, practical ceiling ~300 lines).
   - Comments should explain **WHY** non-obvious architecture or security decisions were made, not restate what the code does.
5. **Comprehensive Testing**:
   - Every bug fix or enhancement must include deterministic unit tests covering standard behavior, boundary conditions, and failure modes.

---

## Pull Request Guidelines

1. Fork the repository and create a feature branch (`git checkout -b feature/my-feature`).
2. Ensure all unit tests pass (`./gradlew testDebugUnitTest`) and the project builds cleanly (`./gradlew assembleDebug assembleRelease`).
3. Submit a Pull Request with a clear summary of changes, rationale, and test results.
