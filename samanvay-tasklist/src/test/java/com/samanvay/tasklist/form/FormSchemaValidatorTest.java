package com.samanvay.tasklist.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samanvay.tasklist.common.ApiError;
import com.samanvay.tasklist.common.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Uses the shipped example form so the example stays valid. */
class FormSchemaValidatorTest {

  private final ObjectMapper json = new ObjectMapper();
  private final FormSchemaValidator validator = new FormSchemaValidator(json);
  private final String schema = exampleSchema();

  @Test
  void acceptsValidApproval() {
    List<ApiError.FieldError> errors = validator.validate(schema, parse("""
        {"invoiceNumber":"INV-1","decision":"approve","costCenter":"IT","paymentDueDate":"2026-11-01"}"""));

    assertThat(errors).isEmpty();
  }

  @Test
  void requiresDecision() {
    assertThat(validator.validate(schema, parse("{}")))
        .extracting(ApiError.FieldError::field)
        .contains("decision");
  }

  @Test
  void conditionalRuleRequiresCommentWhenRejecting() {
    assertThat(validator.validate(schema, parse("{\"decision\":\"reject\"}")))
        .extracting(ApiError.FieldError::field)
        .contains("comment");
    assertThat(validator.validate(schema, parse("{\"decision\":\"reject\",\"comment\":\"too short\"}")))
        .extracting(ApiError.FieldError::field)
        .contains("comment");
    assertThat(validator.validate(schema, parse("{\"decision\":\"reject\",\"comment\":\"Duplicate invoice\"}")))
        .isEmpty();
  }

  @Test
  void rejectsFieldsNotInTheForm() {
    assertThat(validator.validate(schema, parse("""
        {"decision":"reject","comment":"Duplicate invoice","approvedBy":"someone-else"}""")))
        .extracting(ApiError.FieldError::field)
        .contains("approvedBy");
  }

  @Test
  void rejectsUnusableSchemaOnPublish() {
    assertThatThrownBy(() -> validator.assertUsable("{not json")).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> validator.assertUsable("{\"type\":\"string\"}")).isInstanceOf(ApiException.class);
    validator.assertUsable(schema);
  }

  private String exampleSchema() {
    try {
      JsonNode example = json.readTree(Files.readString(Path.of("examples/invoice-approval.form.json")));
      return json.writeValueAsString(example.get("schema"));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private JsonNode parse(String text) {
    try {
      return json.readTree(text);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }
}
