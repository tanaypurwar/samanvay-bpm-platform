package com.eternalhr.onboarding.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Checks the forms deployed with the process: each one the BPMN references exists, and each is a form the
 * Samanvay tasklist can show (JSON Schema object, required fields declared, field order complete).
 */
class FormsTest {

  private static final Path PROCESSES = Path.of("src/main/resources/processes");
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void everyReferencedFormExistsAndEveryFormIsUsed() throws IOException {
    String bpmn = Files.readString(PROCESSES.resolve("eternal-hr-joining.bpmn"));
    Matcher m = Pattern.compile("samanvay:deployment:(forms/[a-z-]+\\.form\\.json)").matcher(bpmn);
    Set<String> referenced = new TreeSet<>();
    while (m.find()) {
      referenced.add(m.group(1));
    }
    Set<String> present = new TreeSet<>();
    try (Stream<Path> files = Files.list(PROCESSES.resolve("forms"))) {
      files.forEach(f -> present.add("forms/" + f.getFileName()));
    }
    assertThat(present).isEqualTo(referenced);
  }

  @Test
  void formsAreWellFormed() throws IOException {
    List<String> problems = new ArrayList<>();
    try (Stream<Path> files = Files.list(PROCESSES.resolve("forms"))) {
      for (Path file : files.toList()) {
        JsonNode form = json.readTree(file.toFile());
        JsonNode schema = form.path("schema");
        JsonNode properties = schema.path("properties");
        if (form.path("name").asText().isBlank()) {
          problems.add(file + ": no name");
        }
        if (!"object".equals(schema.path("type").asText()) || !properties.isObject()) {
          problems.add(file + ": schema is not an object with properties");
        }
        schema.path("required").forEach(r -> {
          if (!properties.has(r.asText())) {
            problems.add(file + ": required field " + r.asText() + " is not declared");
          }
        });
        Set<String> order = new TreeSet<>();
        form.path("uiSchema").path("ui:order").forEach(o -> order.add(o.asText()));
        Set<String> declared = new TreeSet<>();
        properties.fieldNames().forEachRemaining(declared::add);
        if (!order.equals(declared)) {
          problems.add(file + ": ui:order does not list exactly the declared fields");
        }
      }
    }
    assertThat(problems).isEmpty();
  }
}
