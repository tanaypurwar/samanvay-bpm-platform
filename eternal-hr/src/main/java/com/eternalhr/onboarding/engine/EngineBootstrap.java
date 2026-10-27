package com.eternalhr.onboarding.engine;

import com.eternalhr.onboarding.config.EternalHrProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * On startup, deploys this application's processes and forms ({@code classpath:processes/**}) to the engine, and
 * in demo mode creates the role groups and users. The engine may start later than this app, so it retries in the
 * background until it succeeds.
 */
@Component
public class EngineBootstrap {

  public static final String DEPLOYMENT_NAME = "eternal-hr";
  static final String RESOURCE_ROOT = "processes/";

  /** Groups the BPMN refers to, and the demo user created for each. */
  static final List<Role> ROLES = List.of(
      new Role("territory-managers", "Territory Managers", "tm.demo", "Tara", "Menon"),
      new Role("reporting-managers", "Reporting Managers", "rm.demo", "Rahul", "Mehta"),
      new Role("country-heads", "Country Heads", "ch.demo", "Chitra", "Hegde"),
      new Role("project-managers", "Project Managers", "pm.demo", "Pranav", "Mishra"),
      new Role("hr-team", "HR Team", "hr.demo", "Harini", "Rao"));

  record Role(String groupId, String groupName, String userId, String firstName, String lastName) {
  }

  private static final Logger LOG = LoggerFactory.getLogger(EngineBootstrap.class);
  private static final Duration RETRY_EVERY = Duration.ofSeconds(15);

  private final EngineClient engine;
  private final EternalHrProperties properties;

  public EngineBootstrap(EngineClient engine, EternalHrProperties properties) {
    this.engine = engine;
    this.properties = properties;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void start() {
    Thread thread = new Thread(this::runUntilDone, "engine-bootstrap");
    thread.setDaemon(true);
    thread.start();
  }

  private void runUntilDone() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        deployProcesses();
        if (properties.demo() != null && properties.demo().seedUsers()) {
          seedDemoUsers();
        }
        return;
      } catch (RuntimeException e) {
        LOG.warn("Engine not ready ({}); retrying in {}s", e.getMessage(), RETRY_EVERY.toSeconds());
        try {
          Thread.sleep(RETRY_EVERY.toMillis());
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }

  void deployProcesses() {
    Map<String, byte[]> resources = loadResources();
    engine.deploy(DEPLOYMENT_NAME, resources).ifPresentOrElse(
        id -> LOG.info("Deployed {} resources as deployment {}", resources.size(), id),
        () -> LOG.info("Processes unchanged; nothing deployed"));
  }

  /** Every file under processes/, named by its path below it (e.g. forms/rm-review.form.json). */
  static Map<String, byte[]> loadResources() {
    try {
      Map<String, byte[]> resources = new TreeMap<>();
      for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath*:" + RESOURCE_ROOT + "**/*.*")) {
        String url = resource.getURL().toString();
        String name = url.substring(url.lastIndexOf(RESOURCE_ROOT) + RESOURCE_ROOT.length());
        try (InputStream in = resource.getInputStream()) {
          resources.put(name, in.readAllBytes());
        }
      }
      if (resources.isEmpty()) {
        throw new IllegalStateException("No process resources found under " + RESOURCE_ROOT);
      }
      return resources;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  void seedDemoUsers() {
    String password = properties.demo().password();
    if (password == null || password.isBlank()) {
      LOG.warn("Demo users not created: eternal-hr.demo.password is empty");
      return;
    }
    for (Role role : ROLES) {
      if (!engine.groupExists(role.groupId())) {
        engine.createGroup(role.groupId(), role.groupName());
      }
      if (!engine.userExists(role.userId())) {
        engine.createUser(role.userId(), role.firstName(), role.lastName(), role.userId() + "@eternal-hr.demo", password);
        engine.addMember(role.groupId(), role.userId());
        LOG.info("Created demo user {} in {}", role.userId(), role.groupId());
      }
    }
  }
}
