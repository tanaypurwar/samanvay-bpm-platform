package com.samanvay.tasklist.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samanvay.tasklist.engine.dto.VariableValueDto;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CamundaClientTest {

  private static final String BASE = "http://engine/engine-rest";

  private MockRestServiceServer server;
  private CamundaClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    server = MockRestServiceServer.bindTo(builder).build();
    client = new CamundaClient(builder.build(), new ObjectMapper());
  }

  @Test
  void completeSendsTypedVariables() {
    server.expect(once(), requestTo(BASE + "/task/t1/complete"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("{\"variables\":{\"decision\":{\"value\":\"approve\",\"type\":\"String\"}}}"))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));

    client.complete("t1", Map.of("decision", VariableValueDto.of("approve", "String")));

    server.verify();
  }

  @Test
  void completeIsNeverRetried() {
    server.expect(once(), requestTo(BASE + "/task/t1/complete"))
        .andRespond(withException(new IOException("connection reset")));

    assertThatThrownBy(() -> client.complete("t1", Map.of())).isInstanceOf(EngineUnavailableException.class);
    server.verify();
  }

  @Test
  void readsAreRetriedOnce() {
    server.expect(twice(), requestTo(BASE + "/task/t1"))
        .andRespond(withException(new IOException("timeout")));

    assertThatThrownBy(() -> client.getTask("t1")).isInstanceOf(EngineUnavailableException.class);
    server.verify();
  }

  @Test
  void engineErrorsCarryTypeAndStatus() {
    server.expect(requestTo(BASE + "/task/t1/claim"))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
            .body("{\"type\":\"TaskAlreadyClaimedException\",\"message\":\"Task already claimed\"}"));

    assertThatThrownBy(() -> client.claim("t1", "demo"))
        .isInstanceOfSatisfying(EngineException.class, e -> {
          assertThat(e.getStatus()).isEqualTo(500);
          assertThat(e.getType()).isEqualTo("TaskAlreadyClaimedException");
        });
  }

  @Test
  void countParsesResponse() {
    server.expect(requestTo(BASE + "/task/count"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{\"count\":42}", MediaType.APPLICATION_JSON));

    assertThat(client.countTasks(Map.of("assignee", "demo"))).isEqualTo(42);
  }
}
