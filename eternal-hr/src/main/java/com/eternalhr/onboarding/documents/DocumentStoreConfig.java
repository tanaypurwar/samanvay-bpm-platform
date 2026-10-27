package com.eternalhr.onboarding.documents;

import com.eternalhr.onboarding.config.EternalHrProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class DocumentStoreConfig {

  private static final Logger LOG = LoggerFactory.getLogger(DocumentStoreConfig.class);

  @Bean
  DocumentStore documentStore(EternalHrProperties properties) {
    EternalHrProperties.DocumentStore config = properties.documentStore();
    if (config == null || !config.enabled()) {
      LOG.info("Document uploads are off (eternal-hr.document-store.bucket is not set)");
      return new DocumentStore() {
        @Override
        public void put(String processInstanceId, Document document, byte[] data) {
          throw new IllegalStateException("Document uploads are off");
        }

        @Override
        public void delete(String processInstanceId, String documentId) {
          throw new IllegalStateException("Document uploads are off");
        }
      };
    }
    LOG.info("Employee documents go to bucket {}{}", config.bucket(),
        config.endpoint() == null || config.endpoint().isBlank() ? "" : " at " + config.endpoint());
    return S3DocumentStore.create(config);
  }
}
