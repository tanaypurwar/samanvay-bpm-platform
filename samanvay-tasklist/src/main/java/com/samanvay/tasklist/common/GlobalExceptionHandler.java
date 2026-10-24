package com.samanvay.tasklist.common;

import com.samanvay.tasklist.engine.EngineException;
import com.samanvay.tasklist.engine.EngineUnavailableException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns failures into safe {@link ApiError} bodies; technical detail goes to the log only. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiError> handleApi(ApiException e) {
    return body(e.getStatus(), e.getCode(), e.getMessage(), e.getFieldErrors());
  }

  @ExceptionHandler(EngineException.class)
  ResponseEntity<ApiError> handleEngine(EngineException e) {
    LOG.warn("Engine error status={} type={} message={}", e.getStatus(), e.getType(), e.getMessage());
    String type = e.getType() == null ? "" : e.getType();
    if (e.getStatus() == 404) {
      return body(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested item no longer exists.", List.of());
    }
    if (type.contains("AlreadyClaimed")) {
      return body(HttpStatus.CONFLICT, "TASK_ALREADY_CLAIMED", "Someone else has already claimed this task.", List.of());
    }
    if (type.contains("OptimisticLocking")) {
      return body(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
          "The task was changed by someone else. Reload and try again.", List.of());
    }
    if (e.getStatus() == 403) {
      return body(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not allowed to perform this action.", List.of());
    }
    if (e.getStatus() == 400) {
      return body(HttpStatus.BAD_REQUEST, "ENGINE_REJECTED", "The workflow engine rejected the request.", List.of());
    }
    return body(HttpStatus.BAD_GATEWAY, "ENGINE_ERROR", "The workflow engine could not process the request.", List.of());
  }

  @ExceptionHandler(EngineUnavailableException.class)
  ResponseEntity<ApiError> handleUnavailable(EngineUnavailableException e) {
    LOG.error("Engine unavailable", e);
    return body(HttpStatus.SERVICE_UNAVAILABLE, "ENGINE_UNAVAILABLE",
        "The workflow engine is currently unavailable. Please try again shortly.", List.of());
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
  ResponseEntity<ApiError> handleBadRequest(Exception e) {
    return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "The request is invalid.", List.of());
  }

  private ResponseEntity<ApiError> body(HttpStatus status, String code, String message,
      List<ApiError.FieldError> fields) {
    return ResponseEntity.status(status).body(new ApiError(code, message, MDC.get(CorrelationIdFilter.MDC_KEY), fields));
  }
}
