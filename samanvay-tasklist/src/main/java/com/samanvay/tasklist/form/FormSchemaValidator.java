package com.samanvay.tasklist.form;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.samanvay.tasklist.common.ApiError;
import com.samanvay.tasklist.common.ApiException;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Server-side validation of submitted form data against the stored JSON Schema (draft-07, the draft RJSF
 * validates with on the client). Unknown properties are always rejected so clients cannot set arbitrary
 * process variables.
 */
@Component
public class FormSchemaValidator {

  private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
  private final ObjectMapper objectMapper;

  public FormSchemaValidator(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<ApiError.FieldError> validate(String schemaJson, JsonNode formData) {
    JsonSchema schema = factory.getSchema(strict(parse(schemaJson)));
    return schema.validate(formData).stream()
        .map(this::toFieldError)
        .sorted(Comparator.comparing(ApiError.FieldError::field))
        .toList();
  }

  /** Rejects a schema that is not valid JSON or cannot be compiled. Used before publishing. */
  public void assertUsable(String schemaJson) {
    JsonNode node = parse(schemaJson);
    if (!node.isObject() || !"object".equals(node.path("type").asText())) {
      throw ApiException.badRequest("INVALID_SCHEMA", "The form schema must be a JSON Schema with \"type\": \"object\".");
    }
    try {
      factory.getSchema(node);
    } catch (RuntimeException e) {
      throw ApiException.badRequest("INVALID_SCHEMA", "The form schema could not be compiled: " + e.getMessage());
    }
  }

  private JsonNode strict(JsonNode schema) {
    if (schema.isObject() && !schema.has("additionalProperties")) {
      ObjectNode copy = ((ObjectNode) schema).deepCopy();
      copy.put("additionalProperties", false);
      return copy;
    }
    return schema;
  }

  private JsonNode parse(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException e) {
      throw ApiException.badRequest("INVALID_SCHEMA", "The form schema is not valid JSON.");
    }
  }

  private ApiError.FieldError toFieldError(ValidationMessage message) {
    String location = message.getInstanceLocation() == null ? "$" : message.getInstanceLocation().toString();
    String field = location.startsWith("$.") ? location.substring(2) : location.equals("$") ? "" : location;
    Object[] args = message.getArguments();
    if (field.isEmpty() && "required".equals(message.getType()) && args != null && args.length > 0) {
      field = String.valueOf(args[0]);
    }
    if (field.isEmpty() && "additionalProperties".equals(message.getType()) && args != null && args.length > 0) {
      field = String.valueOf(args[0]);
    }
    String text = message.getMessage();
    int separator = text.indexOf(": ");
    if (separator > 0 && text.startsWith("$")) {
      text = text.substring(separator + 2);
    }
    return new ApiError.FieldError(field, text);
  }
}
