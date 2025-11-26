/* (c) 2022 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.jackson.databind.config.dto;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * DTO for COG (Cloud Optimized GeoTIFF) store settings.
 *
 * <p>Extends {@link CogSettingsDto} with credentials and S3-compatible endpoint configuration
 * to support providers like Wasabi, MinIO, and other S3-compatible storage services.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("CogSettingsStore")
@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
public class CogSettingsStoreDto extends CogSettingsDto {

    private String username;
    private String password;

    /**
     * Custom S3 endpoint URL for S3-compatible storage providers.
     *
     * <p>Examples:
     * <ul>
     *   <li>Wasabi: {@code https://s3.wasabisys.com} or {@code https://s3.us-east-1.wasabisys.com}</li>
     *   <li>MinIO: {@code http://localhost:9000}</li>
     *   <li>DigitalOcean Spaces: {@code https://nyc3.digitaloceanspaces.com}</li>
     * </ul>
     *
     * <p>When null or empty, defaults to Amazon S3 endpoints.
     */
    private String s3Endpoint;

    /**
     * AWS region for S3 requests.
     *
     * <p>Required for S3-compatible providers. For Wasabi, use the region matching
     * your bucket location (e.g., "us-east-1", "us-west-1", "eu-central-1").
     */
    private String s3Region;

    /**
     * Whether to use path-style access for S3 requests.
     *
     * <p>When {@code true}, URLs are formatted as {@code https://endpoint/bucket/key}.
     * When {@code false} (default), URLs are formatted as {@code https://bucket.endpoint/key}.
     *
     * <p>Most S3-compatible providers (MinIO, some Wasabi configurations) require
     * path-style access ({@code true}).
     */
    private Boolean s3PathStyleAccess;
}
