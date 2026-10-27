package com.samanvay.tasklist.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.samanvay.tasklist.auth.TasklistUser;
import com.samanvay.tasklist.common.ApiException;
import com.samanvay.tasklist.engine.CamundaClient;
import com.samanvay.tasklist.engine.dto.TaskDto;
import com.samanvay.tasklist.engine.dto.VariableValueDto;
import com.samanvay.tasklist.form.FormResolver;
import com.samanvay.tasklist.form.FormSubmissions;
import com.samanvay.tasklist.form.ResolvedForm;
import com.samanvay.tasklist.form.VariableMapper;
import com.samanvay.tasklist.form.repo.FormVersionRepository;
import com.samanvay.tasklist.task.TaskDtos.CompleteTaskRequest;
import com.samanvay.tasklist.task.TaskDtos.TaskForm;
import com.samanvay.tasklist.task.TaskDtos.TaskPage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
  private final FormSubmissions submissions;
  private final VariableMapper variableMapper;

  public TaskService(CamundaClient engine, TaskAccessGuard guard, FormResolver formResolver,
      FormVersionRepository formVersions, FormSubmissions submissions, VariableMapper variableMapper) {
    this.engine = engine;
    this.guard = guard;
    this.formResolver = formResolver;
    this.formVersions = formVersions;
    this.submissions = submissions;
    this.variableMapper = variableMapper;
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
    Optional<ResolvedForm> resolved = formResolver.resolve(task);
    if (resolved.isEmpty()) {
      return new TaskForm(null, null, null, null, null, null, null, variableMapper.toDisplayValues(variables));
    }
    ResolvedForm form = resolved.get();
    JsonNode schema = submissions.read(form.schemaJson());
    return new TaskForm(form.versionId(), form.formKey(), form.name(), form.version(), schema,
        submissions.read(form.uiSchemaJson()), variableMapper.toFormData(schema, variables), null);
  }

  public void complete(String taskId, CompleteTaskRequest request, TasklistUser user) {
    TaskDto task = guard.requireAssignedTo(taskId, user);
    JsonNode formData = submissions.objectOrEmpty(request.formData());

    Optional<ResolvedForm> resolved = formResolver.resolve(task);
    if (resolved.isEmpty()) {
      if (!formData.isEmpty()) {
        throw ApiException.badRequest("NO_FORM", "This task has no form; it cannot accept form data.");
      }
      engine.complete(taskId, Map.of());
      return;
    }

    ResolvedForm form = resolved.get().fromDeployment()
        ? resolved.get()
        : versionFor(resolved.get(), request.formVersionId());
    engine.complete(taskId, submissions.toVariables(form, formData));
  }

  /**
   * The user may have opened the form before a newer version was published. Accept the version they filled
   * in as long as it belongs to the same form, so work in progress is not lost.
   */
  private ResolvedForm versionFor(ResolvedForm current, Long requestedVersionId) {
    if (requestedVersionId == null || requestedVersionId.equals(current.versionId())) {
      return current;
    }
    return formVersions.findById(requestedVersionId)
        .filter(v -> v.getFormDefinition().getFormKey().equals(current.formKey()))
        .map(ResolvedForm::of)
        .orElseThrow(() -> ApiException.conflict("FORM_CHANGED", "The form for this task has changed. Reload it."));
  }
}
