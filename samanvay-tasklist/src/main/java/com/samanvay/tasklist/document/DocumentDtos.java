package com.samanvay.tasklist.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.samanvay.tasklist.document.DocumentTypes.DocumentType;
import java.util.List;

/** Request and response bodies of the document API. */
public final class DocumentDtos {

  private DocumentDtos() {
  }

  /** What may be attached: off when no store is configured. */
  public record DocumentOptions(boolean enabled, List<DocumentType> types, long maxFileSize, int maxFiles,
      List<String> contentTypes) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record DocumentView(
      String id,
      String type,
      String typeLabel,
      String fileName,
      String contentType,
      long size,
      String uploadedBy,
      String uploadedByName,
      String uploadedAt,
      String step,
      boolean deletable) {

    static DocumentView of(StoredDocument d, boolean deletable) {
      return new DocumentView(d.id(), d.type(), d.typeLabel(), d.fileName(), d.contentType(), d.size(),
          d.uploadedBy(), d.uploadedByName(), d.uploadedAt().toString(), d.step(), deletable);
    }
  }

  /** The documents of a task's process, and whether the user may attach more now. */
  public record TaskDocuments(DocumentOptions options, boolean canUpload, List<DocumentView> documents) {
  }
}
