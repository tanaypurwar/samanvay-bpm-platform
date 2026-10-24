package com.samanvay.tasklist.form;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

/** Request and response bodies of the form administration API. */
public final class FormAdminDtos {

  private FormAdminDtos() {
  }

  public record FormSummary(Long id, String formKey, String name, String description, String status,
      Integer latestVersion, OffsetDateTime updatedAt) {
  }

  public record FormDetail(Long id, String formKey, String name, String description, String status,
      JsonNode draftSchema, JsonNode draftUiSchema, List<VersionSummary> versions, List<BindingView> bindings) {
  }

  public record VersionSummary(Long id, int version, OffsetDateTime publishedAt, String publishedBy) {
  }

  public record VersionDetail(Long id, int version, JsonNode schema, JsonNode uiSchema, OffsetDateTime publishedAt,
      String publishedBy) {
  }

  public record BindingView(Long id, String taskDefinitionKey, String processDefinitionKey, int priority) {
  }

  public record AuditView(Long id, Long formVersionId, String action, String actor, OffsetDateTime occurredAt,
      String correlationId) {
  }

  public record CreateFormRequest(
      @NotBlank @Size(max = 255) @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9_.-]*",
          message = "may only contain letters, digits, '.', '_' and '-'") String formKey,
      @NotBlank @Size(max = 255) String name,
      @Size(max = 2000) String description,
      JsonNode schema,
      JsonNode uiSchema) {
  }

  public record UpdateDraftRequest(
      @NotBlank @Size(max = 255) String name,
      @Size(max = 2000) String description,
      JsonNode schema,
      JsonNode uiSchema) {
  }

  public record BindingRequest(
      @NotBlank @Size(max = 255) String taskDefinitionKey,
      @Size(max = 255) String processDefinitionKey,
      int priority) {
  }
}
