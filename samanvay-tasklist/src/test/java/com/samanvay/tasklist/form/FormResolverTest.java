package com.samanvay.tasklist.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samanvay.tasklist.engine.CamundaClient;
import com.samanvay.tasklist.engine.dto.DeploymentResourceDto;
import com.samanvay.tasklist.engine.dto.FormInfoDto;
import com.samanvay.tasklist.engine.dto.ProcessDefinitionDto;
import com.samanvay.tasklist.engine.dto.TaskDto;
import com.samanvay.tasklist.form.entity.FormBinding;
import com.samanvay.tasklist.form.entity.FormDefinition;
import com.samanvay.tasklist.form.entity.FormVersion;
import com.samanvay.tasklist.form.repo.FormBindingRepository;
import com.samanvay.tasklist.form.repo.FormDefinitionRepository;
import com.samanvay.tasklist.form.repo.FormVersionRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FormResolverTest {

  private final CamundaClient engine = mock(CamundaClient.class);
  private final FormDefinitionRepository definitions = mock(FormDefinitionRepository.class);
  private final FormVersionRepository versions = mock(FormVersionRepository.class);
  private final FormBindingRepository bindings = mock(FormBindingRepository.class);
  private final DeploymentForms deploymentForms = new DeploymentForms(engine, new ObjectMapper());
  private final FormResolver resolver = new FormResolver(engine, definitions, versions, bindings, deploymentForms);

  private final TaskDto task = new TaskDto("t1", "Approve", null, null, null, null, null, null, 50,
      "invoice:1:abc", "pi1", "approveInvoice", null, null);

  @Test
  void bpmnFormKeyWinsOverBindings() {
    FormDefinition declared = definition(1L, "invoice-approval");
    FormVersion v2 = version(declared, 2);
    when(engine.getTaskForm("t1")).thenReturn(new FormInfoDto("samanvay:invoice-approval", null));
    when(definitions.findByFormKey("invoice-approval")).thenReturn(Optional.of(declared));
    when(versions.findFirstByFormDefinitionIdOrderByVersionDesc(1L)).thenReturn(Optional.of(v2));

    assertThat(resolver.resolve(task)).contains(ResolvedForm.of(v2));
    verify(bindings, never()).findByTaskDefinitionKeyOrderByPriorityDesc(any());
  }

  @Test
  void fallsBackToHighestPriorityMatchingBinding() {
    FormDefinition otherProcess = definition(1L, "other");
    FormDefinition generic = definition(2L, "generic");
    FormVersion genericV1 = version(generic, 1);
    when(engine.getTaskForm("t1")).thenReturn(new FormInfoDto(null, null));
    when(bindings.findByTaskDefinitionKeyOrderByPriorityDesc("approveInvoice")).thenReturn(List.of(
        new FormBinding(otherProcess, "approveInvoice", "purchase", 10),
        new FormBinding(generic, "approveInvoice", null, 0)));
    when(engine.getProcessDefinition("invoice:1:abc"))
        .thenReturn(new ProcessDefinitionDto("invoice:1:abc", "invoice", "Invoice", null, 1, "dep1"));
    when(versions.findFirstByFormDefinitionIdOrderByVersionDesc(2L)).thenReturn(Optional.of(genericV1));

    assertThat(resolver.resolve(task)).contains(ResolvedForm.of(genericV1));
    verify(versions, never()).findFirstByFormDefinitionIdOrderByVersionDesc(1L);
  }

  @Test
  void ignoresNonSamanvayFormKeysAndUnpublishedForms() {
    FormDefinition draftOnly = definition(3L, "draft");
    when(engine.getTaskForm("t1")).thenReturn(new FormInfoDto("embedded:app:forms/x.html", null));
    when(bindings.findByTaskDefinitionKeyOrderByPriorityDesc("approveInvoice"))
        .thenReturn(List.of(new FormBinding(draftOnly, "approveInvoice", null, 0)));
    when(versions.findFirstByFormDefinitionIdOrderByVersionDesc(3L)).thenReturn(Optional.empty());

    assertThat(resolver.resolve(task)).isEmpty();
  }

  @Test
  void deploymentFormIsLoadedFromTheProcessDeploymentAndCached() {
    when(engine.getTaskForm("t1")).thenReturn(new FormInfoDto("samanvay:deployment:forms/approve.form.json", null));
    when(engine.getProcessDefinition("invoice:1:abc"))
        .thenReturn(new ProcessDefinitionDto("invoice:1:abc", "invoice", "Invoice", null, 3, "dep1"));
    when(engine.getDeploymentResources("dep1")).thenReturn(List.of(
        new DeploymentResourceDto("r0", "invoice.bpmn", "dep1"),
        new DeploymentResourceDto("r1", "forms/approve.form.json", "dep1")));
    when(engine.getDeploymentResourceData("dep1", "r1")).thenReturn(
        "{\"name\":\"Approve\",\"schema\":{\"type\":\"object\"},\"uiSchema\":{\"ui:order\":[\"*\"]}}"
            .getBytes(StandardCharsets.UTF_8));

    ResolvedForm form = resolver.resolve(task).orElseThrow();
    assertThat(form.fromDeployment()).isTrue();
    assertThat(form.versionId()).isNull();
    assertThat(form.formKey()).isEqualTo("forms/approve.form.json");
    assertThat(form.name()).isEqualTo("Approve");
    assertThat(form.version()).isEqualTo(3);
    assertThat(form.schemaJson()).isEqualTo("{\"type\":\"object\"}");
    assertThat(form.uiSchemaJson()).contains("ui:order");

    resolver.resolve(task);
    verify(engine, times(1)).getDeploymentResourceData("dep1", "r1");
    verify(definitions, never()).findByFormKey(any());
  }

  @Test
  void missingOrInvalidDeploymentFormMeansNoForm() {
    when(engine.getTaskForm("t1")).thenReturn(new FormInfoDto("samanvay:deployment:forms/missing.form.json", null));
    when(engine.getProcessDefinition("invoice:1:abc"))
        .thenReturn(new ProcessDefinitionDto("invoice:1:abc", "invoice", "Invoice", null, 1, "dep2"));
    when(engine.getDeploymentResources("dep2")).thenReturn(List.of(new DeploymentResourceDto("r1", "bad.json", "dep2")));

    assertThat(resolver.resolve(task)).isEmpty();
  }

  @Test
  void startFormComesFromTheStartEventFormKey() {
    ProcessDefinitionDto definition = new ProcessDefinitionDto("hr:2:x", "hr", "HR", null, 2, "dep3");
    when(engine.getStartForm("hr:2:x")).thenReturn(new FormInfoDto("samanvay:deployment:start.form.json", null));
    when(engine.getDeploymentResources("dep3")).thenReturn(List.of(new DeploymentResourceDto("s", "start.form.json", "dep3")));
    when(engine.getDeploymentResourceData("dep3", "s"))
        .thenReturn("{\"schema\":{\"title\":\"Request\",\"type\":\"object\"}}".getBytes(StandardCharsets.UTF_8));

    assertThat(resolver.resolveStartForm(definition)).hasValueSatisfying(f -> assertThat(f.name()).isEqualTo("Request"));

    when(engine.getStartForm("hr:2:x")).thenReturn(new FormInfoDto(null, null));
    assertThat(resolver.resolveStartForm(definition)).isEmpty();
  }

  private static FormDefinition definition(Long id, String key) {
    FormDefinition definition = new FormDefinition(key, key, null);
    ReflectionTestUtils.setField(definition, "id", id);
    return definition;
  }

  private static FormVersion version(FormDefinition definition, int number) {
    return new FormVersion(definition, number, "{\"type\":\"object\"}", null, "test");
  }
}
