package com.samanvay.tasklist.engine;

/** A non-successful response from the engine REST API. */
public class EngineException extends RuntimeException {

  private final int status;
  private final String type;

  public EngineException(int status, String type, String message) {
    super(message);
    this.status = status;
    this.type = type;
  }

  public int getStatus() {
    return status;
  }

  /** Engine exception type, e.g. {@code TaskAlreadyClaimedException}; may be null. */
  public String getType() {
    return type;
  }
}
