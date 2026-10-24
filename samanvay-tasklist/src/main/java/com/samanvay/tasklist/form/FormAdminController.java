package com.samanvay.tasklist.form;

import com.samanvay.tasklist.auth.TasklistUser;
import com.samanvay.tasklist.form.FormAdminDtos.AuditView;
import com.samanvay.tasklist.form.FormAdminDtos.BindingRequest;
import com.samanvay.tasklist.form.FormAdminDtos.BindingView;
import com.samanvay.tasklist.form.FormAdminDtos.CreateFormRequest;
import com.samanvay.tasklist.form.FormAdminDtos.FormDetail;
import com.samanvay.tasklist.form.FormAdminDtos.FormSummary;
import com.samanvay.tasklist.form.FormAdminDtos.UpdateDraftRequest;
import com.samanvay.tasklist.form.FormAdminDtos.VersionDetail;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Form configuration administration (TRD FR-10). Restricted to admin groups in SecurityConfig. */
@RestController
@RequestMapping("/api/admin/forms")
public class FormAdminController {

  private final FormAdminService service;

  public FormAdminController(FormAdminService service) {
    this.service = service;
  }

  @GetMapping
  public List<FormSummary> list() {
    return service.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public FormDetail create(@Valid @RequestBody CreateFormRequest request, @AuthenticationPrincipal TasklistUser user) {
    return service.create(request, user.id());
  }

  @GetMapping("/{id}")
  public FormDetail get(@PathVariable Long id) {
    return service.get(id);
  }

  @PutMapping("/{id}")
  public FormDetail updateDraft(@PathVariable Long id, @Valid @RequestBody UpdateDraftRequest request,
      @AuthenticationPrincipal TasklistUser user) {
    return service.updateDraft(id, request, user.id());
  }

  @PostMapping("/{id}/publish")
  public FormDetail publish(@PathVariable Long id, @AuthenticationPrincipal TasklistUser user) {
    return service.publish(id, user.id());
  }

  @GetMapping("/{id}/versions/{versionId}")
  public VersionDetail version(@PathVariable Long id, @PathVariable Long versionId) {
    return service.getVersion(id, versionId);
  }

  @PostMapping("/{id}/bindings")
  @ResponseStatus(HttpStatus.CREATED)
  public BindingView addBinding(@PathVariable Long id, @Valid @RequestBody BindingRequest request,
      @AuthenticationPrincipal TasklistUser user) {
    return service.addBinding(id, request, user.id());
  }

  @DeleteMapping("/{id}/bindings/{bindingId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeBinding(@PathVariable Long id, @PathVariable Long bindingId,
      @AuthenticationPrincipal TasklistUser user) {
    service.removeBinding(id, bindingId, user.id());
  }

  @GetMapping("/{id}/audit")
  public List<AuditView> audit(@PathVariable Long id) {
    return service.auditTrail(id);
  }
}
