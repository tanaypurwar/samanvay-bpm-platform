package com.eternalhr.onboarding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Eternal HR process application. It runs beside the Samanvay engine and talks to it only through the REST API:
 * it deploys its own processes and forms, works on external tasks, and hosts the public employee joining form.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class EternalHrApplication {

  public static void main(String[] args) {
    SpringApplication.run(EternalHrApplication.class, args);
  }
}
