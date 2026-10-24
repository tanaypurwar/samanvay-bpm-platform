package com.samanvay.tasklist.form.entity;

public enum FormStatus {
  /** Never published; tasks cannot use it yet. */
  DRAFT,
  /** At least one version is published; the latest published version is served to tasks. */
  PUBLISHED
}
