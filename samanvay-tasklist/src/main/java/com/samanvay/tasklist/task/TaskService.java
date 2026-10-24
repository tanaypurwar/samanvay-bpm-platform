package com.samanvay.tasklist.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samanvay.tasklist.auth.TasklistUser;
import com.samanvay.tasklist.common.ApiError;
import com.samanvay.tasklist.common.ApiException;
import com.samanvay.tasklist.engine.CamundaClient;
import com.samanvay.tasklist.engine.dto.TaskDto;
import com.samanvay.tasklist.engine.dto.VariableValueDto;
import com.samanvay.tasklist.form.FormResolver;
import com.samanvay.tasklist.form.FormSchemaValidator;
import com.samanvay.tasklist.form.VariableMapper;
import com.samanvay.tasklist.form.entity.FormVersion;
import com.samanvay.tasklist.form.repo.FormVersionRepository;
import com.samanvay.tasklist.task.TaskDtos.CompleteTaskRequest;
import com.samanvay.tasklist.task.TaskDtos.TaskForm;
import com.samanvay.tasklist.task.TaskDtos.TaskPage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TaskService {

  public enum Scope { MINE, GROUP, ALL }

  static final Set<String> SORTABLE = Set.of("created", "dueDate", "followUpDate", "priority", "name");
  static final int MAX_PAGE_SIZE = 100;

  private final CamundaClient engine;
  private final TaskAccessGuard guard;
  private final FormResolver formResolver;
  private final FormVersionRepository formVersions;
  private final FormSchemaValidator validator;
  private final VariableMapper variableMapper;
  private final ObjectMapper objectMapper;

  public TaskService(CamundaClient engine, TaskAccessGuard guard, FormResolver formResolver,
      FormVersionRepository formVersions, FormSchemaValidator validator, VariableMapper variableMapper,
      ObjectMapper objectMapper) {
    this.engine = engine;
    this.guard = guard;
    this.formResolver = formResolver;
    this.formVersions = formVersions;
    this.validator = validator;
    this.variableMapper = variableMapper;
    this.objectMapper = objectMapper;
  }

  public TaskPage list(TasklistUser user, Scope scope, String search, String sortBy, String sortOrder, int page,
      int size) {
    int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    int pageIndex = Math.max(0, page);

    Map<String, Object> query = switch (scope) {
      case MINE -> new HashMap<>(Map.of("assignee", user.id()));
      // unassigned tasks the user may claim, directly or through a group
      case GROUP -> new HashMap<>(Map.of("candidateUser", user.id()));
      case ALL -> guard.visibleTo(user);
    };
    if (search != null && !search.isBlank()) {
      query.put("nameLike", "%" + search.trim() + "%");
    }
    String sortField = SORTABLE.contains(sortBy) ? sortBy : "created";
    String order = "asc".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
    query.put("sorting", List.of(Map.of("sortBy", sortField, "sortOrder", order)));

    List<TaskDto> items = engine.queryTasks(query, pageIndex * pageSize, pageSize);
    Map<String, Object> countQuery = new HashMap<>(query);
    countQuery.remove("sorting");
    long total = engine.countTasks(countQuery);
    return new TaskPage(items == null ? List.of() : items, total, pageIndex, pageSize);
  }

  public TaskDto get(String taskId, TasklistUser user) {
    return guard.requireVisible(taskId, user);
  }

  public TaskDto claim(String taskId, TasklistUser user) {
    TaskDto task = guard.requireVisible(taskId, user);
    if (task.assignee() != null) {
      throw user.id().equals(task.assignee())
          ? ApiException.conflict("ALREADY_ASSIGNED_TO_YOU", "You already have this task.")
          : ApiException.conflict("TASK_ALREADY_CLAIMED", "Someone else has already claimed this task.");
    }
    engine.claim(taskId, user.id());
    return engine.getTask(taskId);
  }

  public TaskDto unclaim(String taskId, TasklistUser user) {
    guard.requireAssignedTo(taskId, user);
    engine.unclaim(taskId);
    return engine.getTask(taskId);
  }

  public TaskForm form(String taskId, TasklistUser user) {
    TaskDto task = guard.requireVisible(taskId, user);
    Map<String, VariableValueDto> variables = engine.getTaskVariables(taskId);
    Optional<FormVersion> resolved = formResolver.resolve(task);
    if (resolved.isEmpty()) {
      return new TaskForm(null, null, null, null, null, null, null, variableMapper.toDisplayValues(variables));
    }
    FormVersion version = resolved.get();
    JsonNode schema = read(version.getSchemaJson());
    return new TaskForm(version.getId(), version.getFormDefinition().getFormKey(),
        version.getFormDefinition().getName(), version.getVersion(), schema, read(version.getUiSchemaJson()),
        variableMapper.toFormData(schema, variables), null);
  }

  public void complete(String taskId, CompleteTaskRequest request, TasklistUser user) {
    TaskDto task = guard.requireAssignedTo(taskId, user);
    JsonNode formData = request.formData() == null ? objectMapper.createObjectNode() : request.formData();
    if (!formData.isObject()) {
      throw ApiException.badRequest("BAD_REQUEST", "Form data must be a JSON object.");
    }

    Optional<FormVersion> resolved = formResolver.resolve(task);
    if (resolved.isEmpty()) {
      if (!formData.isEmpty()) {
        throw ApiException.badRequest("NO_FORM", "This task has no form; it cannot accept form data.");
      }
      engine.complete(taskId, Map.of());
      return;
    }

    FormVersion version = versionFor(resolved.get(), request.formVersionId());
    List<ApiError.FieldError> errors = validator.validate(version.getSchemaJson(), formData);
    if (!errors.isEmpty()) {
      throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED",
          "Please correct the highlighted fields.", errors);
    }
    engine.complete(taskId, variableMapper.toVariables(read(version.getSchemaJson()), formData));
  }

  /**
   * The user may have opened the form before a newer version was published. Accept the version they filled
   * in as long as it belongs to the same form, so work in progress is not lost.
   */
  private FormVersion versionFor(FormVersion current, Long requestedVersionId) {
    if (requestedVersionId == null || requestedVersionId.equals(current.getId())) {
      return current;
    }
    return formVersions.findById(requestedVersionId)
        .filter(v -> v.getFormDefinition().getId().equals(current.getFormDefinition().getId()))
        .orElseThrow(() -> ApiException.conflict("FORM_CHANGED", "The form for this task has changed. Reload it."));
  }

  private JsonNode read(String json) {
    if (json == null) {
      return null;
    }
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Stored form JSON is invalid", e);
    }
  }
}
