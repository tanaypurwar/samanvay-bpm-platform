package com.eternalhr.onboarding.workers;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base of every external task worker: runs {@link #work}, completes the task with the returned variables, and on
 * failure reports it to the engine so it is retried; after the last retry the engine raises an incident that an
 * operator can see and retry in Cockpit.
 */
public abstract class Worker implements ExternalTaskHandler {

  static final int RETRIES = 3;
  static final long RETRY_DELAY_MS = 60_000;

  protected final Logger log = LoggerFactory.getLogger(getClass());

  /** Does the work and returns the variables to set on the process. */
  protected abstract Map<String, Object> work(ExternalTask task);

  @Override
  public void execute(ExternalTask task, ExternalTaskService service) {
    try {
      Map<String, Object> variables = work(task);
      service.complete(task, variables == null ? Map.of() : variables);
    } catch (RuntimeException e) {
      int retries = task.getRetries() == null ? RETRIES : task.getRetries() - 1;
      log.warn("{} failed for process {} ({} retries left): {}", task.getTopicName(), task.getProcessInstanceId(),
          Math.max(retries, 0), e.getMessage());
      service.handleFailure(task, truncate(e.getMessage()), stackTrace(e), Math.max(retries, 0), RETRY_DELAY_MS);
    }
  }

  protected static String text(ExternalTask task, String name) {
    Object value = task.getVariable(name);
    return value == null ? null : value.toString();
  }

  /** An email model from name/value pairs; unlike {@code Map.of} it allows missing (null) values. */
  protected static Map<String, Object> model(Object... namesAndValues) {
    Map<String, Object> model = new java.util.HashMap<>();
    for (int i = 0; i < namesAndValues.length; i += 2) {
      model.put((String) namesAndValues[i], namesAndValues[i + 1]);
    }
    return model;
  }

  private static String truncate(String message) {
    String m = message == null ? "Unexpected error" : message;
    return m.length() > 600 ? m.substring(0, 600) : m;
  }

  private static String stackTrace(Throwable e) {
    StringWriter out = new StringWriter();
    e.printStackTrace(new PrintWriter(out));
    return out.toString();
  }
}
