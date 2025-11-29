/*
 * (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.cloud.gwc.config.blobstore.valkey;

import org.geoserver.platform.ModuleStatusImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for Valkey BlobStore GeoServer Web UI integration.
 *
 * <p>Registers the ValkeyBlobStoreType bean which makes the Valkey BlobStore
 * available in the GeoServer Web UI's BlobStore configuration page.
 */
@Configuration(proxyBeanMethods = false)
public class ValkeyBlobstoreGsWebUIConfiguration {

    @Bean
    ValkeyBlobStoreType valkeyBlobStoreType() {
        return new ValkeyBlobStoreType();
    }

    @Bean
    ModuleStatusImpl gwcValkeyExtension() {
        ModuleStatusImpl module = new ModuleStatusImpl();
        module.setModule("gs-gwc-valkey");
        module.setName("GeoWebCache Valkey Extension");
        module.setComponent("GeoWebCache Valkey/Redis BlobStore plugin");
        module.setEnabled(true);
        module.setAvailable(true);
        return module;
    }
}
