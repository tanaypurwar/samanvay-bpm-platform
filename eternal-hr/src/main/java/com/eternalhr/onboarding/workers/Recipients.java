package com.eternalhr.onboarding.workers;

import com.eternalhr.onboarding.engine.EngineClient;
import java.util.List;
import org.camunda.bpm.client.task.ExternalTask;
import org.springframework.stereotype.Component;

/** Resolves the people in a joining to e-mail addresses, from the engine's identity service. */
@Component
public class Recipients {

  static final String HR_GROUP = "hr-team";

  private final EngineClient engine;

  public Recipients(EngineClient engine) {
    this.engine = engine;
  }

  /** The new employee. */
  public String candidate(ExternalTask task) {
    return Worker.text(task, "candidateEmail");
  }

  /** The territory manager who raised the joining. */
  public String initiator(ExternalTask task) {
    return engine.userEmail(Worker.text(task, "initiator")).orElse(null);
  }

  /** The project manager who approved the joining. */
  public String projectManager(ExternalTask task) {
    return engine.userEmail(Worker.text(task, "projectManager")).orElse(null);
  }

  public List<String> hrTeam() {
    return engine.groupMemberEmails(HR_GROUP);
  }
}
