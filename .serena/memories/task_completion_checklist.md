# GeoServer Cloud - Task Completion Checklist

## Before Committing Code

1. **Format code**
   ```bash
   make format
   ```

2. **Run linting**
   ```bash
   make lint
   ```

3. **Run tests**
   ```bash
   make test
   ```
   Or for specific module:
   ```bash
   ./mvnw test -pl <module-path>
   ```

## Before Creating PR

1. **Ensure all formatting passes**
   ```bash
   make lint-pom
   make lint-java
   ```

2. **Run acceptance tests** (if applicable)
   ```bash
   make acceptance-tests-pgconfig
   make acceptance-tests-datadir
   ```

3. **Build Docker images** (if changes affect images)
   ```bash
   make build-image
   ```

## CI/CD Validation (PR workflow)
The PR workflow validates:
- POM formatting
- Java code formatting
- Unit tests
- Acceptance tests (datadir and pgconfig backends)

## Commit Message Guidelines
- Do NOT mention Claude or AI assistants
- Use clear, descriptive commit messages
- Focus on the "why" rather than the "what"

## Important Notes
- Gateway service supports native builds, but GeoServer services do NOT
- netty-nio-client is explicitly excluded from dependencies
- Config is a git submodule - ensure it's initialized
