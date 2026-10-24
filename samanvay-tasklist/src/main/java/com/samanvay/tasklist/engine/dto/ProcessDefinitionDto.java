package com.samanvay.tasklist.engine.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProcessDefinitionDto(String id, String key, String name, Integer version) {
}
