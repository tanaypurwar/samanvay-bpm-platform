package com.eternalhr.onboarding.joining;

import com.eternalhr.onboarding.engine.EngineClient;
import com.eternalhr.onboarding.engine.EngineClient.Variable;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Prefills the joining form from the process and hands the submission back to it. */
@Service
public class JoiningFormService {

  public static final String MESSAGE = "JoiningFormSubmitted";

  private final EngineClient engine;
  private final Clock clock;

  public JoiningFormService(EngineClient engine, Clock clock) {
    this.engine = engine;
    this.clock = clock;
  }

  /** What the employee needs to see on the form, if the joining is still running. */
  public record Joining(String candidateName, String candidateEmail, String candidateMobile, String designation) {
  }

  public Optional<Joining> find(String processInstanceId) {
    return engine.getProcessVariables(processInstanceId).map(v -> new Joining(
        text(v, "candidateName"), text(v, "candidateEmail"), text(v, "candidateMobile"), text(v, "designation")));
  }

  /** @return false if the joining is not waiting for the form (already submitted, or no longer running) */
  public boolean submit(String processInstanceId, JoiningForm form) {
    Map<String, Variable> variables = new LinkedHashMap<>();
    variables.put("candidateName", Variable.string(form.getCandidateName()));
    variables.put("dateOfBirth", Variable.date(Date.from(form.getDateOfBirth().atStartOfDay(ZoneOffset.UTC).toInstant())));
    variables.put("gender", Variable.string(form.getGender()));
    variables.put("fatherOrSpouseName", Variable.string(form.getFatherOrSpouseName()));
    variables.put("candidateMobile", Variable.string(form.getCandidateMobile()));
    variables.put("candidateEmail", Variable.string(form.getCandidateEmail()));
    variables.put("currentAddress", Variable.string(form.getCurrentAddress()));
    variables.put("permanentAddress", Variable.string(form.getPermanentAddress()));
    variables.put("emergencyContactName", Variable.string(form.getEmergencyContactName()));
    variables.put("emergencyContactPhone", Variable.string(form.getEmergencyContactPhone()));
    variables.put("panNumber", Variable.string(form.getPanNumber()));
    variables.put("aadhaarLast4", Variable.string(form.getAadhaarLast4()));
    variables.put("bankName", Variable.string(form.getBankName()));
    variables.put("bankIfsc", Variable.string(form.getBankIfsc()));
    variables.put("bankAccountLast4", Variable.string(form.getBankAccountLast4()));
    variables.put("highestQualification", Variable.string(form.getHighestQualification()));
    if (form.getPreviousEmployer() != null && !form.getPreviousEmployer().isBlank()) {
      variables.put("previousEmployer", Variable.string(form.getPreviousEmployer()));
    }
    variables.put("totalExperienceYears", Variable.number(form.getTotalExperienceYears()));
    variables.put("joiningFormSubmittedAt", Variable.date(Date.from(clock.instant())));
    return engine.correlate(MESSAGE, processInstanceId, variables);
  }

  private static String text(Map<String, Variable> variables, String name) {
    Variable variable = variables.get(name);
    return variable == null || variable.value() == null ? null : variable.value().toString();
  }
}
