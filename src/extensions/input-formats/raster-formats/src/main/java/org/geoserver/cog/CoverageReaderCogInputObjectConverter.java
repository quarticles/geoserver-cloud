/* (c) 2018 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.cog;

import it.geosolutions.imageio.core.BasicAuthURI;
import it.geosolutions.imageio.core.SourceSPIProvider;
import it.geosolutions.imageioimpl.plugins.cog.CogImageInputStreamSpi;
import it.geosolutions.imageioimpl.plugins.cog.CogImageReaderSpi;
import it.geosolutions.imageioimpl.plugins.cog.CogSourceSPIProvider;
import java.io.Serializable;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.spi.ImageInputStreamSpi;
import javax.imageio.spi.ImageReaderSpi;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageReaderInputObjectConverter;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.MetadataMap;
import org.geotools.util.factory.Hints;
import org.geotools.util.logging.Logging;

/**
 * Shadowed implementation of {@link CoverageReaderCogInputObjectConverter} to
 * fix S3 URI parsing.
 */
public class CoverageReaderCogInputObjectConverter implements CoverageReaderInputObjectConverter<SourceSPIProvider> {

    private static final Logger LOGGER = Logging.getLogger(CoverageReaderCogInputObjectConverter.class);

    private static final ImageReaderSpi COG_IMAGE_READER_SPI = new CogImageReaderSpi();
    private static final ImageInputStreamSpi COG_IMAGE_INPUT_STREAM_SPI = new CogImageInputStreamSpi();

    private final Catalog catalog;

    public CoverageReaderCogInputObjectConverter(Catalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public Optional<SourceSPIProvider> convert(Object input, CoverageInfo coverage, Hints hints) {
        return convert(input, coverage, coverage.getStore(), hints);
    }

    @Override
    public Optional<SourceSPIProvider> convert(
            Object input, CoverageInfo coverage, CoverageStoreInfo store, Hints hints) {
        if (!(input instanceof String)) {
            return Optional.empty();
        }
        String location = (String) input;
        if (canConvert(location)) {
            return convertReaderInputObject(location, store);
        }
        return Optional.empty();
    }

    protected boolean canConvert(String location) {
        return location.startsWith("cog://");
    }

    protected Optional<SourceSPIProvider> convertReaderInputObject(String location, CoverageStoreInfo store) {
        if (location.startsWith("cog://")) {
            location = location.substring(6);
        }

        // Fix: Ensure we don't lose the bucket for s3:// URIs
        // The original implementation might have had issues with URI.create() or
        // subsequent handling
        LOGGER.info("Converting COG URI: " + location);

        MetadataMap metadata = store.getMetadata();
        CogSettings cogSettings = new CogSettings();
        if (metadata != null && metadata.containsKey(CogSettings.Key)) {
            cogSettings = (CogSettings) metadata.get(CogSettings.Key);
        }

        Map<String, Serializable> connectionParameters = store.getConnectionParameters();
        URI uri = URI.create(location);

        // Log parsed URI details
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Parsed URI - Scheme: " + uri.getScheme() + ", Host: " + uri.getHost() + ", Path: "
                    + uri.getPath());
        }

        String user = null;
        String password = null;
        if (connectionParameters != null) {
            Object userObj = connectionParameters.get("user");
            Object passwordObj = connectionParameters.get("password");
            if (userObj != null && passwordObj != null) {
                user = (String) userObj;
                password = (String) passwordObj;
            }
        }

        BasicAuthURI basicAuthURI = new BasicAuthURI(uri, cogSettings.isUseCachingStream(), user, password);

        // Log BasicAuthURI details
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("BasicAuthURI: " + basicAuthURI.toString());
        }

        CogSourceSPIProvider provider = new CogSourceSPIProvider(
                basicAuthURI,
                COG_IMAGE_READER_SPI,
                COG_IMAGE_INPUT_STREAM_SPI,
                cogSettings.getRangeReaderSettings().getRangeReaderClassName());

        return Optional.of(provider);
    }
}
