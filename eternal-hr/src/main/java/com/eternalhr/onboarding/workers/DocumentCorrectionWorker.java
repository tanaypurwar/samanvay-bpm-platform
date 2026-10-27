package com.eternalhr.onboarding.workers;

import com.eternalhr.onboarding.config.EternalHrProperties;
import com.eternalhr.onboarding.notify.Mailer;
import com.eternalhr.onboarding.notify.Mailer.Email;
import java.util.Date;
import java.util.Map;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.springframework.stereotype.Component;

/** Asks the employee for clear, complete or corrected documents, and starts the escalation clock. */
@Component
@ExternalTaskSubscription("ehr.documents.request-correction")
public class DocumentCorrectionWorker extends Worker {

  private final Mailer mailer;
  private final Recipients recipients;
  private final EternalHrProperties properties;

  public DocumentCorrectionWorker(Mailer mailer, Recipients recipients, EternalHrProperties properties) {
    this.mailer = mailer;
    this.recipients = recipients;
    this.properties = properties;
  }

  @Override
  protected Map<String, Object> work(ExternalTask task) {
    mailer.send(Email.to(recipients.candidate(task), "Action needed: documents for your joining at Eternal HR",
        "document-correction", model(
            "candidateName", text(task, "candidateName"),
            "documentIssues", text(task, "documentIssues"))));
    return Map.of(
        "documentCorrectionRequestedAt", new Date(),
        "documentEscalationAfter", properties.documents().escalateAfter());
  }
}
