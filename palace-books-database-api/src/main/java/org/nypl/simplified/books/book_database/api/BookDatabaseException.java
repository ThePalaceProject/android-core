package org.nypl.simplified.books.book_database.api;

import java.util.List;
import java.util.Objects;

/**
 * An exception that indicates that an operation on a book database failed.
 */

public final class BookDatabaseException extends Exception {

  private final List<Exception> causes;

  /**
   * Construct an exception.
   *
   * @param message The exception message
   * @param causes  The list of causes
   */

  public BookDatabaseException(
    final String message,
    final List<Exception> causes) {
    super(Objects.requireNonNull(message, "Message"));
    this.causes = Objects.requireNonNull(causes, "Causes");
  }

  /**
   * @return The list of exceptions raised that caused this exception
   */

  public List<Exception> causes() {
    return this.causes;
  }
}
