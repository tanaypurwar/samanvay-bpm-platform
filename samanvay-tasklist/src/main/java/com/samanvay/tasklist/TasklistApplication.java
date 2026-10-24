package com.samanvay.tasklist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TasklistApplication {

  public static void main(String[] args) {
    SpringApplication.run(TasklistApplication.class, args);
  }
}
