package com.eternalhr.onboarding.documents;

import com.eternalhr.onboarding.config.EternalHrProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/**
 * Documents the new employee attaches to their joining form. The types come from the same
 * {@code processes/document-types.json} that is deployed with the process (those marked {@code "employee": true}),
 * so HR sees the same labels in the tasklist.
 */
@Service
public class EmployeeDocuments {

  public static final String STEP = "Employee joining form";
  static final String TYPES_RESOURCE = "processes/document-types.json";
  private static final Logger LOG = LoggerFactory.getLogger(EmployeeDocuments.class);

  public record DocumentType(String id, String label) {
  }

  public record Upload(String type, String typeLabel, String fileName, String contentType, byte[] data) {
  }

  /** The files that passed, and a message for each that did not. */
  public record Checked(List<Upload> uploads, List<String> errors) {
  }

  private final DocumentStore store;
  private final EternalHrProperties.DocumentStore config;
  private final List<DocumentType> types;
  private final Clock clock;

  public EmployeeDocuments(DocumentStore store, EternalHrProperties properties, ObjectMapper objectMapper,
      Clock clock) {
    this.store = store;
    this.config = properties.documentStore() == null
        ? new EternalHrProperties.DocumentStore(null, null, null, null, null, false, null, null)
        : properties.documentStore();
    this.types = loadTypes(objectMapper);
    this.clock = clock;
  }

  public boolean enabled() {
    return config.enabled();
  }

  public List<DocumentType> types() {
    return types;
  }

  public DataSize maxFileSize() {
    return config.maxFileSize();
  }

  public int maxFiles() {
    return config.maxFiles();
  }

  /**
   * Pairs each file with the type chosen next to it ({@code documentTypes} by position). Rows without a file are
   * skipped; a file without a type, or that is not a PDF or image within the size limit, is an error.
   */
  public Checked check(List<MultipartFile> files, List<String> documentTypes) {
    List<Upload> uploads = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    if (files == null) {
      return new Checked(uploads, errors);
    }
    for (int i = 0; i < files.size(); i++) {
      MultipartFile file = files.get(i);
      if (file == null || file.isEmpty()) {
        continue;
      }
      String name = FileCheck.fileName(file.getOriginalFilename(), null);
      String typeId = documentTypes != null && i < documentTypes.size() ? documentTypes.get(i) : null;
      DocumentType type = types.stream().filter(t -> t.id().equals(typeId)).findFirst().orElse(null);
      if (type == null) {
        errors.add("Choose what \"" + name + "\" is.");
        continue;
      }
      if (file.getSize() > config.maxFileSize().toBytes()) {
        errors.add("\"" + name + "\" is larger than " + config.maxFileSize().toMegabytes() + " MB.");
        continue;
      }
      byte[] data = bytes(file);
      String contentType = FileCheck.contentType(data).orElse(null);
      if (contentType == null) {
        errors.add("\"" + name + "\" is not a PDF, JPEG or PNG file.");
        continue;
      }
      uploads.add(new Upload(type.id(), type.label(), FileCheck.fileName(file.getOriginalFilename(), contentType),
          contentType, data));
    }
    if (uploads.size() + errors.size() > config.maxFiles()) {
      errors.add("Attach at most " + config.maxFiles() + " documents.");
    }
    return new Checked(uploads, errors);
  }

  /** Stores all or none: if one fails, those already stored are removed and the failure is rethrown. */
  public List<String> store(String processInstanceId, List<Upload> uploads, String employeeName) {
    List<String> stored = new ArrayList<>();
    try {
      for (Upload upload : uploads) {
        String id = UUID.randomUUID().toString();
        store.put(processInstanceId, new DocumentStore.Document(id, upload.type(), upload.typeLabel(),
            upload.fileName(), upload.contentType(), "", employeeName + " (employee)", clock.instant(), STEP),
            upload.data());
        stored.add(id);
      }
      return stored;
    } catch (RuntimeException e) {
      remove(processInstanceId, stored);
      throw e;
    }
  }

  /** Best effort: used to undo {@link #store} when the form could not be submitted after all. */
  public void remove(String processInstanceId, List<String> documentIds) {
    for (String id : documentIds) {
      try {
        store.delete(processInstanceId, id);
      } catch (RuntimeException e) {
        LOG.warn("Could not remove document {} of {}: {}", id, processInstanceId, e.getMessage());
      }
    }
  }

  static List<DocumentType> loadTypes(ObjectMapper objectMapper) {
    try (InputStream in = new ClassPathResource(TYPES_RESOURCE).getInputStream()) {
      List<DocumentType> types = new ArrayList<>();
      for (JsonNode type : objectMapper.readTree(in).path("types")) {
        if (type.path("employee").asBoolean(false)) {
          types.add(new DocumentType(type.path("id").asText(), type.path("label").asText()));
        }
      }
      return List.copyOf(types);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + TYPES_RESOURCE, e);
    }
  }

  private static byte[] bytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
