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

/**
 * Maps a BPMN user task (and optionally its process) to a form, for tasks whose BPMN does not declare a
 * {@code samanvay:} form key. The latest published version of the form is used.
 */
@Entity
@Table(name = "form_binding")
public class FormBinding {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "form_definition_id")
  private FormDefinition formDefinition;

  @Column(name = "task_definition_key", nullable = false)
  private String taskDefinitionKey;

  @Column(name = "process_definition_key")
  private String processDefinitionKey;

  @Column(nullable = false)
  private int priority;

  protected FormBinding() {
  }

  public FormBinding(FormDefinition formDefinition, String taskDefinitionKey, String processDefinitionKey,
      int priority) {
    this.formDefinition = formDefinition;
    this.taskDefinitionKey = taskDefinitionKey;
    this.processDefinitionKey = processDefinitionKey;
    this.priority = priority;
  }

  public Long getId() {
    return id;
  }

  public FormDefinition getFormDefinition() {
    return formDefinition;
  }

  public String getTaskDefinitionKey() {
    return taskDefinitionKey;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public int getPriority() {
    return priority;
  }
}
