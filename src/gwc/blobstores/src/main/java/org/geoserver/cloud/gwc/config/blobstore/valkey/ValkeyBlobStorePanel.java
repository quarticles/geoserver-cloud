/*
 * (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.cloud.gwc.config.blobstore.valkey;

import java.io.Serial;
import java.util.Arrays;
import java.util.List;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.geoserver.web.data.store.PasswordTextFieldWriteOnlyModel;

/**
 * Wicket Panel for configuring Valkey BlobStore settings.
 */
public class ValkeyBlobStorePanel extends Panel {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final List<String> MODE_CHOICES = Arrays.asList("standalone", "cache");

    public ValkeyBlobStorePanel(String id, final IModel<ValkeyBlobStoreInfo> configModel) {
        super(id, configModel);

        // Mode selection (standalone or cache)
        add(new DropDownChoice<>("mode", new PropertyModel<>(configModel, "mode"), MODE_CHOICES)
                .setRequired(true)
                .add(titleModifier("mode.title")));

        // Delegate blob store ID (for cache mode)
        add(new TextField<>("delegateBlobStoreId", new PropertyModel<>(configModel, "delegateBlobStoreId"))
                .add(titleModifier("delegateBlobStoreId.title")));

        // Connection settings
        add(new TextField<>("addresses", new PropertyModel<>(configModel, "addressesAsString"))
                .setRequired(true)
                .add(titleModifier("addresses.title")));

        add(new PasswordTextFieldWriteOnlyModel("password", new PropertyModel<>(configModel, "password"))
                .setRequired(false)
                .add(titleModifier("password.title")));

        add(new TextField<>("database", new PropertyModel<>(configModel, "database"))
                .add(titleModifier("database.title")));

        // Cache settings
        add(new TextField<>("ttlSeconds", new PropertyModel<>(configModel, "ttlSeconds"))
                .add(titleModifier("ttlSeconds.title")));

        add(new TextField<>("keyPrefix", new PropertyModel<>(configModel, "keyPrefix"))
                .add(titleModifier("keyPrefix.title")));

        // Connection pool settings
        add(new TextField<>("connectionPoolSize", new PropertyModel<>(configModel, "connectionPoolSize"))
                .add(titleModifier("connectionPoolSize.title")));

        add(new TextField<>("connectionMinIdleSize", new PropertyModel<>(configModel, "connectionMinIdleSize"))
                .add(titleModifier("connectionMinIdleSize.title")));

        add(new TextField<>("connectionTimeout", new PropertyModel<>(configModel, "connectionTimeout"))
                .add(titleModifier("connectionTimeout.title")));

        // Cluster mode
        add(new CheckBox("clusterMode", new PropertyModel<>(configModel, "clusterMode"))
                .add(titleModifier("clusterMode.title")));

        add(new TextField<>("clusterScanInterval", new PropertyModel<>(configModel, "clusterScanInterval"))
                .add(titleModifier("clusterScanInterval.title")));

        // Sentinel mode
        add(new CheckBox("sentinelMode", new PropertyModel<>(configModel, "sentinelMode"))
                .add(titleModifier("sentinelMode.title")));

        add(new TextField<>("sentinelMasterName", new PropertyModel<>(configModel, "sentinelMasterName"))
                .add(titleModifier("sentinelMasterName.title")));
    }

    private AttributeModifier titleModifier(String resourceKey) {
        return new AttributeModifier("title", new ResourceModel(resourceKey));
    }
}
