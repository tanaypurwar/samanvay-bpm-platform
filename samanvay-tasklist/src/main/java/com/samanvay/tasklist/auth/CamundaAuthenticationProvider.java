package com.samanvay.tasklist.auth;

import com.samanvay.tasklist.config.TasklistProperties;
import com.samanvay.tasklist.engine.CamundaClient;
import com.samanvay.tasklist.engine.EngineException;
import com.samanvay.tasklist.engine.dto.AuthenticationResultDto;
import com.samanvay.tasklist.engine.dto.GroupInfoDto;
import com.samanvay.tasklist.engine.dto.UserProfileDto;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Signs users in against the engine's identity service (Camunda users and groups). Passwords are only
 * forwarded to the engine for verification and never stored.
 */
@Component
public class CamundaAuthenticationProvider implements AuthenticationProvider {

  private static final Logger LOG = LoggerFactory.getLogger(CamundaAuthenticationProvider.class);

  private final CamundaClient engine;
  private final TasklistProperties properties;

  public CamundaAuthenticationProvider(CamundaClient engine, TasklistProperties properties) {
    this.engine = engine;
    this.properties = properties;
  }

  @Override
  public Authentication authenticate(Authentication authentication) {
    String username = authentication.getName();
    String password = String.valueOf(authentication.getCredentials());
    if (username == null || username.isBlank() || password.isEmpty()) {
      throw new BadCredentialsException("Missing credentials");
    }

    AuthenticationResultDto result;
    try {
      result = engine.verifyCredentials(username, password);
    } catch (EngineException e) {
      // usually a misconfigured service account: the engine refused the verification call itself
      LOG.warn("Engine refused credential verification: status={} type={} message={}", e.getStatus(), e.getType(),
          e.getMessage());
      throw new BadCredentialsException("Credentials rejected by engine", e);
    }
    if (result == null || !result.authenticated()) {
      throw new BadCredentialsException("Invalid username or password");
    }

    String userId = result.authenticatedUser() != null ? result.authenticatedUser() : username;
    UserProfileDto profile = engine.getUserProfile(userId);
    GroupInfoDto groupInfo = engine.getGroups(userId);
    List<String> groups = groupInfo == null || groupInfo.groups() == null
        ? List.of()
        : groupInfo.groups().stream().map(GroupInfoDto.Group::id).toList();
    boolean admin = groups.stream().anyMatch(properties.adminGroups()::contains);

    TasklistUser user = new TasklistUser(userId,
        profile == null ? null : profile.firstName(),
        profile == null ? null : profile.lastName(),
        profile == null ? null : profile.email(),
        groups, admin);

    List<GrantedAuthority> authorities = new ArrayList<>();
    groups.forEach(g -> authorities.add(new SimpleGrantedAuthority(TasklistUser.GROUP_AUTHORITY_PREFIX + g)));
    if (admin) {
      authorities.add(new SimpleGrantedAuthority(TasklistUser.ADMIN_AUTHORITY));
    }
    return UsernamePasswordAuthenticationToken.authenticated(user, null, authorities);
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
