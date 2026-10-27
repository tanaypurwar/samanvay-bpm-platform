package com.eternalhr.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import com.eternalhr.onboarding.engine.EngineClient;
import java.util.Map;
import org.camunda.bpm.client.spring.SpringTopicSubscription;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The whole application starts: workers subscribe, the client is configured, templates and web layer load. */
@SpringBootTest(properties = "camunda.bpm.client.disable-auto-fetching=true")
class ApplicationStartupTest {

  @Autowired
  ApplicationContext context;

  @MockitoBean
  EngineClient engine;

  @Test
  void everyWorkerTopicIsSubscribed() {
    Map<String, SpringTopicSubscription> subscriptions = context.getBeansOfType(SpringTopicSubscription.class);
    assertThat(subscriptions.values()).extracting(SpringTopicSubscription::getTopicName).containsExactlyInAnyOrder(
        "ehr.notify.joining-rejected", "ehr.joining-form.send-link", "ehr.joining-form.send-reminder",
        "ehr.hrms.create-employee", "ehr.letter.send-for-approval", "ehr.letter.send-to-employee",
        "ehr.documents.request-correction", "ehr.documents.escalate", "ehr.hrms.mark-verified",
        "ehr.notify.joining-completed");
  }
}
