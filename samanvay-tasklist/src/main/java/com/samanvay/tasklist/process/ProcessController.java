package com.samanvay.tasklist.process;

import com.samanvay.tasklist.auth.TasklistUser;
import com.samanvay.tasklist.process.ProcessDtos.StartForm;
import com.samanvay.tasklist.process.ProcessDtos.StartRequest;
import com.samanvay.tasklist.process.ProcessDtos.StartableProcess;
import com.samanvay.tasklist.process.ProcessDtos.StartedProcess;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/processes")
public class ProcessController {

  private final ProcessService service;

  public ProcessController(ProcessService service) {
    this.service = service;
  }

  @GetMapping
  public List<StartableProcess> startable(@AuthenticationPrincipal TasklistUser user) {
    return service.startable(user);
  }

  @GetMapping("/{key}/start-form")
  public StartForm startForm(@PathVariable String key, @AuthenticationPrincipal TasklistUser user) {
    return service.startForm(key, user);
  }

  @PostMapping(path = "/{key}/start", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public StartedProcess start(@PathVariable String key, @RequestBody StartRequest request,
      @AuthenticationPrincipal TasklistUser user) {
    return service.start(key, request, user);
  }

  /**
   * Starts with documents: part {@code request} is the JSON start request, each {@code files} part a document,
   * and {@code documentTypes} gives each file's type in the same order.
   */
  @PostMapping(path = "/{key}/start", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public StartedProcess startWithDocuments(@PathVariable String key, @RequestPart("request") StartRequest request,
      @RequestPart(name = "files", required = false) List<MultipartFile> files,
      @RequestParam(name = "documentTypes", required = false) List<String> documentTypes,
      @AuthenticationPrincipal TasklistUser user) {
    return service.start(key, request, files == null ? List.of() : files,
        documentTypes == null ? List.of() : documentTypes, user);
  }
}
