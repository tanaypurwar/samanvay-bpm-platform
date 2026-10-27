package com.samanvay.tasklist.config;

import com.samanvay.tasklist.config.TasklistProperties.Branding;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, so the sign-in page can show the brand too. */
@RestController
public class BrandingController {

  private final TasklistProperties properties;

  public BrandingController(TasklistProperties properties) {
    this.properties = properties;
  }

  @GetMapping("/api/branding")
  public Branding branding() {
    return properties.branding();
  }
}
