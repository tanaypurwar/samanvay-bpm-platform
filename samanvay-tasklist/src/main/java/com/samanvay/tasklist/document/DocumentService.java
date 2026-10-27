package com.samanvay.tasklist.document;

import com.samanvay.tasklist.auth.TasklistUser;
import com.samanvay.tasklist.common.ApiError;
import com.samanvay.tasklist.common.ApiException;
import com.samanvay.tasklist.document.DocumentDtos.DocumentOptions;
import com.samanvay.tasklist.document.DocumentDtos.DocumentView;
import com.samanvay.tasklist.document.DocumentDtos.TaskDocuments;
import com.samanvay.tasklist.document.DocumentTypes.DocumentType;
import com.samanvay.tasklist.engine.CamundaClient;
import com.samanvay.tasklist.engine.dto.ProcessDefinitionDto;
import com.samanvay.tasklist.engine.dto.TaskDto;
import com.samanvay.tasklist.task.TaskAccessGuard;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Documents attached to process instances (PDFs and images), kept in the {@link DocumentStore}. They are reached
 * through a task: whoever may see a task may see all documents of its process, so every step shows everything
 * attached so far. Attaching needs the task to be assigned to the user, like completing it. Users may remove
 * their own documents while working on a task; nobody can remove anyone else's.
 */
@Service
public class DocumentService {

  private static final Pattern DOCUMENT_ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
  private static final List<String> CONTENT_TYPES = List.of(FileCheck.PDF, FileCheck.JPEG, FileCheck.PNG);

  /** A checked file, ready to store. */
  public record Upload(String type, String typeLabel, String fileName, String contentType, byte[] data) {
  }

  private final DocumentStore store;
  private final DocumentProperties properties;
  private final DocumentTypes documentTypes;
  private final CamundaClient engine;
  private final TaskAccessGuard guard;
  private final Clock clock = Clock.systemUTC();

  public DocumentService(DocumentStore store, DocumentProperties properties, DocumentTypes documentTypes,
      CamundaClient engine, TaskAccessGuard guard) {
    this.store = store;
    this.properties = properties;
    this.documentTypes = documentTypes;
    this.engine = engine;
    this.guard = guard;
  }

  public DocumentOptions options(ProcessDefinitionDto definition) {
    return new DocumentOptions(properties.enabled(),
        properties.enabled() ? documentTypes.of(definition) : List.of(),
        properties.maxFileSize().toBytes(), properties.maxFilesPerUpload(), CONTENT_TYPES);
  }

  // documents of a task's process

  public TaskDocuments forTask(String taskId, TasklistUser user) {
    TaskDto task = guard.requireVisible(taskId, user);
    DocumentOptions options = options(definitionOf(task));
    boolean working = user.id().equals(task.assignee());
    List<DocumentView> documents = options.enabled()
        ? store.list(task.processInstanceId()).stream()
            .map(d -> DocumentView.of(d, working && user.id().equals(d.uploadedBy())))
            .toList()
        : List.of();
    return new TaskDocuments(options, working && options.enabled(), documents);
  }

  public DocumentView upload(String taskId, MultipartFile file, String type, TasklistUser user) {
    TaskDto task = guard.requireAssignedTo(taskId, user);
    requireEnabled();
    List<Upload> uploads = check(List.of(file), List.of(type == null ? "" : type), definitionOf(task));
    return DocumentView.of(store(task.processInstanceId(), uploads, stepOf(task), user).get(0), true);
  }

  public StoredDocument.Content content(String taskId, String documentId, TasklistUser user) {
    TaskDto task = guard.requireVisible(taskId, user);
    return find(task, documentId);
  }

  public void delete(String taskId, String documentId, TasklistUser user) {
    TaskDto task = guard.requireAssignedTo(taskId, user);
    StoredDocument document = find(task, documentId).document();
    if (!user.id().equals(document.uploadedBy())) {
      throw new ApiException(HttpStatus.FORBIDDEN, "NOT_YOUR_DOCUMENT", "You can only remove documents you attached.");
    }
    store.delete(task.processInstanceId(), documentId);
  }

  // used when starting a process with documents

  /** Checks every file before anything is stored or started; {@code types} pairs with {@code files} by position. */
  public List<Upload> check(List<MultipartFile> files, List<String> types, ProcessDefinitionDto definition) {
    if (files == null || files.isEmpty()) {
      return List.of();
    }
    requireEnabled();
    if (files.size() > properties.maxFilesPerUpload()) {
      throw invalid("files", "Attach at most " + properties.maxFilesPerUpload() + " documents at a time.");
    }
    List<DocumentType> allowed = documentTypes.of(definition);
    List<Upload> uploads = new ArrayList<>();
    for (int i = 0; i < files.size(); i++) {
      MultipartFile file = files.get(i);
      String name = file.getOriginalFilename();
      String typeId = types != null && i < types.size() ? types.get(i) : null;
      DocumentType type = allowed.stream().filter(t -> t.id().equals(typeId)).findFirst()
          .orElseThrow(() -> invalid("documentType", "Choose the document type of " + display(name) + "."));
      if (file.isEmpty()) {
        throw invalid("file", display(name) + " is empty.");
      }
      if (file.getSize() > properties.maxFileSize().toBytes()) {
        throw invalid("file", display(name) + " is larger than " + properties.maxFileSize().toMegabytes() + " MB.");
      }
      byte[] data = bytes(file);
      String contentType = FileCheck.contentType(data)
          .orElseThrow(() -> invalid("file", display(name) + " is not a " + FileCheck.ALLOWED + " file."));
      uploads.add(new Upload(type.id(), type.label(), FileCheck.fileName(name, contentType), contentType, data));
    }
    return uploads;
  }

  public List<StoredDocument> store(String processInstanceId, List<Upload> uploads, String step, TasklistUser user) {
    List<StoredDocument> stored = new ArrayList<>();
    for (Upload upload : uploads) {
      StoredDocument document = new StoredDocument(UUID.randomUUID().toString(), upload.type(), upload.typeLabel(),
          upload.fileName(), upload.contentType(), upload.data().length, user.id(), user.displayName(),
          clock.instant(), step);
      store.put(processInstanceId, document, upload.data());
      stored.add(document);
    }
    return stored;
  }

  private StoredDocument.Content find(TaskDto task, String documentId) {
    if (documentId == null || !DOCUMENT_ID.matcher(documentId).matches()) {
      throw ApiException.notFound("Document not found.");
    }
    return store.get(task.processInstanceId(), documentId)
        .orElseThrow(() -> ApiException.notFound("Document not found."));
  }

  private ProcessDefinitionDto definitionOf(TaskDto task) {
    return task.processDefinitionId() == null ? null : engine.getProcessDefinition(task.processDefinitionId());
  }

  private static String stepOf(TaskDto task) {
    return task.name() == null || task.name().isBlank() ? task.taskDefinitionKey() : task.name();
  }

  private void requireEnabled() {
    if (!properties.enabled()) {
      throw ApiException.conflict("DOCUMENTS_OFF", "Attaching documents is not set up on this server.");
    }
  }

  private static byte[] bytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String display(String fileName) {
    return fileName == null || fileName.isBlank() ? "The file" : "\"" + FileCheck.fileName(fileName, null) + "\"";
  }

  private static ApiException invalid(String field, String message) {
    return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DOCUMENT", message,
        List.of(new ApiError.FieldError(field, message)));
  }
}
