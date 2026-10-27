package com.eternalhr.onboarding.documents;

import com.eternalhr.onboarding.config.EternalHrProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** {@link DocumentStore} on S3, or on an S3-compatible store when an endpoint is configured. */
public class S3DocumentStore implements DocumentStore {

  private static final Logger LOG = LoggerFactory.getLogger(S3DocumentStore.class);

  private final S3Client s3;
  private final String bucket;
  private final boolean createBucket;
  private final AtomicBoolean bucketChecked = new AtomicBoolean();

  S3DocumentStore(S3Client s3, String bucket, boolean createBucket) {
    this.s3 = s3;
    this.bucket = bucket;
    this.createBucket = createBucket;
  }

  public static S3DocumentStore create(EternalHrProperties.DocumentStore config) {
    S3ClientBuilder builder = S3Client.builder()
        .httpClient(UrlConnectionHttpClient.create())
        .region(Region.of(config.region()));
    if (config.endpoint() != null && !config.endpoint().isBlank()) {
      builder.endpointOverride(URI.create(config.endpoint().trim()))
          .forcePathStyle(true)
          .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
          .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
    }
    builder.credentialsProvider(config.accessKey() == null || config.accessKey().isBlank()
        ? DefaultCredentialsProvider.builder().build()
        : StaticCredentialsProvider.create(AwsBasicCredentials.create(config.accessKey(), config.secretKey())));
    return new S3DocumentStore(builder.build(), config.bucket().trim(), config.createBucket());
  }

  static String key(String processInstanceId, String documentId) {
    return "process-instances/" + processInstanceId + "/documents/" + documentId;
  }

  @Override
  public void put(String processInstanceId, Document document, byte[] data) {
    ensureBucket();
    Map<String, String> metadata = new HashMap<>();
    metadata.put("document-type", encode(document.type()));
    metadata.put("document-type-label", encode(document.typeLabel()));
    metadata.put("file-name", encode(document.fileName()));
    metadata.put("uploaded-by", encode(document.uploadedBy()));
    metadata.put("uploaded-by-name", encode(document.uploadedByName()));
    metadata.put("uploaded-at", document.uploadedAt().toString());
    metadata.put("step", encode(document.step()));
    s3.putObject(r -> r.bucket(bucket).key(key(processInstanceId, document.id()))
        .contentType(document.contentType()).metadata(metadata), RequestBody.fromBytes(data));
  }

  @Override
  public void delete(String processInstanceId, String documentId) {
    s3.deleteObject(r -> r.bucket(bucket).key(key(processInstanceId, documentId)));
  }

  private void ensureBucket() {
    if (!createBucket || bucketChecked.get()) {
      return;
    }
    try {
      s3.headBucket(r -> r.bucket(bucket));
    } catch (S3Exception e) {
      if (e.statusCode() != 404) {
        throw e;
      }
      LOG.info("Creating document bucket {}", bucket);
      s3.createBucket(r -> r.bucket(bucket));
    }
    bucketChecked.set(true);
  }

  static String encode(String value) {
    return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
