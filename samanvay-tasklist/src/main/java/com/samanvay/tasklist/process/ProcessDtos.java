package com.samanvay.tasklist.process;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.samanvay.tasklist.document.DocumentDtos.DocumentOptions;

/** Request and response bodies of the process start API. */
public final class ProcessDtos {

  private ProcessDtos() {
  }

  /** A process the signed-in user may start (latest version). */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record StartableProcess(String key, String name, String description, Integer version) {
  }

  /**
   * The form to fill in to start a process. {@code schema} is null when the process has no start form;
   * {@code documents} says what may be attached when starting.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record StartForm(
      String processKey,
      String processName,
      Long formVersionId,
      String formKey,
      String formName,
      Integer formVersion,
      JsonNode schema,
      JsonNode uiSchema,
      DocumentOptions documents) {
  }

  public record StartRequest(Long formVersionId, JsonNode formData) {
  }

  public record StartedProcess(String processInstanceId, Integer documents) {
  }
}
