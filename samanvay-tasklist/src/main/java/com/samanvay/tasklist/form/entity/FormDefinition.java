package com.samanvay.tasklist.form.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Logical form identity plus the editable draft. Published schemas live in {@link FormVersion}. */
@Entity
@Table(name = "form_definition")
public class FormDefinition {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "form_key", nullable = false, unique = true)
  private String formKey;

  @Column(nullable = false)
  private String name;

  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private FormStatus status;

  @Column(name = "draft_schema_json")
  private String draftSchemaJson;

  @Column(name = "draft_ui_schema_json")
  private String draftUiSchemaJson;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected FormDefinition() {
  }

  public FormDefinition(String formKey, String name, String description) {
    this.formKey = formKey;
    this.name = name;
    this.description = description;
    this.status = FormStatus.DRAFT;
    this.createdAt = OffsetDateTime.now();
    this.updatedAt = this.createdAt;
  }

  public void updateDraft(String name, String description, String schemaJson, String uiSchemaJson) {
    this.name = name;
    this.description = description;
    this.draftSchemaJson = schemaJson;
    this.draftUiSchemaJson = uiSchemaJson;
    this.updatedAt = OffsetDateTime.now();
  }

  public void markPublished() {
    this.status = FormStatus.PUBLISHED;
    this.updatedAt = OffsetDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public String getFormKey() {
    return formKey;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public FormStatus getStatus() {
    return status;
  }

  public String getDraftSchemaJson() {
    return draftSchemaJson;
  }

  public String getDraftUiSchemaJson() {
    return draftUiSchemaJson;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
