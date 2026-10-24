package com.samanvay.tasklist.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.samanvay.tasklist.engine.dto.TaskDto;
import java.util.List;
import java.util.Map;

/** Request and response bodies of the task API. */
public final class TaskDtos {

  private TaskDtos() {
  }

  public record TaskPage(List<TaskDto> items, long total, int page, int size) {
  }

  /**
   * The form to render for a task. When the task has no configured form, {@code schema} is null and
   * {@code variables} carries read-only values to display instead.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record TaskForm(
      Long formVersionId,
      String formKey,
      String formName,
      Integer formVersion,
      JsonNode schema,
      JsonNode uiSchema,
      Map<String, Object> formData,
      Map<String, Object> variables) {
  }

  public record CompleteTaskRequest(Long formVersionId, JsonNode formData) {
  }
}
