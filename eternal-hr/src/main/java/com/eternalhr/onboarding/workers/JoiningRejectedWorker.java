package com.eternalhr.onboarding.workers;

import com.eternalhr.onboarding.notify.Mailer;
import com.eternalhr.onboarding.notify.Mailer.Email;
import java.util.Map;
import java.util.Objects;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.springframework.stereotype.Component;

/** Tells the territory manager who rejected the joining request, and why. */
@Component
@ExternalTaskSubscription("ehr.notify.joining-rejected")
public class JoiningRejectedWorker extends Worker {

  private final Mailer mailer;
  private final Recipients recipients;

  public JoiningRejectedWorker(Mailer mailer, Recipients recipients) {
    this.mailer = mailer;
    this.recipients = recipients;
  }

  record Rejection(String by, String comments) {
  }

  @Override
  protected Map<String, Object> work(ExternalTask task) {
    Rejection rejection = rejection(task);
    mailer.send(Email.to(recipients.initiator(task), "Joining request rejected: " + text(task, "candidateName"),
        "joining-rejected", model(
            "candidateName", text(task, "candidateName"),
            "designation", text(task, "designation"),
            "rejectedBy", rejection.by(),
            "comments", rejection.comments())));
    return Map.of("rejectedBy", rejection.by());
  }

  /** The stage that decided "reject", and its comments. */
  static Rejection rejection(ExternalTask task) {
    if ("reject".equals(text(task, "pmDecision"))) {
      return new Rejection("Project manager", Objects.requireNonNullElse(text(task, "pmComments"), "No comments given."));
    }
    if ("reject".equals(text(task, "chDecision"))) {
      return new Rejection("Country head", Objects.requireNonNullElse(text(task, "chComments"), "No comments given."));
    }
    return new Rejection("Reporting manager", Objects.requireNonNullElse(text(task, "rmComments"), "No comments given."));
  }
}
