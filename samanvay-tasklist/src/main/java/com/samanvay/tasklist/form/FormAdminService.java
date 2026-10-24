package com.samanvay.tasklist.form;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samanvay.tasklist.common.ApiException;
import com.samanvay.tasklist.common.CorrelationIdFilter;
import com.samanvay.tasklist.form.FormAdminDtos.AuditView;
import com.samanvay.tasklist.form.FormAdminDtos.BindingRequest;
import com.samanvay.tasklist.form.FormAdminDtos.BindingView;
import com.samanvay.tasklist.form.FormAdminDtos.CreateFormRequest;
import com.samanvay.tasklist.form.FormAdminDtos.FormDetail;
import com.samanvay.tasklist.form.FormAdminDtos.FormSummary;
import com.samanvay.tasklist.form.FormAdminDtos.UpdateDraftRequest;
import com.samanvay.tasklist.form.FormAdminDtos.VersionDetail;
import com.samanvay.tasklist.form.FormAdminDtos.VersionSummary;
import com.samanvay.tasklist.form.entity.FormAuditEvent;
import com.samanvay.tasklist.form.entity.FormBinding;
import com.samanvay.tasklist.form.entity.FormDefinition;
import com.samanvay.tasklist.form.entity.FormVersion;
import com.samanvay.tasklist.form.repo.FormAuditEventRepository;
import com.samanvay.tasklist.form.repo.FormBindingRepository;
import com.samanvay.tasklist.form.repo.FormDefinitionRepository;
import com.samanvay.tasklist.form.repo.FormVersionRepository;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Create, edit, publish and bind form configurations. Every change is audited. */
@Service
@Transactional
public class FormAdminService {

  private final FormDefinitionRepository definitions;
  private final FormVersionRepository versions;
  private final FormBindingRepository bindings;
  private final FormAuditEventRepository audit;
  private final FormSchemaValidator validator;
  private final ObjectMapper objectMapper;

  public FormAdminService(FormDefinitionRepository definitions, FormVersionRepository versions,
      FormBindingRepository bindings, FormAuditEventRepository audit, FormSchemaValidator validator,
      ObjectMapper objectMapper) {
    this.definitions = definitions;
    this.versions = versions;
    this.bindings = bindings;
    this.audit = audit;
    this.validator = validator;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public List<FormSummary> list() {
    return definitions.findAllByOrderByNameAsc().stream()
        .map(d -> new FormSummary(d.getId(), d.getFormKey(), d.getName(), d.getDescription(), d.getStatus().name(),
            versions.findFirstByFormDefinitionIdOrderByVersionDesc(d.getId()).map(FormVersion::getVersion).orElse(null),
            d.getUpdatedAt()))
        .toList();
  }

  @Transactional(readOnly = true)
  public FormDetail get(Long id) {
    return detail(find(id));
  }

  public FormDetail create(CreateFormRequest request, String actor) {
    if (definitions.existsByFormKey(request.formKey())) {
      throw ApiException.conflict("FORM_KEY_EXISTS", "A form with key '" + request.formKey() + "' already exists.");
    }
    FormDefinition definition = new FormDefinition(request.formKey(), request.name(), request.description());
    definition.updateDraft(request.name(), request.description(), write(request.schema()), write(request.uiSchema()));
    definitions.save(definition);
    record(definition.getId(), null, "CREATED", actor);
    return detail(definition);
  }

  public FormDetail updateDraft(Long id, UpdateDraftRequest request, String actor) {
    FormDefinition definition = find(id);
    definition.updateDraft(request.name(), request.description(), write(request.schema()), write(request.uiSchema()));
    record(id, null, "DRAFT_UPDATED", actor);
    return detail(definition);
  }

  /** Freezes the current draft as a new immutable version; tasks pick it up immediately. */
  public FormDetail publish(Long id, String actor) {
    FormDefinition definition = find(id);
    if (definition.getDraftSchemaJson() == null) {
      throw ApiException.badRequest("NO_DRAFT", "Add a schema to the draft before publishing.");
    }
    validator.assertUsable(definition.getDraftSchemaJson());
    int next = versions.findFirstByFormDefinitionIdOrderByVersionDesc(id).map(v -> v.getVersion() + 1).orElse(1);
    FormVersion version = versions.save(new FormVersion(definition, next, definition.getDraftSchemaJson(),
        definition.getDraftUiSchemaJson(), actor));
    definition.markPublished();
    record(id, version.getId(), "PUBLISHED", actor);
    return detail(definition);
  }

  @Transactional(readOnly = true)
  public VersionDetail getVersion(Long id, Long versionId) {
    FormVersion version = versions.findById(versionId)
        .filter(v -> v.getFormDefinition().getId().equals(id))
        .orElseThrow(() -> ApiException.notFound("Form version not found."));
    return new VersionDetail(version.getId(), version.getVersion(), read(version.getSchemaJson()),
        read(version.getUiSchemaJson()), version.getPublishedAt(), version.getPublishedBy());
  }

  public BindingView addBinding(Long id, BindingRequest request, String actor) {
    FormDefinition definition = find(id);
    String processKey = request.processDefinitionKey() == null || request.processDefinitionKey().isBlank()
        ? null
        : request.processDefinitionKey().trim();
    FormBinding binding = bindings.save(
        new FormBinding(definition, request.taskDefinitionKey().trim(), processKey, request.priority()));
    record(id, null, "BINDING_ADDED", actor);
    return toView(binding);
  }

  public void removeBinding(Long id, Long bindingId, String actor) {
    FormBinding binding = bindings.findById(bindingId)
        .filter(b -> b.getFormDefinition().getId().equals(id))
        .orElseThrow(() -> ApiException.notFound("Binding not found."));
    bindings.delete(binding);
    record(id, null, "BINDING_REMOVED", actor);
  }

  @Transactional(readOnly = true)
  public List<AuditView> auditTrail(Long id) {
    find(id);
    return audit.findByFormDefinitionIdOrderByOccurredAtDesc(id).stream()
        .map(e -> new AuditView(e.getId(), e.getFormVersionId(), e.getAction(), e.getActor(), e.getOccurredAt(),
            e.getCorrelationId()))
        .toList();
  }

  private FormDefinition find(Long id) {
    return definitions.findById(id).orElseThrow(() -> ApiException.notFound("Form not found."));
  }

  private FormDetail detail(FormDefinition d) {
    List<VersionSummary> versionList = d.getId() == null ? List.of()
        : versions.findByFormDefinitionIdOrderByVersionDesc(d.getId()).stream()
            .map(v -> new VersionSummary(v.getId(), v.getVersion(), v.getPublishedAt(), v.getPublishedBy()))
            .toList();
    List<BindingView> bindingList = d.getId() == null ? List.of()
        : bindings.findByFormDefinitionIdOrderByPriorityDesc(d.getId()).stream().map(this::toView).toList();
    return new FormDetail(d.getId(), d.getFormKey(), d.getName(), d.getDescription(), d.getStatus().name(),
        read(d.getDraftSchemaJson()), read(d.getDraftUiSchemaJson()), versionList, bindingList);
  }

  private BindingView toView(FormBinding b) {
    return new BindingView(b.getId(), b.getTaskDefinitionKey(), b.getProcessDefinitionKey(), b.getPriority());
  }

  private void record(Long definitionId, Long versionId, String action, String actor) {
    audit.save(new FormAuditEvent(definitionId, versionId, action, actor, MDC.get(CorrelationIdFilter.MDC_KEY)));
  }

  private String write(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(node);
    } catch (JsonProcessingException e) {
      throw ApiException.badRequest("INVALID_JSON", "Invalid JSON.");
    }
  }

  private JsonNode read(String json) {
    if (json == null) {
      return null;
    }
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException e) {
      return null;
    }
  }
}
