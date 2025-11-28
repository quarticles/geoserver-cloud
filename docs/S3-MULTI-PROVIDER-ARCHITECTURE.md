# S3 Multi-Provider Architecture for COG Support

## Overview

This document describes the architectural gap in GeoServer Cloud's COG (Cloud Optimized GeoTIFF) S3 support and proposes a solution for multi-provider, multi-region S3 configurations.

## Problem Statement

### Current Architecture Limitation

GeoServer Cloud currently configures S3 access **globally** at the application level:

```
┌─────────────────────────────────────────────────────────────────┐
│                    CURRENT ARCHITECTURE                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Environment Variables (Global):                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ IIO_S3_AWS_ENDPOINT=https://s3.amazonaws.com            │    │
│  │ IIO_S3_AWS_REGION=us-east-1                             │    │
│  │ IIO_S3_AWS_USER=...                                     │    │
│  │ IIO_S3_AWS_PASSWORD=...                                 │    │
│  └─────────────────────────────────────────────────────────┘    │
│                           │                                      │
│                           ▼                                      │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              All COG Stores Use Same Config              │    │
│  │                                                          │    │
│  │  Store A: cog://s3://aws-bucket/file.tif      ──┐       │    │
│  │  Store B: cog://s3://wasabi-bucket/file.tif   ──┼─► FAIL│    │
│  │  Store C: cog://s3://minio-bucket/file.tif    ──┘       │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│  ❌ Cannot mix AWS + Wasabi + MinIO                             │
│  ❌ Cannot mix AWS regions (us-east-1 + eu-central-1)           │
└─────────────────────────────────────────────────────────────────┘
```

### Configuration Sources (Current)

| Source | Scope | Example |
|--------|-------|---------|
| Environment Variables | Global | `IIO_S3_AWS_ENDPOINT`, `IIO_S3_AWS_REGION` |
| Spring Properties | Global | `geoserver.cog.s3.endpoint`, `geoserver.cog.s3.region` |
| URL Query Parameters | Per-URL | `s3://bucket/key?endpoint=...&region=...` |

The URL query parameter approach works but has significant drawbacks:
- Credentials may be embedded in URLs (security risk)
- URLs become long and unmanageable
- No centralized management
- Not user-friendly in UI

### The imageio-ext Fork Infrastructure

The Quarticle fork of imageio-ext (`io.github.quarticles:imageio-ext-cog-rangereader-s3`) provides infrastructure for per-bucket configuration:

```java
// S3ConfigRegistry - per-bucket config registry (exists but not populated)
S3ConfigRegistry.register("wasabi-bucket",
    S3StorageConfig.builder()
        .endpoint("https://s3.eu-central-1.wasabisys.com")
        .region("eu-central-1")
        .forcePathStyle(true)
        .build());
```

**However, this registry is not connected to:**
- GeoServer REST API
- GeoServer WebUI
- GeoServer Operator CRDs
- Catalog persistence

### The Operator's Incomplete Bridge

The GeoServer Operator CRD has S3 config fields:

```yaml
apiVersion: geoserver.quarticle.ch/v1
kind: CoverageStore
spec:
  geotiff:
    url: "cog://s3://my-bucket/path/data.tif"
    cog:
      enabled: true
      rangeReader: "S3"
      s3:
        region: "eu-central-1"
        endpoint: "https://s3.wasabisys.com"  # ← This exists!
        credentialsSecretRef:
          name: wasabi-creds
```

**But this config is not transmitted to GeoServer** because:
1. GeoServer REST API has no field for per-store S3 endpoint/region
2. The operator calls REST API which doesn't support these fields
3. The S3 config from the CRD is effectively ignored

---

## Proposed Architecture: S3 Provider Registry

### Concept

Introduce **named S3 Providers** that can be referenced by stores:

```
┌─────────────────────────────────────────────────────────────────┐
│                    PROPOSED ARCHITECTURE                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  S3 Providers (Named Configurations):                            │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ "aws-us":     endpoint=null, region=us-east-1           │    │
│  │ "aws-eu":     endpoint=null, region=eu-central-1        │    │
│  │ "wasabi":     endpoint=s3.wasabisys.com, region=eu-...  │    │
│  │ "minio":      endpoint=minio.local:9000, pathStyle=true │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│  Bucket-to-Provider Mapping:                                     │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ "aws-bucket"     → "aws-us"                             │    │
│  │ "wasabi-bucket"  → "wasabi"                             │    │
│  │ "minio-bucket"   → "minio"                              │    │
│  │ "other-*"        → "aws-eu" (wildcard/default)          │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│  COG Stores (Clean URLs):                                        │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ Store A: cog://s3://aws-bucket/file.tif      → aws-us   │    │
│  │ Store B: cog://s3://wasabi-bucket/file.tif   → wasabi   │    │
│  │ Store C: cog://s3://minio-bucket/file.tif    → minio    │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│  ✅ Mix any S3-compatible providers                              │
│  ✅ Clean URLs without credentials                               │
│  ✅ Centralized credential management                            │
└─────────────────────────────────────────────────────────────────┘
```

---

## Implementation Plan

### Phase 1: Operator-Level S3 Provider Configuration

#### Option A: New CRD `S3Provider`

```yaml
apiVersion: geoserver.quarticle.ch/v1
kind: S3Provider
metadata:
  name: wasabi-eu
  namespace: geoserver
spec:
  # Provider connection settings
  endpoint: "https://s3.eu-central-1.wasabisys.com"
  region: "eu-central-1"
  forcePathStyle: true

  # Credentials from K8s Secret
  credentialsSecretRef:
    name: wasabi-credentials
    accessKeyIdKey: accessKeyId
    secretAccessKeyKey: secretAccessKey

  # Which buckets use this provider
  buckets:
    - "wasabi-cog-data"
    - "wasabi-imagery-*"  # Wildcard support

  # Optional: connection tuning
  maxConnections: 50
  connectionTimeout: 10s
```

#### Option B: Inline in GeoServerCloud Spec

```yaml
apiVersion: geoserver.quarticle.ch/v1
kind: GeoServerCloud
metadata:
  name: my-geoserver
spec:
  # ... existing config ...

  s3Providers:
    - name: aws-default
      region: us-east-1
      # No endpoint = AWS default
      buckets: ["*"]  # Default for unmatched buckets

    - name: wasabi-eu
      endpoint: https://s3.eu-central-1.wasabisys.com
      region: eu-central-1
      forcePathStyle: true
      credentialsSecretRef:
        name: wasabi-creds
      buckets:
        - wasabi-bucket-1
        - wasabi-bucket-2

    - name: minio-local
      endpoint: http://minio.minio-system:9000
      region: us-east-1
      forcePathStyle: true
      credentialsSecretRef:
        name: minio-creds
      buckets:
        - minio-*
```

#### Operator Reconciler Changes

The operator would:
1. Read S3Provider configs (or inline from GeoServerCloud)
2. Generate a ConfigMap with provider definitions
3. Mount ConfigMap into GeoServer pods
4. GeoServer reads ConfigMap at startup and populates S3ConfigRegistry

---

### Phase 2: GeoServer Cloud Spring Boot Integration

#### New Auto-Configuration Class

```java
@Configuration
@ConfigurationProperties(prefix = "geoserver.cog.s3")
@EnableConfigurationProperties
public class S3ProviderAutoConfiguration {

    private Map<String, S3ProviderConfig> providers = new HashMap<>();

    @PostConstruct
    public void registerProviders() {
        for (var entry : providers.entrySet()) {
            String providerName = entry.getKey();
            S3ProviderConfig config = entry.getValue();

            S3StorageConfig storageConfig = S3StorageConfig.builder()
                .endpoint(config.getEndpoint())
                .region(config.getRegion())
                .credentials(config.getAccessKeyId(), config.getSecretAccessKey())
                .forcePathStyle(config.isForcePathStyle())
                .build();

            // Register each bucket with this provider
            for (String bucket : config.getBuckets()) {
                if (bucket.endsWith("*")) {
                    // Wildcard - register as pattern
                    S3ConfigRegistry.registerPattern(bucket, storageConfig);
                } else {
                    S3ConfigRegistry.register(bucket, storageConfig);
                }
            }

            log.info("Registered S3 provider '{}' for buckets: {}",
                     providerName, config.getBuckets());
        }
    }

    @Data
    public static class S3ProviderConfig {
        private String endpoint;
        private String region;
        private String accessKeyId;
        private String secretAccessKey;
        private boolean forcePathStyle;
        private List<String> buckets = new ArrayList<>();
    }
}
```

#### Configuration via application.yml

```yaml
geoserver:
  cog:
    s3:
      providers:
        aws-default:
          region: us-east-1
          buckets:
            - "*"  # Default fallback

        wasabi-eu:
          endpoint: https://s3.eu-central-1.wasabisys.com
          region: eu-central-1
          force-path-style: true
          access-key-id: ${WASABI_ACCESS_KEY}
          secret-access-key: ${WASABI_SECRET_KEY}
          buckets:
            - wasabi-cog-bucket
            - wasabi-imagery

        minio-local:
          endpoint: http://minio:9000
          region: us-east-1
          force-path-style: true
          access-key-id: ${MINIO_ACCESS_KEY}
          secret-access-key: ${MINIO_SECRET_KEY}
          buckets:
            - minio-*
```

---

### Phase 3: imageio-ext Fork Enhancements

#### Wildcard/Pattern Matching in S3ConfigRegistry

```java
public class S3ConfigRegistry {
    private static final Map<String, S3StorageConfig> BUCKET_CONFIGS = new ConcurrentHashMap<>();
    private static final List<PatternConfig> PATTERN_CONFIGS = new CopyOnWriteArrayList<>();

    /**
     * Registers a wildcard pattern for bucket matching.
     * Pattern supports * at the end (e.g., "minio-*" matches "minio-bucket1", "minio-data")
     */
    public static void registerPattern(String pattern, S3StorageConfig config) {
        PATTERN_CONFIGS.add(new PatternConfig(pattern, config));
        LOGGER.fine("Registered S3 pattern: " + pattern);
    }

    /**
     * Gets configuration for a bucket with fallback to pattern matching.
     */
    public static Optional<S3StorageConfig> get(String bucketName) {
        // Exact match first (highest priority)
        S3StorageConfig exact = BUCKET_CONFIGS.get(bucketName);
        if (exact != null) {
            return Optional.of(exact);
        }

        // Pattern match (in registration order)
        for (PatternConfig pc : PATTERN_CONFIGS) {
            if (pc.matches(bucketName)) {
                return Optional.of(pc.config);
            }
        }

        // Default config (lowest priority)
        return Optional.ofNullable(defaultConfig);
    }

    private static class PatternConfig {
        final String pattern;
        final S3StorageConfig config;
        final boolean isWildcard;
        final String prefix;

        PatternConfig(String pattern, S3StorageConfig config) {
            this.pattern = pattern;
            this.config = config;
            this.isWildcard = pattern.endsWith("*");
            this.prefix = isWildcard ? pattern.substring(0, pattern.length() - 1) : pattern;
        }

        boolean matches(String bucketName) {
            if (isWildcard) {
                return bucketName.startsWith(prefix);
            }
            return bucketName.equals(pattern);
        }
    }
}
```

---

### Phase 4: UI Enhancements (Optional)

Since deployments are typically via the GeoServer Operator, UI changes are lower priority. However, if manual UI configuration is needed:

#### COG Store Panel Enhancement

Add "S3 Provider" selection to COG store creation:

```
┌─────────────────────────────────────────────────────────────┐
│  New COG Coverage Store                                      │
├─────────────────────────────────────────────────────────────┤
│  Workspace:     [my-workspace     ▼]                        │
│  Store Name:    [imagery-store      ]                       │
│  URL:           [s3://wasabi-bucket/path/to/cog.tif]        │
│                                                              │
│  ── S3 Configuration ──────────────────────────────────     │
│  Provider:      [wasabi-eu        ▼]                        │
│                 ○ Use provider from bucket mapping           │
│                 ● Select specific provider                   │
│                                                              │
│  [Show Advanced] ───────────────────────────────────────    │
│  │ Override Endpoint: [                              ]      │
│  │ Override Region:   [                              ]      │
│  │ Path Style:        [✓]                                   │
│  └──────────────────────────────────────────────────────    │
│                                                              │
│              [Cancel]  [Save]                                │
└─────────────────────────────────────────────────────────────┘
```

#### S3 Providers Management Page

```
┌─────────────────────────────────────────────────────────────┐
│  S3 Providers                                    [+ Add]    │
├─────────────────────────────────────────────────────────────┤
│  Name          │ Endpoint                  │ Region  │ Buckets│
│────────────────┼───────────────────────────┼─────────┼────────│
│  aws-default   │ (AWS Default)             │ us-east │ *      │
│  wasabi-eu     │ s3.eu-central-1.wasabi... │ eu-cen..│ 2      │
│  minio-local   │ minio:9000                │ us-east │ 3      │
└─────────────────────────────────────────────────────────────┘
```

---

## Alternative Approaches

### Per-Store S3 Override via URL Parameter

For cases where provider-level config is too rigid, support provider reference in URL:

```
cog://s3://bucket/key?provider=wasabi-eu
```

The `provider` parameter references a named provider from the registry, keeping credentials out of the URL.

### Per-Store S3 Override via Store Metadata

Store S3 provider reference in catalog metadata:

```xml
<coverageStore>
  <url>cog://s3://bucket/path/file.tif</url>
  <metadata>
    <entry key="s3Provider">wasabi-eu</entry>
  </metadata>
</coverageStore>
```

This would require:
1. GeoServer catalog to persist the metadata
2. A catalog listener to register the bucket in S3ConfigRegistry when store is loaded
3. UI panel to set the provider reference

---

## Implementation Priority

| Phase | Component | Effort | Impact | Priority |
|-------|-----------|--------|--------|----------|
| **2** | GeoServer Spring Boot integration | Medium | Enables multi-provider config | **High** |
| **1** | Operator S3Provider CRD/spec | Medium | GitOps deployment path | **High** |
| **3** | imageio-ext pattern matching | Low | Wildcard bucket support | Medium |
| **4** | WebUI enhancements | High | Manual configuration | Low |

### Recommended Implementation Order

1. **Phase 2 first** - Spring Boot integration is the foundation that enables configuration via `application.yml`
2. **Phase 1** - Operator CRD provides the production deployment path
3. **Phase 3** - Pattern matching is a convenience feature
4. **Phase 4** - UI only if manual configuration is frequently needed

---

## Related Components

### Repositories

| Repository | Purpose |
|------------|---------|
| `geoserver-cloud` | Main GeoServer Cloud distribution |
| `imageio-ext-cog-s3` | Quarticle fork with S3-compatible endpoint support |
| `geoserver-operator` | Kubernetes operator for GeoServer Cloud |

### Key Files

| File | Description |
|------|-------------|
| `imageio-ext-cog-s3/S3ConfigRegistry.java` | Per-bucket configuration registry |
| `imageio-ext-cog-s3/S3StorageConfig.java` | Configuration value object |
| `imageio-ext-cog-s3/S3ConfigurationProperties.java` | URL parsing and config resolution |
| `geoserver-cloud/raster-formats/S3CogAutoConfiguration.java` | Current global S3 auto-config |
| `geoserver-operator/CoverageStoreSpec.java` | Operator CRD with S3 config fields |

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2024-11-28 | Initial architecture proposal |
