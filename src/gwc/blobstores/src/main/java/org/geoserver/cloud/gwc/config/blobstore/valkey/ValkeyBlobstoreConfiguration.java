/*
 * (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.cloud.gwc.config.blobstore.valkey;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for Valkey BlobStore support.
 *
 * <p>Registers the Valkey blob store configuration provider for XML/REST API configuration.
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
}
