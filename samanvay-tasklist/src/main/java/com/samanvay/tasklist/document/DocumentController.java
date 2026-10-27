package com.samanvay.tasklist.document;

import com.samanvay.tasklist.auth.TasklistUser;
import com.samanvay.tasklist.document.DocumentDtos.DocumentView;
import com.samanvay.tasklist.document.DocumentDtos.TaskDocuments;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Documents of the process a task belongs to. */
@RestController
@RequestMapping("/api/tasks/{taskId}/documents")
public class DocumentController {

  private final DocumentService service;

  public DocumentController(DocumentService service) {
    this.service = service;
  }

  @GetMapping
  public TaskDocuments list(@PathVariable String taskId, @AuthenticationPrincipal TasklistUser user) {
    return service.forTask(taskId, user);
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public DocumentView upload(@PathVariable String taskId, @RequestPart("file") MultipartFile file,
      @RequestParam("documentType") String documentType, @AuthenticationPrincipal TasklistUser user) {
    return service.upload(taskId, file, documentType, user);
  }

  /** The file itself, for preview; the content type was verified on upload. */
  @GetMapping("/{documentId}/content")
  public ResponseEntity<byte[]> content(@PathVariable String taskId, @PathVariable String documentId,
      @AuthenticationPrincipal TasklistUser user) {
    StoredDocument.Content content = service.content(taskId, documentId, user);
    StoredDocument document = content.document();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(document.contentType()))
        .header("Content-Disposition", ContentDisposition.inline()
            .filename(document.fileName() == null ? "document" : document.fileName(), StandardCharsets.UTF_8)
            .build().toString())
        .cacheControl(CacheControl.noStore().cachePrivate())
        .body(content.data());
  }

  @DeleteMapping("/{documentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String taskId, @PathVariable String documentId,
      @AuthenticationPrincipal TasklistUser user) {
    service.delete(taskId, documentId, user);
  }
}
