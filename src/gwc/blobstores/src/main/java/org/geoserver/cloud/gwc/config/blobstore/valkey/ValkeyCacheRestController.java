/*
 * (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.cloud.gwc.config.blobstore.valkey;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.geoserver.platform.GeoServerExtensions;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Valkey cache management operations.
 *
 * <p>Provides endpoints for:
 * <ul>
 *   <li>Cache statistics and memory usage</li>
 *   <li>Cache invalidation by layer, gridset, or pattern</li>
 *   <li>Full cache invalidation</li>
 * </ul>
 *
 * <p>All endpoints require appropriate GeoServer administrator permissions.
 *
 * <p>Example usage:
 * <pre>
 * # Get cache statistics
 * GET /geoserver/gwc/rest/valkey/cache/stats
 *
 * # Invalidate all tiles for a layer
 * DELETE /geoserver/gwc/rest/valkey/cache/layers/topp:states
 *
 * # Invalidate all tiles for a layer and gridset
 * DELETE /geoserver/gwc/rest/valkey/cache/layers/topp:states/gridsets/EPSG:4326
 *
 * # Invalidate by pattern
 * DELETE /geoserver/gwc/rest/valkey/cache?pattern=gwc:tile:topp:*
 *
 * # Invalidate entire cache
 * DELETE /geoserver/gwc/rest/valkey/cache
 * </pre>
 */
@RestController
@RequestMapping(path = "${gwc.context.suffix:}/rest/valkey")
@RequiredArgsConstructor
public class ValkeyCacheRestController {

    /**
     * Get cache statistics from all Valkey cache blob stores.
     *
     * @return cache statistics including hits, misses, size, and memory usage
     */
    @GetMapping(path = "/cache/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        List<ValkeyCacheBlobStore> stores = findValkeyCacheBlobStores();
        if (stores.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "No Valkey cache blob stores configured"));
        }

        Map<String, Object> stats = new HashMap<>();
        for (ValkeyCacheBlobStore store : stores) {
            Map<String, Object> storeStats = new HashMap<>();
            storeStats.put("cacheHits", store.getCacheHits());
            storeStats.put("cacheMisses", store.getCacheMisses());
            storeStats.put("hitRatio", store.getCacheHitRatio());
            storeStats.put("cacheSize", store.getCacheSize());
            storeStats.put("memoryUsage", store.getMemoryInfo());
            storeStats.put("delegateType", store.getDelegate().getClass().getSimpleName());
            stats.put("valkey-cache", storeStats);
        }
        return ResponseEntity.ok(stats);
    }

    /**
     * Invalidate all cached tiles for a specific layer.
     *
     * @param layerName the layer name (e.g., "topp:states")
     * @return invalidation result with count of deleted tiles
     */
    @DeleteMapping(path = "/cache/layers/{layerName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> invalidateLayer(@PathVariable String layerName) {
        List<ValkeyCacheBlobStore> stores = findValkeyCacheBlobStores();
        if (stores.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "No Valkey cache blob stores configured"));
        }

        long totalInvalidated = 0;
        for (ValkeyCacheBlobStore store : stores) {
            totalInvalidated += store.invalidateLayer(layerName);
        }

        return ResponseEntity.ok(
                Map.of("layerName", layerName, "invalidatedTiles", totalInvalidated, "message", "Cache invalidated"));
    }

    /**
     * Invalidate all cached tiles for a specific layer and gridset.
     *
     * @param layerName the layer name (e.g., "topp:states")
     * @param gridSetId the gridset ID (e.g., "EPSG:4326")
     * @return invalidation result with count of deleted tiles
     */
    @DeleteMapping(path = "/cache/layers/{layerName}/gridsets/{gridSetId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> invalidateGridset(
            @PathVariable String layerName, @PathVariable String gridSetId) {
        List<ValkeyCacheBlobStore> stores = findValkeyCacheBlobStores();
        if (stores.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "No Valkey cache blob stores configured"));
        }

        long totalInvalidated = 0;
        for (ValkeyCacheBlobStore store : stores) {
            totalInvalidated += store.invalidateGridset(layerName, gridSetId);
        }

        return ResponseEntity.ok(Map.of(
                "layerName", layerName,
                "gridSetId", gridSetId,
                "invalidatedTiles", totalInvalidated,
                "message", "Cache invalidated"));
    }

    /**
     * Invalidate cache by pattern or invalidate all.
     *
     * @param pattern optional Redis key pattern (e.g., "gwc:tile:myLayer:*")
     * @return invalidation result with count of deleted tiles
     */
    @DeleteMapping(path = "/cache", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> invalidateCache(@RequestParam(required = false) String pattern) {
        List<ValkeyCacheBlobStore> stores = findValkeyCacheBlobStores();
        if (stores.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "No Valkey cache blob stores configured"));
        }

        long totalInvalidated = 0;
        for (ValkeyCacheBlobStore store : stores) {
            if (pattern != null && !pattern.isEmpty()) {
                totalInvalidated += store.invalidateByPattern(pattern);
            } else {
                totalInvalidated += store.invalidateAll();
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("invalidatedTiles", totalInvalidated);
        result.put("message", "Cache invalidated");
        if (pattern != null) {
            result.put("pattern", pattern);
        }
        return ResponseEntity.ok(result);
    }

    private List<ValkeyCacheBlobStore> findValkeyCacheBlobStores() {
        return GeoServerExtensions.extensions(ValkeyCacheBlobStore.class);
    }
}
