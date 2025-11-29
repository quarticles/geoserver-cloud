/*
 * (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.cloud.gwc.config.blobstore.valkey;

import java.time.Duration;
import java.util.List;
import org.geowebcache.config.BlobStoreInfo;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.StorageException;

/**
 * Configuration for Valkey/Redis-based tile cache blob store.
 *
 * <p>Supports two modes:
 * <ul>
 *   <li><b>standalone</b>: All tiles stored only in Valkey (ephemeral cache with LRU eviction)</li>
 *   <li><b>cache</b>: Wraps another BlobStore, using Valkey as a fast L1 cache</li>
 * </ul>
 *
 * <p>Configuration example:
 * <pre>{@code
 * <ValkeyBlobStore>
 *   <id>valkey-cache</id>
 *   <enabled>true</enabled>
 *   <default>true</default>
 *   <mode>cache</mode>
 *   <delegateBlobStoreId>s3-storage</delegateBlobStoreId>
 *   <addresses>
 *     <string>valkey://valkey-master:6379</string>
 *   </addresses>
 *   <password>secret</password>
 *   <database>0</database>
 *   <ttlSeconds>86400</ttlSeconds>
 *   <keyPrefix>gwc:</keyPrefix>
 *   <connectionPoolSize>64</connectionPoolSize>
 *   <connectionMinIdleSize>8</connectionMinIdleSize>
 *   <connectionTimeout>3000</connectionTimeout>
 * </ValkeyBlobStore>
 * }</pre>
 */
public class ValkeyBlobStoreInfo extends BlobStoreInfo {

    private static final long serialVersionUID = 1L;

    /** Operation mode: 'standalone' or 'cache' */
    private String mode = "cache";

    /** Delegate blob store ID (for cache mode) */
    private String delegateBlobStoreId;

    /** Valkey server addresses */
    private List<String> addresses;

    /** Authentication password (optional) */
    private String password;

    /** Database index (default: 0) */
    private int database = 0;

    /** Tile TTL in seconds (0 = no expiry, use LRU only) */
    private long ttlSeconds = 86400; // 24 hours

    /** Key prefix for namespacing */
    private String keyPrefix = "gwc:";

    /** Connection pool size */
    private int connectionPoolSize = 64;

    /** Minimum idle connections */
    private int connectionMinIdleSize = 8;

    /** Connection timeout in milliseconds */
    private int connectionTimeout = 3000;

    /** Enable cluster mode */
    private boolean clusterMode = false;

    /** Cluster scan interval in milliseconds */
    private int clusterScanInterval = 5000;

    /** Enable sentinel mode */
    private boolean sentinelMode = false;

    /** Sentinel master name */
    private String sentinelMasterName;

    public ValkeyBlobStoreInfo() {
        super();
    }

    public ValkeyBlobStoreInfo(String id) {
        super(id);
    }

    @Override
    public BlobStore createInstance(TileLayerDispatcher layers, LockProvider lockProvider) throws StorageException {
        if ("standalone".equalsIgnoreCase(mode)) {
            return new ValkeyBlobStore(this, layers, lockProvider);
        } else {
            // Cache mode requires delegate to be resolved by the caller
            throw new StorageException(
                    "Cache mode requires delegate BlobStore. Use ValkeyCacheBlobStore.wrap() instead.");
        }
    }

    @Override
    public String getLocation() {
        if (addresses != null && !addresses.isEmpty()) {
            return addresses.get(0);
        }
        return "valkey://localhost:6379";
    }

    // Getters and setters

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isCacheMode() {
        return "cache".equalsIgnoreCase(mode);
    }

    public boolean isStandaloneMode() {
        return "standalone".equalsIgnoreCase(mode);
    }

    public String getDelegateBlobStoreId() {
        return delegateBlobStoreId;
    }

    public void setDelegateBlobStoreId(String delegateBlobStoreId) {
        this.delegateBlobStoreId = delegateBlobStoreId;
    }

    public List<String> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<String> addresses) {
        this.addresses = addresses;
    }

    /**
     * Get addresses as a comma-separated string for UI binding.
     */
    public String getAddressesAsString() {
        if (addresses == null || addresses.isEmpty()) {
            return "";
        }
        return String.join(",", addresses);
    }

    /**
     * Set addresses from a comma-separated string for UI binding.
     */
    public void setAddressesAsString(String addressesStr) {
        if (addressesStr == null || addressesStr.trim().isEmpty()) {
            this.addresses = List.of();
        } else {
            this.addresses = List.of(addressesStr.split("\\s*,\\s*"));
        }
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getDatabase() {
        return database;
    }

    public void setDatabase(int database) {
        this.database = database;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public Duration getTtl() {
        return ttlSeconds > 0 ? Duration.ofSeconds(ttlSeconds) : null;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public int getConnectionPoolSize() {
        return connectionPoolSize;
    }

    public void setConnectionPoolSize(int connectionPoolSize) {
        this.connectionPoolSize = connectionPoolSize;
    }

    public int getConnectionMinIdleSize() {
        return connectionMinIdleSize;
    }

    public void setConnectionMinIdleSize(int connectionMinIdleSize) {
        this.connectionMinIdleSize = connectionMinIdleSize;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public boolean isClusterMode() {
        return clusterMode;
    }

    public void setClusterMode(boolean clusterMode) {
        this.clusterMode = clusterMode;
    }

    public int getClusterScanInterval() {
        return clusterScanInterval;
    }

    public void setClusterScanInterval(int clusterScanInterval) {
        this.clusterScanInterval = clusterScanInterval;
    }

    public boolean isSentinelMode() {
        return sentinelMode;
    }

    public void setSentinelMode(boolean sentinelMode) {
        this.sentinelMode = sentinelMode;
    }

    public String getSentinelMasterName() {
        return sentinelMasterName;
    }

    public void setSentinelMasterName(String sentinelMasterName) {
        this.sentinelMasterName = sentinelMasterName;
    }

    @Override
    public String toString() {
        return "ValkeyBlobStoreInfo{"
                + "id='" + getName() + '\''
                + ", mode='" + mode + '\''
                + ", addresses=" + addresses
                + ", database=" + database
                + ", ttlSeconds=" + ttlSeconds
                + ", clusterMode=" + clusterMode
                + '}';
    }
}
