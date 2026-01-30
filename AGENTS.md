# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java` contains Spring Boot application code under `com.lh.assist`.
- `src/main/resources` holds configuration such as `application.yaml`.
- `src/test/java` holds JUnit tests (e.g., `LhAssistBackendApplicationTests`).
- `build.gradle.kts` defines dependencies and build logic; Gradle wrapper scripts live at `./gradlew`.
- `docker-compose.yml` is available for local service dependencies (e.g., database).

## Build, Test, and Development Commands
- `./gradlew clean build` builds the application and runs tests.
- `./gradlew test` runs the JUnit test suite only.
- `./gradlew bootRun` starts the Spring Boot app in dev mode.
- `java -jar build/libs/lh-assist-backend-0.0.1-SNAPSHOT.jar` runs the packaged jar.

## Coding Style & Naming Conventions
- Java 21 toolchain; use standard Spring Boot conventions.
- Indentation: 4 spaces for Java and Gradle Kotlin DSL.
- Naming: `PascalCase` for classes, `camelCase` for methods/fields, `UPPER_SNAKE_CASE` for constants.
- Prefer Lombok annotations where already used in the codebase.

## Testing Guidelines
- Frameworks: JUnit Platform via Spring Boot Test.
- Test classes live in `src/test/java` and typically end with `Tests` (e.g., `LhAssistBackendApplicationTests`).
- Run tests with `./gradlew test`; keep unit tests fast and isolated.

## Commit & Pull Request Guidelines
- Git history uses conventional prefixes like `docs:` and `chore:`; follow this pattern for new commits.
- Keep commit messages short and descriptive (e.g., `docs: update README`).
- PRs should describe the change, note any config updates, and include test results when applicable.

## Security & Configuration Tips
- Copy `src/main/resources/application-sample.yml` to `src/main/resources/application-secrets.yml` and fill in required secrets.
- Avoid committing credentials; keep secrets in local config or environment variables.
