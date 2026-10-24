package com.samanvay.tasklist.engine.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Response of POST /identity/verify. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthenticationResultDto(String authenticatedUser, boolean authenticated) {
}
