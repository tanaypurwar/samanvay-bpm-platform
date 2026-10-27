package com.eternalhr.onboarding.joining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eternalhr.onboarding.config.EternalHrProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JoiningFormLinksTest {

  private static final String SECRET = "test-secret-0123456789-0123456789-abc";
  private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

  private static JoiningFormLinks links(String secret, Instant now) {
    var properties = new EternalHrProperties(null,
        new EternalHrProperties.JoiningForm("https://hr.example.com/", secret, Duration.ofDays(14), "R3/P2D"),
        null, null, null, null, null);
    return new JoiningFormLinks(properties, Clock.fixed(now, ZoneOffset.UTC));
  }

  @Test
  void linkCarriesTheProcessInstanceAndVerifies() {
    JoiningFormLinks.Link link = links(SECRET, NOW).create("pi-123");

    assertThat(link.url()).startsWith("https://hr.example.com/joining/");
    assertThat(link.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
    assertThat(links(SECRET, NOW).verify(token(link))).contains("pi-123");
  }

  @Test
  void expiredAlteredOrForeignTokensAreRejected() {
    String token = token(links(SECRET, NOW).create("pi-123"));

    assertThat(links(SECRET, NOW.plus(Duration.ofDays(15))).verify(token)).isEmpty();
    assertThat(links(SECRET + "x", NOW).verify(token)).isEmpty();
    String otherInstance = token(links(SECRET, NOW).create("pi-999"));
    String forged = otherInstance.substring(0, otherInstance.indexOf('.')) + token.substring(token.indexOf('.'));
    assertThat(links(SECRET, NOW).verify(forged)).isEmpty();
    assertThat(links(SECRET, NOW).verify("not-a-token")).isEmpty();
    assertThat(links(SECRET, NOW).verify("a.b!c")).isEmpty();
    assertThat(links(SECRET, NOW).verify(null)).isEmpty();
  }

  @Test
  void shortSecretsAreRefused() {
    assertThatThrownBy(() -> links("too-short", NOW)).isInstanceOf(IllegalStateException.class);
  }

  private static String token(JoiningFormLinks.Link link) {
    return link.url().substring(link.url().lastIndexOf('/') + 1);
  }
}
