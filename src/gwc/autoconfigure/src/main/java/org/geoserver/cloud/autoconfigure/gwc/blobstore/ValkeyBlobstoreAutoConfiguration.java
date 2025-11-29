/*
 * (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.cloud.autoconfigure.gwc.blobstore;

import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import org.geoserver.cloud.autoconfigure.gwc.ConditionalOnValkeyBlobstoreEnabled;
import org.geoserver.cloud.gwc.config.blobstore.valkey.ValkeyBlobstoreConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for Valkey BlobStore support.
 *
 * <p>Enabled when:
 * <ul>
 *   <li>{@code gwc.enabled=true}</li>
 *   <li>{@code gwc.blobstores.valkey=true}</li>
 *   <li>Valkey blobstore classes are on the classpath</li>
 * </ul>
 *
 * <p>Configuration example:
 * <pre>{@code
 * gwc:
 *   enabled: true
 *   blobstores:
 *     valkey: true
 * }</pre>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnValkeyBlobstoreEnabled
@Import(ValkeyBlobstoreConfiguration.class)
public class ValkeyBlobstoreAutoConfiguration {

    private static final Logger LOGGER = Logger.getLogger(ValkeyBlobstoreAutoConfiguration.class.getName());

    @PostConstruct
    void log() {
        LOGGER.info("GeoWebCache Valkey BlobStore auto-configuration enabled");
    }
}
