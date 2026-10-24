package com.samanvay.tasklist.form;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samanvay.tasklist.common.ApiException;
import com.samanvay.tasklist.engine.dto.VariableValueDto;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Converts between RJSF form data (JSON) and Camunda typed process variables, driven by the form's JSON
 * Schema (TRD section 5.3).
 *
 * <ul>
 *   <li>{@code string} to String; with {@code format: date} or {@code date-time} to Date</li>
 *   <li>{@code integer} to Long, {@code number} to Double, {@code boolean} to Boolean</li>
 *   <li>{@code array} / {@code object} to Json</li>
 * </ul>
 * A property may set {@code "x-camunda-type"} (e.g. {@code "Integer"}, {@code "String"}) to override the
 * default. Properties marked {@code readOnly} are display-only and never written back.
 */
@Component
public class VariableMapper {

  /** Default date format of the Camunda REST API. */
  static final DateTimeFormatter ENGINE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

  private final ObjectMapper objectMapper;

  public VariableMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Form data to engine variables. Expects data already validated against {@code schema}. */
  public Map<String, VariableValueDto> toVariables(JsonNode schema, JsonNode formData) {
    Map<String, VariableValueDto> variables = new LinkedHashMap<>();
    JsonNode properties = schema.path("properties");
    Iterator<Map.Entry<String, JsonNode>> fields = formData.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> field = fields.next();
      JsonNode property = properties.get(field.getKey());
      if (property == null || property.path("readOnly").asBoolean(false)) {
        continue;
      }
      variables.put(field.getKey(), toVariable(field.getKey(), property, field.getValue()));
    }
    return variables;
  }

  /** Engine variables to initial form data, limited to the properties the schema declares. */
  public Map<String, Object> toFormData(JsonNode schema, Map<String, VariableValueDto> variables) {
    Map<String, Object> formData = new LinkedHashMap<>();
    if (variables == null) {
      return formData;
    }
    Iterator<Map.Entry<String, JsonNode>> properties = schema.path("properties").fields();
    while (properties.hasNext()) {
      Map.Entry<String, JsonNode> property = properties.next();
      VariableValueDto variable = variables.get(property.getKey());
      if (variable == null || variable.value() == null) {
        continue;
      }
      formData.put(property.getKey(), toFormValue(property.getValue(), variable));
    }
    return formData;
  }

  /** Engine variables to plain values, for showing a task that has no configured form. */
  public Map<String, Object> toDisplayValues(Map<String, VariableValueDto> variables) {
    Map<String, Object> values = new LinkedHashMap<>();
    if (variables != null) {
      variables.forEach((name, variable) -> {
        String type = variable.type() == null ? "" : variable.type();
        if (type.equals("Bytes") || type.equals("File") || type.equals("Object")) {
          values.put(name, "[" + type + "]");
        } else {
          values.put(name, variable.value());
        }
      });
    }
    return values;
  }

  private VariableValueDto toVariable(String name, JsonNode property, JsonNode value) {
    if (value == null || value.isNull()) {
      return VariableValueDto.of(null, "Null");
    }
    String override = property.path("x-camunda-type").asText(null);
    String schemaType = property.path("type").asText(inferType(value));
    String format = property.path("format").asText("");

    if (override != null) {
      return switch (override) {
        case "String" -> VariableValueDto.of(value.asText(), "String");
        case "Integer" -> VariableValueDto.of(value.asInt(), "Integer");
        case "Long" -> VariableValueDto.of(value.asLong(), "Long");
        case "Short" -> VariableValueDto.of((short) value.asInt(), "Short");
        case "Double" -> VariableValueDto.of(value.asDouble(), "Double");
        case "Boolean" -> VariableValueDto.of(value.asBoolean(), "Boolean");
        case "Date" -> VariableValueDto.of(toEngineDate(name, value.asText(), format), "Date");
        case "Json" -> VariableValueDto.of(value.toString(), "Json");
        default -> throw ApiException.badRequest("INVALID_FORM_CONFIG",
            "Field '" + name + "' uses unsupported x-camunda-type '" + override + "'.");
      };
    }

    return switch (schemaType) {
      case "string" -> format.equals("date") || format.equals("date-time")
          ? VariableValueDto.of(toEngineDate(name, value.asText(), format), "Date")
          : VariableValueDto.of(value.asText(), "String");
      case "integer" -> VariableValueDto.of(value.asLong(), "Long");
      case "number" -> VariableValueDto.of(value.asDouble(), "Double");
      case "boolean" -> VariableValueDto.of(value.asBoolean(), "Boolean");
      case "array", "object" -> VariableValueDto.of(value.toString(), "Json");
      default -> VariableValueDto.of(value.asText(), "String");
    };
  }

  private Object toFormValue(JsonNode property, VariableValueDto variable) {
    String type = variable.type() == null ? "" : variable.type();
    Object value = variable.value();
    switch (type) {
      case "Date": {
        OffsetDateTime dateTime = OffsetDateTime.parse(value.toString(), ENGINE_DATE);
        return "date".equals(property.path("format").asText())
            ? dateTime.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate().toString()
            : dateTime.toString();
      }
      case "Json":
        return readJson(value.toString());
      case "Object": {
        Object format = variable.valueInfo() == null ? null : variable.valueInfo().get("serializationDataFormat");
        return "application/json".equals(format) ? readJson(value.toString()) : null;
      }
      default:
        return value;
    }
  }

  private String toEngineDate(String name, String text, String format) {
    try {
      OffsetDateTime dateTime = format.equals("date") || text.length() == 10
          ? LocalDate.parse(text).atStartOfDay().atOffset(ZoneOffset.UTC)
          : OffsetDateTime.parse(text);
      return ENGINE_DATE.format(dateTime);
    } catch (DateTimeParseException e) {
      throw ApiException.badRequest("INVALID_DATE", "Field '" + name + "' is not a valid date.");
    }
  }

  private JsonNode readJson(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  private static String inferType(JsonNode value) {
    if (value.isBoolean()) {
      return "boolean";
    }
    if (value.isIntegralNumber()) {
      return "integer";
    }
    if (value.isNumber()) {
      return "number";
    }
    if (value.isArray()) {
      return "array";
    }
    if (value.isObject()) {
      return "object";
    }
    return "string";
  }
}
