package com.eternalhr.onboarding.joining;

import com.eternalhr.onboarding.documents.EmployeeDocuments;
import com.eternalhr.onboarding.joining.JoiningFormService.Joining;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

/**
 * The new employee's joining form, opened from the emailed link. Public: the signed token in the URL is the only
 * credential, and it only grants submitting this one joining's form once. The employee can attach documents
 * (PDF or image, each with its type); they are stored with the process, where HR sees them on every task.
 */
@Controller
@RequestMapping("/joining")
public class JoiningFormController {

  private static final Logger LOG = LoggerFactory.getLogger(JoiningFormController.class);

  private final JoiningFormLinks links;
  private final JoiningFormService service;
  private final EmployeeDocuments documents;

  public JoiningFormController(JoiningFormLinks links, JoiningFormService service, EmployeeDocuments documents) {
    this.links = links;
    this.service = service;
    this.documents = documents;
  }

  @GetMapping("/{token}")
  public ModelAndView show(@PathVariable String token) {
    Optional<String> processInstanceId = links.verify(token);
    Optional<Joining> joining = processInstanceId.flatMap(service::find);
    if (joining.isEmpty()) {
      return unavailable();
    }
    JoiningForm form = new JoiningForm();
    form.setCandidateName(joining.get().candidateName());
    form.setCandidateEmail(joining.get().candidateEmail());
    form.setCandidateMobile(joining.get().candidateMobile());
    return page(token, joining.get(), form);
  }

  @PostMapping("/{token}")
  public ModelAndView submit(@PathVariable String token, @Valid @ModelAttribute("form") JoiningForm form,
      BindingResult errors,
      @RequestParam(name = "documents", required = false) List<MultipartFile> files,
      @RequestParam(name = "documentTypes", required = false) List<String> documentTypes) {
    Optional<String> processInstanceId = links.verify(token);
    Optional<Joining> joining = processInstanceId.flatMap(service::find);
    if (joining.isEmpty()) {
      return unavailable();
    }
    EmployeeDocuments.Checked checked = documents.enabled()
        ? documents.check(files, documentTypes)
        : new EmployeeDocuments.Checked(List.of(), List.of());
    boolean filesChosen = files != null && files.stream().anyMatch(f -> f != null && !f.isEmpty());
    if (errors.hasErrors() || !checked.errors().isEmpty()) {
      return retry(token, joining.get(), form, checked.errors(), filesChosen, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    List<String> stored;
    try {
      stored = documents.store(processInstanceId.get(), checked.uploads(), form.getCandidateName());
    } catch (RuntimeException e) {
      LOG.error("Could not store the joining documents of {}: {}", processInstanceId.get(), e.getMessage());
      return retry(token, joining.get(), form,
          List.of("We could not save your documents just now. Please try again in a few minutes."), filesChosen,
          HttpStatus.SERVICE_UNAVAILABLE);
    }
    if (!service.submit(processInstanceId.get(), form)) {
      documents.remove(processInstanceId.get(), stored);
      return unavailable();
    }
    ModelAndView done = new ModelAndView("joining/submitted");
    done.addObject("candidateName", form.getCandidateName());
    done.addObject("documentCount", stored.size());
    return done;
  }

  private ModelAndView page(String token, Joining joining, JoiningForm form) {
    ModelAndView page = new ModelAndView("joining/form");
    page.addObject("token", token);
    page.addObject("joining", joining);
    page.addObject("form", form);
    page.addObject("documentsEnabled", documents.enabled());
    page.addObject("documentTypes", documents.types());
    page.addObject("maxFileSizeMb", documents.maxFileSize().toMegabytes());
    page.addObject("maxFiles", documents.maxFiles());
    return page;
  }

  /** The form again, with what needs fixing. Browsers cannot keep chosen files, so ask to attach them again. */
  private ModelAndView retry(String token, Joining joining, JoiningForm form, List<String> documentErrors,
      boolean filesChosen, HttpStatus status) {
    ModelAndView page = page(token, joining, form);
    page.addObject("documentErrors", documentErrors);
    page.addObject("reattach", filesChosen);
    page.setStatus(status);
    return page;
  }

  /** Expired, altered, already used, or the joining has ended: one message, so links reveal nothing. */
  private static ModelAndView unavailable() {
    ModelAndView page = new ModelAndView("joining/unavailable");
    page.setStatus(HttpStatus.NOT_FOUND);
    return page;
  }
}
