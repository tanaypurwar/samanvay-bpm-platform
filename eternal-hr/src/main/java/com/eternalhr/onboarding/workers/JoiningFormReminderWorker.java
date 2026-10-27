package com.eternalhr.onboarding.workers;

import com.eternalhr.onboarding.joining.JoiningFormLinks;
import com.eternalhr.onboarding.notify.Mailer;
import com.eternalhr.onboarding.notify.Mailer.Email;
import java.util.Date;
import java.util.Map;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.springframework.stereotype.Component;

/** Reminds the new employee to submit the joining form, with a fresh link. */
@Component
@ExternalTaskSubscription("ehr.joining-form.send-reminder")
public class JoiningFormReminderWorker extends Worker {

  private final JoiningFormLinks links;
  private final Mailer mailer;
  private final Recipients recipients;

  public JoiningFormReminderWorker(JoiningFormLinks links, Mailer mailer, Recipients recipients) {
    this.links = links;
    this.mailer = mailer;
    this.recipients = recipients;
  }

  @Override
  protected Map<String, Object> work(ExternalTask task) {
    JoiningFormLinks.Link link = links.create(task.getProcessInstanceId());
    mailer.send(Email.to(recipients.candidate(task), "Reminder: your Eternal HR joining form is waiting",
        "joining-form-link", SendJoiningFormLinkWorker.model(task, link, true)));
    return Map.of("joiningFormLink", link.url(), "joiningFormLinkExpiresAt", Date.from(link.expiresAt()));
  }
}
