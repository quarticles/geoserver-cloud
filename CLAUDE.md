# CLAUDE.md

This file provides guidance to AI assistants when working with code in this repository.

## Project Overview

GeoServer Cloud is a cloud-native, microservices-based distribution of GeoServer built on Spring Boot and Spring Cloud. It decomposes the traditional GeoServer monolith into independently deployable, scalable microservices for each OWS service, the Web UI, and the REST API.

### Native Builds Support

The **gateway** service supports GraalVM native image builds for instant startup (~2-5s) and reduced memory footprint (~50MB). See [docs/native-builds.md](docs/native-builds.md) for details.

**Native build quick start**:
```bash
cd src/apps/infrastructure/gateway
./mvnw clean package -Pnative -DskipTests
```

**Note**: GeoServer services (wms, wfs, etc.) are NOT suitable for native builds due to heavy reflection usage in GeoServer/GeoTools libraries.

## Build System

This is a Maven-based Java 21 project with a comprehensive Makefile for common operations.

### Essential Build Commands

```bash
# Full build (compile, test, install, build Docker images)
make

# Install without tests
make install

# Run tests only
make test

# Clean build
make clean

# Build all Docker images
make build-image

# Build specific image groups
make build-base-images
make build-image-infrastructure
make build-image-geoserver

# Build specific GeoServer service images
make build-image-geoserver wms wfs

# Lint and format
make lint              # Run all linting (pom + java)
make lint-pom          # Lint pom.xml files only
make lint-java         # Lint Java files only
make format            # Format all files (pom + java)
make format-pom        # Format pom.xml files
make format-java       # Format Java code
```

### Maven Commands

```bash
# Use the Maven wrapper
./mvnw clean install -DskipTests -ntp -U -T1C

# Run a specific service for development
./mvnw -f src/apps/geoserver/wfs spring-boot:run -Dspring-boot.run.profiles=local

# Package specific modules
./mvnw clean package -DskipTests -T1C -ntp -am -pl src/apps/geoserver/wms

# Validate with quality checks (used in lint)
./mvnw validate -Dqa -fae -ntp -T1C
```

## Architecture

### Microservices Structure

**Infrastructure Services:**
- `config` - Spring Cloud Config server (externalized configuration)
- `discovery` - Spring Cloud Netflix Eureka (service discovery)
- `gateway` - Spring Cloud Gateway (API gateway, single entry point)

**GeoServer Services:**
- `wms` - Web Map Service
- `wfs` - Web Feature Service
- `wcs` - Web Coverage Service
- `wps` - Web Processing Service
- `gwc` - GeoWebCache (tile caching)
- `restconfig` - REST Configuration API
- `webui` - Administration Web UI

### Catalog Backends

GeoServer Cloud supports multiple catalog storage backends:

1. **pgconfig** (Recommended for production) - PostgreSQL-based catalog backend
2. **datadir** - Traditional shared data directory (requires ReadWriteMany volume in k8s)
3. **jdbcconfig** (Deprecated) - JDBC-based catalog backend

Backend selection is controlled via Spring Boot profiles.

### Module Structure

```
src/
├── apps/                           # Microservice applications
│   ├── base-images/               # Docker base images (jre, spring-boot, spring-boot3, geoserver)
│   ├── infrastructure/            # Infrastructure services (config, discovery, gateway)
│   └── geoserver/                 # GeoServer service applications (wms, wfs, etc.)
├── catalog/                       # Catalog and config core libraries
│   ├── backends/                  # Catalog backend implementations (datadir, jdbcconfig, pgconfig)
│   ├── cache/                     # JCache support for catalog
│   ├── events/                    # Catalog event model
│   ├── event-bus/                 # Spring Cloud Bus integration for events
│   ├── jackson-bindings/          # JSON serialization for GeoServer/GeoTools objects
│   └── plugin/                    # Core catalog implementation
├── extensions/                    # GeoServer extensions
│   ├── security/                  # Security extensions (acl, jdbc, ldap, auth-key, etc.)
│   ├── input-formats/             # Raster and vector formats
│   ├── output-formats/            # Output format extensions (vector-tiles, dxf, flatgeobuf)
│   └── [others]/                  # css-styling, mapbox-styling, importer, ogcapi, etc.
├── gwc/                           # GeoWebCache integration
│   ├── backends/                  # GWC storage backends (pgconfig)
│   ├── blobstores/               # Blobstore implementations
│   └── services/                  # GWC services support
├── library/                       # Common libraries
├── starters/                      # Spring Boot starters
└── integration-tests/             # Integration tests
```

## Development Workflow

### Running Development Environment

Development docker compositions are in `compose/` with convenience scripts:

```bash
cd compose/

# Start with PostgreSQL catalog backend (recommended)
./pgconfig up -d

# Start with shared data directory
./datadir up -d

# Start with jdbcconfig (deprecated)
./jdbcconfig up -d
```

Access GeoServer at: http://localhost:9090/geoserver/cloud/
- Default credentials: admin/geoserver
- Gateway routes all services through port 9090

### Running Single Service Locally

For debugging a specific service outside Docker:

```bash
# Start essential infrastructure
docker compose up -d discovery rabbitmq config database gateway

# Run service locally (example: wfs-service)
./mvnw -f src/apps/geoserver/wfs spring-boot:run -Dspring-boot.run.profiles=local
```

Local service ports (when using `-Dspring-boot.run.profiles=local`):
- wfs: 9101
- wms: 9102
- wcs: 9103
- wps: 9104
- restconfig: 9105
- webui: 9106

## Code Standards

### Formatting and Style

The project uses automated code formatting enforced during build:
- **Spotless** with Palantir Java Format for Java code
- **SortPOM** for pom.xml files
- **Checkstyle** for code style rules

Key rules:
- Line length: 120 characters max
- No wildcard imports
- Proper license headers required on all Java files
- Standard Java naming conventions

### Running Tests

```bash
# All tests
make test

# Specific module
./mvnw test -pl src/apps/geoserver/wms

# Integration tests
./mvnw verify -pl src/integration-tests
```

### Acceptance Tests

```bash
# With pgconfig backend
make acceptance-tests-pgconfig

# With datadir backend
make acceptance-tests-datadir

# Manual control
make build-acceptance
make start-acceptance-tests-pgconfig
make run-acceptance-tests-pgconfig
make clean-acceptance-tests-pgconfig
```

## Docker Image Building

### Local Platform Images

```bash
# Build all images for local platform (defaults to ghcr.io/quarticles)
make build-image

# Skip repackaging (faster if JARs already built)
REPACKAGE=false make build-image-geoserver wms wfs

# Override repository for testing
REPOSITORY=localhost:5000 make build-image
```

### Multi-platform Images (amd64/arm64)

Images are built and pushed to GitHub Container Registry (ghcr.io/quarticles) by CI/CD.

For manual multi-platform builds:

```bash
# Requires docker buildx with qemu
docker buildx create --name gscloud-builder --driver docker-container --bootstrap --use

# Login to GHCR
echo $GITHUB_TOKEN | docker login ghcr.io -u USERNAME --password-stdin

# Build and push multi-platform images (defaults to ghcr.io/quarticles)
make build-image-multiplatform

# Override repository if needed
REPOSITORY=ghcr.io/myuser make build-image-multiplatform

# Cleanup
docker buildx stop gscloud-builder
docker buildx rm gscloud-builder
```

### Using Images from GHCR

Images are publicly available at `ghcr.io/quarticles/`:
- `ghcr.io/quarticles/geoserver-cloud-wms:TAG`
- `ghcr.io/quarticles/geoserver-cloud-wfs:TAG`
- `ghcr.io/quarticles/geoserver-cloud-wcs:TAG`
- etc.

The default repository is configured in `docker-build/.env`.

### Image Signing and Verification

All images are signed using Cosign with keyless signing (OIDC):

```bash
# Install cosign
curl -LO https://github.com/sigstore/cosign/releases/latest/download/cosign-linux-amd64
sudo install cosign-linux-amd64 /usr/local/bin/cosign

# Verify an image signature (signed by GitHub Actions)
cosign verify ghcr.io/quarticles/geoserver-cloud-wms:TAG \
  --certificate-identity-regexp="https://github.com/quarticles/geoserver-cloud" \
  --certificate-oidc-issuer=https://token.actions.githubusercontent.com

# View signature details
cosign verify ghcr.io/quarticles/geoserver-cloud-wms:TAG \
  --certificate-identity-regexp=".*" \
  --certificate-oidc-issuer-regexp=".*" | jq

# Sign images locally (requires authentication)
make sign-image-keyless

# Verify locally-signed images
make verify-image-keyless
```

Keyless signing advantages:
- No secrets to manage
- Signatures tied to your GitHub identity
- Stored in public transparency log (Rekor)
- Anyone can verify without sharing keys

## Important Implementation Details

### Custom GeoServer Branch

This project depends on a custom GeoServer branch `gscloud/gs_version/integration` hosted at https://github.com/camptocamp/geoserver-cloud-geoserver with patches not yet in upstream. Artifacts are published to GitHub Packages and configured in the root pom.xml.

### Event Distribution

Services communicate catalog/config changes via Spring Cloud Bus (backed by RabbitMQ in dev compositions). Events are defined in `src/catalog/events/` and integrated via `src/catalog/event-bus/`.

### Catalog Plugin Architecture

The core catalog implementation in `src/catalog/plugin/` extends GeoServer's catalog with:
- Event-driven updates across services
- Pluggable backend storage
- Jackson-based serialization for distributed systems
- Optional caching layer

### Service Communication

Services discover each other via Eureka (`discovery-service`) and retrieve configuration from Spring Cloud Config (`config-service`). The gateway proxies all external requests to backend services.

## CI/CD

### Active Workflows

**build-and-push.yaml** - Builds and pushes multi-platform (amd64/arm64) Docker images to GitHub Container Registry (ghcr.io) on pushes to main/release branches:
1. Base images (jre, spring-boot, spring-boot3, geoserver-common)
2. Infrastructure images (config, discovery, gateway)
3. GeoServer service images (matrix strategy: wms, wfs, wcs, wps, gwc, restconfig, webui)

Images are pushed to `ghcr.io/quarticles/` namespace. No additional secrets needed - uses `GITHUB_TOKEN`.

**pull-request.yaml** - Runs on PRs to validate:
- POM formatting
- Java code formatting
- Unit tests
- Acceptance tests (datadir and pgconfig backends)

**Image Signing** - All images are signed using Cosign with keyless signing (OIDC):
- Uses GitHub OIDC tokens (no secrets needed)
- Signatures stored in transparency log (Rekor)
- Anyone can verify signatures using cosign

### Disabled Workflows

These workflows are disabled for the fork (require special secrets/configuration):
- `sonarcloud.yaml` - Code quality analysis (needs SONAR_TOKEN)
- `sonarcloud-fork-pr.yaml` - Fork PR analysis (needs SONAR_TOKEN)
- `backport.yaml` - Automated backports (needs GH_TOKEN_BOT)

## Testing and Verification

```bash
# Verify services after starting compose
curl "http://localhost:9090/geoserver/cloud/ows?request=getcapabilities&service=WMS"
curl -u admin:geoserver "http://localhost:9090/geoserver/cloud/rest/workspaces.json"
```

## Submodules

The `config/` directory is a git submodule pointing to https://github.com/geoserver/geoserver-cloud-config containing externalized Spring configuration.

Clone with submodules:
```bash
git clone --recurse-submodules git@github.com:geoserver/geoserver-cloud.git
```

Or initialize after cloning:
```bash
git submodule update --init --recursive
```
- when commit, don't mention Claude, here the commiter is mihai.csaky@quarticle.ro