package com.samanvay.tasklist.task;

import com.samanvay.tasklist.auth.TasklistUser;
import com.samanvay.tasklist.engine.dto.TaskDto;
import com.samanvay.tasklist.task.TaskDtos.CompleteTaskRequest;
import com.samanvay.tasklist.task.TaskDtos.TaskForm;
import com.samanvay.tasklist.task.TaskDtos.TaskPage;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

  private final TaskService service;

  public TaskController(TaskService service) {
    this.service = service;
  }

  @GetMapping
  public TaskPage list(@AuthenticationPrincipal TasklistUser user,
      @RequestParam(defaultValue = "all") String scope,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "created") String sort,
      @RequestParam(defaultValue = "desc") String order,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    TaskService.Scope parsed;
    try {
      parsed = TaskService.Scope.valueOf(scope.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      parsed = TaskService.Scope.ALL;
    }
    return service.list(user, parsed, q, sort, order, page, size);
  }

  @GetMapping("/{id}")
  public TaskDto get(@PathVariable String id, @AuthenticationPrincipal TasklistUser user) {
    return service.get(id, user);
  }

  @PostMapping("/{id}/claim")
  public TaskDto claim(@PathVariable String id, @AuthenticationPrincipal TasklistUser user) {
    return service.claim(id, user);
  }

  @PostMapping("/{id}/unclaim")
  public TaskDto unclaim(@PathVariable String id, @AuthenticationPrincipal TasklistUser user) {
    return service.unclaim(id, user);
  }

  @GetMapping("/{id}/form")
  public TaskForm form(@PathVariable String id, @AuthenticationPrincipal TasklistUser user) {
    return service.form(id, user);
  }

  @PostMapping("/{id}/complete")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void complete(@PathVariable String id, @RequestBody CompleteTaskRequest request,
      @AuthenticationPrincipal TasklistUser user) {
    service.complete(id, request, user);
  }
}
