# GeoServer Cloud - Codebase Structure

## Root Directory
```
geoserver-cloud/
├── src/                    # Main source code
├── compose/                # Docker compose files for development
├── docker-build/           # Docker build configuration
├── config/                 # Git submodule for Spring configuration
├── acceptance_tests/       # Acceptance test suite
├── ci/                     # CI/CD scripts and configuration
├── docs/                   # Documentation
├── build-tools/            # Build tooling
├── .github/                # GitHub Actions workflows
├── pom.xml                 # Root Maven POM
└── Makefile                # Build automation
```

## Source Structure (src/)
```
src/
├── apps/
│   ├── base-images/        # Docker base images (jre, spring-boot, geoserver)
│   ├── infrastructure/     
│   │   ├── config/         # Spring Cloud Config server
│   │   ├── discovery/      # Eureka service discovery
│   │   └── gateway/        # Spring Cloud Gateway
│   └── geoserver/
│       ├── wms/            # Web Map Service
│       ├── wfs/            # Web Feature Service
│       ├── wcs/            # Web Coverage Service
│       ├── wps/            # Web Processing Service
│       ├── gwc/            # GeoWebCache
│       ├── restconfig/     # REST API
│       └── webui/          # Admin Web UI
├── catalog/
│   ├── backends/
│   │   ├── datadir/        # File-based catalog backend
│   │   ├── jdbcconfig/     # JDBC catalog backend (deprecated)
│   │   └── pgconfig/       # PostgreSQL catalog backend
│   ├── cache/              # Catalog caching
│   ├── events/             # Catalog event definitions
│   ├── event-bus/          # Event distribution via Spring Cloud Bus
│   ├── jackson-bindings/   # JSON serialization
│   └── plugin/             # Core catalog plugin
├── extensions/
│   ├── security/           # Security extensions
│   ├── input-formats/      # Data input formats (raster/vector)
│   │   └── raster-formats/ # COG, GeoTIFF, etc. (includes Quarticle S3 fork)
│   └── output-formats/     # Output format extensions
├── gwc/                    # GeoWebCache integration
│   ├── backends/           # GWC storage backends
│   ├── blobstores/         # Blobstore implementations
│   └── services/           # GWC services
├── library/                # Shared libraries
├── starters/               # Spring Boot starters
└── integration-tests/      # Integration tests
```

## Key Files
- `pom.xml` - Root Maven POM with dependency management
- `Makefile` - Build automation and common tasks
- `CLAUDE.md` - AI assistant guidance
- `compose/*.yaml` - Docker compose files for different backends
- `.github/workflows/` - CI/CD workflows

## Important Paths
- Raster formats (COG/S3): `src/extensions/input-formats/raster-formats/`
- Quarticle S3 dependency: Uses `io.github.quarticles:imageio-ext-cog-rangereader-s3`
- Spring config: `config/` submodule
