package com.samanvay.tasklist.form;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samanvay.tasklist.common.ApiError;
import com.samanvay.tasklist.common.ApiException;
import com.samanvay.tasklist.engine.dto.VariableValueDto;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Validates submitted form data against a resolved form and turns it into engine variables. */
@Component
public class FormSubmissions {

  private final FormSchemaValidator validator;
  private final VariableMapper variableMapper;
  private final ObjectMapper objectMapper;

  public FormSubmissions(FormSchemaValidator validator, VariableMapper variableMapper, ObjectMapper objectMapper) {
    this.validator = validator;
    this.variableMapper = variableMapper;
    this.objectMapper = objectMapper;
  }

  /** Throws 422 with field errors when the data does not match the form's schema. */
  public Map<String, VariableValueDto> toVariables(ResolvedForm form, JsonNode formData) {
    List<ApiError.FieldError> errors = validator.validate(form.schemaJson(), formData);
    if (!errors.isEmpty()) {
      throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED",
          "Please correct the highlighted fields.", errors);
    }
    return variableMapper.toVariables(read(form.schemaJson()), formData);
  }

  /** Request form data, defaulting to an empty object. */
  public JsonNode objectOrEmpty(JsonNode formData) {
    JsonNode data = formData == null ? objectMapper.createObjectNode() : formData;
    if (!data.isObject()) {
      throw ApiException.badRequest("BAD_REQUEST", "Form data must be a JSON object.");
    }
    return data;
  }

  public JsonNode read(String json) {
    if (json == null) {
      return null;
    }
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Stored form JSON is invalid", e);
    }
  }
}
