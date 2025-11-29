/*
 * (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.cloud.gwc.config.blobstore.valkey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ValkeyBlobStoreInfo}.
 */
class ValkeyBlobStoreInfoTest {

    @Test
    void testDefaultValues() {
        ValkeyBlobStoreInfo info = new ValkeyBlobStoreInfo();

        assertThat(info.getMode()).isEqualTo("cache");
        assertThat(info.getDatabase()).isZero();
        assertThat(info.getTtlSeconds()).isEqualTo(86400L);
        assertThat(info.getKeyPrefix()).isEqualTo("gwc:");
        assertThat(info.getConnectionPoolSize()).isEqualTo(64);
        assertThat(info.getConnectionMinIdleSize()).isEqualTo(8);
        assertThat(info.getConnectionTimeout()).isEqualTo(3000);
        assertThat(info.isClusterMode()).isFalse();
        assertThat(info.getClusterScanInterval()).isEqualTo(5000);
        assertThat(info.isSentinelMode()).isFalse();
    }

    @Test
    void testStandaloneMode() {
        ValkeyBlobStoreInfo info = new ValkeyBlobStoreInfo();
        info.setMode("standalone");
        info.setAddresses(List.of("localhost:6379"));

        assertThat(info.getMode()).isEqualTo("standalone");
        assertThat(info.getAddresses()).containsExactly("localhost:6379");
    }

    @Test
    void testCacheModeWithDelegate() {
        ValkeyBlobStoreInfo info = new ValkeyBlobStoreInfo();
        info.setMode("cache");
        info.setDelegateBlobStoreId("s3-backend");
        info.setAddresses(List.of("valkey.internal:6379"));

        assertThat(info.getMode()).isEqualTo("cache");
        assertThat(info.getDelegateBlobStoreId()).isEqualTo("s3-backend");
    }

    @Test
    void testClusterConfiguration() {
        ValkeyBlobStoreInfo info = new ValkeyBlobStoreInfo();
        info.setClusterMode(true);
        info.setAddresses(List.of("node1:6379", "node2:6379", "node3:6379"));
        info.setClusterScanInterval(5000);

        assertThat(info.isClusterMode()).isTrue();
        assertThat(info.getAddresses()).hasSize(3);
        assertThat(info.getClusterScanInterval()).isEqualTo(5000);
    }

    @Test
    void testSentinelConfiguration() {
        ValkeyBlobStoreInfo info = new ValkeyBlobStoreInfo();
        info.setSentinelMode(true);
        info.setSentinelMasterName("mymaster");
        info.setAddresses(List.of("sentinel1:26379", "sentinel2:26379", "sentinel3:26379"));

        assertThat(info.isSentinelMode()).isTrue();
        assertThat(info.getSentinelMasterName()).isEqualTo("mymaster");
        assertThat(info.getAddresses()).hasSize(3);
    }

    @Test
    void testConnectionSettings() {
        ValkeyBlobStoreInfo info = new ValkeyBlobStoreInfo();
        info.setConnectionPoolSize(128);
        info.setConnectionMinIdleSize(16);
        info.setConnectionTimeout(5000);
        info.setPassword("secret");

        assertThat(info.getConnectionPoolSize()).isEqualTo(128);
        assertThat(info.getConnectionMinIdleSize()).isEqualTo(16);
        assertThat(info.getConnectionTimeout()).isEqualTo(5000);
        assertThat(info.getPassword()).isEqualTo("secret");
    }

    @Test
    void testTtlSettings() {
        ValkeyBlobStoreInfo info = new ValkeyBlobStoreInfo();
        info.setTtlSeconds(3600L); // 1 hour

        assertThat(info.getTtlSeconds()).isEqualTo(3600L);
    }

    @Test
    void testNameAndEnabled() {
        ValkeyBlobStoreInfo info = new ValkeyBlobStoreInfo();
        info.setName("valkey-cache-1");
        info.setEnabled(true);
        info.setDefault(true);

        assertThat(info.getName()).isEqualTo("valkey-cache-1");
        assertThat(info.isEnabled()).isTrue();
        assertThat(info.isDefault()).isTrue();
    }
}
