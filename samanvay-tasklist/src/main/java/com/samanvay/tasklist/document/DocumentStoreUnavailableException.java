package com.samanvay.tasklist.document;

/** The document store (S3) could not be reached or refused the request. */
public class DocumentStoreUnavailableException extends RuntimeException {

  public DocumentStoreUnavailableException(Throwable cause) {
    super("Document store is unavailable", cause);
  }
}
