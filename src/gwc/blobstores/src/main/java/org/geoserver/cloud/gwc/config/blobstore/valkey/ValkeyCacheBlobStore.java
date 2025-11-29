/*
 * (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.cloud.gwc.config.blobstore.valkey;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;

/**
 * Valkey-backed caching wrapper for another BlobStore.
 *
 * <p>This implementation uses Valkey as a fast L1 cache in front of a durable
 * blob store (S3, Azure, File, etc.). Cache misses fall through to the delegate
 * and the result is cached in Valkey for subsequent requests.
 *
 * <p>Benefits:
 * <ul>
 *   <li>Sub-millisecond reads for cached tiles</li>
 *   <li>Shared cache across all GeoServer pods</li>
 *   <li>Automatic LRU eviction when memory limit reached</li>
 *   <li>Write-through to durable storage</li>
 * </ul>
 *
 * <p>Key structure:
 * <ul>
 *   <li>{@code tile:{layer}:{gridset}:{z}:{x}:{y}:{format}:{params}} - Tile binary data</li>
 * </ul>
 */
public class ValkeyCacheBlobStore implements BlobStore {

    private static final Logger LOGGER = Logger.getLogger(ValkeyCacheBlobStore.class.getName());

    private final BlobStore delegate;
    private final ValkeyBlobStoreInfo config;

    private RedisClient redisClient;
    private StatefulRedisConnection<String, byte[]> connection;
    private RedisCommands<String, byte[]> commands;

    private final String keyPrefix;
    private final Duration ttl;

    // Metrics
    private long cacheHits = 0;
    private long cacheMisses = 0;

    /**
     * Wraps an existing BlobStore with Valkey caching.
     *
     * @param delegate the underlying blob store for durable storage
     * @param config   Valkey configuration
     */
    public ValkeyCacheBlobStore(BlobStore delegate, ValkeyBlobStoreInfo config) throws StorageException {
        this.delegate = delegate;
        this.config = config;
        this.keyPrefix = config.getKeyPrefix() != null ? config.getKeyPrefix() : "gwc:";
        this.ttl = config.getTtl();

        initializeConnection();
    }

    /**
     * Static factory method to wrap a BlobStore with Valkey caching.
     */
    public static ValkeyCacheBlobStore wrap(BlobStore delegate, ValkeyBlobStoreInfo config) throws StorageException {
        return new ValkeyCacheBlobStore(delegate, config);
    }

    private void initializeConnection() throws StorageException {
        try {
            String address =
                    config.getAddresses() != null && !config.getAddresses().isEmpty()
                            ? config.getAddresses().get(0)
                            : "redis://localhost:6379";

            RedisURI.Builder uriBuilder = RedisURI.builder(RedisURI.create(address))
                    .withDatabase(config.getDatabase())
                    .withTimeout(Duration.ofMillis(config.getConnectionTimeout()));

            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                uriBuilder.withPassword(config.getPassword().toCharArray());
            }

            redisClient = RedisClient.create(uriBuilder.build());

            RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
            connection = redisClient.connect(codec);
            commands = connection.sync();

            // Test connection
            commands.ping();
            LOGGER.info("Valkey cache connected at " + address + " (wrapping "
                    + delegate.getClass().getSimpleName() + ")");

        } catch (Exception e) {
            throw new StorageException("Failed to connect to Valkey cache", e);
        }
    }

    @Override
    public boolean get(TileObject tile) throws StorageException {
        String key = buildTileKey(tile);

        // Try cache first
        try {
            byte[] cached = commands.get(key);
            if (cached != null) {
                tile.setBlobSize(cached.length);
                tile.setBlob(new ByteArrayResource(cached));
                tile.setCreated(System.currentTimeMillis());
                cacheHits++;
                LOGGER.finest(() -> "Cache HIT: " + key);
                return true;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Valkey cache read error, falling back to delegate", e);
        }

        // Cache miss - get from delegate
        cacheMisses++;
        LOGGER.finest(() -> "Cache MISS: " + key);

        if (delegate.get(tile)) {
            // Cache the result
            try {
                byte[] data = toByteArray(tile.getBlob());
                if (ttl != null) {
                    commands.setex(key, ttl.getSeconds(), data);
                } else {
                    commands.set(key, data);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to cache tile in Valkey", e);
            }
            return true;
        }

        return false;
    }

    @Override
    public void put(TileObject tile) throws StorageException {
        // Write-through: persist to delegate first
        delegate.put(tile);

        // Then cache in Valkey
        String key = buildTileKey(tile);
        try {
            byte[] data = toByteArray(tile.getBlob());
            if (ttl != null) {
                commands.setex(key, ttl.getSeconds(), data);
            } else {
                commands.set(key, data);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to cache tile in Valkey", e);
        }
    }

    @Override
    public boolean delete(TileObject tile) throws StorageException {
        String key = buildTileKey(tile);

        // Delete from cache
        try {
            commands.del(key);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to delete tile from Valkey cache", e);
        }

        // Delete from delegate
        return delegate.delete(tile);
    }

    @Override
    public boolean delete(String layerName) throws StorageException {
        // Invalidate cache
        try {
            deleteByPattern(keyPrefix + "tile:" + layerName + ":*");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to invalidate layer cache in Valkey", e);
        }

        // Delete from delegate
        return delegate.delete(layerName);
    }

    @Override
    public boolean deleteByGridsetId(String layerName, String gridSetId) throws StorageException {
        // Invalidate cache
        try {
            deleteByPattern(keyPrefix + "tile:" + layerName + ":" + gridSetId + ":*");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to invalidate gridset cache in Valkey", e);
        }

        // Delete from delegate
        return delegate.deleteByGridsetId(layerName, gridSetId);
    }

    @Override
    public boolean delete(TileRange tileRange) throws StorageException {
        // For range deletes, invalidate the entire layer cache (simpler than per-tile)
        String layerName = tileRange.getLayerName();
        String gridSetId = tileRange.getGridSetId();

        try {
            deleteByPattern(keyPrefix + "tile:" + layerName + ":" + gridSetId + ":*");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to invalidate range cache in Valkey", e);
        }

        return delegate.delete(tileRange);
    }

    @Override
    public boolean deleteByParametersId(String layerName, String parametersId) throws StorageException {
        // Invalidate cache
        try {
            deleteByPattern(keyPrefix + "tile:" + layerName + ":*:*:*:*:*:" + parametersId);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to invalidate parameters cache in Valkey", e);
        }

        return delegate.deleteByParametersId(layerName, parametersId);
    }

    @Override
    public boolean rename(String oldLayerName, String newLayerName) throws StorageException {
        // Invalidate old layer cache (new layer will be cached on demand)
        try {
            deleteByPattern(keyPrefix + "tile:" + oldLayerName + ":*");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to invalidate renamed layer cache in Valkey", e);
        }

        return delegate.rename(oldLayerName, newLayerName);
    }

    @Override
    public void clear() throws StorageException {
        // Clear cache
        try {
            deleteByPattern(keyPrefix + "tile:*");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to clear Valkey cache", e);
        }

        delegate.clear();
    }

    @Override
    public void destroy() {
        if (connection != null) {
            connection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
        delegate.destroy();
        LOGGER.info("Valkey cache connection closed");
    }

    @Override
    public void addListener(BlobStoreListener listener) {
        delegate.addListener(listener);
    }

    @Override
    public boolean removeListener(BlobStoreListener listener) {
        return delegate.removeListener(listener);
    }

    // Delegate to underlying store for metadata operations

    @Override
    public boolean layerExists(String layerName) {
        return delegate.layerExists(layerName);
    }

    @Override
    public String getLayerMetadata(String layerName, String key) {
        return delegate.getLayerMetadata(layerName, key);
    }

    @Override
    public void putLayerMetadata(String layerName, String key, String value) {
        delegate.putLayerMetadata(layerName, key, value);
    }

    @Override
    public Map<String, Optional<Map<String, String>>> getParametersMapping(String layerName) {
        return delegate.getParametersMapping(layerName);
    }

    @Override
    public Set<String> getParameterIds(String layerName) throws StorageException {
        return delegate.getParameterIds(layerName);
    }

    // Helper methods

    private String buildTileKey(TileObject tile) {
        StringBuilder sb = new StringBuilder(keyPrefix);
        sb.append("tile:")
                .append(tile.getLayerName())
                .append(":")
                .append(tile.getGridSetId())
                .append(":")
                .append(tile.getXYZ()[2])
                .append(":")
                .append(tile.getXYZ()[0])
                .append(":")
                .append(tile.getXYZ()[1])
                .append(":")
                .append(tile.getBlobFormat() != null ? tile.getBlobFormat() : "unknown");
        if (tile.getParametersId() != null) {
            sb.append(":").append(tile.getParametersId());
        }
        return sb.toString();
    }

    private void deleteByPattern(String pattern) {
        io.lettuce.core.ScanIterator<String> iterator =
                io.lettuce.core.ScanIterator.scan(commands, io.lettuce.core.ScanArgs.Builder.matches(pattern));

        while (iterator.hasNext()) {
            commands.del(iterator.next());
        }
    }

    private byte[] toByteArray(Resource resource) throws StorageException {
        try {
            if (resource instanceof ByteArrayResource) {
                return ((ByteArrayResource) resource).getContents();
            }
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            resource.transferTo(java.nio.channels.Channels.newChannel(baos));
            return baos.toByteArray();
        } catch (IOException e) {
            throw new StorageException("Error reading resource bytes", e);
        }
    }

    // Metrics

    public long getCacheHits() {
        return cacheHits;
    }

    public long getCacheMisses() {
        return cacheMisses;
    }

    public double getCacheHitRatio() {
        long total = cacheHits + cacheMisses;
        return total > 0 ? (double) cacheHits / total : 0.0;
    }

    public BlobStore getDelegate() {
        return delegate;
    }

    // Cache management methods for manual invalidation

    /**
     * Invalidate all cached tiles for a layer.
     * Use this when tiles have been modified externally (not through GWC).
     *
     * @param layerName the layer name
     * @return number of keys invalidated
     */
    public long invalidateLayer(String layerName) {
        String pattern = keyPrefix + "tile:" + layerName + ":*";
        return invalidateByPattern(pattern);
    }

    /**
     * Invalidate all cached tiles for a layer and gridset.
     *
     * @param layerName the layer name
     * @param gridSetId the gridset ID
     * @return number of keys invalidated
     */
    public long invalidateGridset(String layerName, String gridSetId) {
        String pattern = keyPrefix + "tile:" + layerName + ":" + gridSetId + ":*";
        return invalidateByPattern(pattern);
    }

    /**
     * Invalidate all cached tiles matching a pattern.
     *
     * @param pattern Redis key pattern (e.g., "gwc:tile:myLayer:*")
     * @return number of keys invalidated
     */
    public long invalidateByPattern(String pattern) {
        long count = 0;
        try {
            io.lettuce.core.ScanIterator<String> iterator =
                    io.lettuce.core.ScanIterator.scan(commands, io.lettuce.core.ScanArgs.Builder.matches(pattern));

            while (iterator.hasNext()) {
                commands.del(iterator.next());
                count++;
            }
            LOGGER.info("Invalidated " + count + " cached tiles matching: " + pattern);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to invalidate cache by pattern: " + pattern, e);
        }
        return count;
    }

    /**
     * Invalidate the entire tile cache.
     * Use with caution - this will cause all tiles to be re-fetched from the delegate.
     *
     * @return number of keys invalidated
     */
    public long invalidateAll() {
        String pattern = keyPrefix + "tile:*";
        return invalidateByPattern(pattern);
    }

    /**
     * Get approximate cache size (number of cached tiles).
     *
     * @return estimated number of cached tiles
     */
    public long getCacheSize() {
        try {
            // Use DBSIZE for a quick estimate
            // Note: This counts all keys in the database, not just tiles
            return commands.dbsize();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get cache size", e);
            return -1;
        }
    }

    /**
     * Get memory info from Valkey.
     *
     * @return memory usage string or null on error
     */
    public String getMemoryInfo() {
        try {
            String info = commands.info("memory");
            // Extract used_memory_human
            for (String line : info.split("\n")) {
                if (line.startsWith("used_memory_human:")) {
                    return line.split(":")[1].trim();
                }
            }
            return info;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get memory info", e);
            return null;
        }
    }
}
