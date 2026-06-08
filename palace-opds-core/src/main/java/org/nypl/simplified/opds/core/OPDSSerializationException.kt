package org.nypl.simplified.opds.core

import java.io.IOException

/**
 * The type of errors raised during attempts to serialize OPDS objects.
 */
class OPDSSerializationException : IOException {
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
