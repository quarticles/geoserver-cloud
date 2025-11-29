/*
 * (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.cloud.gwc.config.blobstore.valkey;

import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.geoserver.cloud.event.catalog.CatalogInfoModified;
import org.geoserver.cloud.event.catalog.CatalogInfoRemoved;
import org.geoserver.cloud.event.info.ConfigInfoType;
import org.geowebcache.storage.CompositeBlobStore;
import org.geowebcache.storage.StorageException;
import org.springframework.context.event.EventListener;

/**
 * Listens to GeoServer Cloud catalog events and invalidates GeoWebCache tiles for affected layers.
 *
 * <p>This listener subscribes to Spring Cloud Bus events that are broadcast across
 * all GeoServer Cloud nodes. When a layer or layer group is modified or removed,
 * it invalidates the corresponding tiles via the CompositeBlobStore.
 *
 * <p>Events handled:
 * <ul>
 *   <li>{@link CatalogInfoModified} - Layer/LayerGroup modifications trigger cache invalidation</li>
 *   <li>{@link CatalogInfoRemoved} - Layer/LayerGroup removals trigger cache invalidation</li>
 * </ul>
 *
 * <p>This listener operates in services that have active BlobStores (gwc-service, wms-service).
 * The CompositeBlobStore routes delete operations to the appropriate BlobStore based on
 * layer configuration, including any configured ValkeyCacheBlobStore instances.
 */
@RequiredArgsConstructor
public class ValkeyCacheInvalidationListener {

    private static final Logger LOGGER = Logger.getLogger(ValkeyCacheInvalidationListener.class.getName());

    /**
     * The CompositeBlobStore manages live BlobStore instances and routes tile operations
     * to the appropriate store based on layer configuration.
     */
    private final @NonNull CompositeBlobStore compositeBlobStore;

    /**
     * Handle catalog object modification events.
     * Invalidates cache for modified layers and layer groups.
     */
    @EventListener
    public void onCatalogModify(CatalogInfoModified event) {
        ConfigInfoType type = event.getObjectType();
        if (isLayerType(type)) {
            String layerName = event.getObjectName();
            deleteLayerTiles(layerName);

            // If renamed, also invalidate the old name
            String oldName = event.getOldName();
            if (oldName != null && !oldName.equals(layerName)) {
                deleteLayerTiles(oldName);
            }
        }
    }

    /**
     * Handle catalog object removal events.
     * Invalidates cache for removed layers and layer groups.
     */
    @EventListener
    public void onCatalogRemove(CatalogInfoRemoved event) {
        ConfigInfoType type = event.getObjectType();
        if (isLayerType(type)) {
            String layerName = event.getObjectName();
            deleteLayerTiles(layerName);
        }
    }

    private boolean isLayerType(ConfigInfoType type) {
        return type == ConfigInfoType.LAYER || type == ConfigInfoType.LAYERGROUP;
    }

    /**
     * Delete all cached tiles for the specified layer.
     *
     * <p>Uses CompositeBlobStore.delete() which routes to the appropriate BlobStore
     * based on the layer's configured blobStoreId. This works transparently with
     * any BlobStore implementation including ValkeyCacheBlobStore.
     *
     * @param layerName the layer name to delete tiles for
     */
    private void deleteLayerTiles(String layerName) {
        if (layerName == null || layerName.isEmpty()) {
            return;
        }

        try {
            LOGGER.fine(() -> "Deleting cached tiles for layer: " + layerName);
            boolean deleted = compositeBlobStore.delete(layerName);
            if (deleted) {
                LOGGER.info("Deleted cached tiles for layer: " + layerName);
            } else {
                LOGGER.fine(() -> "No tiles found to delete for layer: " + layerName);
            }
        } catch (StorageException e) {
            LOGGER.log(Level.WARNING, "Failed to delete cached tiles for layer: " + layerName, e);
        }
    }
}
