# GeoServer Cloud - Suggested Commands

## Build Commands

```bash
# Full build (compile, test, install, build Docker images)
make

# Install without tests (fastest for development)
make install

# Run tests only
make test

# Clean build
make clean

# Maven wrapper with common flags
./mvnw clean install -DskipTests -ntp -U -T1C
```

## Linting and Formatting

```bash
# Run all linting (pom + java)
make lint

# Lint pom.xml files only
make lint-pom

# Lint Java files only
make lint-java

# Format all files (pom + java)
make format

# Format pom.xml files
make format-pom

# Format Java code
make format-java

# Validate with quality checks
./mvnw validate -Dqa -fae -ntp -T1C
```

## Docker Images

```bash
# Build all Docker images
make build-image

# Build specific image groups
make build-base-images
make build-image-infrastructure
make build-image-geoserver

# Build specific GeoServer service images
make build-image-geoserver wms wfs

# Skip repackaging (faster if JARs already built)
REPACKAGE=false make build-image-geoserver wms wfs
```

## Development Environment

```bash
# Start with PostgreSQL catalog backend (recommended)
cd compose/ && ./pgconfig up -d

# Start with shared data directory
cd compose/ && ./datadir up -d

# Run specific service locally
./mvnw -f src/apps/geoserver/wfs spring-boot:run -Dspring-boot.run.profiles=local
```

## Testing

```bash
# All tests
make test

# Specific module
./mvnw test -pl src/apps/geoserver/wms

# Integration tests
./mvnw verify -pl src/integration-tests

# Acceptance tests
make acceptance-tests-pgconfig
make acceptance-tests-datadir
```

## Package Specific Module

```bash
./mvnw clean package -DskipTests -T1C -ntp -am -pl src/apps/geoserver/wms
```

## Git Submodules

```bash
# Clone with submodules
git clone --recurse-submodules git@github.com:geoserver/geoserver-cloud.git

# Initialize after cloning
git submodule update --init --recursive
```

## Verification

```bash
# Test WMS capabilities
curl "http://localhost:9090/geoserver/cloud/ows?request=getcapabilities&service=WMS"

# Test REST API
curl -u admin:geoserver "http://localhost:9090/geoserver/cloud/rest/workspaces.json"
```
