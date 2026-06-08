package org.nypl.simplified.opds.core

import java.io.IOException

/**
 * The type of errors raised during attempts to parse OPDS feeds.
 */
class OPDSParseException : IOException {
  /**
   * Construct an exception with no message or cause.
   */

  constructor() : super()

  /**
   * Construct an exception.
   *
   * @param message The message
   */

  constructor(message: String) : super(message)

  /**
   * Construct an exception.
   *
   * @param message The message
   * @param cause   The cause
   */

  constructor(
    message: String,
    cause: Throwable
  ) : super(message, cause)

  /**
   * Construct an exception
   *
   * @param cause The case
   */

  constructor(cause: Throwable) : super(cause)
}
