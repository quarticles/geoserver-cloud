/*
 * (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.cloud.gwc.config.blobstore.valkey;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.RequiredArgsConstructor;
import org.geoserver.cloud.event.catalog.CatalogInfoModified;
import org.geoserver.cloud.event.catalog.CatalogInfoRemoved;
import org.geoserver.cloud.event.info.ConfigInfoType;
import org.geoserver.platform.GeoServerExtensions;
import org.springframework.context.event.EventListener;

/**
 * Listens to GeoServer Cloud catalog events and invalidates Valkey cache for affected layers.
 *
 * <p>This listener subscribes to Spring Cloud Bus events that are broadcast across
 * all GeoServer Cloud nodes. When a layer or layer group is modified or removed,
 * it invalidates the corresponding tiles in all Valkey cache blob stores.
 *
 * <p>Events handled:
 * <ul>
 *   <li>{@link CatalogInfoModified} - Layer/LayerGroup modifications trigger cache invalidation</li>
 *   <li>{@link CatalogInfoRemoved} - Layer/LayerGroup removals trigger cache invalidation</li>
 * </ul>
 */
@RequiredArgsConstructor
public class ValkeyCacheInvalidationListener {

    private static final Logger LOGGER = Logger.getLogger(ValkeyCacheInvalidationListener.class.getName());

    /**
     * Handle catalog object modification events.
     * Invalidates cache for modified layers and layer groups.
     */
    @EventListener
    public void onCatalogModify(CatalogInfoModified event) {
        ConfigInfoType type = event.getObjectType();
        if (isLayerType(type)) {
            String layerName = event.getObjectName();
            invalidateLayer(layerName);

            // If renamed, also invalidate the old name
            String oldName = event.getOldName();
            if (oldName != null && !oldName.equals(layerName)) {
                invalidateLayer(oldName);
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
            invalidateLayer(layerName);
        }
    }

    private boolean isLayerType(ConfigInfoType type) {
        return type == ConfigInfoType.LAYER || type == ConfigInfoType.LAYERGROUP;
    }

    private void invalidateLayer(String layerName) {
        if (layerName == null || layerName.isEmpty()) {
            return;
        }

        List<ValkeyCacheBlobStore> stores = findValkeyCacheBlobStores();
        if (stores.isEmpty()) {
            LOGGER.finest(() -> "No ValkeyCacheBlobStore instances found, skipping invalidation for: " + layerName);
            return;
        }

        LOGGER.fine(() -> "Invalidating Valkey cache for layer: " + layerName);
        for (ValkeyCacheBlobStore store : stores) {
            try {
                long count = store.invalidateLayer(layerName);
                if (count > 0) {
                    LOGGER.info("Invalidated " + count + " tiles for layer '" + layerName + "' in Valkey cache");
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to invalidate Valkey cache for layer " + layerName, e);
            }
        }
    }

    private List<ValkeyCacheBlobStore> findValkeyCacheBlobStores() {
        return GeoServerExtensions.extensions(ValkeyCacheBlobStore.class);
    }
}
