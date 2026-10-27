package com.samanvay.tasklist.process;

import com.fasterxml.jackson.databind.JsonNode;
import com.samanvay.tasklist.auth.TasklistUser;
import com.samanvay.tasklist.common.ApiException;
import com.samanvay.tasklist.document.DocumentDtos.DocumentOptions;
import com.samanvay.tasklist.document.DocumentService;
import com.samanvay.tasklist.engine.CamundaClient;
import com.samanvay.tasklist.engine.dto.ProcessDefinitionDto;
import com.samanvay.tasklist.engine.dto.ProcessInstanceDto;
import com.samanvay.tasklist.engine.dto.VariableValueDto;
import com.samanvay.tasklist.form.FormResolver;
import com.samanvay.tasklist.form.FormSubmissions;
import com.samanvay.tasklist.form.ResolvedForm;
import com.samanvay.tasklist.process.ProcessDtos.StartForm;
import com.samanvay.tasklist.process.ProcessDtos.StartRequest;
import com.samanvay.tasklist.process.ProcessDtos.StartableProcess;
import com.samanvay.tasklist.process.ProcessDtos.StartedProcess;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Starting processes from the tasklist. A user may start the latest version of a process whose BPMN names them,
 * or one of their groups, in {@code camunda:candidateStarterUsers/Groups} and that is startable in a tasklist.
 * The tasklist calls the engine with its service account, so this check is enforced here.
 */
@Service
public class ProcessService {

  /** Set on every instance started here: the id of the user who started it. */
  public static final String INITIATOR = "initiator";
  /** The display name of {@link #INITIATOR}, for showing in forms and emails. */
  public static final String INITIATOR_NAME = "initiatorName";

  private static final Logger LOG = LoggerFactory.getLogger(ProcessService.class);

  private final CamundaClient engine;
  private final FormResolver formResolver;
  private final FormSubmissions submissions;
  private final DocumentService documents;

  public ProcessService(CamundaClient engine, FormResolver formResolver, FormSubmissions submissions,
      DocumentService documents) {
    this.engine = engine;
    this.formResolver = formResolver;
    this.submissions = submissions;
    this.documents = documents;
  }

  public List<StartableProcess> startable(TasklistUser user) {
    return startableDefinitions(user).stream()
        .map(d -> new StartableProcess(d.key(), d.name() == null ? d.key() : d.name(), d.description(), d.version()))
        .toList();
  }

  public StartForm startForm(String processKey, TasklistUser user) {
    ProcessDefinitionDto definition = requireStartable(processKey, user);
    String processName = processName(definition);
    DocumentOptions documentOptions = documents.options(definition);
    Optional<ResolvedForm> resolved = formResolver.resolveStartForm(definition);
    if (resolved.isEmpty()) {
      return new StartForm(definition.key(), processName, null, null, null, null, null, null, documentOptions);
    }
    ResolvedForm form = resolved.get();
    return new StartForm(definition.key(), processName, form.versionId(), form.formKey(), form.name(),
        form.version(), submissions.read(form.schemaJson()), submissions.read(form.uiSchemaJson()), documentOptions);
  }

  public StartedProcess start(String processKey, StartRequest request, TasklistUser user) {
    return start(processKey, request, List.of(), List.of(), user);
  }

  /**
   * Starts the process with documents attached. Form and files are checked before the process starts; if the
   * documents cannot be stored afterwards, the new instance is deleted again so nothing half-done remains.
   */
  public StartedProcess start(String processKey, StartRequest request, List<MultipartFile> files,
      List<String> documentTypes, TasklistUser user) {
    ProcessDefinitionDto definition = requireStartable(processKey, user);
    JsonNode formData = submissions.objectOrEmpty(request == null ? null : request.formData());
    Long formVersionId = request == null ? null : request.formVersionId();

    Map<String, VariableValueDto> variables = new LinkedHashMap<>();
    Optional<ResolvedForm> resolved = formResolver.resolveStartForm(definition);
    if (resolved.isEmpty()) {
      if (!formData.isEmpty()) {
        throw ApiException.badRequest("NO_FORM", "This process has no start form; it cannot accept form data.");
      }
    } else {
      ResolvedForm form = resolved.get();
      if (!form.fromDeployment() && formVersionId != null && !formVersionId.equals(form.versionId())) {
        throw ApiException.conflict("FORM_CHANGED", "The start form has changed. Reload it.");
      }
      variables.putAll(submissions.toVariables(form, formData));
    }
    List<DocumentService.Upload> uploads = documents.check(files, documentTypes, definition);
    // set last, so form data cannot impersonate another user
    variables.put(INITIATOR, VariableValueDto.of(user.id(), "String"));
    variables.put(INITIATOR_NAME, VariableValueDto.of(user.displayName(), "String"));

    ProcessInstanceDto instance = engine.startProcess(definition.id(), variables);
    String instanceId = instance == null ? null : instance.id();
    if (!uploads.isEmpty() && instanceId != null) {
      try {
        documents.store(instanceId, uploads, processName(definition), user);
      } catch (RuntimeException e) {
        LOG.warn("Documents for new instance {} could not be stored; deleting the instance", instanceId);
        try {
          engine.deleteProcessInstance(instanceId);
        } catch (RuntimeException cleanup) {
          LOG.error("Could not delete process instance {} after failing to store its documents", instanceId, cleanup);
        }
        throw e;
      }
    }
    return new StartedProcess(instanceId, uploads.size());
  }

  private static String processName(ProcessDefinitionDto definition) {
    return definition.name() == null ? definition.key() : definition.name();
  }

  private ProcessDefinitionDto requireStartable(String processKey, TasklistUser user) {
    return startableDefinitions(user).stream()
        .filter(d -> d.key().equals(processKey))
        .findFirst()
        .orElseThrow(() -> ApiException.notFound("This process does not exist or you may not start it."));
  }

  private List<ProcessDefinitionDto> startableDefinitions(TasklistUser user) {
    List<ProcessDefinitionDto> definitions = engine.getStartableProcessDefinitions(user.id());
    return definitions == null ? List.of() : definitions;
  }
}
