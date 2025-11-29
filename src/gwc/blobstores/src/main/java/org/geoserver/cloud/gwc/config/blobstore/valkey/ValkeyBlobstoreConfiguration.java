/*
 * (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.cloud.gwc.config.blobstore.valkey;

import org.geowebcache.storage.BlobStoreAggregator;
import org.geowebcache.storage.CompositeBlobStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for Valkey BlobStore support.
 *
 * <p>Registers the Valkey blob store configuration provider for XML/REST API configuration,
 * the cache invalidation listener for catalog events, and the REST controller for cache management.
 *
 * <p>Service deployment:
 * <ul>
 *   <li>Config provider: Available in all services for configuration handling</li>
 *   <li>Event listener: Operates in services with active BlobStores (gwc-service, wms-service)</li>
 *   <li>REST controller: Available in gwc-service where live BlobStore instances exist</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class ValkeyBlobstoreConfiguration {

    /**
     * Registers the Valkey BlobStore configuration provider.
     *
     * <p>This enables configuration of Valkey blob stores via geowebcache.xml and REST API.
     * Available in all services that handle blob store configuration.
     */
    @Bean(name = "ValkeyBlobStoreConfigProvider")
    ValkeyBlobStoreConfigProvider valkeyBlobStoreConfigProvider() {
        return new ValkeyBlobStoreConfigProvider();
    }

    /**
     * Registers the cache invalidation listener for catalog events.
     *
     * <p>This listener subscribes to catalog modification/removal events broadcast
     * via Spring Cloud Bus and deletes cached tiles for affected layers.
     *
     * <p>Only created when CompositeBlobStore is available (services with active BlobStores).
     * Services without blob stores will not have this bean and will not process events.
     *
     * @param compositeBlobStore the blob store that routes tile operations to appropriate stores
     */
    @Bean
    @ConditionalOnBean(CompositeBlobStore.class)
    ValkeyCacheInvalidationListener valkeyCacheInvalidationListener(CompositeBlobStore compositeBlobStore) {
        return new ValkeyCacheInvalidationListener(compositeBlobStore);
    }

    /**
     * Registers the REST controller for Valkey cache management.
     *
     * <p>This controller provides endpoints for cache configuration info and
     * cache invalidation by layer or gridset.
     *
     * <p>Only created in web applications that have CompositeBlobStore (gwc-service).
     *
     * @param compositeBlobStore the blob store that routes tile operations to appropriate stores
     * @param blobStoreAggregator provides access to blob store configurations
     */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnBean(CompositeBlobStore.class)
    ValkeyCacheRestController valkeyCacheRestController(
            CompositeBlobStore compositeBlobStore, BlobStoreAggregator blobStoreAggregator) {
        return new ValkeyCacheRestController(compositeBlobStore, blobStoreAggregator);
    }
}
