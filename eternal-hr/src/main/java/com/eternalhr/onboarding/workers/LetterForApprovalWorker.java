package com.eternalhr.onboarding.workers;

import com.eternalhr.onboarding.notify.Mailer;
import com.eternalhr.onboarding.notify.Mailer.Email;
import java.util.HashMap;
import java.util.Map;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.springframework.stereotype.Component;

/** Emails the appointment letter to the approving project manager; they approve it in the tasklist. */
@Component
@ExternalTaskSubscription("ehr.letter.send-for-approval")
public class LetterForApprovalWorker extends Worker {

  private final Mailer mailer;
  private final Recipients recipients;

  public LetterForApprovalWorker(Mailer mailer, Recipients recipients) {
    this.mailer = mailer;
    this.recipients = recipients;
  }

  @Override
  protected Map<String, Object> work(ExternalTask task) {
    boolean revised = task.getVariable("letterChanges") != null;
    mailer.send(Email.to(recipients.projectManager(task),
        (revised ? "Revised appointment letter for approval: " : "Appointment letter for approval: ")
            + text(task, "candidateName"),
        "letter-for-approval", letterModel(task, revised)));
    return Map.of();
  }

  static Map<String, Object> letterModel(ExternalTask task, boolean revised) {
    Map<String, Object> model = new HashMap<>();
    model.put("candidateName", text(task, "candidateName"));
    model.put("employeeCode", text(task, "employeeCode"));
    model.put("designation", text(task, "designation"));
    model.put("annualCtc", task.getVariable("proposedAnnualCtc"));
    model.put("joiningDate", task.getVariable("proposedJoiningDate"));
    model.put("workLocation", text(task, "workLocation"));
    model.put("letterReference", text(task, "letterReference"));
    model.put("letterNotes", text(task, "letterNotes"));
    model.put("letterChanges", text(task, "letterChanges"));
    model.put("revised", revised);
    return model;
  }
}
