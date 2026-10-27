package com.eternalhr.onboarding.documents;

import java.time.Instant;

/**
 * Writes documents to a process instance's document folder in the platform's bucket. The Samanvay tasklist lists
 * and previews them on every task of the process, so the layout must match the tasklist's:
 * <pre>
 * process-instances/{processInstanceId}/documents/{documentId}
 *   Content-Type, metadata (URL-encoded): document-type, document-type-label, file-name, uploaded-by,
 *   uploaded-by-name, uploaded-at (ISO-8601), step
 * </pre>
 */
public interface DocumentStore {

  /** @param uploadedBy a user id, or empty for someone without an account */
  record Document(String id, String type, String typeLabel, String fileName, String contentType,
      String uploadedBy, String uploadedByName, Instant uploadedAt, String step) {
  }

  void put(String processInstanceId, Document document, byte[] data);

  void delete(String processInstanceId, String documentId);
}
