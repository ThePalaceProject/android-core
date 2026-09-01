package org.nypl.simplified.profiles.api;

import org.nypl.simplified.accounts.database.api.AccountsDatabaseException;

import java.util.Collections;
import java.util.Objects;

/**
 * An exception caused by an underlying accounts database exception.
 */

public final class ProfileDatabaseAccountsException extends ProfileDatabaseException {

  /**
   * Construct an exception.
   *
   * @param message The exception message
   */

  public ProfileDatabaseAccountsException(
      final String message,
      final AccountsDatabaseException exception) {
    super(message, Collections.singletonList(Objects.requireNonNull(exception, "exception")));
  }
}
