package com.samanvay.tasklist.form;

import com.samanvay.tasklist.engine.CamundaClient;
import com.samanvay.tasklist.engine.EngineException;
import com.samanvay.tasklist.engine.dto.FormInfoDto;
import com.samanvay.tasklist.engine.dto.ProcessDefinitionDto;
import com.samanvay.tasklist.engine.dto.TaskDto;
import com.samanvay.tasklist.form.entity.FormBinding;
import com.samanvay.tasklist.form.repo.FormBindingRepository;
import com.samanvay.tasklist.form.repo.FormDefinitionRepository;
import com.samanvay.tasklist.form.repo.FormVersionRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds the form for a task, in this order:
 * <ol>
 *   <li>the BPMN user task's {@code camunda:formKey}: {@code samanvay:deployment:<file>} for a form deployed with
 *       the process, or {@code samanvay:<form_key>} for a form published in the form admin</li>
 *   <li>a {@code form_binding} on the task definition key (and optionally process definition key), highest
 *       priority first</li>
 * </ol>
 * For admin forms the latest published version is returned. No match means the task has no form. Start forms
 * are resolved the same way from the start event's form key.
 */
@Component
public class FormResolver {

  public static final String FORM_KEY_PREFIX = "samanvay:";
  private static final Logger LOG = LoggerFactory.getLogger(FormResolver.class);

  private final CamundaClient engine;
  private final FormDefinitionRepository definitions;
  private final FormVersionRepository versions;
  private final FormBindingRepository bindings;
  private final DeploymentForms deploymentForms;

  public FormResolver(CamundaClient engine, FormDefinitionRepository definitions, FormVersionRepository versions,
      FormBindingRepository bindings, DeploymentForms deploymentForms) {
    this.engine = engine;
    this.definitions = definitions;
    this.versions = versions;
    this.bindings = bindings;
    this.deploymentForms = deploymentForms;
  }

  @Transactional(readOnly = true)
  public Optional<ResolvedForm> resolve(TaskDto task) {
    String bpmnFormKey = formKey(() -> engine.getTaskForm(task.id()));
    if (bpmnFormKey != null && bpmnFormKey.startsWith(FORM_KEY_PREFIX)) {
      return resolveKey(bpmnFormKey.substring(FORM_KEY_PREFIX.length()), task.processDefinitionId(), task.id());
    }

    if (task.taskDefinitionKey() == null) {
      return Optional.empty();
    }
    List<FormBinding> candidates = bindings.findByTaskDefinitionKeyOrderByPriorityDesc(task.taskDefinitionKey());
    if (candidates.isEmpty()) {
      return Optional.empty();
    }
    String processKey = candidates.stream().anyMatch(b -> b.getProcessDefinitionKey() != null)
        ? processDefinitionKey(task)
        : null;
    for (FormBinding binding : candidates) {
      if (binding.getProcessDefinitionKey() == null || Objects.equals(binding.getProcessDefinitionKey(), processKey)) {
        Optional<ResolvedForm> version = versions
            .findFirstByFormDefinitionIdOrderByVersionDesc(binding.getFormDefinition().getId())
            .map(ResolvedForm::of);
        if (version.isPresent()) {
          return version;
        }
      }
    }
    return Optional.empty();
  }

  /** The start event's form, if it declares a {@code samanvay:} form key. */
  @Transactional(readOnly = true)
  public Optional<ResolvedForm> resolveStartForm(ProcessDefinitionDto definition) {
    String key = formKey(() -> engine.getStartForm(definition.id()));
    if (key == null || !key.startsWith(FORM_KEY_PREFIX)) {
      return Optional.empty();
    }
    String formKey = key.substring(FORM_KEY_PREFIX.length());
    if (formKey.startsWith(DeploymentForms.PREFIX)) {
      return deploymentForms.load(definition, formKey.substring(DeploymentForms.PREFIX.length()));
    }
    return publishedVersion(formKey, "start of " + definition.key());
  }

  private Optional<ResolvedForm> resolveKey(String formKey, String processDefinitionId, String taskId) {
    if (formKey.startsWith(DeploymentForms.PREFIX)) {
      ProcessDefinitionDto definition = processDefinitionId == null ? null
          : engine.getProcessDefinition(processDefinitionId);
      return deploymentForms.load(definition, formKey.substring(DeploymentForms.PREFIX.length()));
    }
    return publishedVersion(formKey, "task " + taskId);
  }

  private Optional<ResolvedForm> publishedVersion(String formKey, String usedBy) {
    Optional<ResolvedForm> version = definitions.findByFormKey(formKey)
        .flatMap(d -> versions.findFirstByFormDefinitionIdOrderByVersionDesc(d.getId()))
        .map(ResolvedForm::of);
    if (version.isEmpty()) {
      LOG.warn("Form '{}' is declared for {} but no published version exists", formKey, usedBy);
    }
    return version;
  }

  private static String formKey(Supplier<FormInfoDto> call) {
    try {
      FormInfoDto form = call.get();
      return form == null ? null : form.key();
    } catch (EngineException e) {
      // elements without any form key may be reported as an error by some engine versions
      return null;
    }
  }

  private String processDefinitionKey(TaskDto task) {
    if (task.processDefinitionId() == null) {
      return null;
    }
    var definition = engine.getProcessDefinition(task.processDefinitionId());
    return definition == null ? null : definition.key();
  }
}
