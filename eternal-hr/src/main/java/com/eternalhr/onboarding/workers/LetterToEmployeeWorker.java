package com.eternalhr.onboarding.workers;

import com.eternalhr.onboarding.notify.Mailer;
import com.eternalhr.onboarding.notify.Mailer.Email;
import java.util.Date;
import java.util.Map;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.springframework.stereotype.Component;

/** Emails the approved appointment letter to the employee, before the joining date. */
@Component
@ExternalTaskSubscription("ehr.letter.send-to-employee")
public class LetterToEmployeeWorker extends Worker {

  private final Mailer mailer;
  private final Recipients recipients;

  public LetterToEmployeeWorker(Mailer mailer, Recipients recipients) {
    this.mailer = mailer;
    this.recipients = recipients;
  }

  @Override
  protected Map<String, Object> work(ExternalTask task) {
    boolean sent = mailer.send(Email.to(recipients.candidate(task), "Your appointment letter from Eternal HR",
        "letter-to-employee", LetterForApprovalWorker.letterModel(task, false)));
    if (!sent) {
      throw new IllegalStateException("The employee has no email address; correct it and retry");
    }
    return Map.of("appointmentLetterSentAt", new Date());
  }
}
