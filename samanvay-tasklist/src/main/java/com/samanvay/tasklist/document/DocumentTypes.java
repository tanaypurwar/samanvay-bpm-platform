package com.samanvay.tasklist.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samanvay.tasklist.engine.CamundaClient;
import com.samanvay.tasklist.engine.dto.DeploymentResourceDto;
import com.samanvay.tasklist.engine.dto.ProcessDefinitionDto;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The kinds of document a process accepts, from {@code document-types.json} in the process's deployment:
 * <pre>{ "types": [ { "id": "pan", "label": "PAN card" }, ... ] }</pre>
 * A process without the file accepts any document as "Document". Cached per deployment, which never changes.
 */
@Component
public class DocumentTypes {

  public static final String RESOURCE = "document-types.json";
  static final List<DocumentType> DEFAULT = List.of(new DocumentType("document", "Document"));

  private static final Logger LOG = LoggerFactory.getLogger(DocumentTypes.class);
  private static final int MAX_CACHED = 200;

  public record DocumentType(String id, String label) {
  }

  private final CamundaClient engine;
  private final ObjectMapper objectMapper;
  private final Map<String, List<DocumentType>> cache = new ConcurrentHashMap<>();

  public DocumentTypes(CamundaClient engine, ObjectMapper objectMapper) {
    this.engine = engine;
    this.objectMapper = objectMapper;
  }

  public List<DocumentType> of(ProcessDefinitionDto definition) {
    if (definition == null || definition.deploymentId() == null) {
      return DEFAULT;
    }
    List<DocumentType> cached = cache.get(definition.deploymentId());
    if (cached != null) {
      return cached;
    }
    List<DocumentType> types = load(definition.deploymentId());
    if (cache.size() >= MAX_CACHED) {
      cache.clear();
    }
    cache.put(definition.deploymentId(), types);
    return types;
  }

  private List<DocumentType> load(String deploymentId) {
    List<DeploymentResourceDto> resources = engine.getDeploymentResources(deploymentId);
    DeploymentResourceDto resource = resources == null ? null
        : resources.stream().filter(r -> RESOURCE.equals(r.name())).findFirst().orElse(null);
    if (resource == null) {
      return DEFAULT;
    }
    try {
      JsonNode file = objectMapper.readTree(engine.getDeploymentResourceData(deploymentId, resource.id()));
      List<DocumentType> types = new ArrayList<>();
      for (JsonNode type : file.path("types")) {
        String id = type.path("id").asText("");
        if (!id.isBlank()) {
          types.add(new DocumentType(id, type.path("label").asText(id)));
        }
      }
      return types.isEmpty() ? DEFAULT : List.copyOf(types);
    } catch (IOException e) {
      LOG.warn("{} in deployment {} is not valid JSON: {}", RESOURCE, deploymentId, e.getMessage());
      return DEFAULT;
    }
  }
}
