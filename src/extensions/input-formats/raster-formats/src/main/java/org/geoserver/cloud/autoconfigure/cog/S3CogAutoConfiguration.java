/* (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.cloud.autoconfigure.cog;

import static org.geoserver.cloud.autoconfigure.cog.S3CogConfigurationProperties.IIO_S3_AWS_ENDPOINT;
import static org.geoserver.cloud.autoconfigure.cog.S3CogConfigurationProperties.IIO_S3_AWS_FORCE_PATH_STYLE;
import static org.geoserver.cloud.autoconfigure.cog.S3CogConfigurationProperties.IIO_S3_AWS_PASSWORD;
import static org.geoserver.cloud.autoconfigure.cog.S3CogConfigurationProperties.IIO_S3_AWS_REGION;
import static org.geoserver.cloud.autoconfigure.cog.S3CogConfigurationProperties.IIO_S3_AWS_USER;

import lombok.extern.slf4j.Slf4j;
import org.geoserver.cog.CogSettings;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Auto-configuration for S3-compatible COG (Cloud Optimized GeoTIFF) storage.
 *
 * <p>This configuration enables the use of S3-compatible storage providers such as Wasabi, MinIO,
 * DigitalOcean Spaces, and others for COG raster data.
 *
 * <p>The configuration works by setting system properties that the imageio-ext COG library reads to
 * configure its S3 client:
 *
 * <ul>
 *   <li>{@code IIO_S3_AWS_ENDPOINT} - Custom S3 endpoint URL
 *   <li>{@code IIO_S3_AWS_REGION} - AWS region
 *   <li>{@code IIO_S3_AWS_USER} - Access key ID
 *   <li>{@code IIO_S3_AWS_PASSWORD} - Secret access key
 *   <li>{@code IIO_S3_AWS_FORCE_PATH_STYLE} - Enable path-style access (required for most
 *       S3-compatible providers)
 * </ul>
 *
 * <p>Example configuration in application.yml:
 *
 * <pre>
 * geoserver:
 *   cog:
 *     s3:
 *       endpoint: https://s3.wasabisys.com
 *       region: us-east-1
 *       path-style-access: true
 *       access-key-id: ${WASABI_ACCESS_KEY}
 *       secret-access-key: ${WASABI_SECRET_KEY}
 * </pre>
 *
 * @see S3CogConfigurationProperties
 * @see COGAutoConfiguration
 */
@AutoConfiguration(before = COGAutoConfiguration.class)
@ConditionalOnClass(CogSettings.class)
@ConditionalOnProperty(prefix = "geoserver.cog.s3", name = "endpoint")
@EnableConfigurationProperties(S3CogConfigurationProperties.class)
@Slf4j
public class S3CogAutoConfiguration implements InitializingBean {

    private final S3CogConfigurationProperties properties;

    public S3CogAutoConfiguration(S3CogConfigurationProperties properties) {
        this.properties = properties;
    }

    /**
     * Configures the imageio-ext S3 client by setting system properties.
     *
     * <p>This method runs after the bean is constructed to ensure the S3 configuration is available
     * before any COG operations are attempted.
     */
    @Override
    public void afterPropertiesSet() {
        log.info("Configuring S3-compatible COG storage with endpoint: {}", properties.getEndpoint());

        setSystemPropertyIfNotNull(IIO_S3_AWS_ENDPOINT, properties.getEndpoint());
        setSystemPropertyIfNotNull(IIO_S3_AWS_REGION, properties.getRegion());
        setSystemPropertyIfNotNull(IIO_S3_AWS_USER, properties.getAccessKeyId());
        setSystemPropertyIfNotNull(IIO_S3_AWS_PASSWORD, properties.getSecretAccessKey());

        if (properties.isPathStyleAccess()) {
            System.setProperty(IIO_S3_AWS_FORCE_PATH_STYLE, "true");
            log.debug("S3 path-style access enabled");
        }

        logConfiguration();
    }

    private void setSystemPropertyIfNotNull(String key, String value) {
        if (value != null && !value.isBlank()) {
            System.setProperty(key, value);
        }
    }

    private void logConfiguration() {
        if (log.isDebugEnabled()) {
            log.debug(
                    "S3 COG configuration: endpoint={}, region={}, pathStyleAccess={}, credentialsProvided={}",
                    properties.getEndpoint(),
                    properties.getRegion(),
                    properties.isPathStyleAccess(),
                    properties.getAccessKeyId() != null);
        }
    }
}
