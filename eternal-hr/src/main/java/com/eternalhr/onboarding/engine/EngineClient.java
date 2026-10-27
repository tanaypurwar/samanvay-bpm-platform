package com.eternalhr.onboarding.engine;

import com.eternalhr.onboarding.config.EternalHrProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * The calls Eternal HR makes to the engine REST API besides external tasks (those go through the external task
 * client). Uses the same service account.
 */
@Component
public class EngineClient {

  private final RestClient rest;

  public EngineClient(RestClient.Builder builder, EternalHrProperties properties) {
    EternalHrProperties.Engine engine = properties.engine();
    this.rest = builder.baseUrl(engine.baseUrl())
        .defaultHeaders(h -> h.setBasicAuth(engine.username(), engine.password()))
        .build();
  }

  /** A typed engine variable, as the REST API expects it. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Variable(Object value, String type) {

    public static Variable string(String value) {
      return new Variable(value, "String");
    }

    public static Variable date(Date value) {
      // the REST API's default date format
      return new Variable(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(value), "Date");
    }

    public static Variable number(Double value) {
      return new Variable(value, "Double");
    }
  }

  // deployments

  /**
   * Deploys the resources as one deployment. If nothing changed since the last deployment with this name, the
   * engine skips it; if anything changed, everything is deployed again so forms always sit next to their BPMN.
   *
   * @return the new deployment's id, or empty when nothing changed
   */
  public Optional<String> deploy(String name, Map<String, byte[]> resources) {
    MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
    parts.add("deployment-name", name);
    parts.add("deployment-source", name);
    parts.add("enable-duplicate-filtering", "true");
    parts.add("deploy-changed-only", "false");
    resources.forEach((resourceName, bytes) -> parts.add(resourceName, new ByteArrayResource(bytes) {
      @Override
      public String getFilename() {
        return resourceName;
      }
    }));
    JsonNode result = rest.post().uri("/deployment/create")
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(parts)
        .retrieve()
        .body(JsonNode.class);
    boolean deployedSomething = result != null && result.path("deployedProcessDefinitions").size() > 0;
    return deployedSomething ? Optional.of(result.path("id").asText()) : Optional.empty();
  }

  // process instances

  /** Variables of a running instance, or empty if it has ended or never existed. */
  public Optional<Map<String, Variable>> getProcessVariables(String processInstanceId) {
    try {
      return Optional.ofNullable(rest.get()
          .uri(u -> u.path("/process-instance/{id}/variables").queryParam("deserializeValues", false)
              .build(processInstanceId))
          .retrieve()
          .body(new ParameterizedTypeReference<Map<String, Variable>>() { }));
    } catch (HttpClientErrorException.NotFound e) {
      return Optional.empty();
    }
  }

  /**
   * Delivers a message to one process instance.
   *
   * @return false if the instance is not waiting for this message (anymore)
   */
  public boolean correlate(String messageName, String processInstanceId, Map<String, Variable> variables) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("messageName", messageName);
    body.put("processInstanceId", processInstanceId);
    body.put("processVariables", variables);
    try {
      rest.post().uri("/message").contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
      return true;
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode() == HttpStatus.BAD_REQUEST || e.getStatusCode() == HttpStatus.NOT_FOUND) {
        return false;
      }
      throw e;
    }
  }

  // identity

  public Optional<String> userEmail(String userId) {
    if (userId == null || userId.isBlank()) {
      return Optional.empty();
    }
    try {
      JsonNode profile = rest.get().uri("/user/{id}/profile", userId).retrieve().body(JsonNode.class);
      String email = profile == null ? null : profile.path("email").asText(null);
      return email == null || email.isBlank() ? Optional.empty() : Optional.of(email);
    } catch (HttpClientErrorException.NotFound e) {
      return Optional.empty();
    }
  }

  /** E-mail addresses of a group's members, e.g. everyone in HR. */
  public List<String> groupMemberEmails(String groupId) {
    List<JsonNode> users = rest.get().uri(u -> u.path("/user").queryParam("memberOfGroup", groupId).build())
        .retrieve().body(new ParameterizedTypeReference<List<JsonNode>>() { });
    return users == null ? List.of() : users.stream()
        .map(u -> u.path("email").asText(""))
        .filter(e -> !e.isBlank())
        .toList();
  }

  public boolean groupExists(String groupId) {
    return exists("/group/{id}", groupId);
  }

  public void createGroup(String groupId, String name) {
    rest.post().uri("/group/create").contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("id", groupId, "name", name, "type", "WORKFLOW"))
        .retrieve().toBodilessEntity();
  }

  public boolean userExists(String userId) {
    return exists("/user/{id}/profile", userId);
  }

  public void createUser(String userId, String firstName, String lastName, String email, String password) {
    rest.post().uri("/user/create").contentType(MediaType.APPLICATION_JSON)
        .body(Map.of(
            "profile", Map.of("id", userId, "firstName", firstName, "lastName", lastName, "email", email),
            "credentials", Map.of("password", password)))
        .retrieve().toBodilessEntity();
  }

  public void addMember(String groupId, String userId) {
    rest.put().uri("/group/{group}/members/{user}", groupId, userId).retrieve().toBodilessEntity();
  }

  private boolean exists(String path, String id) {
    try {
      rest.get().uri(path, id).retrieve().toBodilessEntity();
      return true;
    } catch (HttpClientErrorException.NotFound e) {
      return false;
    }
  }
}
