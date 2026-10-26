package com.samanvay.tasklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samanvay.tasklist.engine.CamundaClient;
import com.samanvay.tasklist.engine.dto.AuthenticationResultDto;
import com.samanvay.tasklist.engine.dto.GroupInfoDto;
import com.samanvay.tasklist.engine.dto.UserProfileDto;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The mobile app's sign-in flow over real HTTP: a client that keeps cookies (like the phone's cookie store)
 * but cannot read them fetches the CSRF token from /api/auth/csrf, then signs in and calls the API.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:tasklist-native;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class NativeClientSignInTest {

  @LocalServerPort
  int port;

  @Autowired
  ObjectMapper json;

  @MockitoBean
  CamundaClient engine;

  @Test
  void signsInWithTokenFromCsrfEndpoint() throws Exception {
    when(engine.verifyCredentials("demo", "demo")).thenReturn(new AuthenticationResultDto("demo", true));
    when(engine.getUserProfile("demo")).thenReturn(new UserProfileDto("demo", "Demo", "User", null));
    when(engine.getGroups("demo")).thenReturn(new GroupInfoDto(List.of()));
    HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

    JsonNode csrf = json.readTree(send(client, get("/api/auth/csrf")).body());
    assertThat(csrf.path("headerName").asText()).isEqualTo("X-XSRF-TOKEN");

    HttpResponse<String> withoutToken = send(client, post("/api/auth/login", null, null));
    assertThat(withoutToken.statusCode()).isEqualTo(403);

    HttpResponse<String> login = send(client,
        post("/api/auth/login", csrf.path("headerName").asText(), csrf.path("token").asText()));
    assertThat(login.statusCode()).isEqualTo(200);
    assertThat(json.readTree(login.body()).path("id").asText()).isEqualTo("demo");

    assertThat(send(client, get("/api/auth/me")).statusCode()).isEqualTo(200);
  }

  private HttpRequest get(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
  }

  private HttpRequest post(String path, String header, String token) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"demo\",\"password\":\"demo\"}"));
    if (header != null) {
      builder.header(header, token);
    }
    return builder.build();
  }

  private static HttpResponse<String> send(HttpClient client, HttpRequest request) throws Exception {
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
