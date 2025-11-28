# GeoServer Cloud - Code Style and Conventions

## Formatting Tools
- **Spotless** with Palantir Java Format for Java code
- **SortPOM** for pom.xml files
- **Checkstyle** for code style rules

## Key Rules
- Line length: 120 characters max
- No wildcard imports
- Proper license headers required on all Java files
- Standard Java naming conventions

## Formatting Commands
```bash
# Format all files
make format

# Format Java only
make format-java

# Format pom.xml only
make format-pom
```

## Linting Commands
```bash
# Run all linting
make lint

# Validate with quality checks
./mvnw validate -Dqa -fae -ntp -T1C
```

## Module Structure Convention
```
src/
├── apps/                           # Microservice applications
│   ├── base-images/               # Docker base images
│   ├── infrastructure/            # Infrastructure services (config, discovery, gateway)
│   └── geoserver/                 # GeoServer service applications (wms, wfs, etc.)
├── catalog/                       # Catalog and config core libraries
│   ├── backends/                  # Catalog backend implementations
│   ├── cache/                     # JCache support for catalog
│   ├── events/                    # Catalog event model
│   ├── event-bus/                 # Spring Cloud Bus integration
│   ├── jackson-bindings/          # JSON serialization
│   └── plugin/                    # Core catalog implementation
├── extensions/                    # GeoServer extensions
├── gwc/                           # GeoWebCache integration
├── library/                       # Common libraries
├── starters/                      # Spring Boot starters
└── integration-tests/             # Integration tests
```

## Commit Message Guidelines
- Do not mention Claude or AI assistants in commit messages
- Use conventional commit style when appropriate
- Be concise but descriptive

## Dependency Management
- netty-nio-client is explicitly excluded - use apache-client for S3 operations
- Custom GeoServer branch required from camptocamp repository
- Quarticle fork used for imageio-ext S3 support
