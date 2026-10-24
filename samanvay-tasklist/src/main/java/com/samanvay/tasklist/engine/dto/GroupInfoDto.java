package com.samanvay.tasklist.engine.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Response of GET /identity/groups?userId=. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GroupInfoDto(List<Group> groups) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Group(String id, String name) {
  }
}
