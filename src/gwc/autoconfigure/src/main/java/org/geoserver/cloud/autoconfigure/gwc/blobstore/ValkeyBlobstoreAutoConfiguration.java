/*
 * (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.cloud.autoconfigure.gwc.blobstore;

import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.geoserver.cloud.autoconfigure.gwc.ConditionalOnGeoServerWebUIEnabled;
import org.geoserver.cloud.autoconfigure.gwc.ConditionalOnValkeyBlobstoreEnabled;
import org.geoserver.cloud.autoconfigure.gwc.blobstore.ValkeyBlobstoreAutoConfiguration.GsWebUIAutoConfiguration;
import org.geoserver.cloud.gwc.config.blobstore.valkey.ValkeyBlobstoreConfiguration;
import org.geoserver.cloud.gwc.config.blobstore.valkey.ValkeyBlobstoreGsWebUIConfiguration;
import org.geoserver.gwc.web.blob.BlobStorePage;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
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
@AutoConfiguration
@SuppressWarnings("java:S1118")
@ConditionalOnValkeyBlobstoreEnabled
@Import({ValkeyBlobstoreConfiguration.class, GsWebUIAutoConfiguration.class})
@Slf4j(topic = "org.geoserver.cloud.autoconfigure.gwc.blobstore")
public class ValkeyBlobstoreAutoConfiguration {

    public @PostConstruct void log() {
        log.info("GeoWebCache Valkey BlobStore integration enabled");
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnGeoServerWebUIEnabled
    @ConditionalOnClass(BlobStorePage.class)
    @Import(ValkeyBlobstoreGsWebUIConfiguration.class)
    static class GsWebUIAutoConfiguration {}
}
