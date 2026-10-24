package com.samanvay.tasklist.engine.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Subset of the engine REST task representation used by the tasklist. Dates are kept as engine-formatted strings. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskDto(
    String id,
    String name,
    String description,
    String assignee,
    String owner,
    String created,
    String due,
    String followUp,
    Integer priority,
    String processDefinitionId,
    String processInstanceId,
    String taskDefinitionKey,
    String formKey,
    String tenantId) {
}
