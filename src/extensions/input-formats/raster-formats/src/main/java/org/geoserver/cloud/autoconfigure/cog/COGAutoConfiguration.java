/* (c) 2022 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.cloud.autoconfigure.cog;

import org.geoserver.catalog.Catalog;
import org.geoserver.cloud.config.factory.ImportFilteredResource;
import org.geoserver.cog.CogSettings;
import org.geoserver.cog.CoverageReaderCogInputObjectConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Auto configuration to enable the COG (Cloud Optimized GeoTIFF) support as raster data format. */
@AutoConfiguration
@SuppressWarnings("java:S1118") // Suppress SonarLint warning, constructor needs to be public
@ConditionalOnClass({CogSettings.class})
@ImportFilteredResource(
        "jar:gs-cog-.*!/applicationContext.xml#name=" + COGAutoConfiguration.EXCLUDE_WEBUI_AND_COG_CONVERTER_BEANS)
public class COGAutoConfiguration {

    static final String EXCLUDE_WEBUI_BEANS = COGWebUIAutoConfiguration.WEBUI_BEAN_NAMES;
    static final String COG_CONVERTER_BEAN = "coverageReaderInputObjectCogConverter";
    static final String EXCLUDE_WEBUI_AND_COG_CONVERTER_BEANS =
            "^(?!" + EXCLUDE_WEBUI_BEANS + "|" + COG_CONVERTER_BEAN + ").*$";

    /**
     * Registers the shadowed {@link CoverageReaderCogInputObjectConverter} to override the one from gs-cog-core.
     *
     * <p>This version includes fixes for S3 URI parsing to support S3-compatible storage providers.
     */
    @Bean(name = COG_CONVERTER_BEAN)
    @Primary
    CoverageReaderCogInputObjectConverter coverageReaderInputObjectCogConverter(Catalog catalog) {
        return new CoverageReaderCogInputObjectConverter(catalog);
    }
}
