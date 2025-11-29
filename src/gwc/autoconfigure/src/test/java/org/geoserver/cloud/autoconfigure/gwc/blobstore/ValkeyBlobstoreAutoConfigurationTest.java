/*
 * (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.cloud.autoconfigure.gwc.blobstore;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import org.geoserver.cloud.autoconfigure.gwc.GeoWebCacheContextRunner;
import org.geoserver.cloud.gwc.config.blobstore.valkey.ValkeyBlobStoreConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

/**
 * {@link ValkeyBlobstoreAutoConfiguration} tests
 *
 * @since 1.0
 */
class ValkeyBlobstoreAutoConfigurationTest {

    WebApplicationContextRunner runner;

    @TempDir
    File tmpDir;

    @BeforeEach
    void setUp() {
        runner = GeoWebCacheContextRunner.newMinimalGeoWebCacheContextRunner(tmpDir)
                .withConfiguration(AutoConfigurations.of(ValkeyBlobstoreAutoConfiguration.class));
    }

    @Test
    void disabledByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(ValkeyBlobStoreConfigProvider.class);
        });
    }

    @Test
    void enabledWhenPropertySet() {
        runner.withPropertyValues("gwc.blobstores.valkey=true").run(context -> {
            assertThat(context).hasSingleBean(ValkeyBlobStoreConfigProvider.class);
        });
    }

    @Test
    void disabledWhenClassNotOnClasspath() {
        runner.withClassLoader(new FilteredClassLoader(ValkeyBlobStoreConfigProvider.class))
                .withPropertyValues("gwc.blobstores.valkey=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ValkeyBlobStoreConfigProvider.class);
                });
    }

    @Test
    void disabledWhenGwcDisabled() {
        runner.withPropertyValues("gwc.enabled=false", "gwc.blobstores.valkey=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ValkeyBlobStoreConfigProvider.class);
                });
    }
}
