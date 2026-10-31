package com.samanvay.tasklist.config;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("samanvay.tasklist")
public record TasklistProperties(List<String> adminGroups, Branding branding) {

  public TasklistProperties {
    adminGroups = adminGroups == null ? List.of() : List.copyOf(adminGroups);
    branding = branding == null ? new Branding(null, null, null, null, null) : branding;
  }

  /**
   * How the tasklist presents itself, so one build can be branded per deployment. The app bar shows
   * the logo (if any), then {@code name} in bold followed by {@code product}. {@code logoUrl} is a path
   * on the same site (e.g. served by a process application) or an https URL; anything else is ignored.
   */
  public record Branding(
      String name, String product, String primaryColor, String secondaryColor, String logoUrl) {

    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final Pattern LOGO_URL = Pattern.compile("(/(?!/)|https://)[^\\s\"'<>]+");

    public Branding {
      name = blankTo(name, "Samanvay");
      product = blankTo(product, "Tasklist");
      primaryColor = colorOr(primaryColor, "#1f4e79");
      secondaryColor = colorOr(secondaryColor, "#00796b");
      logoUrl = logoUrl != null && LOGO_URL.matcher(logoUrl.trim()).matches() ? logoUrl.trim() : null;
    }

    private static String blankTo(String value, String fallback) {
      return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String colorOr(String value, String fallback) {
      return value != null && HEX_COLOR.matcher(value.trim()).matches() ? value.trim() : fallback;
    }
  }
}
