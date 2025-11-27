/*
 *    ImageI/O-Ext - OpenSource Java Image translation Library
 *    http://www.geo-solutions.it/
 *    https://github.com/geosolutions-it/imageio-ext
 *    (C) 2019, GeoSolutions
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    either version 3 of the License, or (at your option) any later version.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 *
 *    MODIFIED by GeoServer Cloud to support S3-compatible endpoints (Wasabi, MinIO, etc.)
 *    The original getURL() method hardcoded ".amazonaws.com" which breaks S3-compatible providers.
 */
package it.geosolutions.imageioimpl.plugins.cog;

import static software.amazon.awssdk.core.async.AsyncResponseTransformer.toBytes;

import it.geosolutions.imageio.core.BasicAuthURI;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * Reads URIs from S3 with the format https://s3-<region>.amazonaws.com/<bucket>/<key>
 * https://s3.<region>.amazonaws.com/<bucket>/<key> https://<bucket>.s3-<region>.amazonaws.com/<key>
 * https://<bucket>.s3.<region>.amazonaws.com/<key> or s3://<bucket>/<key> For the latter, the region must be set via
 * environment variables, system properties or be provided via the URL parameter `region`:
 * s3://<bucket>/<key>?region=us-west-2
 *
 * <p>This is a shadowed version that supports S3-compatible endpoints (Wasabi, MinIO, etc.)
 * by using the configured endpoint instead of hardcoded ".amazonaws.com".
 *
 * <p>API documentation: https://docs.aws.amazon.com/sdk-for-java/v2/developer-guide/aws-sdk-java-dg-v2.pdf
 *
 * @author joshfix Created on 2019-08-21
 * @author GeoServer Cloud - Modified to support S3-compatible endpoints
 */
public class S3RangeReader extends AbstractRangeReader {

    protected S3AsyncClient client;
    protected S3ConfigurationProperties configProps;

    private static final Logger LOGGER = Logger.getLogger(S3RangeReader.class.getName());

    public S3RangeReader(String url, int headerLength) {
        this(URI.create(url), headerLength);
    }

    public S3RangeReader(URL url, int headerLength) {
        this(URI.create(url.toString()), headerLength);
    }

    public S3RangeReader(URI uri, int headerLength) {
        this(new BasicAuthURI(uri), headerLength);
    }

    public S3RangeReader(BasicAuthURI uri, int headerLength) {
        super(uri, headerLength);
        configProps = new S3ConfigurationProperties(uri.getUri().getScheme(), uri);
        client = S3ClientFactory.getS3Client(configProps);
    }

    @Override
    public byte[] fetchHeader() {
        byte[] currentHeader = data.get(0L);
        if (currentHeader != null) {
            headerOffset = currentHeader.length;
        }
        GetObjectRequest headerRequest = buildRequest();
        try {
            ResponseBytes<GetObjectResponse> responseBytes =
                    client.getObject(headerRequest, toBytes()).get();

            // get the header bytes
            byte[] headerBytes = responseBytes.asByteArray();
            if (headerOffset != 0) {
                byte[] oldHeader = data.get(0L);
                byte[] newHeader = new byte[headerBytes.length + oldHeader.length];
                System.arraycopy(oldHeader, 0, newHeader, 0, oldHeader.length);
                System.arraycopy(headerBytes, 0, newHeader, oldHeader.length, headerBytes.length);
                headerBytes = newHeader;
            }

            data.put(0L, headerBytes);
            return headerBytes;
        } catch (Exception e) {
            LOGGER.severe("Error reading header for " + uri);
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<Long, byte[]> read(Collection<long[]> ranges) {
        return read(ranges.toArray(new long[][] {}));
    }

    @Override
    public byte[] readHeader() {
        LOGGER.fine("reading header");
        byte[] currentHeader = HEADERS_CACHE.get(uri.toString());

        if (currentHeader != null) {
            return currentHeader;
        }
        GetObjectRequest headerRequest = buildRequest();
        try {
            ResponseBytes<GetObjectResponse> responseBytes =
                    client.getObject(headerRequest, toBytes()).get();

            // get the header bytes
            byte[] headerBytes = responseBytes.asByteArray();
            data.put(0L, headerBytes);
            HEADERS_CACHE.put(uri.toString(), headerBytes);
            return headerBytes;
        } catch (Exception e) {
            LOGGER.severe("Error reading header for " + uri);
            throw new RuntimeException(e);
        }
    }

    private GetObjectRequest buildRequest() {
        return GetObjectRequest.builder()
                .bucket(configProps.getBucket())
                .key(configProps.getKey())
                .range("bytes=" + headerOffset + "-" + (headerOffset + headerLength - 1))
                .build();
    }

    @Override
    public Map<Long, byte[]> read(long[]... ranges) {
        ranges = reconcileRanges(ranges);

        Instant start = Instant.now();
        Map<Long, CompletableFuture<ResponseBytes<GetObjectResponse>>> downloads = new HashMap<>(ranges.length);

        Map<Long, byte[]> values = new HashMap<>();
        int[] missingRanges = new int[ranges.length];
        int missing = 0;
        for (int i = 0; i < ranges.length; i++) {
            final long[] range = ranges[i];
            final long rangeStart = range[0];
            byte[] dataRange = data.get(rangeStart);
            // Check for available data.
            if (dataRange == null) {
                long rangeEnd = range[1];
                CompletableFuture<ResponseBytes<GetObjectResponse>> futureGet = readAsync(rangeStart, rangeEnd);
                downloads.put(rangeStart, futureGet);
                // Mark the range as missing
                missingRanges[missing++] = i;
            } else {
                values.put(rangeStart, dataRange);
            }
        }

        awaitCompletion(values, downloads);
        Instant end = Instant.now();
        LOGGER.fine("Time to read all ranges: " + Duration.between(start, end));
        for (int k = 0; k < missing; k++) {
            long range = ranges[missingRanges[k]][0];
            data.put(range, values.get(range));
        }
        return values;
    }

    CompletableFuture<ResponseBytes<GetObjectResponse>> readAsync(final long rangeStart, long rangeEnd) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(configProps.getBucket())
                .key(configProps.getKey())
                .range("bytes=" + rangeStart + "-" + rangeEnd)
                .build();
        return client.getObject(request, AsyncResponseTransformer.toBytes());
    }

    /**
     * Blocks until all ranges have been read and written to the ByteBuffer
     *
     * @param data
     * @param downloads
     */
    protected void awaitCompletion(
            Map<Long, byte[]> data, Map<Long, CompletableFuture<ResponseBytes<GetObjectResponse>>> downloads) {
        boolean stillWaiting = true;
        List<Long> completed = new ArrayList<>(downloads.size());
        while (stillWaiting) {
            boolean allDone = true;
            for (Map.Entry<Long, CompletableFuture<ResponseBytes<GetObjectResponse>>> entry : downloads.entrySet()) {
                long key = entry.getKey();
                CompletableFuture<ResponseBytes<GetObjectResponse>> future = entry.getValue();
                if (future.isDone()) {
                    if (!completed.contains(key)) {
                        try {
                            data.put(key, future.get().asByteArray());
                            completed.add(key);
                        } catch (Exception e) {
                            LOGGER.warning(
                                    "Unable to write data from S3 to the destination ByteBuffer. " + e.getMessage());
                        }
                    }
                } else {
                    allDone = false;
                }
            }
            stillWaiting = !allDone;
        }
    }

    /**
     * Returns the URL for this S3 resource.
     *
     * <p>MODIFIED: This version uses the configured endpoint from environment variables
     * instead of hardcoding ".amazonaws.com". This enables support for S3-compatible
     * storage providers like Wasabi, MinIO, DigitalOcean Spaces, etc.
     *
     * <p>If no custom endpoint is configured, falls back to the standard AWS S3 URL format.
     */
    @Override
    public URL getURL() throws MalformedURLException {
        String scheme = uri.getScheme().toLowerCase();
        if (scheme.startsWith("s3")) {
            String endpoint = configProps.getEndpoint();
            if (endpoint != null && !endpoint.isEmpty()) {
                // Use configured endpoint for S3-compatible providers (Wasabi, MinIO, etc.)
                // Remove trailing slash if present
                if (endpoint.endsWith("/")) {
                    endpoint = endpoint.substring(0, endpoint.length() - 1);
                }
                // Build URL using the configured endpoint
                // Format: https://endpoint/bucket/key (path-style) or derive from endpoint
                String urlStr;
                if (configProps.getForcePathStyle()) {
                    // Path-style: https://s3.wasabisys.com/bucket/key
                    urlStr = endpoint + "/" + configProps.getBucket() + "/" + configProps.getKey();
                } else {
                    // Virtual-hosted-style: https://bucket.s3.region.wasabisys.com/key
                    // Extract host from endpoint URL
                    try {
                        URI endpointUri = URI.create(endpoint);
                        String endpointHost = endpointUri.getHost();
                        String endpointScheme = endpointUri.getScheme();
                        if (endpointScheme == null) {
                            endpointScheme = "https";
                        }
                        urlStr = endpointScheme + "://" + configProps.getBucket() + "." + endpointHost + "/"
                                + configProps.getKey();
                    } catch (Exception e) {
                        // Fallback to path-style if endpoint parsing fails
                        LOGGER.warning("Failed to parse endpoint URI, using path-style: " + e.getMessage());
                        urlStr = endpoint + "/" + configProps.getBucket() + "/" + configProps.getKey();
                    }
                }
                LOGGER.fine("Generated S3-compatible URL: " + urlStr);
                return new URL(urlStr);
            } else {
                // No custom endpoint - use default AWS S3 URL format (original behavior)
                return new URL("https://" + configProps.getBucket() + ".s3." + configProps.getRegion()
                        + ".amazonaws.com/" + configProps.getKey());
            }
        }
        return super.getURL();
    }
}
