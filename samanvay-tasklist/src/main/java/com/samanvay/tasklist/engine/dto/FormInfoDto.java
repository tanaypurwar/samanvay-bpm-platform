package com.samanvay.tasklist.engine.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Response of GET /task/{id}/form: the form key declared on the BPMN user task. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FormInfoDto(String key, String contextPath) {
}
