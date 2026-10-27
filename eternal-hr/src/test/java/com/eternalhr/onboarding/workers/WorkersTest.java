package com.eternalhr.onboarding.workers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eternalhr.onboarding.config.EternalHrProperties;
import com.eternalhr.onboarding.hrms.HrmsClient;
import com.eternalhr.onboarding.joining.JoiningFormLinks;
import com.eternalhr.onboarding.notify.Mailer;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkersTest {

  private final EternalHrProperties properties = new EternalHrProperties(null,
      new EternalHrProperties.JoiningForm("https://hr.example.com", "test-secret-0123456789-0123456789-abc",
          Duration.ofDays(14), "R3/PT2M"),
      new EternalHrProperties.Documents("PT5M"), null, null, null, "https://hr.example.com");
  private final Mailer mailer = mock(Mailer.class);
  private final Recipients recipients = mock(Recipients.class);
  private final ExternalTaskService service = mock(ExternalTaskService.class);

  @SuppressWarnings("unchecked")
  @Test
  void joiningFormLinkIsEmailedAndRecordedWithTheReminderCycle() {
    ExternalTask task = task("pi-7");
    when(task.getVariable("candidateName")).thenReturn("Asha Rao");
    when(recipients.candidate(task)).thenReturn("asha@example.com");
    when(mailer.send(any())).thenReturn(true);
    var worker = new SendJoiningFormLinkWorker(
        new JoiningFormLinks(properties, Clock.systemUTC()), mailer, recipients, properties);

    worker.execute(task, service);

    ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
    verify(service).complete(eq(task), variables.capture());
    assertThat(variables.getValue().get("joiningFormLink").toString()).startsWith("https://hr.example.com/joining/");
    assertThat(variables.getValue()).containsEntry("joiningFormReminderCycle", "R3/PT2M");
    ArgumentCaptor<Mailer.Email> email = ArgumentCaptor.forClass(Mailer.Email.class);
    verify(mailer).send(email.capture());
    assertThat(email.getValue().to()).containsExactly("asha@example.com");
    assertThat(email.getValue().model()).containsEntry("link", variables.getValue().get("joiningFormLink"));
  }

  @Test
  void failureIsReportedWithOneRetryLess() {
    ExternalTask task = task("pi-8");
    when(task.getRetries()).thenReturn(2);
    when(mailer.send(any())).thenReturn(false); // no candidate address
    var worker = new SendJoiningFormLinkWorker(
        new JoiningFormLinks(properties, Clock.systemUTC()), mailer, recipients, properties);

    worker.execute(task, service);

    verify(service, never()).complete(any(ExternalTask.class), anyMap());
    verify(service).handleFailure(eq(task), anyString(), anyString(), eq(1), anyLong());
  }

  @SuppressWarnings("unchecked")
  @Test
  void hrmsRecordIsNotCreatedTwiceOnRetry() {
    HrmsClient hrms = mock(HrmsClient.class);
    ExternalTask task = task("pi-9");
    when(task.getVariable("employeeCode")).thenReturn("EHR-2610-00042");

    new HrmsCreateEmployeeWorker(hrms).execute(task, service);

    verify(hrms, never()).createEmployee(anyMap());
    ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
    verify(service).complete(eq(task), variables.capture());
    assertThat(variables.getValue()).containsEntry("employeeCode", "EHR-2610-00042");
  }

  @Test
  void rejectionNamesTheStageThatRejected() {
    ExternalTask task = task("pi-10");
    when(task.getVariable("rmDecision")).thenReturn("forward");
    when(task.getVariable("chDecision")).thenReturn("reject");
    when(task.getVariable("chComments")).thenReturn("No budget");

    assertThat(JoiningRejectedWorker.rejection(task))
        .isEqualTo(new JoiningRejectedWorker.Rejection("Country head", "No budget"));
  }

  private static ExternalTask task(String processInstanceId) {
    ExternalTask task = mock(ExternalTask.class);
    when(task.getProcessInstanceId()).thenReturn(processInstanceId);
    when(task.getTopicName()).thenReturn("test-topic");
    return task;
  }
}
