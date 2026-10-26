package com.samanvay.tasklist.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samanvay.tasklist.auth.CamundaAuthenticationProvider;
import com.samanvay.tasklist.auth.TasklistUser;
import com.samanvay.tasklist.common.ApiError;
import com.samanvay.tasklist.common.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Session-based security for the React app: HTTP-only session cookie, CSRF token in an {@code XSRF-TOKEN}
 * cookie that the SPA echoes back in {@code X-XSRF-TOKEN}, JSON 401/403 responses for the API.
 */
@Configuration
public class SecurityConfig {

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  AuthenticationManager authenticationManager(CamundaAuthenticationProvider provider) {
    return new ProviderManager(provider);
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository,
      ObjectMapper objectMapper, ServerProperties serverProperties) throws Exception {
    // load the CSRF token on every request so the cookie is always present for the SPA
    CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
    csrfHandler.setCsrfRequestAttributeName(null);
    String sessionCookie = serverProperties.getServlet().getSession().getCookie().getName();

    http
        .csrf(csrf -> csrf
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .csrfTokenRequestHandler(csrfHandler))
        .securityContext(sc -> sc.securityContextRepository(contextRepository))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
            .requestMatchers("/actuator/health").permitAll()
            .requestMatchers("/api/admin/**").hasAuthority(TasklistUser.ADMIN_AUTHORITY)
            .requestMatchers("/api/**", "/actuator/**").authenticated()
            .anyRequest().permitAll())
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint((req, res, e) ->
                writeError(res, objectMapper, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Please sign in."))
            .accessDeniedHandler((req, res, e) ->
                writeError(res, objectMapper, HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "You are not allowed to perform this action.")))
        .logout(logout -> logout
            .logoutUrl("/api/auth/logout")
            .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
            .deleteCookies(sessionCookie == null ? "JSESSIONID" : sessionCookie))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable);
    return http.build();
  }

  private static void writeError(HttpServletResponse response, ObjectMapper mapper, HttpStatus status, String code,
      String message) throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    mapper.writeValue(response.getOutputStream(),
        new ApiError(code, message, MDC.get(CorrelationIdFilter.MDC_KEY), List.of()));
  }
}
