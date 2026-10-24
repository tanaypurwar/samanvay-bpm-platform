package com.samanvay.tasklist.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the React app for client-side routes (anything that is not an API call or a static file). */
@Controller
public class SpaForwardingController {

  private static final String SEGMENT = "(?!api$|actuator$)[^.]*";

  @GetMapping({
      "/{p1:" + SEGMENT + "}",
      "/{p1:" + SEGMENT + "}/{p2:[^.]*}",
      "/{p1:" + SEGMENT + "}/{p2:[^.]*}/{p3:[^.]*}"
  })
  public String forward() {
    return "forward:/index.html";
  }
}
