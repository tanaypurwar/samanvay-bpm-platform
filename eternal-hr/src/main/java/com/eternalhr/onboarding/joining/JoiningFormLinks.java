package com.eternalhr.onboarding.joining;

import com.eternalhr.onboarding.config.EternalHrProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Signed links to the employee's joining form. The token carries the process instance id and an expiry time,
 * signed with HMAC-SHA256, so no server-side state is needed and a token cannot be altered or guessed.
 */
@Component
public class JoiningFormLinks {

  static final String PATH = "/joining/";
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  private final EternalHrProperties.JoiningForm config;
  private final byte[] key;
  private final Clock clock;

  public JoiningFormLinks(EternalHrProperties properties, Clock clock) {
    this.config = properties.joiningForm();
    if (config.secret() == null || config.secret().length() < 32) {
      throw new IllegalStateException("eternal-hr.joining-form.secret must be at least 32 characters");
    }
    this.key = config.secret().getBytes(StandardCharsets.UTF_8);
    this.clock = clock;
  }

  public record Link(String url, Instant expiresAt) {
  }

  public Link create(String processInstanceId) {
    Instant expiresAt = clock.instant().plus(config.linkValidity());
    String payload = ENCODER.encodeToString(
        (processInstanceId + "|" + expiresAt.getEpochSecond()).getBytes(StandardCharsets.UTF_8));
    String token = payload + "." + ENCODER.encodeToString(sign(payload));
    return new Link(stripTrailingSlash(config.publicBaseUrl()) + PATH + token, expiresAt);
  }

  /** The process instance id, if the token is genuine and not expired. */
  public Optional<String> verify(String token) {
    if (token == null) {
      return Optional.empty();
    }
    int dot = token.indexOf('.');
    if (dot <= 0 || dot == token.length() - 1) {
      return Optional.empty();
    }
    String payload = token.substring(0, dot);
    try {
      byte[] signature = DECODER.decode(token.substring(dot + 1));
      if (!MessageDigest.isEqual(sign(payload), signature)) {
        return Optional.empty();
      }
      String[] parts = new String(DECODER.decode(payload), StandardCharsets.UTF_8).split("\\|");
      if (parts.length != 2 || clock.instant().isAfter(Instant.ofEpochSecond(Long.parseLong(parts[1])))) {
        return Optional.empty();
      }
      return Optional.of(parts[0]);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private byte[] sign(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String stripTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
