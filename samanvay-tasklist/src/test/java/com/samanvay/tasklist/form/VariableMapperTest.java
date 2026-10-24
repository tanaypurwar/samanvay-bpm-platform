package com.samanvay.tasklist.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samanvay.tasklist.common.ApiException;
import com.samanvay.tasklist.engine.dto.VariableValueDto;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VariableMapperTest {

  private final ObjectMapper json = new ObjectMapper();
  private final VariableMapper mapper = new VariableMapper(json);

  private final JsonNode schema = parse("""
      {"type":"object","properties":{
        "name":{"type":"string"},
        "count":{"type":"integer"},
        "amount":{"type":"number"},
        "ok":{"type":"boolean"},
        "due":{"type":"string","format":"date"},
        "at":{"type":"string","format":"date-time"},
        "tags":{"type":"array","items":{"type":"string"}},
        "small":{"type":"integer","x-camunda-type":"Integer"},
        "shown":{"type":"string","readOnly":true}
      }}""");

  @Test
  void mapsSchemaTypesToEngineTypes() {
    Map<String, VariableValueDto> vars = mapper.toVariables(schema, parse("""
        {"name":"Asha","count":3,"amount":12.5,"ok":true,"due":"2026-10-23",
         "at":"2026-10-23T10:15:30Z","tags":["a","b"],"small":7}"""));

    assertThat(vars.get("name")).isEqualTo(VariableValueDto.of("Asha", "String"));
    assertThat(vars.get("count")).isEqualTo(VariableValueDto.of(3L, "Long"));
    assertThat(vars.get("amount")).isEqualTo(VariableValueDto.of(12.5, "Double"));
    assertThat(vars.get("ok")).isEqualTo(VariableValueDto.of(true, "Boolean"));
    assertThat(vars.get("due")).isEqualTo(VariableValueDto.of("2026-10-23T00:00:00.000+0000", "Date"));
    assertThat(vars.get("at")).isEqualTo(VariableValueDto.of("2026-10-23T10:15:30.000+0000", "Date"));
    assertThat(vars.get("tags")).isEqualTo(VariableValueDto.of("[\"a\",\"b\"]", "Json"));
    assertThat(vars.get("small")).isEqualTo(VariableValueDto.of(7, "Integer"));
  }

  @Test
  void neverWritesReadOnlyOrUnknownFields() {
    Map<String, VariableValueDto> vars = mapper.toVariables(schema, parse("""
        {"shown":"changed","unknown":"x","name":"ok"}"""));

    assertThat(vars).containsOnlyKeys("name");
  }

  @Test
  void mapsNullToNullVariable() {
    Map<String, VariableValueDto> vars = mapper.toVariables(schema, parse("{\"name\":null}"));

    assertThat(vars.get("name")).isEqualTo(VariableValueDto.of(null, "Null"));
  }

  @Test
  void rejectsInvalidDate() {
    assertThatThrownBy(() -> mapper.toVariables(schema, parse("{\"due\":\"23/10/2026\"}")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("due");
  }

  @Test
  void buildsFormDataFromVariablesLimitedToSchema() {
    Map<String, Object> data = mapper.toFormData(schema, Map.of(
        "name", VariableValueDto.of("Asha", "String"),
        "due", VariableValueDto.of("2026-10-23T00:00:00.000+0000", "Date"),
        "tags", VariableValueDto.of("[\"a\"]", "Json"),
        "secret", VariableValueDto.of("not in form", "String")));

    assertThat(data).containsOnlyKeys("name", "due", "tags");
    assertThat(data.get("due")).isEqualTo("2026-10-23");
    assertThat(data.get("tags")).isEqualTo(parse("[\"a\"]"));
  }

  private JsonNode parse(String text) {
    try {
      return json.readTree(text);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }
}
