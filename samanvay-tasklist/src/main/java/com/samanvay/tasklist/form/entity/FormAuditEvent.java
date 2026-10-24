package com.samanvay.tasklist.form.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Who changed which form configuration, and when (TRD FR-11). */
@Entity
@Table(name = "form_audit_event")
public class FormAuditEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "form_definition_id", nullable = false)
  private Long formDefinitionId;

  @Column(name = "form_version_id")
  private Long formVersionId;

  @Column(nullable = false)
  private String action;

  @Column(nullable = false)
  private String actor;

  @Column(name = "occurred_at", nullable = false)
  private OffsetDateTime occurredAt;

  @Column(name = "correlation_id")
  private String correlationId;

  protected FormAuditEvent() {
  }

  public FormAuditEvent(Long formDefinitionId, Long formVersionId, String action, String actor,
      String correlationId) {
    this.formDefinitionId = formDefinitionId;
    this.formVersionId = formVersionId;
    this.action = action;
    this.actor = actor;
    this.correlationId = correlationId;
    this.occurredAt = OffsetDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public Long getFormDefinitionId() {
    return formDefinitionId;
  }

  public Long getFormVersionId() {
    return formVersionId;
  }

  public String getAction() {
    return action;
  }

  public String getActor() {
    return actor;
  }

  public OffsetDateTime getOccurredAt() {
    return occurredAt;
  }

  public String getCorrelationId() {
    return correlationId;
  }
}
