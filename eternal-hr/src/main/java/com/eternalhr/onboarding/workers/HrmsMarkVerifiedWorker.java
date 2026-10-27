package com.eternalhr.onboarding.workers;

import com.eternalhr.onboarding.hrms.HrmsClient;
import java.util.Date;
import java.util.Map;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.springframework.stereotype.Component;

/** Marks the employee record as verified in the HRMS once HR has checked it against the documents. */
@Component
@ExternalTaskSubscription("ehr.hrms.mark-verified")
public class HrmsMarkVerifiedWorker extends Worker {

  private final HrmsClient hrms;

  public HrmsMarkVerifiedWorker(HrmsClient hrms) {
    this.hrms = hrms;
  }

  @Override
  protected Map<String, Object> work(ExternalTask task) {
    hrms.markVerified(text(task, "employeeCode"));
    return Map.of("hrmsVerifiedAt", new Date());
  }
}
