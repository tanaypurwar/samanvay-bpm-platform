package com.samanvay.tasklist.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samanvay.tasklist.engine.dto.AuthenticationResultDto;
import com.samanvay.tasklist.engine.dto.CountDto;
import com.samanvay.tasklist.engine.dto.FormInfoDto;
import com.samanvay.tasklist.engine.dto.GroupInfoDto;
import com.samanvay.tasklist.engine.dto.ProcessDefinitionDto;
import com.samanvay.tasklist.engine.dto.TaskDto;
import com.samanvay.tasklist.engine.dto.UserProfileDto;
import com.samanvay.tasklist.engine.dto.VariableValueDto;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Typed access to the Camunda 7 REST API of the Samanvay BPM engine. All engine calls go through this
 * class so API changes in the fork are absorbed in one place.
 */
@Component
public class CamundaClient {

  private static final Logger LOG = LoggerFactory.getLogger(CamundaClient.class);
  private static final int READ_ATTEMPTS = 2;

  private final RestClient rest;
  private final ObjectMapper objectMapper;

  public CamundaClient(RestClient engineRestClient, ObjectMapper objectMapper) {
    this.rest = engineRestClient;
    this.objectMapper = objectMapper;
  }

  // identity

  public AuthenticationResultDto verifyCredentials(String username, String password) {
    return write(() -> rest.post().uri("/identity/verify")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("username", username, "password", password))
        .retrieve().onStatus(HttpStatusCode::isError, this::raise)
        .body(AuthenticationResultDto.class));
  }

  public GroupInfoDto getGroups(String userId) {
    return read(() -> rest.get().uri(u -> u.path("/identity/groups").queryParam("userId", userId).build())
        .retrieve().onStatus(HttpStatusCode::isError, this::raise)
        .body(GroupInfoDto.class));
  }

  public UserProfileDto getUserProfile(String userId) {
    return read(() -> rest.get().uri("/user/{id}/profile", userId)
        .retrieve().onStatus(HttpStatusCode::isError, this::raise)
        .body(UserProfileDto.class));
  }

  // tasks

  /** POST /task, the engine's JSON task query. Read-only, so safe to retry. */
  public List<TaskDto> queryTasks(Map<String, Object> query, int firstResult, int maxResults) {
    return read(() -> rest.post()
        .uri(u -> u.path("/task").queryParam("firstResult", firstResult).queryParam("maxResults", maxResults).build())
        .contentType(MediaType.APPLICATION_JSON)
        .body(query)
        .retrieve().onStatus(HttpStatusCode::isError, this::raise)
        .body(new ParameterizedTypeReference<List<TaskDto>>() { }));
  }

  public long countTasks(Map<String, Object> query) {
    CountDto count = read(() -> rest.post().uri("/task/count")
        .contentType(MediaType.APPLICATION_JSON)
        .body(query)
        .retrieve().onStatus(HttpStatusCode::isError, this::raise)
        .body(CountDto.class));
    return count == null ? 0 : count.count();
  }

  public TaskDto getTask(String taskId) {
    return read(() -> rest.get().uri("/task/{id}", taskId)
        .retrieve().onStatus(HttpStatusCode::isError, this::raise)
        .body(TaskDto.class));
  }

  public FormInfoDto getTaskForm(String taskId) {
    return read(() -> rest.get().uri("/task/{id}/form", taskId)
        .retrieve().onStatus(HttpStatusCode::isError, this::raise)
        .body(FormInfoDto.class));
  }

  /** Variables visible from the task scope, with Json/Object values left serialized. */
  public Map<String, VariableValueDto> getTaskVariables(String taskId) {
    return read(() -> rest.get()
        .uri(u -> u.path("/task/{id}/variables").queryParam("deserializeValues", false).build(taskId))
        .retrieve().onStatus(HttpStatusCode::isError, this::raise)
        .body(new ParameterizedTypeReference<Map<String, VariableValueDto>>() { }));
  }

  public void claim(String taskId, String userId) {
    write(() -> rest.post().uri("/task/{id}/claim", taskId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("userId", userId))
        .retrieve().onStatus(HttpStatusCode::isError, this::raise)
        .toBodilessEntity());
  }

  public void unclaim(String taskId) {
    write(() -> rest.post().uri("/task/{id}/unclaim", taskId)
        .retrieve().onStatus(HttpStatusCode::isError, this::raise)
        .toBodilessEntity());
  }

  /** Completes the task. Never retried: a timeout does not prove the engine did not complete it. */
  public void complete(String taskId, Map<String, VariableValueDto> variables) {
    write(() -> rest.post().uri("/task/{id}/complete", taskId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("variables", variables))
        .retrieve().onStatus(HttpStatusCode::isError, this::raise)
        .toBodilessEntity());
  }

  // process definitions

  public ProcessDefinitionDto getProcessDefinition(String processDefinitionId) {
    return read(() -> rest.get().uri("/process-definition/{id}", processDefinitionId)
        .retrieve().onStatus(HttpStatusCode::isError, this::raise)
        .body(ProcessDefinitionDto.class));
  }

  // plumbing

  private <T> T read(Supplier<T> call) {
    ResourceAccessException last = null;
    for (int attempt = 1; attempt <= READ_ATTEMPTS; attempt++) {
      try {
        return call.get();
      } catch (ResourceAccessException e) {
        last = e;
        LOG.warn("Engine read failed (attempt {}/{}): {}", attempt, READ_ATTEMPTS, e.getMessage());
      }
    }
    throw new EngineUnavailableException("Engine is unavailable", last);
  }

  private <T> T write(Supplier<T> call) {
    try {
      return call.get();
    } catch (ResourceAccessException e) {
      throw new EngineUnavailableException("Engine is unavailable", e);
    }
  }

  private void raise(HttpRequest request, ClientHttpResponse response) throws IOException {
    String type = null;
    String message = response.getStatusText();
    try {
      JsonNode body = objectMapper.readTree(response.getBody());
      if (body != null) {
        type = body.path("type").asText(null);
        message = body.path("message").asText(message);
      }
    } catch (IOException ignored) {
      // non-JSON error body; keep the status text
    }
    throw new EngineException(response.getStatusCode().value(), type, message);
  }
}
