package com.samanvay.tasklist.document;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Where process documents are stored: an S3 bucket, or a bucket on any S3-compatible store when {@code endpoint}
 * is set (a local stand-in such as RustFS). Documents are off while {@code bucket} is empty.
 *
 * @param endpoint     empty for AWS S3; the store's URL otherwise (path-style addressing is used then)
 * @param accessKey    empty to use the default AWS credentials chain (e.g. the EC2 instance role)
 * @param createBucket create the bucket if it is missing (local stores only)
 */
@ConfigurationProperties("samanvay.documents")
public record DocumentProperties(String bucket, String region, String endpoint, String accessKey, String secretKey,
    boolean createBucket, DataSize maxFileSize, Integer maxFilesPerUpload) {

  public DocumentProperties {
    region = blank(region) ? "ap-south-1" : region.trim();
    maxFileSize = maxFileSize == null ? DataSize.ofMegabytes(10) : maxFileSize;
    maxFilesPerUpload = maxFilesPerUpload == null ? 10 : maxFilesPerUpload;
  }

  public boolean enabled() {
    return !blank(bucket);
  }

  static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
