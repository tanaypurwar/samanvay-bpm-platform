package com.samanvay.tasklist.form;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samanvay.tasklist.engine.CamundaClient;
import com.samanvay.tasklist.engine.dto.DeploymentResourceDto;
import com.samanvay.tasklist.engine.dto.ProcessDefinitionDto;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Loads forms deployed together with the BPMN, referenced as {@code samanvay:deployment:<resource name>}. A
 * process application then ships its forms with its processes and needs no form admin. The file is JSON:
 * <pre>{ "name": "...", "schema": { JSON Schema }, "uiSchema": { optional } }</pre>
 * Deployments never change, so loaded forms are cached by deployment and resource name.
 */
@Component
public class DeploymentForms {

  public static final String PREFIX = "deployment:";
  private static final Logger LOG = LoggerFactory.getLogger(DeploymentForms.class);
  private static final int MAX_CACHED = 500;

  private final CamundaClient engine;
  private final ObjectMapper objectMapper;
  private final Map<String, Optional<ResolvedForm>> cache = new ConcurrentHashMap<>();

  public DeploymentForms(CamundaClient engine, ObjectMapper objectMapper) {
    this.engine = engine;
    this.objectMapper = objectMapper;
  }

  public Optional<ResolvedForm> load(ProcessDefinitionDto definition, String resourceName) {
    if (definition == null || definition.deploymentId() == null) {
      return Optional.empty();
    }
    String cacheKey = definition.deploymentId() + "/" + resourceName;
    Optional<ResolvedForm> cached = cache.get(cacheKey);
    if (cached != null) {
      return cached;
    }
    Optional<ResolvedForm> form = fetch(definition, resourceName);
    if (cache.size() >= MAX_CACHED) {
      cache.clear();
    }
    cache.put(cacheKey, form);
    return form;
  }

  private Optional<ResolvedForm> fetch(ProcessDefinitionDto definition, String resourceName) {
    List<DeploymentResourceDto> resources = engine.getDeploymentResources(definition.deploymentId());
    Optional<DeploymentResourceDto> resource = resources == null ? Optional.empty()
        : resources.stream().filter(r -> resourceName.equals(r.name())).findFirst();
    if (resource.isEmpty()) {
      LOG.warn("Form '{}' is not part of deployment {} ({})", resourceName, definition.deploymentId(),
          definition.key());
      return Optional.empty();
    }
    try {
      JsonNode file = objectMapper.readTree(engine.getDeploymentResourceData(definition.deploymentId(),
          resource.get().id()));
      JsonNode schema = file.path("schema");
      if (!schema.isObject()) {
        LOG.warn("Form '{}' in deployment {} has no \"schema\" object", resourceName, definition.deploymentId());
        return Optional.empty();
      }
      JsonNode uiSchema = file.path("uiSchema");
      String name = file.path("name").asText(schema.path("title").asText(resourceName));
      return Optional.of(new ResolvedForm(null, resourceName, name, definition.version(), schema.toString(),
          uiSchema.isObject() ? uiSchema.toString() : null, true));
    } catch (IOException e) {
      LOG.warn("Form '{}' in deployment {} is not valid JSON: {}", resourceName, definition.deploymentId(),
          e.getMessage());
      return Optional.empty();
    }
  }
}
