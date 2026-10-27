package com.eternalhr.onboarding.workers;

import com.eternalhr.onboarding.hrms.HrmsClient;
import java.util.Date;
import java.util.Map;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.springframework.stereotype.Component;

/** Creates the employee's joining record in the HRMS from the submitted joining form. */
@Component
@ExternalTaskSubscription("ehr.hrms.create-employee")
public class HrmsCreateEmployeeWorker extends Worker {

  private final HrmsClient hrms;

  public HrmsCreateEmployeeWorker(HrmsClient hrms) {
    this.hrms = hrms;
  }

  @Override
  protected Map<String, Object> work(ExternalTask task) {
    // a retry after the HRMS call succeeded must not create a second record
    String existing = text(task, "employeeCode");
    String code = existing != null ? existing : hrms.createEmployee(task.getAllVariables());
    return Map.of("employeeCode", code, "hrmsRecordCreatedAt", new Date());
  }
}
