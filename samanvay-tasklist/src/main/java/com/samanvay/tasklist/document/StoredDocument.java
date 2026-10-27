package com.samanvay.tasklist.document;

import java.time.Instant;

/**
 * A document attached to a process instance. Stored as one object per document under
 * {@code process-instances/<processInstanceId>/documents/<id>}, with these fields as object metadata, so that
 * process applications (e.g. Eternal HR's joining form) can attach documents by writing the same layout.
 *
 * @param uploadedBy the user id, or empty for someone without an account (e.g. a new employee)
 * @param step       where it was attached, e.g. the task name
 */
public record StoredDocument(
    String id,
    String type,
    String typeLabel,
    String fileName,
    String contentType,
    long size,
    String uploadedBy,
    String uploadedByName,
    Instant uploadedAt,
    String step) {

  /** The document and its bytes. */
  public record Content(StoredDocument document, byte[] data) {
  }
}
