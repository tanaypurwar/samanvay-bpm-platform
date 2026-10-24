package com.samanvay.tasklist.task;

import com.samanvay.tasklist.auth.TasklistUser;
import com.samanvay.tasklist.common.ApiException;
import com.samanvay.tasklist.engine.CamundaClient;
import com.samanvay.tasklist.engine.dto.TaskDto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The tasklist calls the engine with a service account, so it must enforce task visibility itself. A user may
 * see a task that is assigned to them, or that is unassigned and lists them as a candidate (directly or through
 * one of their groups). User identity always comes from the session, never from the request.
 */
@Component
public class TaskAccessGuard {

  private final CamundaClient engine;

  public TaskAccessGuard(CamundaClient engine) {
    this.engine = engine;
  }

  /** Engine query criteria matching every task the user may see. */
  public Map<String, Object> visibleTo(TasklistUser user) {
    Map<String, Object> or = new HashMap<>();
    or.put("assignee", user.id());
    or.put("candidateUser", user.id());
    Map<String, Object> query = new HashMap<>();
    query.put("orQueries", List.of(or));
    return query;
  }

  /** Loads the task if the user may see it; otherwise reports it as not found so ids cannot be probed. */
  public TaskDto requireVisible(String taskId, TasklistUser user) {
    Map<String, Object> query = visibleTo(user);
    query.put("taskId", taskId);
    List<TaskDto> tasks = engine.queryTasks(query, 0, 1);
    if (tasks == null || tasks.isEmpty()) {
      throw ApiException.notFound("Task not found.");
    }
    return tasks.get(0);
  }

  public TaskDto requireAssignedTo(String taskId, TasklistUser user) {
    TaskDto task = requireVisible(taskId, user);
    if (!user.id().equals(task.assignee())) {
      throw ApiException.conflict("TASK_NOT_ASSIGNED_TO_YOU", "Claim this task before working on it.");
    }
    return task;
  }
}
