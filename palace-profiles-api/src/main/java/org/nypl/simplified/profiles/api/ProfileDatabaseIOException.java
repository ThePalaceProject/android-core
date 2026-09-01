package org.nypl.simplified.profiles.api;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;

/**
 * An exception caused by an underlying I/O exception.
 */

public final class ProfileDatabaseIOException extends ProfileDatabaseException {

  /**
   * Construct an exception.
   *
   * @param message The exception message
   */

  public ProfileDatabaseIOException(
      final String message,
      final IOException exception) {
    super(message, Collections.singletonList(Objects.requireNonNull(exception, "exception")));
  }
}
