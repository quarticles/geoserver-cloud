# GeoServer Cloud Native Builds with GraalVM

This document describes how to build and deploy GeoServer Cloud microservices as GraalVM native images.

## Overview

Native image builds compile Java applications ahead-of-time to standalone executables, providing:
- **Instant Startup**: 2-5 seconds vs 20-30 seconds for JVM
- **Lower Memory**: ~50MB vs 200-300MB for JVM
- **Better Resource Density**: More containers per node
- **Improved Scaling**: Faster scale-up for auto-scaling

## Supported Services

### ✅ Gateway Service (Recommended)
The **gateway** service is the best candidate for native builds:
- Already using Spring Boot 3.4.3
- Reactive architecture (WebFlux)
- Minimal dependencies (no GeoServer libraries)
- Native profile configured and ready to use

### ⚠️ Infrastructure Services (Requires Migration)
- **discovery** (Eureka): Requires Spring Boot 3 migration + reflection configuration
- **config** (Spring Cloud Config): Requires Spring Boot 3 migration

### ❌ GeoServer Services (Not Recommended)
GeoServer services (wms, wfs, wcs, wps, gwc, restconfig, webui) are **not suitable** for native builds due to:
- Heavy use of reflection in GeoServer/GeoTools
- Dynamic class loading for plugins
- Complex image processing libraries (JAI/Imagen)
- JNDI and JDBC metadata introspection
- Very large native image size

**Recommendation**: Keep GeoServer services as traditional JVM applications.

## Prerequisites

### Install GraalVM

**Option 1: Using SDKMAN (Recommended)**
```bash
sdk install java 21.0.2-graalce
sdk use java 21.0.2-graalce
```

**Option 2: Manual Installation**
```bash
# Download GraalVM from https://www.graalvm.org/downloads/
# Extract and set JAVA_HOME
export JAVA_HOME=/path/to/graalvm-ce-java21
export PATH=$JAVA_HOME/bin:$PATH
```

**Verify Installation**
```bash
java -version
# Should show: OpenJDK Runtime Environment GraalVM CE 21...
```

## Building Gateway Native Image

### Local Native Build

Build the gateway service as a native executable:

```bash
cd src/apps/infrastructure/gateway

# Build native image (takes 5-10 minutes)
./mvnw clean package -Pnative -DskipTests

# The native executable will be at:
# target/gs-cloud-gateway
```

### Test the Native Executable

```bash
# Run the native executable
./target/gs-cloud-gateway

# It should start in 2-5 seconds
```

### Build Native Docker Image

#### Using Spring Boot Build-Image (Paketo Buildpacks)

```bash
cd src/apps/infrastructure/gateway

# Build native container image
./mvnw spring-boot:build-image -Pnative

# This creates: ghcr.io/quarticles/geoserver-cloud-gateway:2.28.0-SNAPSHOT
```

#### Using Custom Dockerfile

Create `src/apps/infrastructure/gateway/Dockerfile.native`:

```dockerfile
# Stage 1: Build native image
FROM ghcr.io/graalvm/graalvm-ce:21 AS builder

WORKDIR /build

# Install native-image
RUN gu install native-image

# Copy source
COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY src src

# Build native image
RUN ./mvnw clean package -Pnative -DskipTests

# Stage 2: Runtime
FROM ubuntu:22.04

RUN apt-get update && \
    apt-get install -y ca-certificates && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy native executable
COPY --from=builder /build/target/gs-cloud-gateway /app/gateway

# Create non-root user
RUN useradd -r -u 1000 -g root geoserver && \
    chown -R geoserver:root /app

USER geoserver

EXPOSE 8080

ENTRYPOINT ["/app/gateway"]
```

Build:
```bash
docker build -f Dockerfile.native -t ghcr.io/quarticles/geoserver-cloud-gateway-native:latest .
```

## Configuration

### Native Build Options

The native profile in `pom.xml` supports several configuration options:

```xml
<buildArgs>
  <!-- Detailed error reporting -->
  <arg>-H:+ReportExceptionStackTraces</arg>

  <!-- Show initialization details -->
  <arg>-H:+PrintClassInitialization</arg>

  <!-- Verbose output -->
  <arg>--verbose</arg>

  <!-- Quick build mode (faster, larger binary) -->
  <arg>-Ob</arg>

  <!-- Cross-platform compatibility -->
  <arg>-march=compatibility</arg>

  <!-- Enable JFR and monitoring -->
  <arg>--enable-monitoring=jfr,jvmstat</arg>
</buildArgs>
```

### Production Build (Optimized)

For production, use full optimization:

```bash
# Edit buildArgs in pom.xml, replace -Ob with:
# <arg>-O3</arg>  <!-- Full optimization -->

# Build with full optimization (takes longer)
./mvnw clean package -Pnative -DskipTests
```

### Memory Configuration

Native image build requires significant memory:

```bash
# Set Maven memory options
export MAVEN_OPTS="-Xmx8g"

# Build
./mvnw clean package -Pnative -DskipTests
```

## Deployment

### Docker Compose

Update `compose/pgconfig/docker-compose.yml`:

```yaml
services:
  gateway:
    image: ghcr.io/quarticles/geoserver-cloud-gateway-native:latest
    environment:
      SPRING_PROFILES_ACTIVE: native
    ports:
      - "9090:8080"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 3
      start_period: 5s  # Native starts much faster
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: gateway-native
spec:
  replicas: 3
  selector:
    matchLabels:
      app: gateway-native
  template:
    metadata:
      labels:
        app: gateway-native
    spec:
      containers:
      - name: gateway
        image: ghcr.io/quarticles/geoserver-cloud-gateway-native:latest
        ports:
        - containerPort: 8080
        resources:
          requests:
            memory: "64Mi"    # Much lower than JVM
            cpu: "100m"
          limits:
            memory: "128Mi"   # Much lower than JVM
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 5  # Native starts quickly
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 3
          periodSeconds: 5
```

## Performance Comparison

### Startup Time
| Service | JVM (seconds) | Native (seconds) | Improvement |
|---------|--------------|------------------|-------------|
| Gateway | 25-30        | 2-5              | **6x faster** |

### Memory Usage
| Service | JVM (MB) | Native (MB) | Improvement |
|---------|----------|-------------|-------------|
| Gateway | 200-300  | 50-80       | **3-4x less** |

### Image Size
| Service | JVM Image | Native Image | Improvement |
|---------|-----------|--------------|-------------|
| Gateway | ~350 MB   | ~120 MB      | **3x smaller** |

## Troubleshooting

### Build Failures

#### Missing Reflection Configuration
```
Error: Classes that should be initialized at run time got initialized during image building
```

**Solution**: Add reflection hints in `src/main/resources/META-INF/native-image/reflect-config.json`

#### OutOfMemoryError During Build
```
java.lang.OutOfMemoryError: Java heap space
```

**Solution**:
```bash
export MAVEN_OPTS="-Xmx12g"
./mvnw clean package -Pnative -DskipTests
```

### Runtime Issues

#### Missing Native Library
```
java.lang.UnsatisfiedLinkError: no xyz in java.library.path
```

**Solution**: Some libraries aren't compatible with native images. Check GraalVM compatibility.

#### Reflection Errors
```
java.lang.ClassNotFoundException at runtime
```

**Solution**: Add missing classes to reflection configuration.

## CI/CD Integration

### GitHub Actions

Add to `.github/workflows/build-native.yaml`:

```yaml
name: Build Native Images

on:
  push:
    branches:
      - quarticles-main
    paths:
      - 'src/apps/infrastructure/gateway/**'

jobs:
  build-gateway-native:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup GraalVM
        uses: graalvm/setup-graalvm@v1
        with:
          java-version: '21'
          distribution: 'graalvm-community'
          github-token: ${{ secrets.GITHUB_TOKEN }}

      - name: Build Native Image
        working-directory: src/apps/infrastructure/gateway
        run: ./mvnw clean package -Pnative -DskipTests

      - name: Build Docker Image
        run: |
          docker build -f src/apps/infrastructure/gateway/Dockerfile.native \
            -t ghcr.io/quarticles/geoserver-cloud-gateway-native:${{ github.sha }} \
            src/apps/infrastructure/gateway

      - name: Login to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Push Image
        run: |
          docker push ghcr.io/quarticles/geoserver-cloud-gateway-native:${{ github.sha }}
          docker tag ghcr.io/quarticles/geoserver-cloud-gateway-native:${{ github.sha }} \
            ghcr.io/quarticles/geoserver-cloud-gateway-native:latest
          docker push ghcr.io/quarticles/geoserver-cloud-gateway-native:latest
```

## Best Practices

1. **Use for Stateless Services**: Native images work best for stateless, reactive services
2. **Keep Dependencies Minimal**: Fewer dependencies = easier native compilation
3. **Test Thoroughly**: Native images have subtle differences from JVM
4. **Monitor Memory**: Native apps use less memory but in different ways
5. **Profile Before Optimization**: Use `-O3` only when you need maximum performance

## Future Work

- Migrate Discovery service to Spring Boot 3 and add native support
- Migrate Config service to Spring Boot 3 and add native support
- Investigate lightweight alternatives to Eureka (e.g., Consul native, Kubernetes native service discovery)

## References

- [Spring Boot 3 Native Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/native-image.html)
- [GraalVM Native Image](https://www.graalvm.org/latest/reference-manual/native-image/)
- [Spring Cloud Gateway Native Support](https://spring.io/blog/2023/03/03/spring-cloud-gateway-native-support)
- [Paketo Buildpacks](https://paketo.io/docs/howto/java/#build-a-native-image-application)
