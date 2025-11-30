/*
 * (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.cloud.autoconfigure.gwc.integration;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.geoserver.gwc.GWC;
import org.geoserver.wms.GetMapRequest;
import org.geoserver.wms.MapLayerInfo;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.StorageBroker;
import org.locationtech.jts.geom.Envelope;
import org.springframework.beans.factory.DisposableBean;

/**
 * Service to trigger background tile generation based on WMS requests.
 *
 * <p>When a WMS request is served dynamically (because it doesn't match the grid exactly),
 * this warmer calculates the overlapping tiles and triggers their generation in the background.
 * This ensures that browsing the map via WMS effectively "warms up" the GWC cache for future requests.
 */
@Slf4j
public class WMSCacheWarmer implements DisposableBean {

    private final GWC gwc;
    private final StorageBroker storageBroker;
    private final ExecutorService executor;

    /** Maximum number of tiles to seed per WMS request (safety limit) */
    private static final int MAX_TILES_PER_REQUEST = 100;

    public WMSCacheWarmer(GWC gwc, StorageBroker storageBroker) {
        this.gwc = gwc;
        this.storageBroker = storageBroker;
        // Use virtual threads if available (Java 21+), otherwise cached thread pool
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        log.info("WMS Cache Warmer initialized");
    }

    /**
     * Asynchronously seeds the tiles overlapping the provided WMS request.
     *
     * @param request the WMS GetMap request
     */
    public void warmCacheAsync(GetMapRequest request) {
        executor.submit(() -> {
            try {
                warmCache(request);
            } catch (Exception e) {
                log.debug("Failed to warm cache from WMS request: {}", e.getMessage());
            }
        });
    }

    private void warmCache(GetMapRequest request) {
        List<MapLayerInfo> layers = request.getLayers();
        if (layers == null || layers.isEmpty()) {
            return;
        }

        String format = request.getFormat();
        MimeType mimeType = null;
        try {
            mimeType = MimeType.createFromFormat(format);
        } catch (Exception e) {
            log.trace("Could not parse mime type from format: {}", format);
        }

        for (MapLayerInfo layerInfo : layers) {
            try {
                warmLayerTiles(layerInfo, request, mimeType);
            } catch (Exception e) {
                log.trace("Error warming tiles for layer {}: {}", layerInfo.getName(), e.getMessage());
            }
        }
    }

    private void warmLayerTiles(MapLayerInfo layerInfo, GetMapRequest request, MimeType mimeType) {
        // Resolve the TileLayer
        String layerName = layerInfo.getName();
        TileLayer tileLayer = gwc.getTileLayerByName(layerName);

        if (tileLayer == null && layerInfo.getLayerInfo() != null) {
            // Try with workspace prefix
            tileLayer = gwc.getTileLayerByName(layerInfo.getLayerInfo().prefixedName());
        }

        if (tileLayer == null) {
            log.trace("No tile layer found for: {}", layerName);
            return;
        }

        if (!tileLayer.isEnabled()) {
            log.trace("Tile layer {} is disabled", layerName);
            return;
        }

        // Find the GridSubset matching the request CRS
        String srs = request.getSRS();
        GridSubset gridSubset = findGridSubset(tileLayer, srs);
        if (gridSubset == null) {
            log.trace("No grid subset found for SRS {} on layer {}", srs, layerName);
            return;
        }

        // Calculate BBOX and Resolution
        Envelope env = request.getBbox();
        BoundingBox requestBbox = new BoundingBox(env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY());

        double resX = requestBbox.getWidth() / request.getWidth();
        double resY = requestBbox.getHeight() / request.getHeight();
        double requestRes = Math.max(resX, resY);

        // Find closest Zoom Level
        int zoomLevel = findClosestZoomLevel(gridSubset.getGridSet(), requestRes);

        // Calculate overlapping tiles
        long[] coverage = gridSubset.getCoverageIntersection(zoomLevel, requestBbox);
        if (coverage == null) {
            log.trace("No coverage intersection for layer {} at zoom {}", layerName, zoomLevel);
            return;
        }

        long minX = coverage[0];
        long minY = coverage[1];
        long maxX = coverage[2];
        long maxY = coverage[3];

        // Safety limit: prevent massive seeding from a global view request
        long tileCount = (maxX - minX + 1) * (maxY - minY + 1);
        if (tileCount > MAX_TILES_PER_REQUEST) {
            log.trace(
                    "Skipping cache warming for {} - too many tiles: {} (max: {})",
                    layerName,
                    tileCount,
                    MAX_TILES_PER_REQUEST);
            return;
        }

        log.debug(
                "Warming cache for layer {} at zoom {}: {} tiles ({},{} to {},{})",
                layerName,
                zoomLevel,
                tileCount,
                minX,
                minY,
                maxX,
                maxY);

        // Determine mime type to use
        MimeType tileFormat = mimeType;
        if (tileFormat == null || !tileLayer.getMimeTypes().contains(tileFormat)) {
            // Use first available format
            tileFormat = tileLayer.getMimeTypes().isEmpty()
                    ? null
                    : tileLayer.getMimeTypes().get(0);
        }

        if (tileFormat == null) {
            log.trace("No suitable mime type for layer {}", layerName);
            return;
        }

        // Trigger tile generation for each tile
        int warmed = 0;
        for (long x = minX; x <= maxX; x++) {
            for (long y = minY; y <= maxY; y++) {
                try {
                    if (warmTile(tileLayer, gridSubset.getName(), x, y, zoomLevel, tileFormat)) {
                        warmed++;
                    }
                } catch (Exception e) {
                    log.trace(
                            "Failed to warm tile [{},{},{}] for layer {}: {}",
                            x,
                            y,
                            zoomLevel,
                            layerName,
                            e.getMessage());
                }
            }
        }

        if (warmed > 0) {
            log.debug("Warmed {} tiles for layer {} at zoom {}", warmed, layerName, zoomLevel);
        }
    }

    private GridSubset findGridSubset(TileLayer tileLayer, String srs) {
        for (String gridSetId : tileLayer.getGridSubsets()) {
            GridSubset gs = tileLayer.getGridSubset(gridSetId);
            if (gs != null && gs.getSRS().toString().equalsIgnoreCase(srs)) {
                return gs;
            }
        }
        return null;
    }

    /**
     * Find the zoom level whose resolution is closest to the requested resolution.
     *
     * @param gridSet the grid set
     * @param requestResolution the requested resolution (map units per pixel)
     * @return the closest zoom level index
     */
    private int findClosestZoomLevel(GridSet gridSet, double requestResolution) {
        int closestLevel = 0;
        double minDiff = Double.MAX_VALUE;

        int numLevels = gridSet.getNumLevels();
        for (int i = 0; i < numLevels; i++) {
            // Grid resolution at level i (each level is typically 2x the previous)
            double res = gridSet.getGrid(i).getResolution();
            double diff = Math.abs(res - requestResolution);

            if (diff < minDiff) {
                minDiff = diff;
                closestLevel = i;
            }
        }
        return closestLevel;
    }

    private boolean warmTile(TileLayer tileLayer, String gridSetId, long x, long y, int z, MimeType mimeType)
            throws GeoWebCacheException, IOException {

        long[] gridLoc = {x, y, z};

        ConveyorTile tile =
                new ConveyorTile(storageBroker, tileLayer.getName(), gridSetId, gridLoc, mimeType, null, null, null);

        // Set the tile layer - required for getTile to work
        tile.setTileLayer(tileLayer);

        // Calling getTile triggers fetch/save if the tile is missing
        tileLayer.getTile(tile);

        return true;
    }

    @Override
    public void destroy() {
        log.info("Shutting down WMS Cache Warmer");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
