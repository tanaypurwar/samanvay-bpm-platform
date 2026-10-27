package com.eternalhr.onboarding.documents;

import java.util.Optional;

/**
 * Accepts PDFs and images (JPEG, PNG) only, recognised by their content rather than the name or the type the
 * browser claims. Same rules as the Samanvay tasklist, which previews these files.
 */
public final class FileCheck {

  public static final String PDF = "application/pdf";
  public static final String JPEG = "image/jpeg";
  public static final String PNG = "image/png";

  private FileCheck() {
  }

  public static Optional<String> contentType(byte[] data) {
    if (data == null) {
      return Optional.empty();
    }
    if (startsWith(data, 0x25, 0x50, 0x44, 0x46, 0x2D)) { // %PDF-
      return Optional.of(PDF);
    }
    if (startsWith(data, 0xFF, 0xD8, 0xFF)) {
      return Optional.of(JPEG);
    }
    if (startsWith(data, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
      return Optional.of(PNG);
    }
    return Optional.empty();
  }

  /** A safe display name: no path, no control characters, at most 150 characters. */
  public static String fileName(String original, String contentType) {
    String name = original == null ? "" : original;
    name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
    name = name.replaceAll("[\\p{Cntrl}\"]", "").trim();
    if (name.isEmpty()) {
      name = "document" + switch (contentType == null ? "" : contentType) {
        case PDF -> ".pdf";
        case JPEG -> ".jpg";
        case PNG -> ".png";
        default -> "";
      };
    }
    return name.length() > 150 ? name.substring(name.length() - 150) : name;
  }

  private static boolean startsWith(byte[] data, int... prefix) {
    if (data.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if ((data[i] & 0xFF) != prefix[i]) {
        return false;
      }
    }
    return true;
  }
}
