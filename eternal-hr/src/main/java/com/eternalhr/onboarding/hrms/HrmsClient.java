package com.eternalhr.onboarding.hrms;

import java.util.Map;

/** The HR management system where employee records live. */
public interface HrmsClient {

  /** Creates the joining record and returns the employee code the HRMS assigned. */
  String createEmployee(Map<String, Object> joiningDetails);

  /** Marks the employee's record and documents as verified. */
  void markVerified(String employeeCode);
}
