package com.samanvay.tasklist.document;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Documents in an S3 bucket. Each document is one object; its details are object metadata, URL-encoded because
 * S3 metadata must be ASCII:
 * <pre>
 * process-instances/{processInstanceId}/documents/{documentId}
 *   Content-Type, x-amz-meta-document-type, -document-type-label, -file-name, -uploaded-by, -uploaded-by-name,
 *   -uploaded-at (ISO-8601), -step
 * </pre>
 */
public class S3DocumentStore implements DocumentStore {

  static final String TYPE = "document-type";
  static final String TYPE_LABEL = "document-type-label";
  static final String FILE_NAME = "file-name";
  static final String UPLOADED_BY = "uploaded-by";
  static final String UPLOADED_BY_NAME = "uploaded-by-name";
  static final String UPLOADED_AT = "uploaded-at";
  static final String STEP = "step";

  private static final Logger LOG = LoggerFactory.getLogger(S3DocumentStore.class);

  private final S3Client s3;
  private final String bucket;
  private final boolean createBucket;
  private final AtomicBoolean bucketChecked = new AtomicBoolean();

  public S3DocumentStore(S3Client s3, String bucket, boolean createBucket) {
    this.s3 = s3;
    this.bucket = bucket;
    this.createBucket = createBucket;
  }

  public static S3DocumentStore create(DocumentProperties properties) {
    S3ClientBuilder builder = S3Client.builder()
        .httpClient(UrlConnectionHttpClient.create())
        .region(Region.of(properties.region()));
    if (!DocumentProperties.blank(properties.endpoint())) {
      // S3-compatible stores: path-style addressing, and checksums only where S3 requires them
      builder.endpointOverride(URI.create(properties.endpoint().trim()))
          .forcePathStyle(true)
          .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
          .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
    }
    builder.credentialsProvider(DocumentProperties.blank(properties.accessKey())
        ? DefaultCredentialsProvider.builder().build()
        : StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())));
    return new S3DocumentStore(builder.build(), properties.bucket().trim(), properties.createBucket());
  }

  static String prefix(String processInstanceId) {
    return "process-instances/" + processInstanceId + "/documents/";
  }

  @Override
  public List<StoredDocument> list(String processInstanceId) {
    try {
      return s3.listObjectsV2Paginator(r -> r.bucket(bucket).prefix(prefix(processInstanceId)))
          .contents().stream()
          .map(S3Object::key)
          .map(key -> read(key, s3.headObject(r -> r.bucket(bucket).key(key))))
          .sorted(Comparator.comparing(StoredDocument::uploadedAt))
          .toList();
    } catch (NoSuchBucketException e) {
      return List.of();
    } catch (SdkException e) {
      throw unavailable(e);
    }
  }

  @Override
  public Optional<StoredDocument.Content> get(String processInstanceId, String documentId) {
    String key = prefix(processInstanceId) + documentId;
    try {
      ResponseBytes<GetObjectResponse> object = s3.getObjectAsBytes(r -> r.bucket(bucket).key(key));
      GetObjectResponse response = object.response();
      return Optional.of(new StoredDocument.Content(
          toDocument(key, response.contentType(), response.contentLength(), response.metadata()),
          object.asByteArray()));
    } catch (NoSuchKeyException | NoSuchBucketException e) {
      return Optional.empty();
    } catch (SdkException e) {
      throw unavailable(e);
    }
  }

  @Override
  public void put(String processInstanceId, StoredDocument document, byte[] data) {
    ensureBucket();
    Map<String, String> metadata = new HashMap<>();
    metadata.put(TYPE, encode(document.type()));
    metadata.put(TYPE_LABEL, encode(document.typeLabel()));
    metadata.put(FILE_NAME, encode(document.fileName()));
    metadata.put(UPLOADED_BY, encode(document.uploadedBy()));
    metadata.put(UPLOADED_BY_NAME, encode(document.uploadedByName()));
    metadata.put(UPLOADED_AT, document.uploadedAt().toString());
    metadata.put(STEP, encode(document.step()));
    try {
      s3.putObject(r -> r.bucket(bucket).key(prefix(processInstanceId) + document.id())
          .contentType(document.contentType()).metadata(metadata), RequestBody.fromBytes(data));
    } catch (SdkException e) {
      throw unavailable(e);
    }
  }

  @Override
  public void delete(String processInstanceId, String documentId) {
    try {
      s3.deleteObject(r -> r.bucket(bucket).key(prefix(processInstanceId) + documentId));
    } catch (SdkException e) {
      throw unavailable(e);
    }
  }

  private void ensureBucket() {
    if (!createBucket || bucketChecked.get()) {
      return;
    }
    try {
      s3.headBucket(r -> r.bucket(bucket));
    } catch (NoSuchBucketException e) {
      LOG.info("Creating document bucket {}", bucket);
      s3.createBucket(r -> r.bucket(bucket));
    } catch (S3Exception e) {
      if (e.statusCode() != 404) {
        throw unavailable(e);
      }
      LOG.info("Creating document bucket {}", bucket);
      s3.createBucket(r -> r.bucket(bucket));
    } catch (SdkException e) {
      throw unavailable(e);
    }
    bucketChecked.set(true);
  }

  private StoredDocument read(String key, HeadObjectResponse head) {
    return toDocument(key, head.contentType(), head.contentLength(), head.metadata());
  }

  static StoredDocument toDocument(String key, String contentType, Long size, Map<String, String> metadata) {
    String id = key.substring(key.lastIndexOf('/') + 1);
    return new StoredDocument(id,
        decode(metadata.get(TYPE)),
        decode(metadata.get(TYPE_LABEL)),
        decode(metadata.get(FILE_NAME)),
        contentType,
        size == null ? 0 : size,
        decode(metadata.get(UPLOADED_BY)),
        decode(metadata.get(UPLOADED_BY_NAME)),
        instant(metadata.get(UPLOADED_AT)),
        decode(metadata.get(STEP)));
  }

  static String encode(String value) {
    return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  static String decode(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return value;
    }
  }

  private static Instant instant(String value) {
    try {
      return value == null ? Instant.EPOCH : Instant.parse(value);
    } catch (DateTimeParseException e) {
      return Instant.EPOCH;
    }
  }

  private static DocumentStoreUnavailableException unavailable(SdkException e) {
    LOG.error("Document store error: {}", e.getMessage());
    return new DocumentStoreUnavailableException(e);
  }
}
