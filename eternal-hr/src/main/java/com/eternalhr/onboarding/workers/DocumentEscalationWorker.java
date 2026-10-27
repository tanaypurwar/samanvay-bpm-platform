package com.eternalhr.onboarding.workers;

import com.eternalhr.onboarding.notify.Mailer;
import com.eternalhr.onboarding.notify.Mailer.Email;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.springframework.stereotype.Component;

/** The employee has not sent the documents in time: asks the project manager to follow up. */
@Component
@ExternalTaskSubscription("ehr.documents.escalate")
public class DocumentEscalationWorker extends Worker {

  private final Mailer mailer;
  private final Recipients recipients;

  public DocumentEscalationWorker(Mailer mailer, Recipients recipients) {
    this.mailer = mailer;
    this.recipients = recipients;
  }

  @Override
  protected Map<String, Object> work(ExternalTask task) {
    List<String> cc = new ArrayList<>(recipients.hrTeam());
    cc.add(recipients.initiator(task));
    mailer.send(new Email(Collections.singletonList(recipients.projectManager(task)), cc,
        "Documents still missing: " + text(task, "candidateName"), "document-escalation", model(
            "candidateName", text(task, "candidateName"),
            "employeeCode", text(task, "employeeCode"),
            "documentIssues", text(task, "documentIssues"))));
    return Map.of("documentsEscalatedAt", new Date());
  }
}
