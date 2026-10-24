package com.samanvay.tasklist.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("samanvay.tasklist")
public record TasklistProperties(List<String> adminGroups) {

  public TasklistProperties {
    adminGroups = adminGroups == null ? List.of() : List.copyOf(adminGroups);
  }
}
