package com.eternalhr.onboarding.workers;

import com.eternalhr.onboarding.notify.Mailer;
import com.eternalhr.onboarding.notify.Mailer.Email;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.springframework.stereotype.Component;

/** Tells the territory manager and project manager that the employee's joining is complete. */
@Component
@ExternalTaskSubscription("ehr.notify.joining-completed")
public class JoiningCompletedWorker extends Worker {

  private final Mailer mailer;
  private final Recipients recipients;

  public JoiningCompletedWorker(Mailer mailer, Recipients recipients) {
    this.mailer = mailer;
    this.recipients = recipients;
  }

  @Override
  protected Map<String, Object> work(ExternalTask task) {
    mailer.send(new Email(Arrays.asList(recipients.initiator(task), recipients.projectManager(task)),
        recipients.hrTeam(), "Joining complete: " + text(task, "candidateName"), "joining-completed", model(
            "candidateName", text(task, "candidateName"),
            "employeeCode", text(task, "employeeCode"),
            "designation", text(task, "designation"),
            "projectName", text(task, "projectName"))));
    return Map.of("joiningCompletedAt", new Date());
  }
}
