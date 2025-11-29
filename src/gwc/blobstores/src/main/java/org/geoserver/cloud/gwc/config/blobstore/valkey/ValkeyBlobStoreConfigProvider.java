/*
 * (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.cloud.gwc.config.blobstore.valkey;

import com.thoughtworks.xstream.XStream;
import org.geowebcache.config.Info;
import org.geowebcache.config.XMLConfigurationProvider;

/**
 * XStream configuration provider for {@link ValkeyBlobStoreInfo}.
 *
 * <p>Enables XML serialization/deserialization of Valkey blob store configurations
 * in geowebcache.xml and via the REST API.
 */
public class ValkeyBlobStoreConfigProvider implements XMLConfigurationProvider {

    @Override
    public boolean canSave(Info info) {
        return info instanceof ValkeyBlobStoreInfo;
    }

    @Override
    public XStream getConfiguredXStream(XStream xs) {
        xs.alias("ValkeyBlobStore", ValkeyBlobStoreInfo.class);

        // Allow the class for security
        xs.allowTypes(new Class[] {ValkeyBlobStoreInfo.class});

        // Configure field aliases for cleaner XML
        xs.aliasField("addresses", ValkeyBlobStoreInfo.class, "addresses");
        xs.aliasField("password", ValkeyBlobStoreInfo.class, "password");
        xs.aliasField("database", ValkeyBlobStoreInfo.class, "database");
        xs.aliasField("ttlSeconds", ValkeyBlobStoreInfo.class, "ttlSeconds");
        xs.aliasField("keyPrefix", ValkeyBlobStoreInfo.class, "keyPrefix");
        xs.aliasField("mode", ValkeyBlobStoreInfo.class, "mode");
        xs.aliasField("delegateBlobStoreId", ValkeyBlobStoreInfo.class, "delegateBlobStoreId");
        xs.aliasField("connectionPoolSize", ValkeyBlobStoreInfo.class, "connectionPoolSize");
        xs.aliasField("connectionMinIdleSize", ValkeyBlobStoreInfo.class, "connectionMinIdleSize");
        xs.aliasField("connectionTimeout", ValkeyBlobStoreInfo.class, "connectionTimeout");
        xs.aliasField("clusterMode", ValkeyBlobStoreInfo.class, "clusterMode");
        xs.aliasField("clusterScanInterval", ValkeyBlobStoreInfo.class, "clusterScanInterval");
        xs.aliasField("sentinelMode", ValkeyBlobStoreInfo.class, "sentinelMode");
        xs.aliasField("sentinelMasterName", ValkeyBlobStoreInfo.class, "sentinelMasterName");

        return xs;
    }
}
