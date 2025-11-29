/*
 * (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.cloud.gwc.config.blobstore.valkey;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.geowebcache.config.BlobStoreInfo;
import org.geowebcache.storage.BlobStoreAggregator;
import org.geowebcache.storage.CompositeBlobStore;
import org.geowebcache.storage.StorageException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Valkey cache management operations.
 *
 * <p>Provides endpoints for:
 * <ul>
 *   <li>Cache statistics (configuration-based, limited stats available)</li>
 *   <li>Cache invalidation by layer or gridset</li>
 * </ul>
 *
 * <p>All endpoints require appropriate GeoServer administrator permissions.
 *
 * <p>This controller should be deployed in the gwc-service, as it requires access to
 * the CompositeBlobStore for tile operations.
 *
 * <p>Example usage:
 * <pre>
 * # Get cache configuration info
 * GET /geoserver/gwc/rest/valkey/cache/stats
 *
 * # Invalidate all tiles for a layer
 * DELETE /geoserver/gwc/rest/valkey/cache/layers/topp:states
 *
 * # Invalidate all tiles for a layer and gridset
 * DELETE /geoserver/gwc/rest/valkey/cache/layers/topp:states/gridsets/EPSG:4326
 * </pre>
 */
@RestController
@RequestMapping(path = "${gwc.context.suffix:}/rest/valkey")
@RequiredArgsConstructor
public class ValkeyCacheRestController {

    private static final Logger LOGGER = Logger.getLogger(ValkeyCacheRestController.class.getName());

    /**
     * The CompositeBlobStore manages live BlobStore instances and routes tile operations
     * to the appropriate store based on layer configuration.
     */
    private final @NonNull CompositeBlobStore compositeBlobStore;

    /**
     * The BlobStoreAggregator provides access to BlobStoreInfo configurations.
     */
    private final @NonNull BlobStoreAggregator blobStoreAggregator;

    /**
     * Get cache configuration info for all Valkey blob stores.
     *
     * @return configuration info for configured Valkey blob stores
     */
    @GetMapping(path = "/cache/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        int valkeyStoreCount = 0;

        for (BlobStoreInfo info : blobStoreAggregator.getBlobStores()) {
            if (info instanceof ValkeyBlobStoreInfo valkeyInfo) {
                Map<String, Object> storeStats = new HashMap<>();
                storeStats.put("id", valkeyInfo.getName());
                storeStats.put("enabled", valkeyInfo.isEnabled());
                storeStats.put("default", valkeyInfo.isDefault());
                storeStats.put("addresses", valkeyInfo.getAddressesAsString());
                storeStats.put("mode", valkeyInfo.getMode());
                storeStats.put("keyPrefix", valkeyInfo.getKeyPrefix());
                storeStats.put("delegateBlobStoreId", valkeyInfo.getDelegateBlobStoreId());
                storeStats.put("ttlSeconds", valkeyInfo.getTtlSeconds());
                stats.put("valkey-" + valkeyInfo.getName(), storeStats);
                valkeyStoreCount++;
            }
        }

        if (valkeyStoreCount == 0) {
            return ResponseEntity.ok(Map.of("message", "No Valkey cache blob stores configured"));
        }

        stats.put("totalValkeyStores", valkeyStoreCount);
        return ResponseEntity.ok(stats);
    }

    /**
     * Invalidate all cached tiles for a specific layer.
     *
     * <p>Uses CompositeBlobStore.delete() which routes to the appropriate BlobStore
     * based on the layer's configuration.
     *
     * @param layerName the layer name (e.g., "topp:states")
     * @return invalidation result
     */
    @DeleteMapping(path = "/cache/layers/{layerName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> invalidateLayer(@PathVariable String layerName) {
        try {
            boolean deleted = compositeBlobStore.delete(layerName);
            return ResponseEntity.ok(Map.of(
                    "layerName", layerName,
                    "deleted", deleted,
                    "message", deleted ? "Cache invalidated for layer" : "No tiles found for layer"));
        } catch (StorageException e) {
            LOGGER.log(Level.WARNING, "Failed to invalidate cache for layer: " + layerName, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to invalidate cache: " + e.getMessage()));
        }
    }

    /**
     * Invalidate all cached tiles for a specific layer and gridset.
     *
     * <p>Uses CompositeBlobStore.deleteByGridsetId() which routes to the appropriate BlobStore
     * based on the layer's configuration.
     *
     * @param layerName the layer name (e.g., "topp:states")
     * @param gridSetId the gridset ID (e.g., "EPSG:4326")
     * @return invalidation result
     */
    @DeleteMapping(path = "/cache/layers/{layerName}/gridsets/{gridSetId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> invalidateGridset(
            @PathVariable String layerName, @PathVariable String gridSetId) {
        try {
            boolean deleted = compositeBlobStore.deleteByGridsetId(layerName, gridSetId);
            return ResponseEntity.ok(Map.of(
                    "layerName", layerName,
                    "gridSetId", gridSetId,
                    "deleted", deleted,
                    "message", deleted ? "Cache invalidated for layer/gridset" : "No tiles found for layer/gridset"));
        } catch (StorageException e) {
            LOGGER.log(
                    Level.WARNING, "Failed to invalidate cache for layer/gridset: " + layerName + "/" + gridSetId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to invalidate cache: " + e.getMessage()));
        }
    }
}
