package com.samanvay.tasklist.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samanvay.tasklist.engine.CamundaClient;
import com.samanvay.tasklist.engine.dto.FormInfoDto;
import com.samanvay.tasklist.engine.dto.ProcessDefinitionDto;
import com.samanvay.tasklist.engine.dto.TaskDto;
import com.samanvay.tasklist.form.entity.FormBinding;
import com.samanvay.tasklist.form.entity.FormDefinition;
import com.samanvay.tasklist.form.entity.FormVersion;
import com.samanvay.tasklist.form.repo.FormBindingRepository;
import com.samanvay.tasklist.form.repo.FormDefinitionRepository;
import com.samanvay.tasklist.form.repo.FormVersionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FormResolverTest {

  private final CamundaClient engine = mock(CamundaClient.class);
  private final FormDefinitionRepository definitions = mock(FormDefinitionRepository.class);
  private final FormVersionRepository versions = mock(FormVersionRepository.class);
  private final FormBindingRepository bindings = mock(FormBindingRepository.class);
  private final FormResolver resolver = new FormResolver(engine, definitions, versions, bindings);

  private final TaskDto task = new TaskDto("t1", "Approve", null, null, null, null, null, null, 50,
      "invoice:1:abc", "pi1", "approveInvoice", null, null);

  @Test
  void bpmnFormKeyWinsOverBindings() {
    FormDefinition declared = definition(1L, "invoice-approval");
    FormVersion v2 = version(declared, 2);
    when(engine.getTaskForm("t1")).thenReturn(new FormInfoDto("samanvay:invoice-approval", null));
    when(definitions.findByFormKey("invoice-approval")).thenReturn(Optional.of(declared));
    when(versions.findFirstByFormDefinitionIdOrderByVersionDesc(1L)).thenReturn(Optional.of(v2));

    assertThat(resolver.resolve(task)).contains(v2);
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
        .thenReturn(new ProcessDefinitionDto("invoice:1:abc", "invoice", "Invoice", 1));
    when(versions.findFirstByFormDefinitionIdOrderByVersionDesc(2L)).thenReturn(Optional.of(genericV1));

    assertThat(resolver.resolve(task)).contains(genericV1);
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

  private static FormDefinition definition(Long id, String key) {
    FormDefinition definition = new FormDefinition(key, key, null);
    ReflectionTestUtils.setField(definition, "id", id);
    return definition;
  }

  private static FormVersion version(FormDefinition definition, int number) {
    return new FormVersion(definition, number, "{\"type\":\"object\"}", null, "test");
  }
}
