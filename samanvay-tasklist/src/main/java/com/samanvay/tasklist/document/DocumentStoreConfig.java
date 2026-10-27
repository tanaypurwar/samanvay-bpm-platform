package com.samanvay.tasklist.document;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class DocumentStoreConfig {

  private static final Logger LOG = LoggerFactory.getLogger(DocumentStoreConfig.class);

  @Bean
  DocumentStore documentStore(DocumentProperties properties) {
    if (!properties.enabled()) {
      LOG.info("Process documents are off (samanvay.documents.bucket is not set)");
      return new NoDocumentStore();
    }
    LOG.info("Process documents in bucket {}{}", properties.bucket(),
        DocumentProperties.blank(properties.endpoint()) ? "" : " at " + properties.endpoint());
    return S3DocumentStore.create(properties);
  }

  /** Used while documents are off; the API refuses uploads before reaching it. */
  static class NoDocumentStore implements DocumentStore {

    @Override
    public List<StoredDocument> list(String processInstanceId) {
      return List.of();
    }

    @Override
    public Optional<StoredDocument.Content> get(String processInstanceId, String documentId) {
      return Optional.empty();
    }

    @Override
    public void put(String processInstanceId, StoredDocument document, byte[] data) {
      throw new IllegalStateException("Process documents are off");
    }

    @Override
    public void delete(String processInstanceId, String documentId) {
      throw new IllegalStateException("Process documents are off");
    }
  }
}
