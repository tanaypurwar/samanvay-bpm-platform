package com.samanvay.tasklist.form;

import com.samanvay.tasklist.form.entity.FormVersion;

/**
 * The form to show for a task or a process start, from either source:
 * <ul>
 *   <li>a version published in the tasklist's form admin ({@code versionId} set)</li>
 *   <li>a form file deployed with the BPMN ({@code fromDeployment}); it is as immutable as the deployment, so it
 *       needs no version id</li>
 * </ul>
 */
public record ResolvedForm(
    Long versionId,
    String formKey,
    String name,
    Integer version,
    String schemaJson,
    String uiSchemaJson,
    boolean fromDeployment) {

  public static ResolvedForm of(FormVersion version) {
    return new ResolvedForm(version.getId(), version.getFormDefinition().getFormKey(),
        version.getFormDefinition().getName(), version.getVersion(), version.getSchemaJson(),
        version.getUiSchemaJson(), false);
  }
}
