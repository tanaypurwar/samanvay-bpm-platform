package com.samanvay.tasklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samanvay.tasklist.auth.TasklistUser;
import com.samanvay.tasklist.document.DocumentStore;
import com.samanvay.tasklist.document.StoredDocument;
import com.samanvay.tasklist.engine.CamundaClient;
import com.samanvay.tasklist.engine.dto.DeploymentResourceDto;
import com.samanvay.tasklist.engine.dto.FormInfoDto;
import com.samanvay.tasklist.engine.dto.ProcessDefinitionDto;
import com.samanvay.tasklist.engine.dto.ProcessInstanceDto;
import com.samanvay.tasklist.engine.dto.TaskDto;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:tasklist-documents;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "samanvay.documents.bucket=documents",
    "samanvay.documents.endpoint=http://localhost:1",
    "samanvay.documents.access-key=test",
    "samanvay.documents.secret-key=test",
    "samanvay.documents.max-file-size=1KB"
})
@AutoConfigureMockMvc
class DocumentApiTest {

  static final byte[] PDF = "%PDF-1.7\n1 0 obj\n".getBytes(StandardCharsets.US_ASCII);
  static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

  private static final TasklistUser HR = new TasklistUser("hr1", "Harini", "Rao", null, List.of("hr-team"), false);
  private static final TasklistUser PM = new TasklistUser("pm1", "Pranav", "M", null, List.of("project-managers"), false);
  private static final ProcessDefinitionDto JOINING =
      new ProcessDefinitionDto("joining:4:x", "joining", "Employee joining", null, 4, "dep4");
  private static final TaskDto REVIEW = new TaskDto("t1", "Review documents", null, "hr1", null, null, null, null,
      50, "joining:4:x", "pi1", "Task_DocsReview", null, null);
  private static final String DOCUMENT_TYPES = """
      {"types":[{"id":"pan","label":"PAN card"},{"id":"photo","label":"Photograph"}]}""";

  @TestConfiguration
  static class InMemoryStore {
    @Bean
    @Primary
    MemoryDocumentStore memoryDocumentStore() {
      return new MemoryDocumentStore();
    }
  }

  /** Keeps documents in memory; {@code failPuts} simulates S3 being down. */
  static class MemoryDocumentStore implements DocumentStore {
    final Map<String, List<StoredDocument.Content>> byInstance = new ConcurrentHashMap<>();
    boolean failPuts;

    @Override
    public List<StoredDocument> list(String processInstanceId) {
      return byInstance.getOrDefault(processInstanceId, List.of()).stream().map(StoredDocument.Content::document).toList();
    }

    @Override
    public Optional<StoredDocument.Content> get(String processInstanceId, String documentId) {
      return byInstance.getOrDefault(processInstanceId, List.of()).stream()
          .filter(c -> c.document().id().equals(documentId)).findFirst();
    }

    @Override
    public void put(String processInstanceId, StoredDocument document, byte[] data) {
      if (failPuts) {
        throw new com.samanvay.tasklist.document.DocumentStoreUnavailableException(new RuntimeException("down"));
      }
      byInstance.computeIfAbsent(processInstanceId, k -> new ArrayList<>()).add(new StoredDocument.Content(document, data));
    }

    @Override
    public void delete(String processInstanceId, String documentId) {
      byInstance.getOrDefault(processInstanceId, new ArrayList<>()).removeIf(c -> c.document().id().equals(documentId));
    }
  }

  @Autowired
  MockMvc mvc;

  @Autowired
  MemoryDocumentStore store;

  @MockitoBean
  CamundaClient engine;

  @BeforeEach
  void engine() {
    store.byInstance.clear();
    store.failPuts = false;
    when(engine.getProcessDefinition("joining:4:x")).thenReturn(JOINING);
    when(engine.getDeploymentResources("dep4")).thenReturn(List.of(
        new DeploymentResourceDto("r1", "document-types.json", "dep4"),
        new DeploymentResourceDto("r2", "forms/start.form.json", "dep4")));
    when(engine.getDeploymentResourceData("dep4", "r1")).thenReturn(DOCUMENT_TYPES.getBytes(StandardCharsets.UTF_8));
    when(engine.getDeploymentResourceData("dep4", "r2")).thenReturn("""
        {"schema":{"type":"object","properties":{"candidateName":{"type":"string"}}}}""".getBytes(StandardCharsets.UTF_8));
    when(engine.getStartableProcessDefinitions("hr1")).thenReturn(List.of(JOINING));
    when(engine.getStartForm("joining:4:x")).thenReturn(new FormInfoDto("samanvay:deployment:forms/start.form.json", null));
    // hr1 sees t1 (assigned to them); pm1 sees nothing
    when(engine.queryTasks(argThat(q -> q != null && "hr1".equals(assigneeOf(q))), eq(0), eq(1)))
        .thenReturn(List.of(REVIEW));
    when(engine.queryTasks(argThat(q -> q != null && "pm1".equals(assigneeOf(q))), eq(0), eq(1)))
        .thenReturn(List.of());
  }

  @Test
  void uploadedDocumentsAreListedWithTypeAndStepAndCanBePreviewed() throws Exception {
    String id = upload("t1", "pan.pdf", PDF, "pan");

    mvc.perform(get("/api/tasks/t1/documents").with(authentication(signedIn(HR))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.options.enabled").value(true))
        .andExpect(jsonPath("$.options.types[0].label").value("PAN card"))
        .andExpect(jsonPath("$.canUpload").value(true))
        .andExpect(jsonPath("$.documents[0].id").value(id))
        .andExpect(jsonPath("$.documents[0].typeLabel").value("PAN card"))
        .andExpect(jsonPath("$.documents[0].fileName").value("pan.pdf"))
        .andExpect(jsonPath("$.documents[0].step").value("Review documents"))
        .andExpect(jsonPath("$.documents[0].uploadedByName").value("Harini Rao"))
        .andExpect(jsonPath("$.documents[0].deletable").value(true));

    mvc.perform(get("/api/tasks/t1/documents/" + id + "/content").with(authentication(signedIn(HR))))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")))
        .andExpect(content().bytes(PDF));
  }

  @Test
  void theContentTypeComesFromTheBytesNotTheName() throws Exception {
    upload("t1", "photo.pdf", PNG, "photo");
    assertThat(store.list("pi1").get(0).contentType()).isEqualTo("image/png");
  }

  @Test
  void onlyPdfAndImagesOfAKnownTypeAndSizeAreAccepted() throws Exception {
    byte[] html = "<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
    mvc.perform(multipart("/api/tasks/t1/documents").file(new MockMultipartFile("file", "x.pdf", "application/pdf", html))
            .param("documentType", "pan").with(authentication(signedIn(HR))).with(csrf()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT"));
    mvc.perform(multipart("/api/tasks/t1/documents").file(new MockMultipartFile("file", "a.pdf", "application/pdf", PDF))
            .param("documentType", "passport").with(authentication(signedIn(HR))).with(csrf()))
        .andExpect(status().isUnprocessableEntity());
    byte[] big = new byte[2048];
    System.arraycopy(PDF, 0, big, 0, PDF.length);
    mvc.perform(multipart("/api/tasks/t1/documents").file(new MockMultipartFile("file", "big.pdf", "application/pdf", big))
            .param("documentType", "pan").with(authentication(signedIn(HR))).with(csrf()))
        .andExpect(status().isUnprocessableEntity());
    assertThat(store.list("pi1")).isEmpty();
  }

  @Test
  void documentsOfTasksTheUserCannotSeeAreNotFound() throws Exception {
    String id = upload("t1", "pan.pdf", PDF, "pan");
    mvc.perform(get("/api/tasks/t1/documents").with(authentication(signedIn(PM))))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/tasks/t1/documents/" + id + "/content").with(authentication(signedIn(PM))))
        .andExpect(status().isNotFound());
  }

  @Test
  void usersCanRemoveOnlyTheirOwnDocuments() throws Exception {
    store.put("pi1", new StoredDocument("8d2c7a52-1f0e-4a3b-9c11-1a2b3c4d5e6f", "pan", "PAN card", "pan.pdf",
        "application/pdf", PDF.length, null, "Asha Rao (employee)", Instant.now(), "Employee joining form"), PDF);
    mvc.perform(delete("/api/tasks/t1/documents/8d2c7a52-1f0e-4a3b-9c11-1a2b3c4d5e6f")
            .with(authentication(signedIn(HR))).with(csrf()))
        .andExpect(status().isForbidden());

    String mine = upload("t1", "photo.png", PNG, "photo");
    mvc.perform(delete("/api/tasks/t1/documents/" + mine).with(authentication(signedIn(HR))).with(csrf()))
        .andExpect(status().isNoContent());
    assertThat(store.list("pi1")).hasSize(1);
  }

  @Test
  void startFormOffersTheProcessDocumentTypes() throws Exception {
    mvc.perform(get("/api/processes/joining/start-form").with(authentication(signedIn(HR))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documents.enabled").value(true))
        .andExpect(jsonPath("$.documents.types[1].id").value("photo"))
        .andExpect(jsonPath("$.documents.contentTypes[0]").value("application/pdf"));
  }

  @Test
  void documentsAttachedWhenStartingAreStoredOnTheNewInstance() throws Exception {
    when(engine.startProcess(eq("joining:4:x"), anyMap())).thenReturn(new ProcessInstanceDto("pi7", "joining:4:x", null));

    mvc.perform(multipart("/api/processes/joining/start")
            .file(new MockMultipartFile("request", "", "application/json",
                "{\"formData\":{\"candidateName\":\"Asha\"}}".getBytes(StandardCharsets.UTF_8)))
            .file(new MockMultipartFile("files", "pan.pdf", "application/pdf", PDF))
            .file(new MockMultipartFile("files", "me.png", "image/png", PNG))
            .param("documentTypes", "pan", "photo")
            .with(authentication(signedIn(HR))).with(csrf()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.processInstanceId").value("pi7"))
        .andExpect(jsonPath("$.documents").value(2));

    assertThat(store.list("pi7")).extracting(StoredDocument::type).containsExactly("pan", "photo");
    assertThat(store.list("pi7")).extracting(StoredDocument::step).containsOnly("Employee joining");
  }

  @Test
  void anInvalidAttachmentStopsTheStart() throws Exception {
    mvc.perform(multipart("/api/processes/joining/start")
            .file(new MockMultipartFile("request", "", "application/json", "{\"formData\":{}}".getBytes(StandardCharsets.UTF_8)))
            .file(new MockMultipartFile("files", "notes.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)))
            .param("documentTypes", "pan")
            .with(authentication(signedIn(HR))).with(csrf()))
        .andExpect(status().isUnprocessableEntity());
    verify(engine, never()).startProcess(anyString(), anyMap());
  }

  @Test
  void theInstanceIsRemovedWhenItsDocumentsCannotBeStored() throws Exception {
    when(engine.startProcess(eq("joining:4:x"), anyMap())).thenReturn(new ProcessInstanceDto("pi8", "joining:4:x", null));
    store.failPuts = true;

    mvc.perform(multipart("/api/processes/joining/start")
            .file(new MockMultipartFile("request", "", "application/json", "{\"formData\":{}}".getBytes(StandardCharsets.UTF_8)))
            .file(new MockMultipartFile("files", "pan.pdf", "application/pdf", PDF))
            .param("documentTypes", "pan")
            .with(authentication(signedIn(HR))).with(csrf()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("DOCUMENT_STORE_UNAVAILABLE"));
    verify(engine).deleteProcessInstance("pi8");
  }

  private String upload(String taskId, String name, byte[] data, String type) throws Exception {
    String body = mvc.perform(multipart("/api/tasks/" + taskId + "/documents")
            .file(new MockMultipartFile("file", name, "application/octet-stream", data))
            .param("documentType", type)
            .with(authentication(signedIn(HR))).with(csrf()))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return com.jayway.jsonpath.JsonPath.read(body, "$.id");
  }

  @SuppressWarnings("unchecked")
  private static String assigneeOf(Map<String, Object> query) {
    Object or = query.get("orQueries");
    if (or instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
      return (String) ((Map<String, Object>) first).get("assignee");
    }
    return null;
  }

  private static UsernamePasswordAuthenticationToken signedIn(TasklistUser user) {
    return UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());
  }
}
