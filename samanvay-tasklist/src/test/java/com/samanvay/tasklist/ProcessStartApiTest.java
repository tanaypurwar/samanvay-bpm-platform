package com.samanvay.tasklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.samanvay.tasklist.config.TasklistProperties.Branding;
import com.samanvay.tasklist.engine.CamundaClient;
import com.samanvay.tasklist.engine.dto.DeploymentResourceDto;
import com.samanvay.tasklist.engine.dto.FormInfoDto;
import com.samanvay.tasklist.engine.dto.ProcessDefinitionDto;
import com.samanvay.tasklist.engine.dto.ProcessInstanceDto;
import com.samanvay.tasklist.engine.dto.VariableValueDto;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:tasklist-start;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "samanvay.tasklist.branding.name=Eternal HR",
    "samanvay.tasklist.branding.product=Onboarding",
    "samanvay.tasklist.branding.primary-color=#4527a0",
    "samanvay.tasklist.branding.secondary-color=not-a-color",
    "samanvay.tasklist.branding.logo-url=/joining/assets/logo.webp"
})
@AutoConfigureMockMvc
class ProcessStartApiTest {

  private static final TasklistUser TM = new TasklistUser("tm1", "Tara", "M", null, List.of("territory-managers"), false);
  private static final ProcessDefinitionDto JOINING =
      new ProcessDefinitionDto("joining:4:x", "joining", "Employee joining", "Joining request", 4, "dep4");

  @Autowired
  MockMvc mvc;

  @MockitoBean
  CamundaClient engine;

  @BeforeEach
  void startForm() {
    when(engine.getStartableProcessDefinitions("tm1")).thenReturn(List.of(JOINING));
    when(engine.getStartForm("joining:4:x")).thenReturn(new FormInfoDto("samanvay:deployment:forms/start.form.json", null));
    when(engine.getDeploymentResources("dep4")).thenReturn(List.of(new DeploymentResourceDto("r", "forms/start.form.json", "dep4")));
    when(engine.getDeploymentResourceData("dep4", "r")).thenReturn("""
        {"name":"New joining request","schema":{"type":"object","required":["candidateName"],
         "properties":{"candidateName":{"type":"string","minLength":2},"joiningDate":{"type":"string","format":"date"},
                       "initiator":{"type":"string"}}}}""".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void brandingIsPublicAndIgnoresInvalidColors() throws Exception {
    mvc.perform(get("/api/branding"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Eternal HR"))
        .andExpect(jsonPath("$.product").value("Onboarding"))
        .andExpect(jsonPath("$.primaryColor").value("#4527a0"))
        .andExpect(jsonPath("$.secondaryColor").value("#00796b"))
        .andExpect(jsonPath("$.logoUrl").value("/joining/assets/logo.webp"));
  }

  @Test
  void brandingIgnoresLogoUrlsThatAreNotSameSiteOrHttps() {
    assertThat(new Branding(null, null, null, null, "javascript:alert(1)").logoUrl()).isNull();
    assertThat(new Branding(null, null, null, null, "//evil.example/logo.png").logoUrl()).isNull();
    assertThat(new Branding(null, null, null, null, "http://cdn.example/logo.png").logoUrl()).isNull();
    assertThat(new Branding(null, null, null, null, " https://cdn.example/logo.png ").logoUrl())
        .isEqualTo("https://cdn.example/logo.png");
  }

  @Test
  void listsOnlyProcessesTheUserMayStart() throws Exception {
    mvc.perform(get("/api/processes").with(authentication(signedIn(TM))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].key").value("joining"))
        .andExpect(jsonPath("$[0].name").value("Employee joining"));
  }

  @Test
  void startFormComesFromTheDeployment() throws Exception {
    mvc.perform(get("/api/processes/joining/start-form").with(authentication(signedIn(TM))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.processName").value("Employee joining"))
        .andExpect(jsonPath("$.formName").value("New joining request"))
        .andExpect(jsonPath("$.formVersionId").doesNotExist())
        .andExpect(jsonPath("$.schema.required[0]").value("candidateName"));
  }

  @Test
  void cannotStartAProcessTheUserIsNotAStarterOf() throws Exception {
    mvc.perform(post("/api/processes/payroll/start").with(authentication(signedIn(TM))).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"formData\":{}}"))
        .andExpect(status().isNotFound());
    verify(engine, never()).startProcess(anyString(), anyMap());
  }

  @Test
  void invalidStartDataIsRejected() throws Exception {
    mvc.perform(post("/api/processes/joining/start").with(authentication(signedIn(TM))).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"formData\":{\"candidateName\":\"A\"}}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("candidateName"));
    verify(engine, never()).startProcess(anyString(), anyMap());
  }

  @SuppressWarnings("unchecked")
  @Test
  void startsWithTypedVariablesAndTheRealInitiator() throws Exception {
    when(engine.startProcess(eq("joining:4:x"), anyMap())).thenReturn(new ProcessInstanceDto("pi9", "joining:4:x", null));

    mvc.perform(post("/api/processes/joining/start").with(authentication(signedIn(TM))).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"formData\":{\"candidateName\":\"Asha Rao\",\"joiningDate\":\"2026-12-01\",\"initiator\":\"boss\"}}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.processInstanceId").value("pi9"));

    ArgumentCaptor<Map<String, VariableValueDto>> variables = ArgumentCaptor.forClass(Map.class);
    verify(engine).startProcess(eq("joining:4:x"), variables.capture());
    assertThat(variables.getValue())
        .containsEntry("candidateName", VariableValueDto.of("Asha Rao", "String"))
        .containsEntry("joiningDate", VariableValueDto.of("2026-12-01T00:00:00.000+0000", "Date"))
        .containsEntry("initiator", VariableValueDto.of("tm1", "String"))
        .containsEntry("initiatorName", VariableValueDto.of("Tara M", "String"));
  }

  private static UsernamePasswordAuthenticationToken signedIn(TasklistUser user) {
    return UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());
  }
}
