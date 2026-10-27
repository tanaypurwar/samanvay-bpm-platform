package com.eternalhr.onboarding.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** Renders every email template, with and without optional values, to catch template errors before runtime. */
class MailTemplatesTest {

  private static final SpringTemplateEngine ENGINE = new SpringTemplateEngine();

  static {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    ENGINE.setTemplateResolver(resolver);
  }

  @ParameterizedTest
  @ValueSource(strings = {"joining-form-link", "joining-rejected", "letter-for-approval", "letter-to-employee",
      "document-correction", "document-escalation", "joining-completed"})
  void rendersWithFullAndMinimalData(String template) {
    String full = render(template, full());
    assertThat(full).contains("Eternal HR").contains("Asha Rao").doesNotContain("null");

    Map<String, Object> minimal = new HashMap<>();
    minimal.put("candidateName", "Asha Rao");
    minimal.put("reminder", false);
    minimal.put("revised", false);
    minimal.put("link", "https://x/joining/t");
    minimal.put("expiresAt", new Date());
    assertThat(render(template, minimal)).contains("Asha Rao").doesNotContain(">null<");
  }

  private static Map<String, Object> full() {
    Map<String, Object> model = new HashMap<>();
    model.put("candidateName", "Asha Rao");
    model.put("designation", "Field Executive");
    model.put("joiningDate", new Date());
    model.put("link", "https://hr.example.com/joining/token");
    model.put("expiresAt", new Date());
    model.put("reminder", true);
    model.put("rejectedBy", "Country head");
    model.put("comments", "Budget not approved");
    model.put("employeeCode", "EHR-2610-00001");
    model.put("annualCtc", 650000.0);
    model.put("workLocation", "Pune");
    model.put("letterReference", "https://docs/letter-1");
    model.put("letterNotes", "Standard terms");
    model.put("letterChanges", "Move DOJ by a week");
    model.put("revised", true);
    model.put("documentIssues", "PAN copy is blurred");
    model.put("projectName", "Acme rollout");
    model.put("tasklistUrl", "https://hr.example.com");
    return model;
  }

  private static String render(String template, Map<String, Object> model) {
    Context context = new Context();
    context.setVariables(model);
    return ENGINE.process("mail/" + template, context);
  }
}
