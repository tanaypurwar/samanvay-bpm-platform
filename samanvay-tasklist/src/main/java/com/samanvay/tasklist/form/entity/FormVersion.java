package com.samanvay.tasklist.form.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.Immutable;

/** A published, immutable form schema. Edits create a new version so tasks in progress are unaffected. */
@Entity
@Immutable
@Table(name = "form_version")
public class FormVersion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "form_definition_id")
  private FormDefinition formDefinition;

  @Column(nullable = false)
  private int version;

  @Column(name = "schema_json", nullable = false)
  private String schemaJson;

  @Column(name = "ui_schema_json")
  private String uiSchemaJson;

  @Column(name = "published_at", nullable = false)
  private OffsetDateTime publishedAt;

  @Column(name = "published_by", nullable = false)
  private String publishedBy;

  protected FormVersion() {
  }

  public FormVersion(FormDefinition formDefinition, int version, String schemaJson, String uiSchemaJson,
      String publishedBy) {
    this.formDefinition = formDefinition;
    this.version = version;
    this.schemaJson = schemaJson;
    this.uiSchemaJson = uiSchemaJson;
    this.publishedBy = publishedBy;
    this.publishedAt = OffsetDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public FormDefinition getFormDefinition() {
    return formDefinition;
  }

  public int getVersion() {
    return version;
  }

  public String getSchemaJson() {
    return schemaJson;
  }

  public String getUiSchemaJson() {
    return uiSchemaJson;
  }

  public OffsetDateTime getPublishedAt() {
    return publishedAt;
  }

  public String getPublishedBy() {
    return publishedBy;
  }
}
