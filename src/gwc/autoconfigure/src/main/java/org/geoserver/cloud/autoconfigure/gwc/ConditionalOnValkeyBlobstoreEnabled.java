/*
 * (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.cloud.autoconfigure.gwc;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.geoserver.cloud.gwc.config.blobstore.valkey.ValkeyBlobStoreConfigProvider;
import org.geoserver.cloud.gwc.config.blobstore.valkey.ValkeyBlobstoreConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Conditionals for Valkey BlobStore:
 *
 * <ul>
 *   <li>{@literal gwc.enabled=true}: Core gwc integration is enabled
 *   <li>{@literal gwc-cloud-blobstore-valkey.jar}: is in the classpath
 *   <li>{@literal gwc.blobstores.valkey=true}: Valkey blobstore integration is enabled
 * </ul>
 *
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
@ConditionalOnGeoWebCacheEnabled
@ConditionalOnClass({ValkeyBlobstoreConfiguration.class, ValkeyBlobStoreConfigProvider.class})
@ConditionalOnProperty(name = "gwc.blobstores.valkey", havingValue = "true", matchIfMissing = false)
public @interface ConditionalOnValkeyBlobstoreEnabled {}
