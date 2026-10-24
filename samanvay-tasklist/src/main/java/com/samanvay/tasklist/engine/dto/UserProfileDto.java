package com.samanvay.tasklist.engine.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserProfileDto(String id, String firstName, String lastName, String email) {
}
