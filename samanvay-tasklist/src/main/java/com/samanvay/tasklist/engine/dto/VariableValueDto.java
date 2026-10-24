package com.samanvay.tasklist.engine.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VariableValueDto(Object value, String type, Map<String, Object> valueInfo) {

  public static VariableValueDto of(Object value, String type) {
    return new VariableValueDto(value, type, null);
  }
}
