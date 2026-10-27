package com.samanvay.tasklist.document;

import java.util.List;
import java.util.Optional;

/** Stores the documents of process instances. */
public interface DocumentStore {

  /** Oldest first. */
  List<StoredDocument> list(String processInstanceId);

  Optional<StoredDocument.Content> get(String processInstanceId, String documentId);

  void put(String processInstanceId, StoredDocument document, byte[] data);

  void delete(String processInstanceId, String documentId);
}
