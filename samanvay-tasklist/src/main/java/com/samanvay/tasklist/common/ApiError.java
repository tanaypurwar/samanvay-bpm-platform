package com.samanvay.tasklist.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Error body returned to the UI: safe message, stable code, correlation id for support. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String code, String message, String correlationId, List<FieldError> fieldErrors) {

  public record FieldError(String field, String message) {
  }
}
