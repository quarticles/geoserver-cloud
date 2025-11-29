/*
 * (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.cloud.gwc.config.blobstore.valkey;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.geoserver.gwc.web.blob.BlobStoreType;

/**
 * BlobStoreType implementation for Valkey/Redis blob store.
 *
 * <p>Registers the Valkey blob store in GeoServer's Web UI for configuration.
 */
public class ValkeyBlobStoreType implements BlobStoreType<ValkeyBlobStoreInfo> {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String toString() {
        return "Valkey BlobStore";
    }

    @Override
    public ValkeyBlobStoreInfo newConfigObject() {
        ValkeyBlobStoreInfo config = new ValkeyBlobStoreInfo();
        config.setEnabled(true);
        config.setMode("cache");
        config.setAddresses(new ArrayList<>(Collections.singletonList("localhost:6379")));
        config.setDatabase(0);
        config.setTtlSeconds(86400);
        config.setKeyPrefix("gwc:");
        config.setConnectionPoolSize(64);
        return config;
    }

    @Override
    public Class<ValkeyBlobStoreInfo> getConfigClass() {
        return ValkeyBlobStoreInfo.class;
    }

    @Override
    public Panel createPanel(String id, IModel<ValkeyBlobStoreInfo> model) {
        return new ValkeyBlobStorePanel(id, model);
    }
}
