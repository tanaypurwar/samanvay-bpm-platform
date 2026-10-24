package com.samanvay.tasklist.common;

import java.util.List;
import org.springframework.http.HttpStatus;

/** An error the tasklist raises deliberately, with a message that is safe to show to users. */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;
  private final List<ApiError.FieldError> fieldErrors;

  public ApiException(HttpStatus status, String code, String message) {
    this(status, code, message, List.of());
  }

  public ApiException(HttpStatus status, String code, String message, List<ApiError.FieldError> fieldErrors) {
    super(message);
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }

  public static ApiException notFound(String message) {
    return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
  }

  public static ApiException conflict(String code, String message) {
    return new ApiException(HttpStatus.CONFLICT, code, message);
  }

  public static ApiException badRequest(String code, String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, code, message);
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }

  public List<ApiError.FieldError> getFieldErrors() {
    return fieldErrors;
  }
}
