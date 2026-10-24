package com.samanvay.tasklist.auth;

import java.io.Serializable;
import java.util.List;

/** The signed-in user, resolved from the engine's identity service and kept in the HTTP session. */
public record TasklistUser(
    String id,
    String firstName,
    String lastName,
    String email,
    List<String> groups,
    boolean admin) implements Serializable {

  public static final String ADMIN_AUTHORITY = "ROLE_TASKLIST_ADMIN";
  public static final String GROUP_AUTHORITY_PREFIX = "GROUP_";

  public TasklistUser {
    groups = groups == null ? List.of() : List.copyOf(groups);
  }

  public String displayName() {
    String full = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    return full.isEmpty() ? id : full;
  }
}
