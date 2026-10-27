package com.eternalhr.onboarding.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.complete;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.execute;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.externalTask;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.job;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.runtimeService;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.task;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.taskService;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.withVariables;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.junit5.ProcessEngineExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Runs the joining BPMN on an in-memory engine. External tasks are completed directly with the variables the
 * workers would return, so this checks the process logic, not the workers.
 */
@Deployment(resources = "processes/eternal-hr-joining.bpmn")
class JoiningProcessTest {

  @RegisterExtension
  static ProcessEngineExtension engine = ProcessEngineExtension.builder().build();

  ProcessEngine processEngine;

  @Test
  void approvedJoiningRunsToVerifiedEmployee() {
    ProcessInstance pi = startJoining();
    approveAll(pi);

    assertThat(pi).isWaitingAt("Task_HrStart");
    assertThat(task()).hasCandidateGroup("hr-team");
    completeAs("hr.demo", withVariables("approvalReceived", true, "supportingDocsReceived", true));

    completeExternal(pi, "ehr.joining-form.send-link",
        withVariables("joiningFormLink", "https://x/joining/t", "joiningFormReminderCycle", "R3/P2D"));
    assertThat(pi).isWaitingAt("Task_WaitJoiningForm");
    submitJoiningForm(pi);

    completeExternal(pi, "ehr.hrms.create-employee", withVariables("employeeCode", "EHR-2610-00001"));
    assertThat(pi).isWaitingAt("Task_Induction", "Task_LetterPrepare");

    // induction runs in parallel with the letter and documents
    complete(task("Task_Induction", pi), withVariables("inductionDate", new Date()));

    complete(task("Task_LetterPrepare", pi), withVariables("letterReference", "DOC-1"));
    completeExternal(pi, "ehr.letter.send-for-approval", Map.of());
    assertThat(task("Task_LetterReview", pi)).isAssignedTo("pm.demo");
    complete(task("Task_LetterReview", pi), withVariables("letterDecision", "approve"));
    completeExternal(pi, "ehr.letter.send-to-employee", Map.of());

    complete(task("Task_DocsCollect", pi), withVariables("documentsMovedToHrms", true));
    complete(task("Task_DocsReview", pi), withVariables("documentsClear", true, "documentsMatchData", true));
    complete(task("Task_DataVerify", pi), withVariables("recordVerified", true));
    completeExternal(pi, "ehr.hrms.mark-verified", Map.of());
    completeExternal(pi, "ehr.notify.joining-completed", Map.of());

    assertThat(pi).isEnded().hasPassed("EndEvent_Onboarded").hasNotPassed("EndEvent_Rejected");
  }

  @Test
  void sentBackRequestReturnsToInitiatorAndStartsApprovalAgain() {
    ProcessInstance pi = startJoining();
    completeAs("rm.demo", withVariables("rmDecision", "forward"));
    completeAs("ch.demo", withVariables("chDecision", "sendBack", "chComments", "Please fix the CTC"));

    assertThat(pi).isWaitingAt("Task_TmRevise");
    assertThat(task()).isAssignedTo("tm.demo");
    complete(task(), withVariables("proposedAnnualCtc", 700000.0, "revisionNote", "CTC corrected"));

    assertThat(pi).isWaitingAt("Task_RmReview");
    // earlier decisions are cleared so reviewers start fresh; comments stay for context
    assertThat(pi).variables().doesNotContainKeys("rmDecision", "chDecision").containsEntry("chComments", "Please fix the CTC");
    assertThat(pi).variables().containsEntry("reportingManager", "rm.demo");
  }

  @Test
  void rejectionNotifiesInitiatorAndEnds() {
    ProcessInstance pi = startJoining();
    completeAs("rm.demo", withVariables("rmDecision", "forward"));
    completeAs("ch.demo", withVariables("chDecision", "approve"));
    completeAs("pm.demo", withVariables("pmDecision", "reject", "pmComments", "Position closed"));

    completeExternal(pi, "ehr.notify.joining-rejected", Map.of());
    assertThat(pi).isEnded().hasPassed("EndEvent_Rejected");
  }

  @Test
  void employeeIsRemindedWhileTheFormIsOutstanding() {
    ProcessInstance pi = startJoining();
    approveAll(pi);
    completeAs("hr.demo", Map.of());
    completeExternal(pi, "ehr.joining-form.send-link", withVariables("joiningFormReminderCycle", "R2/PT1H"));

    execute(job("Timer_FormReminder", pi));
    completeExternal(pi, "ehr.joining-form.send-reminder", Map.of());

    // still waiting for the form, and the form still completes the step
    assertThat(pi).isWaitingAt("Task_WaitJoiningForm").hasPassed("EndEvent_ReminderSent");
    submitJoiningForm(pi);
    assertThat(pi).isWaitingAt("Task_HrmsCreate");
  }

  @Test
  void letterChangesAndDocumentIssuesLoopBackAndEscalate() {
    ProcessInstance pi = toLetterAndDocuments();

    complete(task("Task_LetterPrepare", pi), withVariables("letterReference", "DOC-1"));
    completeExternal(pi, "ehr.letter.send-for-approval", Map.of());
    complete(task("Task_LetterReview", pi), withVariables("letterDecision", "changes", "letterChanges", "DOJ moves"));
    assertThat(task("Task_LetterRevise", pi)).hasCandidateGroup("hr-team");
    complete(task("Task_LetterRevise", pi), withVariables("pmWrittenApproval", true));
    completeExternal(pi, "ehr.letter.send-for-approval", Map.of());
    complete(task("Task_LetterReview", pi), withVariables("letterDecision", "approve"));
    completeExternal(pi, "ehr.letter.send-to-employee", Map.of());

    complete(task("Task_DocsCollect", pi), Map.of());
    complete(task("Task_DocsReview", pi),
        withVariables("documentsClear", false, "documentsMatchData", true, "documentIssues", "PAN is blurred"));
    completeExternal(pi, "ehr.documents.request-correction", withVariables("documentEscalationAfter", "PT1H"));
    assertThat(pi).isWaitingAt("Task_DocsFollowUp");

    // no response in time: the PM is emailed, HR keeps following up
    execute(job("Timer_Escalate", pi));
    completeExternal(pi, "ehr.documents.escalate", Map.of());
    assertThat(pi).hasPassed("EndEvent_Escalated").isWaitingAt("Task_DocsFollowUp");

    complete(task("Task_DocsFollowUp", pi), withVariables("correctedDocumentsReceived", true));
    assertThat(pi).isWaitingAt("Task_DocsReview");
    assertThat(pi).variables().doesNotContainKeys("documentsClear", "documentsMatchData");
  }

  // helpers

  private ProcessInstance startJoining() {
    Map<String, Object> request = new HashMap<>();
    request.put("candidateName", "Asha Rao");
    request.put("candidateEmail", "asha@example.com");
    request.put("designation", "Field Executive");
    request.put("proposedAnnualCtc", 600000.0);
    request.put("initiator", "tm.demo");
    request.put("initiatorName", "Tara Menon");
    ProcessInstance pi = runtimeService().startProcessInstanceByKey("eternal-hr-joining", request);
    assertThat(pi).isWaitingAt("Task_RmReview");
    assertThat(task()).hasCandidateGroup("reporting-managers").hasName("Review joining: Asha Rao");
    return pi;
  }

  private void approveAll(ProcessInstance pi) {
    completeAs("rm.demo", withVariables("rmDecision", "forward"));
    assertThat(task()).hasCandidateGroup("country-heads");
    completeAs("ch.demo", withVariables("chDecision", "approve"));
    assertThat(task()).hasCandidateGroup("project-managers");
    completeAs("pm.demo", withVariables("pmDecision", "approve"));
    assertThat(pi).variables()
        .containsEntry("reportingManager", "rm.demo")
        .containsEntry("countryHead", "ch.demo")
        .containsEntry("projectManager", "pm.demo");
  }

  private ProcessInstance toLetterAndDocuments() {
    ProcessInstance pi = startJoining();
    approveAll(pi);
    completeAs("hr.demo", Map.of());
    completeExternal(pi, "ehr.joining-form.send-link", withVariables("joiningFormReminderCycle", "R3/P2D"));
    submitJoiningForm(pi);
    completeExternal(pi, "ehr.hrms.create-employee", withVariables("employeeCode", "EHR-2610-00002"));
    return pi;
  }

  private void submitJoiningForm(ProcessInstance pi) {
    runtimeService().createMessageCorrelation("JoiningFormSubmitted")
        .processInstanceId(pi.getId())
        .setVariable("panNumber", "ABCDE1234F")
        .correlate();
  }

  /** Claims and completes the current user task as that user, as the tasklist does. */
  private void completeAs(String userId, Map<String, Object> variables) {
    Task current = task();
    taskService().claim(current.getId(), userId);
    taskService().complete(current.getId(), variables);
  }

  private void completeExternal(ProcessInstance pi, String topic, Map<String, Object> variables) {
    var externalTask = externalTask(pi);
    assertThat(externalTask).hasTopicName(topic);
    complete(externalTask, variables);
  }
}
