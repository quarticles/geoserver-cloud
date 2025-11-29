# GeoWebCache Valkey BlobStore

## Overview

This document describes a Valkey (Redis-compatible) BlobStore implementation for GeoWebCache in GeoServer Cloud. Valkey provides sub-millisecond tile retrieval, shared caching across all pods, and automatic LRU eviction.

## Problem Statement

### Current Storage Options

| Backend | Storage | Latency | Limitations |
|---------|---------|---------|-------------|
| FileBlobStore | Local disk/NFS | 5-20ms | Requires ReadWriteMany volume, NFS overhead |
| S3BlobStore | AWS S3 | 50-200ms | High latency, cost per request |
| AzureBlobStore | Azure Blob | 50-200ms | High latency, cost per request |
| GCSBlobStore | Google Cloud | 50-200ms | High latency, cost per request |

### Issues in Kubernetes

1. **File-based storage** requires shared volumes (NFS/ReadWriteMany)
   - NFS adds 5-20ms latency overhead
   - Single point of failure
   - Volume provisioning complexity

2. **Cloud object storage** has high latency
   - 50-200ms per tile request
   - Acceptable for cold storage, not hot cache

3. **No distributed in-memory cache**
   - Each pod maintains its own local cache
   - No shared hot cache across cluster
   - Repeated cache misses on different pods

## Solution: Valkey BlobStore

### Why Valkey?

- **Sub-millisecond latency** (<1ms vs 50-200ms for S3)
- **Distributed cache** shared across all pods
- **LRU eviction** built-in for automatic memory management
- **BSD licensed** (vs Redis SSPL after v7.4)
- **Cluster mode** for horizontal scaling
- **Persistence options** (RDB/AOF) if durability needed

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Kubernetes Cluster                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐            │
│  │ GWC Pod │  │ GWC Pod │  │ GWC Pod │  │ GWC Pod │            │
│  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘            │
│       │            │            │            │                   │
│       └────────────┴─────┬──────┴────────────┘                  │
│                          ↓                                       │
│              ┌───────────────────────┐                          │
│              │   Valkey Cluster      │  ← Sub-ms latency        │
│              │   (3-6 nodes)         │  ← Shared across pods    │
│              └───────────┬───────────┘  ← LRU eviction          │
│                          ↓                                       │
│              ┌───────────────────────┐                          │
│              │   S3 / Azure / GCS    │  ← Durable storage       │
│              │   (Optional L2 cache) │  ← Cold tiles            │
│              └───────────────────────┘                          │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Tile Request Flow

```
HTTP Tile Request
       ↓
GWC Dispatcher → TileLayer.getTile()
       ↓
ValkeyCacheBlobStore.get(tile)
       ↓
┌──────┴──────┐
│ Valkey GET  │
└──────┬──────┘
       ↓
┌──────────────┐     ┌─────────────────────────┐
│ Cache Hit?   │────→│ Return tile (<1ms)      │
└──────┬───────┘ Yes └─────────────────────────┘
       │ No
       ↓
┌──────────────┐
│ Delegate.get │ (S3/File)
└──────┬───────┘
       ↓
┌──────────────┐     ┌─────────────────────────┐
│ Found?       │────→│ Cache in Valkey + Return│
└──────┬───────┘ Yes └─────────────────────────┘
       │ No
       ↓
┌──────────────┐
│ WMS GetMap   │ (Render tile)
└──────┬───────┘
       ↓
┌──────────────┐
│ Store tile   │ → Valkey + Delegate
└──────────────┘
```

## Implementation

### BlobStore Interface

The GeoWebCache `BlobStore` interface requires these methods:

```java
public interface BlobStore {
    // Tile operations
    boolean get(TileObject tile);           // Retrieve tile
    void put(TileObject tile);              // Store tile
    boolean delete(TileObject tile);        // Delete single tile
    boolean delete(TileRange range);        // Delete tile range

    // Layer operations
    boolean delete(String layerName);       // Delete all layer tiles
    boolean deleteByGridsetId(String layer, String gridSetId);
    boolean deleteByParametersId(String layer, String parametersId);
    boolean rename(String oldName, String newName);
    boolean layerExists(String layerName);

    // Metadata
    String getLayerMetadata(String layer, String key);
    void putLayerMetadata(String layer, String key, String value);
    Map<String, Optional<Map<String, String>>> getParametersMapping(String layer);
    Set<String> getParameterIds(String layer);

    // Lifecycle
    void clear();
    void addListener(BlobStoreListener listener);
    boolean removeListener(BlobStoreListener listener);
}
```

### Valkey Key Structure

```
# Tile data (binary blob)
tile:{layer}:{gridset}:{z}:{x}:{y}:{format}:{params_hash}

# Layer metadata
meta:{layer}:{key}

# Parameters mapping
params:{layer}:{params_id}

# Layer existence marker
layer:{layer}:exists

# Examples:
tile:topp:states:EPSG:4326:5:12:23:image/png:abc123
meta:topp:states:lastModified
params:topp:states:def456
layer:topp:states:exists
```

### Two Implementation Modes

#### 1. Cache Mode (Recommended)

Wraps another BlobStore (S3/File) for durability:

```java
public class ValkeyCacheBlobStore implements BlobStore {
    private final BlobStore delegate;      // S3, Azure, or File
    private final RedissonClient valkey;   // Valkey client
    private final Duration ttl;            // Cache TTL

    @Override
    public boolean get(TileObject tile) {
        String key = buildTileKey(tile);
        byte[] cached = valkey.getBucket(key).get();

        if (cached != null) {
            tile.setBlob(new ByteArrayResource(cached));
            return true;
        }

        // Cache miss - get from delegate
        if (delegate.get(tile)) {
            // Cache the tile
            byte[] data = tile.getBlob().getContents();
            valkey.getBucket(key).set(data, ttl);
            return true;
        }
        return false;
    }

    @Override
    public void put(TileObject tile) {
        // Write-through: persist to delegate first
        delegate.put(tile);

        // Then cache in Valkey
        String key = buildTileKey(tile);
        byte[] data = tile.getBlob().getContents();
        valkey.getBucket(key).set(data, ttl);
    }

    @Override
    public boolean delete(TileObject tile) {
        String key = buildTileKey(tile);
        valkey.getBucket(key).delete();
        return delegate.delete(tile);
    }
}
```

#### 2. Standalone Mode

All tiles stored only in Valkey (ephemeral cache):

```java
public class ValkeyBlobStore implements BlobStore {
    private final RedissonClient valkey;

    // All tiles stored in Valkey only
    // LRU eviction when memory limit reached
    // Optional RDB/AOF persistence for durability
}
```

## Configuration

### Spring Boot Properties

```yaml
gwc:
  enabled: true
  blobstores:
    valkey:
      enabled: true
      mode: cache              # 'cache' (wraps delegate) or 'standalone'
      delegate: s3             # Delegate blobstore ID (for cache mode)

      # Valkey connection
      connection:
        addresses:
          - "valkey://valkey-master:6379"
        password: ${VALKEY_PASSWORD:}
        database: 0
        timeout: 3000
        connection-pool-size: 64
        connection-minimum-idle-size: 8

      # Cluster mode (optional)
      cluster:
        enabled: false
        node-addresses:
          - "valkey://valkey-node-1:6379"
          - "valkey://valkey-node-2:6379"
          - "valkey://valkey-node-3:6379"
        scan-interval: 5000

      # Sentinel mode (optional)
      sentinel:
        enabled: false
        master-name: mymaster
        addresses:
          - "valkey://sentinel-1:26379"
          - "valkey://sentinel-2:26379"

      # Cache settings
      cache:
        ttl: 24h               # Tile TTL (0 = no expiry, use LRU only)
        key-prefix: "gwc:"     # Key prefix for namespacing

      # Memory settings (for standalone mode)
      memory:
        max-memory: 8GB
        eviction-policy: allkeys-lru
```

### Kubernetes Deployment Example

```yaml
# Valkey StatefulSet
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: valkey
spec:
  serviceName: valkey
  replicas: 3
  template:
    spec:
      containers:
      - name: valkey
        image: valkey/valkey:8.1
        args:
          - "--maxmemory"
          - "8gb"
          - "--maxmemory-policy"
          - "allkeys-lru"
          - "--appendonly"
          - "no"
        ports:
        - containerPort: 6379
        resources:
          requests:
            memory: "8Gi"
          limits:
            memory: "10Gi"
---
# GeoServer Cloud ConfigMap
apiVersion: v1
kind: ConfigMap
metadata:
  name: geoserver-config
data:
  application.yml: |
    gwc:
      enabled: true
      blobstores:
        valkey:
          enabled: true
          mode: cache
          delegate: s3
          connection:
            addresses:
              - "valkey://valkey:6379"
          cache:
            ttl: 4h
```

## Performance Comparison

| Metric | File (NFS) | S3 | Valkey | Valkey+S3 |
|--------|-----------|-----|--------|-----------|
| Get latency (hot) | 5-20ms | 50-200ms | <1ms | <1ms |
| Get latency (cold) | 5-20ms | 50-200ms | N/A | 50-200ms |
| Put latency | 10-50ms | 100-300ms | <1ms | 100-300ms |
| Throughput | 1K-5K/s | 500-2K/s | 50K-100K/s | 50K-100K/s |
| Shared cache | Via NFS | Yes | Yes | Yes |
| Durability | Volume | High | Optional | High |
| Memory usage | Disk | N/A | RAM | RAM+S3 |
| Cost | Volume | $/request | RAM | RAM+S3 |

## Module Structure

```
src/gwc/blobstores/valkey/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/org/geoserver/cloud/gwc/config/blobstore/valkey/
    │   │   ├── ValkeyBlobstoreConfiguration.java
    │   │   ├── ValkeyBlobstoreProperties.java
    │   │   ├── ValkeyBlobStore.java
    │   │   ├── ValkeyCacheBlobStore.java
    │   │   ├── ValkeyBlobStoreInfo.java
    │   │   ├── ValkeyBlobStoreConfigProvider.java
    │   │   └── ValkeyClientFactory.java
    │   └── resources/
    │       └── META-INF/
    │           └── spring.factories
    └── test/
        └── java/org/geoserver/cloud/gwc/config/blobstore/valkey/
            ├── ValkeyBlobStoreTest.java
            ├── ValkeyCacheBlobStoreTest.java
            └── ValkeyBlobStoreIT.java

src/gwc/autoconfigure/src/main/java/org/geoserver/cloud/autoconfigure/gwc/
├── ConditionalOnValkeyBlobstoreEnabled.java
└── blobstore/
    └── ValkeyBlobstoreAutoConfiguration.java
```

## Dependencies

```xml
<!-- Valkey/Redis client -->
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.27.0</version>
</dependency>

<!-- Or Lettuce (lighter weight) -->
<dependency>
    <groupId>io.lettuce</groupId>
    <artifactId>lettuce-core</artifactId>
    <version>6.3.0.RELEASE</version>
</dependency>
```

## Implementation Phases

### Phase 1: Core Implementation
1. Create `valkey` module under `src/gwc/blobstores/`
2. Implement `ValkeyBlobStoreInfo` (configuration model)
3. Implement `ValkeyBlobStore` (standalone mode)
4. Implement `ValkeyCacheBlobStore` (cache wrapper mode)
5. Add Spring Boot auto-configuration

### Phase 2: Integration
1. Add `ValkeyBlobStoreConfigProvider` for XML/REST configuration
2. Implement cluster mode support
3. Add metrics (Micrometer) for cache hits/misses
4. Integration tests with Testcontainers

### Phase 3: Production Hardening
1. Connection pooling optimization
2. Circuit breaker for Valkey failures (fallback to delegate)
3. Compression support for large tiles
4. Monitoring dashboards (Grafana)

## Monitoring

### Metrics

```
# Cache metrics
gwc_valkey_cache_hits_total
gwc_valkey_cache_misses_total
gwc_valkey_cache_hit_ratio

# Latency metrics
gwc_valkey_get_latency_seconds
gwc_valkey_put_latency_seconds

# Connection metrics
gwc_valkey_connections_active
gwc_valkey_connections_idle

# Memory metrics (from Valkey)
valkey_used_memory_bytes
valkey_evicted_keys_total
```

### Health Check

```java
@Component
public class ValkeyHealthIndicator implements HealthIndicator {
    private final RedissonClient valkey;

    @Override
    public Health health() {
        try {
            valkey.getBucket("health").set("ok", Duration.ofSeconds(1));
            return Health.up()
                .withDetail("cluster", valkey.getClusterNodes())
                .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
```

## References

- [Valkey GitHub](https://github.com/valkey-io/valkey)
- [GeoWebCache BlobStore Interface](https://github.com/GeoWebCache/geowebcache/wiki/Pluggable-BlobStores)
- [Redisson Documentation](https://redisson.org/docs/)
- [GeoServer Cloud GWC Module](src/gwc/)

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2024-11-28 | Initial design document |
