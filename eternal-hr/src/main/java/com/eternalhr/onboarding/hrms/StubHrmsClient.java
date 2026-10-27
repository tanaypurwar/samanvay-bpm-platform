package com.eternalhr.onboarding.hrms;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stand-in until the real HRMS API is connected: issues an employee code and logs the calls. Replace with a client
 * for the HRMS by providing another {@link HrmsClient} bean.
 */
@Component
public class StubHrmsClient implements HrmsClient {

  private static final Logger LOG = LoggerFactory.getLogger(StubHrmsClient.class);
  private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyMM");
  private final SecureRandom random = new SecureRandom();

  @Override
  public String createEmployee(Map<String, Object> joiningDetails) {
    String code = "EHR-" + LocalDate.now().format(YEAR_MONTH) + "-" + String.format("%05d", random.nextInt(100_000));
    LOG.info("HRMS (stub): created employee {} for {}", code, joiningDetails.get("candidateName"));
    return code;
  }

  @Override
  public void markVerified(String employeeCode) {
    LOG.info("HRMS (stub): employee {} verified", employeeCode);
  }
}
