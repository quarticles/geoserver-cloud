/*
 * (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.cloud.gwc.config.blobstore.valkey;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for Valkey BlobStore support.
 *
 * <p>Registers the Valkey blob store configuration provider for XML/REST API configuration,
 * the cache invalidation listener for catalog events, and the REST controller for cache management.
 */
@Configuration(proxyBeanMethods = false)
public class ValkeyBlobstoreConfiguration {

    /**
     * Registers the Valkey BlobStore configuration provider.
     *
     * <p>This enables configuration of Valkey blob stores via geowebcache.xml and REST API.
     */
    @Bean(name = "ValkeyBlobStoreConfigProvider")
    ValkeyBlobStoreConfigProvider valkeyBlobStoreConfigProvider() {
        return new ValkeyBlobStoreConfigProvider();
    }

    /**
     * Registers the cache invalidation listener for catalog events.
     *
     * <p>This listener subscribes to catalog modification/removal events broadcast
     * via Spring Cloud Bus and invalidates the Valkey cache for affected layers.
     */
    @Bean
    ValkeyCacheInvalidationListener valkeyCacheInvalidationListener() {
        return new ValkeyCacheInvalidationListener();
    }

    /**
     * Registers the REST controller for Valkey cache management.
     *
     * <p>This controller provides endpoints for cache statistics, invalidation by layer,
     * gridset, pattern, or full cache flush.
     */
    @Bean
    @ConditionalOnWebApplication
    ValkeyCacheRestController valkeyCacheRestController() {
        return new ValkeyCacheRestController();
    }
}
