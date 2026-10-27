package com.eternalhr.onboarding.workers;

import com.eternalhr.onboarding.config.EternalHrProperties;
import com.eternalhr.onboarding.joining.JoiningFormLinks;
import com.eternalhr.onboarding.notify.Mailer;
import com.eternalhr.onboarding.notify.Mailer.Email;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.springframework.stereotype.Component;

/** Emails the new employee a signed link to their joining form. */
@Component
@ExternalTaskSubscription("ehr.joining-form.send-link")
public class SendJoiningFormLinkWorker extends Worker {

  private final JoiningFormLinks links;
  private final Mailer mailer;
  private final Recipients recipients;
  private final EternalHrProperties properties;

  public SendJoiningFormLinkWorker(JoiningFormLinks links, Mailer mailer, Recipients recipients,
      EternalHrProperties properties) {
    this.links = links;
    this.mailer = mailer;
    this.recipients = recipients;
    this.properties = properties;
  }

  @Override
  protected Map<String, Object> work(ExternalTask task) {
    JoiningFormLinks.Link link = links.create(task.getProcessInstanceId());
    boolean sent = mailer.send(Email.to(recipients.candidate(task),
        "Welcome to Eternal HR: complete your joining form", "joining-form-link", model(task, link, false)));
    if (!sent) {
      throw new IllegalStateException("The candidate has no email address; correct it and retry");
    }
    return Map.of(
        "joiningFormLink", link.url(),
        "joiningFormLinkExpiresAt", Date.from(link.expiresAt()),
        "joiningFormReminderCycle", properties.joiningForm().reminderCycle());
  }

  static Map<String, Object> model(ExternalTask task, JoiningFormLinks.Link link, boolean reminder) {
    Map<String, Object> model = new HashMap<>();
    model.put("candidateName", text(task, "candidateName"));
    model.put("designation", text(task, "designation"));
    model.put("joiningDate", task.getVariable("proposedJoiningDate"));
    model.put("link", link.url());
    model.put("expiresAt", Date.from(link.expiresAt()));
    model.put("reminder", reminder);
    return model;
  }
}
