package com.eternalhr.onboarding.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("eternal-hr")
public record EternalHrProperties(
    Engine engine,
    JoiningForm joiningForm,
    Documents documents,
    DocumentStore documentStore,
    Mail mail,
    Demo demo,
    /** Where people open their tasks, used in emails. */
    String tasklistUrl) {

  /**
   * The platform's document bucket (S3, or an S3-compatible store at {@code endpoint}), where the employee's
   * uploads go. Uploads are off while {@code bucket} is empty. An empty {@code accessKey} uses the default AWS
   * credentials (e.g. the EC2 instance role).
   */
  public record DocumentStore(String bucket, String region, String endpoint, String accessKey, String secretKey,
      boolean createBucket, DataSize maxFileSize, Integer maxFiles) {

    public DocumentStore {
      region = region == null || region.isBlank() ? "ap-south-1" : region.trim();
      maxFileSize = maxFileSize == null ? DataSize.ofMegabytes(10) : maxFileSize;
      maxFiles = maxFiles == null ? 10 : maxFiles;
    }

    public boolean enabled() {
      return bucket != null && !bucket.isBlank();
    }
  }

  /** Engine REST API, for deployments, message correlation and identity lookups. */
  public record Engine(String baseUrl, String username, String password) {
  }

  /**
   * The employee's joining form. Links are signed with {@code secret} and expire after {@code linkValidity};
   * reminders follow {@code reminderCycle} (ISO 8601 repeating interval, e.g. {@code R3/P2D}).
   */
  public record JoiningForm(String publicBaseUrl, String secret, Duration linkValidity, String reminderCycle) {
  }

  /** How long the employee has to send corrected documents before the PM is emailed (ISO 8601 duration). */
  public record Documents(String escalateAfter) {
  }

  public record Mail(String from, String hrEmail) {
  }

  /** Demo only: create the role groups and one user per role in the engine. */
  public record Demo(boolean seedUsers, String password) {
  }
}
