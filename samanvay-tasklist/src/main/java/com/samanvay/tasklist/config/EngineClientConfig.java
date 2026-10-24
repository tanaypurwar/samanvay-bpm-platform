package com.samanvay.tasklist.config;

import com.samanvay.tasklist.common.CorrelationIdFilter;
import org.slf4j.MDC;
import java.util.List;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class EngineClientConfig {

  @Bean
  RestClient engineRestClient(RestClient.Builder builder, EngineProperties props) {
    var settings = ClientHttpRequestFactorySettings.defaults()
        .withConnectTimeout(props.connectTimeout())
        .withReadTimeout(props.readTimeout());
    return builder
        .baseUrl(props.baseUrl())
        .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
        .defaultHeaders(h -> {
          h.setBasicAuth(props.username(), props.password());
          h.setAccept(List.of(MediaType.APPLICATION_JSON));
        })
        .requestInterceptor((request, body, execution) -> {
          String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
          if (correlationId != null) {
            request.getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
          }
          return execution.execute(request, body);
        })
        .build();
  }
}
