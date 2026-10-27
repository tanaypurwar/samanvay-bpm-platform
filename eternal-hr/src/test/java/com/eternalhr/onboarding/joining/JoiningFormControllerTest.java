package com.eternalhr.onboarding.joining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eternalhr.onboarding.config.ClockConfig;
import com.eternalhr.onboarding.config.EternalHrProperties;
import com.eternalhr.onboarding.documents.DocumentStore;
import com.eternalhr.onboarding.documents.EmployeeDocuments;
import com.eternalhr.onboarding.joining.JoiningFormService.Joining;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(JoiningFormController.class)
@Import({JoiningFormLinks.class, ClockConfig.class, EmployeeDocuments.class})
@EnableConfigurationProperties(EternalHrProperties.class)
@TestPropertySource(properties = {
    "eternal-hr.joining-form.public-base-url=https://hr.example.com",
    "eternal-hr.joining-form.secret=test-secret-0123456789-0123456789-abc",
    "eternal-hr.joining-form.link-validity=P14D",
    "eternal-hr.document-store.bucket=documents",
    "eternal-hr.document-store.max-file-size=1KB"
})
class JoiningFormControllerTest {

  @Autowired
  MockMvc mvc;

  @Autowired
  JoiningFormLinks links;

  @MockitoBean
  JoiningFormService service;

  @MockitoBean
  DocumentStore store;

  String token;

  @BeforeEach
  void link() {
    String url = links.create("pi-1").url();
    token = url.substring(url.lastIndexOf('/') + 1);
    when(service.find("pi-1")).thenReturn(Optional.of(
        new Joining("Asha Rao", "asha@example.com", "9876543210", "Field Executive")));
  }

  @Test
  void formIsPrefilledFromTheJoining() throws Exception {
    mvc.perform(get("/joining/" + token))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Welcome, <span>Asha Rao</span>")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"asha@example.com\"")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Field Executive")));
  }

  @Test
  void invalidOrUnknownLinksShowTheSameNotAvailablePage() throws Exception {
    mvc.perform(get("/joining/bogus")).andExpect(status().isNotFound())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("This link is not available")));
    when(service.find("pi-1")).thenReturn(Optional.empty());
    mvc.perform(get("/joining/" + token)).andExpect(status().isNotFound());
  }

  @Test
  void invalidDetailsAreShownBackWithoutSubmitting() throws Exception {
    mvc.perform(valid(post("/joining/" + token), "panNumber", "123", "declaration", "false"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("enter a PAN like ABCDE1234F")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("please confirm")));
    verify(service, never()).submit(any(), any());
  }

  @Test
  void validDetailsAreNormalizedAndSubmitted() throws Exception {
    when(service.submit(eq("pi-1"), any())).thenReturn(true);

    mvc.perform(valid(post("/joining/" + token), "panNumber", " abcde1234f ", "bankIfsc", "hdfc0001234"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Thank you")));

    ArgumentCaptor<JoiningForm> form = ArgumentCaptor.forClass(JoiningForm.class);
    verify(service).submit(eq("pi-1"), form.capture());
    assertThat(form.getValue().getPanNumber()).isEqualTo("ABCDE1234F");
    assertThat(form.getValue().getBankIfsc()).isEqualTo("HDFC0001234");
  }

  @Test
  void alreadySubmittedFormIsNotAvailable() throws Exception {
    when(service.submit(eq("pi-1"), any())).thenReturn(false);
    mvc.perform(valid(post("/joining/" + token))).andExpect(status().isNotFound());
  }

  @Test
  void theFormOffersTheEmployeeDocumentTypesOnly() throws Exception {
    mvc.perform(get("/joining/" + token))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("enctype=\"multipart/form-data\"")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString(">PAN card</option>")))
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Appointment letter"))));
  }

  @Test
  void attachedDocumentsAreStoredWithTheirTypeBeforeTheFormIsSubmitted() throws Exception {
    when(service.submit(eq("pi-1"), any())).thenReturn(true);

    mvc.perform(valid(multipart("/joining/" + token)
            .file(new MockMultipartFile("documents", "pan.pdf", "application/pdf", PDF))
            .file(new MockMultipartFile("documents", "", "application/octet-stream", new byte[0]))
            .file(new MockMultipartFile("documents", "me.png", "image/png", PNG))
            .param("documentTypes", "pan", "", "photo")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("2 documents")));

    ArgumentCaptor<DocumentStore.Document> stored = ArgumentCaptor.forClass(DocumentStore.Document.class);
    verify(store, times(2)).put(eq("pi-1"), stored.capture(), any());
    assertThat(stored.getAllValues()).extracting(DocumentStore.Document::typeLabel)
        .containsExactly("PAN card", "Passport-size photograph");
    assertThat(stored.getAllValues()).extracting(DocumentStore.Document::contentType)
        .containsExactly("application/pdf", "image/png");
    assertThat(stored.getAllValues().get(0).step()).isEqualTo("Employee joining form");
    assertThat(stored.getAllValues().get(0).uploadedByName()).isEqualTo("Asha Rao (employee)");
    verify(service).submit(eq("pi-1"), any());
  }

  @Test
  void documentsWithoutATypeOrNotPdfOrImageAreRefused() throws Exception {
    mvc.perform(valid(multipart("/joining/" + token)
            .file(new MockMultipartFile("documents", "scan.pdf", "application/pdf", PDF))
            .file(new MockMultipartFile("documents", "notes.pdf", "application/pdf", "hello".getBytes()))
            .param("documentTypes", "", "pan")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Choose what &quot;scan.pdf&quot; is.")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("is not a PDF, JPEG or PNG file")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Please attach your documents again")));
    verify(store, never()).put(any(), any(), any());
    verify(service, never()).submit(any(), any());
  }

  @Test
  void documentsAreRemovedAgainWhenTheFormCannotBeSubmitted() throws Exception {
    when(service.submit(eq("pi-1"), any())).thenReturn(false);
    mvc.perform(valid(multipart("/joining/" + token)
            .file(new MockMultipartFile("documents", "pan.pdf", "application/pdf", PDF))
            .param("documentTypes", "pan")))
        .andExpect(status().isNotFound());
    verify(store).delete(eq("pi-1"), any());
  }

  static final byte[] PDF = "%PDF-1.7 test".getBytes();
  static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0};

  /** A complete, valid submission; {@code overrides} are name/value pairs replacing fields. */
  private static MockHttpServletRequestBuilder valid(MockHttpServletRequestBuilder request, String... overrides) {
    java.util.Map<String, String> fields = new java.util.LinkedHashMap<>(VALID);
    for (int i = 0; i < overrides.length; i += 2) {
      fields.put(overrides[i], overrides[i + 1]);
    }
    fields.forEach(request::param);
    return request;
  }

  private static final java.util.Map<String, String> VALID = java.util.Map.ofEntries(
      java.util.Map.entry("candidateName", "Asha Rao"),
      java.util.Map.entry("dateOfBirth", "1998-04-12"),
      java.util.Map.entry("gender", "female"),
      java.util.Map.entry("fatherOrSpouseName", "Ravi Rao"),
      java.util.Map.entry("candidateMobile", "9876543210"),
      java.util.Map.entry("candidateEmail", "asha@example.com"),
      java.util.Map.entry("currentAddress", "12 MG Road, Pune"),
      java.util.Map.entry("permanentAddress", "12 MG Road, Pune"),
      java.util.Map.entry("emergencyContactName", "Ravi Rao"),
      java.util.Map.entry("emergencyContactPhone", "9876500000"),
      java.util.Map.entry("panNumber", "ABCDE1234F"),
      java.util.Map.entry("aadhaarLast4", "1234"),
      java.util.Map.entry("bankName", "HDFC Bank"),
      java.util.Map.entry("bankIfsc", "HDFC0001234"),
      java.util.Map.entry("bankAccountLast4", "5678"),
      java.util.Map.entry("highestQualification", "B.Com"),
      java.util.Map.entry("totalExperienceYears", "2.5"),
      java.util.Map.entry("declaration", "true"));
}
