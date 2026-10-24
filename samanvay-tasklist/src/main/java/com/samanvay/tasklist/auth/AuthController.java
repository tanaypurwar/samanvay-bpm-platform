package com.samanvay.tasklist.auth;

import com.samanvay.tasklist.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Session login for the React app. Logout is handled by Spring Security at POST /api/auth/logout. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository securityContextRepository;
  private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

  public AuthController(AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository) {
    this.authenticationManager = authenticationManager;
    this.securityContextRepository = securityContextRepository;
  }

  public record LoginRequest(@NotBlank String username, @NotBlank String password) {
  }

  @PostMapping("/login")
  public TasklistUser login(@Valid @RequestBody LoginRequest login, HttpServletRequest request,
      HttpServletResponse response) {
    Authentication authentication;
    try {
      authentication = authenticationManager.authenticate(
          UsernamePasswordAuthenticationToken.unauthenticated(login.username(), login.password()));
    } catch (AuthenticationException e) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password.");
    }

    // protect against session fixation
    if (request.getSession(false) != null) {
      request.changeSessionId();
    }
    SecurityContext context = contextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    contextHolder.setContext(context);
    securityContextRepository.saveContext(context, request, response);
    return (TasklistUser) authentication.getPrincipal();
  }

  @GetMapping("/me")
  public TasklistUser me(@AuthenticationPrincipal TasklistUser user) {
    return user;
  }
}
