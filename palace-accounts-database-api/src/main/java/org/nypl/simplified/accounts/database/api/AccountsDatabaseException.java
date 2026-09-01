package org.nypl.simplified.accounts.database.api;

import java.util.List;
import java.util.Objects;

/**
 * An exception that indicates that an operation on the accounts database failed.
 */

public abstract class AccountsDatabaseException extends Exception {

  private final List<Exception> causes;

  /**
   * Construct an exception.
   *
   * @param message The exception message
   * @param causes  The list of causes
   */

  public AccountsDatabaseException(
      final String message,
      final List<Exception> causes) {
    super(Objects.requireNonNull(message, "Message"));
    this.causes = Objects.requireNonNull(causes, "Causes");
  }

  /**
   * Construct an exception.
   *
   * @param message The exception message
   *                @param cause The primary cause
   * @param causes  The list of causes
   */

  public AccountsDatabaseException(
      final String message,
      final Exception cause,
      final List<Exception> causes) {
    super(Objects.requireNonNull(message, "Message"),
        Objects.requireNonNull(cause, "Cause"));
    this.causes = Objects.requireNonNull(causes, "Causes");
  }

  /**
   * @return The list of exceptions raised that caused this exception
   */

  public final List<Exception> causes() {
    return this.causes;
  }
}
