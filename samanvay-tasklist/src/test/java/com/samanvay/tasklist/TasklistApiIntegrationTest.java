package com.samanvay.tasklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samanvay.tasklist.auth.TasklistUser;
import com.samanvay.tasklist.engine.CamundaClient;
import com.samanvay.tasklist.engine.dto.AuthenticationResultDto;
import com.samanvay.tasklist.engine.dto.FormInfoDto;
import com.samanvay.tasklist.engine.dto.GroupInfoDto;
import com.samanvay.tasklist.engine.dto.TaskDto;
import com.samanvay.tasklist.engine.dto.UserProfileDto;
import com.samanvay.tasklist.engine.dto.VariableValueDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full Spring context on in-memory H2 (Flyway migrations plus the dev seed form), with the engine mocked.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:tasklist-it;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class TasklistApiIntegrationTest {

  private static final TasklistUser DEMO = new TasklistUser("demo", "Demo", "User", null, List.of("accounting"), false);

  @Autowired
  MockMvc mvc;

  @MockitoBean
  CamundaClient engine;

  @Test
  void apiRequiresSignIn() throws Exception {
    mvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void loginRequiresCsrfToken() throws Exception {
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"demo\",\"password\":\"demo\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void loginVerifiesAgainstEngineIdentity() throws Exception {
    when(engine.verifyCredentials("demo", "demo")).thenReturn(new AuthenticationResultDto("demo", true));
    when(engine.getUserProfile("demo")).thenReturn(new UserProfileDto("demo", "Demo", "User", "demo@example.com"));
    when(engine.getGroups("demo")).thenReturn(new GroupInfoDto(List.of(new GroupInfoDto.Group("camunda-admin", "Admins"))));

    mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"demo\",\"password\":\"demo\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("demo"))
        .andExpect(jsonPath("$.admin").value(true))
        .andExpect(jsonPath("$.groups[0]").value("camunda-admin"));
  }

  @Test
  void wrongPasswordIsRejected() throws Exception {
    when(engine.verifyCredentials("demo", "nope")).thenReturn(new AuthenticationResultDto(null, false));

    mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"demo\",\"password\":\"nope\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void adminApiNeedsAdminGroup() throws Exception {
    mvc.perform(get("/api/admin/forms").with(authentication(signedIn(DEMO))))
        .andExpect(status().isForbidden());

    TasklistUser admin = new TasklistUser("boss", null, null, null, List.of("camunda-admin"), true);
    mvc.perform(get("/api/admin/forms").with(authentication(signedIn(admin))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].formKey").value("invoice-approval"))
        .andExpect(jsonPath("$[0].latestVersion").value(1));
  }

  @Test
  void invisibleTaskIsReportedAsNotFound() throws Exception {
    when(engine.queryTasks(anyMap(), anyInt(), anyInt())).thenReturn(List.of());

    mvc.perform(get("/api/tasks/someone-elses").with(authentication(signedIn(DEMO))))
        .andExpect(status().isNotFound());
  }

  @Test
  void taskFormIsResolvedFromBpmnFormKeyAndPrefilled() throws Exception {
    givenVisibleTask("demo");
    when(engine.getTaskForm("t1")).thenReturn(new FormInfoDto("samanvay:invoice-approval", null));
    when(engine.getTaskVariables("t1")).thenReturn(Map.of(
        "invoiceNumber", VariableValueDto.of("INV-7", "String"),
        "invoiceDate", VariableValueDto.of("2026-10-01T00:00:00.000+0000", "Date"),
        "internalNote", VariableValueDto.of("not for the form", "String")));

    mvc.perform(get("/api/tasks/t1/form").with(authentication(signedIn(DEMO))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.formKey").value("invoice-approval"))
        .andExpect(jsonPath("$.schema.properties.decision").exists())
        .andExpect(jsonPath("$.formData.invoiceNumber").value("INV-7"))
        .andExpect(jsonPath("$.formData.invoiceDate").value("2026-10-01"))
        .andExpect(jsonPath("$.formData.internalNote").doesNotExist());
  }

  @Test
  void invalidFormDataDoesNotCompleteTask() throws Exception {
    givenVisibleTask("demo");
    when(engine.getTaskForm("t1")).thenReturn(new FormInfoDto("samanvay:invoice-approval", null));

    mvc.perform(post("/api/tasks/t1/complete").with(authentication(signedIn(DEMO))).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"formData\":{\"decision\":\"reject\"}}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("comment"));
    verify(engine, never()).complete(eq("t1"), anyMap());
  }

  @SuppressWarnings("unchecked")
  @Test
  void validFormDataCompletesTaskWithTypedVariables() throws Exception {
    givenVisibleTask("demo");
    when(engine.getTaskForm("t1")).thenReturn(new FormInfoDto("samanvay:invoice-approval", null));

    mvc.perform(post("/api/tasks/t1/complete").with(authentication(signedIn(DEMO))).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"formData":{"invoiceNumber":"INV-7","decision":"approve","costCenter":"IT",
                 "paymentDueDate":"2026-11-15"}}"""))
        .andExpect(status().isNoContent());

    ArgumentCaptor<Map<String, VariableValueDto>> variables = ArgumentCaptor.forClass(Map.class);
    verify(engine).complete(eq("t1"), variables.capture());
    assertThat(variables.getValue())
        .doesNotContainKey("invoiceNumber") // read-only in the form
        .containsEntry("decision", VariableValueDto.of("approve", "String"))
        .containsEntry("paymentDueDate", VariableValueDto.of("2026-11-15T00:00:00.000+0000", "Date"));
  }

  @Test
  void cannotCompleteTaskAssignedToSomeoneElse() throws Exception {
    givenVisibleTask(null);

    mvc.perform(post("/api/tasks/t1/complete").with(authentication(signedIn(DEMO))).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"formData\":{}}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TASK_NOT_ASSIGNED_TO_YOU"));
  }

  private void givenVisibleTask(String assignee) {
    TaskDto task = new TaskDto("t1", "Approve invoice", null, assignee, null, null, null, null, 50,
        "invoice-approval:1:x", "pi1", "approveInvoice", null, null);
    when(engine.queryTasks(anyMap(), anyInt(), anyInt())).thenReturn(List.of(task));
  }

  private static UsernamePasswordAuthenticationToken signedIn(TasklistUser user) {
    var authorities = new java.util.ArrayList<SimpleGrantedAuthority>();
    user.groups().forEach(g -> authorities.add(new SimpleGrantedAuthority(TasklistUser.GROUP_AUTHORITY_PREFIX + g)));
    if (user.admin()) {
      authorities.add(new SimpleGrantedAuthority(TasklistUser.ADMIN_AUTHORITY));
    }
    return UsernamePasswordAuthenticationToken.authenticated(user, null, authorities);
  }
}
