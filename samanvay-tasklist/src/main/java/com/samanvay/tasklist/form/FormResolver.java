package com.samanvay.tasklist.form;

import com.samanvay.tasklist.engine.CamundaClient;
import com.samanvay.tasklist.engine.EngineException;
import com.samanvay.tasklist.engine.dto.FormInfoDto;
import com.samanvay.tasklist.engine.dto.TaskDto;
import com.samanvay.tasklist.form.entity.FormBinding;
import com.samanvay.tasklist.form.entity.FormVersion;
import com.samanvay.tasklist.form.repo.FormBindingRepository;
import com.samanvay.tasklist.form.repo.FormDefinitionRepository;
import com.samanvay.tasklist.form.repo.FormVersionRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds the form for a task, in this order:
 * <ol>
 *   <li>the BPMN user task's {@code camunda:formKey="samanvay:<form_key>"}</li>
 *   <li>a {@code form_binding} on the task definition key (and optionally process definition key), highest
 *       priority first</li>
 * </ol>
 * The latest published version of the matched form is returned. No match means the task has no form.
 */
@Component
public class FormResolver {

  public static final String FORM_KEY_PREFIX = "samanvay:";
  private static final Logger LOG = LoggerFactory.getLogger(FormResolver.class);

  private final CamundaClient engine;
  private final FormDefinitionRepository definitions;
  private final FormVersionRepository versions;
  private final FormBindingRepository bindings;

  public FormResolver(CamundaClient engine, FormDefinitionRepository definitions, FormVersionRepository versions,
      FormBindingRepository bindings) {
    this.engine = engine;
    this.definitions = definitions;
    this.versions = versions;
    this.bindings = bindings;
  }

  @Transactional(readOnly = true)
  public Optional<FormVersion> resolve(TaskDto task) {
    String bpmnFormKey = bpmnFormKey(task.id());
    if (bpmnFormKey != null && bpmnFormKey.startsWith(FORM_KEY_PREFIX)) {
      String formKey = bpmnFormKey.substring(FORM_KEY_PREFIX.length());
      Optional<FormVersion> version = definitions.findByFormKey(formKey)
          .flatMap(d -> versions.findFirstByFormDefinitionIdOrderByVersionDesc(d.getId()));
      if (version.isEmpty()) {
        LOG.warn("Task {} declares form '{}' but no published version exists", task.id(), formKey);
      }
      return version;
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
        Optional<FormVersion> version =
            versions.findFirstByFormDefinitionIdOrderByVersionDesc(binding.getFormDefinition().getId());
        if (version.isPresent()) {
          return version;
        }
      }
    }
    return Optional.empty();
  }

  private String bpmnFormKey(String taskId) {
    try {
      FormInfoDto form = engine.getTaskForm(taskId);
      return form == null ? null : form.key();
    } catch (EngineException e) {
      // tasks without any form key may be reported as an error by some engine versions
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
