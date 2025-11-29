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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.BlobStoreListenerList;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;

/**
 * Valkey/Redis-based BlobStore implementation for GeoWebCache.
 *
 * <p>Stores tiles directly in Valkey with configurable TTL and LRU eviction.
 * This is a standalone implementation where Valkey is the only storage.
 *
 * <p>Key structure:
 * <ul>
 *   <li>{@code tile:{layer}:{gridset}:{z}:{x}:{y}:{format}:{params}} - Tile binary data</li>
 *   <li>{@code meta:{layer}:{key}} - Layer metadata</li>
 *   <li>{@code layer:{layer}:exists} - Layer existence marker</li>
 *   <li>{@code params:{layer}:{id}} - Parameters mapping</li>
 * </ul>
 */
public class ValkeyBlobStore implements BlobStore {

    private static final Logger LOGGER = Logger.getLogger(ValkeyBlobStore.class.getName());

    private final ValkeyBlobStoreInfo config;
    private final TileLayerDispatcher layers;
    private final LockProvider lockProvider;
    private final BlobStoreListenerList listeners = new BlobStoreListenerList();

    private RedisClient redisClient;
    private StatefulRedisConnection<String, byte[]> connection;
    private RedisCommands<String, byte[]> commands;

    private final String keyPrefix;
    private final Duration ttl;

    public ValkeyBlobStore(ValkeyBlobStoreInfo config, TileLayerDispatcher layers, LockProvider lockProvider)
            throws StorageException {
        this.config = config;
        this.layers = layers;
        this.lockProvider = lockProvider;
        this.keyPrefix = config.getKeyPrefix() != null ? config.getKeyPrefix() : "gwc:";
        this.ttl = config.getTtl();

        initializeConnection();
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

            // Use composite codec: String keys, byte[] values
            RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
            connection = redisClient.connect(codec);
            commands = connection.sync();

            // Test connection
            commands.ping();
            LOGGER.info("Connected to Valkey at " + address);

        } catch (Exception e) {
            throw new StorageException("Failed to connect to Valkey", e);
        }
    }

    @Override
    public boolean get(TileObject tile) throws StorageException {
        String key = buildTileKey(tile);
        try {
            byte[] data = commands.get(key);
            if (data != null) {
                tile.setBlobSize(data.length);
                tile.setBlob(new ByteArrayResource(data));
                tile.setCreated(System.currentTimeMillis()); // Valkey doesn't store creation time
                return true;
            }
            return false;
        } catch (Exception e) {
            throw new StorageException("Error getting tile from Valkey: " + key, e);
        }
    }

    @Override
    public void put(TileObject tile) throws StorageException {
        String key = buildTileKey(tile);
        try {
            Resource blob = tile.getBlob();
            byte[] data = toByteArray(blob);

            if (ttl != null) {
                commands.setex(key, ttl.getSeconds(), data);
            } else {
                commands.set(key, data);
            }

            // Mark layer as existing
            String layerKey = keyPrefix + "layer:" + tile.getLayerName() + ":exists";
            commands.set(layerKey, "1".getBytes());

            tile.setBlobSize(data.length);
            tile.setCreated(System.currentTimeMillis());

            listeners.sendTileStored(tile);

        } catch (Exception e) {
            throw new StorageException("Error putting tile to Valkey: " + key, e);
        }
    }

    @Override
    public boolean delete(TileObject tile) throws StorageException {
        String key = buildTileKey(tile);
        try {
            long deleted = commands.del(key);
            if (deleted > 0) {
                listeners.sendTileDeleted(tile);
                return true;
            }
            return false;
        } catch (Exception e) {
            throw new StorageException("Error deleting tile from Valkey: " + key, e);
        }
    }

    @Override
    public boolean delete(String layerName) throws StorageException {
        String pattern = keyPrefix + "tile:" + layerName + ":*";
        try {
            deleteByPattern(pattern);
            // Also delete metadata
            deleteByPattern(keyPrefix + "meta:" + layerName + ":*");
            deleteByPattern(keyPrefix + "params:" + layerName + ":*");
            commands.del(keyPrefix + "layer:" + layerName + ":exists");

            listeners.sendLayerDeleted(layerName);
            return true;
        } catch (Exception e) {
            throw new StorageException("Error deleting layer from Valkey: " + layerName, e);
        }
    }

    @Override
    public boolean deleteByGridsetId(String layerName, String gridSetId) throws StorageException {
        String pattern = keyPrefix + "tile:" + layerName + ":" + gridSetId + ":*";
        try {
            deleteByPattern(pattern);
            return true;
        } catch (Exception e) {
            throw new StorageException("Error deleting gridset from Valkey: " + gridSetId, e);
        }
    }

    @Override
    public boolean delete(TileRange tileRange) throws StorageException {
        String layerName = tileRange.getLayerName();
        String gridSetId = tileRange.getGridSetId();
        MimeType mimeType = tileRange.getMimeType();
        String format = mimeType != null ? mimeType.getFormat() : "*";

        int zoomStart = tileRange.getZoomStart();
        int zoomStop = tileRange.getZoomStop();

        int deletedCount = 0;
        for (int z = zoomStart; z <= zoomStop; z++) {
            long[] bounds;
            try {
                bounds = tileRange.rangeBounds(z);
            } catch (IllegalArgumentException | IllegalStateException e) {
                // No bounds for this zoom level
                continue;
            }

            long minX = bounds[0];
            long minY = bounds[1];
            long maxX = bounds[2];
            long maxY = bounds[3];

            for (long x = minX; x <= maxX; x++) {
                for (long y = minY; y <= maxY; y++) {
                    String key = buildTileKey(layerName, gridSetId, z, x, y, format, null);
                    try {
                        deletedCount += commands.del(key);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error deleting tile: " + key, e);
                    }
                }
            }
        }

        final int finalDeletedCount = deletedCount;
        LOGGER.fine(() -> "Deleted " + finalDeletedCount + " tiles for range in layer " + layerName);
        return deletedCount > 0;
    }

    @Override
    public boolean deleteByParametersId(String layerName, String parametersId) throws StorageException {
        String pattern = keyPrefix + "tile:" + layerName + ":*:*:*:*:*:" + parametersId;
        try {
            deleteByPattern(pattern);
            commands.del(keyPrefix + "params:" + layerName + ":" + parametersId);
            return true;
        } catch (Exception e) {
            throw new StorageException("Error deleting by parameters from Valkey", e);
        }
    }

    @Override
    public boolean rename(String oldLayerName, String newLayerName) throws StorageException {
        // Valkey doesn't support efficient key renaming with patterns
        // We need to copy all keys and delete old ones
        try {
            String oldPattern = keyPrefix + "tile:" + oldLayerName + ":*";
            io.lettuce.core.ScanIterator<String> iterator =
                    io.lettuce.core.ScanIterator.scan(commands, io.lettuce.core.ScanArgs.Builder.matches(oldPattern));

            while (iterator.hasNext()) {
                String oldKey = iterator.next();
                String newKey = oldKey.replace(":" + oldLayerName + ":", ":" + newLayerName + ":");
                byte[] value = commands.get(oldKey);
                if (value != null) {
                    if (ttl != null) {
                        commands.setex(newKey, ttl.getSeconds(), value);
                    } else {
                        commands.set(newKey, value);
                    }
                    commands.del(oldKey);
                }
            }

            // Rename metadata
            renameKeys(keyPrefix + "meta:" + oldLayerName + ":", keyPrefix + "meta:" + newLayerName + ":");
            renameKeys(keyPrefix + "params:" + oldLayerName + ":", keyPrefix + "params:" + newLayerName + ":");

            // Update layer existence marker
            commands.del(keyPrefix + "layer:" + oldLayerName + ":exists");
            commands.set(keyPrefix + "layer:" + newLayerName + ":exists", "1".getBytes());

            listeners.sendLayerRenamed(oldLayerName, newLayerName);
            return true;
        } catch (Exception e) {
            throw new StorageException("Error renaming layer in Valkey", e);
        }
    }

    @Override
    public void clear() throws StorageException {
        try {
            deleteByPattern(keyPrefix + "*");
            LOGGER.info("Cleared all tiles from Valkey");
        } catch (Exception e) {
            throw new StorageException("Error clearing Valkey cache", e);
        }
    }

    @Override
    public void destroy() {
        if (connection != null) {
            connection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
        LOGGER.info("Valkey connection closed");
    }

    @Override
    public void addListener(BlobStoreListener listener) {
        listeners.addListener(listener);
    }

    @Override
    public boolean removeListener(BlobStoreListener listener) {
        return listeners.removeListener(listener);
    }

    @Override
    public boolean layerExists(String layerName) {
        String key = keyPrefix + "layer:" + layerName + ":exists";
        try {
            return commands.exists(key) > 0;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error checking layer existence in Valkey", e);
            return false;
        }
    }

    @Override
    public String getLayerMetadata(String layerName, String key) {
        String metaKey = keyPrefix + "meta:" + layerName + ":" + key;
        try {
            byte[] value = commands.get(metaKey);
            return value != null ? new String(value) : null;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting layer metadata from Valkey", e);
            return null;
        }
    }

    @Override
    public void putLayerMetadata(String layerName, String key, String value) {
        String metaKey = keyPrefix + "meta:" + layerName + ":" + key;
        try {
            commands.set(metaKey, value.getBytes());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error putting layer metadata to Valkey", e);
        }
    }

    @Override
    public Map<String, Optional<Map<String, String>>> getParametersMapping(String layerName) {
        Map<String, Optional<Map<String, String>>> result = new HashMap<>();
        String pattern = keyPrefix + "params:" + layerName + ":*";
        try {
            io.lettuce.core.ScanIterator<String> iterator =
                    io.lettuce.core.ScanIterator.scan(commands, io.lettuce.core.ScanArgs.Builder.matches(pattern));

            while (iterator.hasNext()) {
                String key = iterator.next();
                String paramsId = key.substring(key.lastIndexOf(':') + 1);
                // For simplicity, store empty map - full implementation would deserialize params
                result.put(paramsId, Optional.of(new HashMap<>()));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting parameters mapping from Valkey", e);
        }
        return result;
    }

    @Override
    public Set<String> getParameterIds(String layerName) {
        Set<String> result = new HashSet<>();
        String pattern = keyPrefix + "params:" + layerName + ":*";
        try {
            io.lettuce.core.ScanIterator<String> iterator =
                    io.lettuce.core.ScanIterator.scan(commands, io.lettuce.core.ScanArgs.Builder.matches(pattern));

            while (iterator.hasNext()) {
                String key = iterator.next();
                String paramsId = key.substring(key.lastIndexOf(':') + 1);
                result.add(paramsId);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting parameter IDs from Valkey", e);
        }
        return result;
    }

    // Helper methods

    private String buildTileKey(TileObject tile) {
        return buildTileKey(
                tile.getLayerName(),
                tile.getGridSetId(),
                (int) tile.getXYZ()[2],
                tile.getXYZ()[0],
                tile.getXYZ()[1],
                tile.getBlobFormat(),
                tile.getParametersId());
    }

    private String buildTileKey(String layer, String gridset, int z, long x, long y, String format, String paramsId) {
        StringBuilder sb = new StringBuilder(keyPrefix);
        sb.append("tile:")
                .append(layer)
                .append(":")
                .append(gridset)
                .append(":")
                .append(z)
                .append(":")
                .append(x)
                .append(":")
                .append(y)
                .append(":")
                .append(format != null ? format : "unknown");
        if (paramsId != null) {
            sb.append(":").append(paramsId);
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

    private void renameKeys(String oldPrefix, String newPrefix) {
        io.lettuce.core.ScanIterator<String> iterator =
                io.lettuce.core.ScanIterator.scan(commands, io.lettuce.core.ScanArgs.Builder.matches(oldPrefix + "*"));

        while (iterator.hasNext()) {
            String oldKey = iterator.next();
            String newKey = oldKey.replace(oldPrefix, newPrefix);
            byte[] value = commands.get(oldKey);
            if (value != null) {
                commands.set(newKey, value);
                commands.del(oldKey);
            }
        }
    }

    private byte[] toByteArray(Resource resource) throws StorageException {
        try {
            if (resource instanceof ByteArrayResource) {
                return ((ByteArrayResource) resource).getContents();
            }
            // Read from input stream
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            resource.transferTo(java.nio.channels.Channels.newChannel(baos));
            return baos.toByteArray();
        } catch (IOException e) {
            throw new StorageException("Error reading resource bytes", e);
        }
    }
}
