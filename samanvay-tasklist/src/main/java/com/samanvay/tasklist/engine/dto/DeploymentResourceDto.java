package com.samanvay.tasklist.engine.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One file of an engine deployment (GET /deployment/{id}/resources). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeploymentResourceDto(String id, String name, String deploymentId) {
}
