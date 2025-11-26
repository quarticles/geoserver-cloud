/* (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.cloud.autoconfigure.cog;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for S3-compatible COG (Cloud Optimized GeoTIFF) storage.
 *
 * <p>These properties allow configuring custom S3 endpoints for providers like Wasabi, MinIO,
 * DigitalOcean Spaces, and other S3-compatible storage services.
 *
 * <p>The configuration is applied to the imageio-ext COG library via system properties:
 *
 * <ul>
 *   <li>{@code IIO_S3_AWS_ENDPOINT} - Custom S3 endpoint URL
 *   <li>{@code IIO_S3_AWS_REGION} - AWS region
 *   <li>{@code IIO_S3_AWS_USER} - Access key ID
 *   <li>{@code IIO_S3_AWS_PASSWORD} - Secret access key
 *   <li>{@code IIO_S3_AWS_FORCE_PATH_STYLE} - Enable path-style access
 * </ul>
 *
 * <p>Example configuration for Wasabi:
 *
 * <pre>
 * geoserver:
 *   cog:
 *     s3:
 *       endpoint: https://s3.wasabisys.com
 *       region: us-east-1
 *       path-style-access: true
 *       access-key-id: your-access-key
 *       secret-access-key: your-secret-key
 * </pre>
 *
 * <p>Example configuration for MinIO:
 *
 * <pre>
 * geoserver:
 *   cog:
 *     s3:
 *       endpoint: http://localhost:9000
 *       region: us-east-1
 *       path-style-access: true
 *       access-key-id: minioadmin
 *       secret-access-key: minioadmin
 * </pre>
 *
 * @see S3CogAutoConfiguration
 */
@Data
@ConfigurationProperties(prefix = "geoserver.cog.s3")
public class S3CogConfigurationProperties {

    /** System property name for S3 endpoint URL in imageio-ext. */
    public static final String IIO_S3_AWS_ENDPOINT = "IIO_S3_AWS_ENDPOINT";

    /** System property name for S3 region in imageio-ext. */
    public static final String IIO_S3_AWS_REGION = "IIO_S3_AWS_REGION";

    /** System property name for S3 access key in imageio-ext. */
    public static final String IIO_S3_AWS_USER = "IIO_S3_AWS_USER";

    /** System property name for S3 secret key in imageio-ext. */
    public static final String IIO_S3_AWS_PASSWORD = "IIO_S3_AWS_PASSWORD";

    /** System property name for S3 path-style access in imageio-ext. */
    public static final String IIO_S3_AWS_FORCE_PATH_STYLE = "IIO_S3_AWS_FORCE_PATH_STYLE";

    /**
     * Custom S3 endpoint URL.
     *
     * <p>When set, overrides the default AWS S3 endpoint. Must be a valid URL including protocol
     * (http:// or https://).
     *
     * <p>Examples:
     *
     * <ul>
     *   <li>Wasabi: {@code https://s3.wasabisys.com} or {@code https://s3.us-east-1.wasabisys.com}
     *   <li>MinIO: {@code http://localhost:9000}
     *   <li>DigitalOcean Spaces: {@code https://nyc3.digitaloceanspaces.com}
     * </ul>
     */
    private String endpoint;

    /**
     * AWS region for S3 requests.
     *
     * <p>Required when using custom endpoints. Common values: us-east-1, us-west-1, us-west-2,
     * eu-west-1, eu-central-1
     */
    private String region;

    /**
     * Whether to use path-style access instead of virtual-hosted-style.
     *
     * <p>Path-style: {@code https://s3.endpoint.com/bucket/key}<br>
     * Virtual-hosted-style: {@code https://bucket.s3.endpoint.com/key}
     *
     * <p>Most S3-compatible providers (MinIO, Wasabi) require path-style access ({@code true}).
     * Default: {@code false} (virtual-hosted-style, standard for AWS S3)
     */
    private boolean pathStyleAccess = false;

    /**
     * AWS Access Key ID for authentication.
     *
     * <p>Can also be provided via {@code AWS_ACCESS_KEY_ID} environment variable or through other
     * AWS SDK credential providers.
     */
    private String accessKeyId;

    /**
     * AWS Secret Access Key for authentication.
     *
     * <p>Can also be provided via {@code AWS_SECRET_ACCESS_KEY} environment variable or through
     * other AWS SDK credential providers.
     */
    private String secretAccessKey;

    /**
     * Checks if any S3 configuration is provided.
     *
     * @return true if at least one S3 property is configured
     */
    public boolean isConfigured() {
        return endpoint != null || region != null || accessKeyId != null || secretAccessKey != null;
    }
}
